package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.simulation.api.CorrectionLadder
import de.drehtuer.dinfinity.simulation.api.FaceReader
import de.drehtuer.dinfinity.simulation.api.Reading
import de.drehtuer.dinfinity.simulation.api.RestTracker
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * One roll, from the first step to the reading — the correction ladder made
 * into a loop (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked
 * dice").
 *
 * It is a loop over a [PhysicsWorld] and nothing more, so the engine
 * underneath can be Jolt on a phone or a fake on the JVM, and the rules below
 * are the same rules either way. That is the whole reason this is not written
 * in C++.
 *
 * The rule it exists to keep is the one that matters most in the app:
 * **nothing touches a die that has come to rest.** Every correction is gated
 * on [CorrectionLadder.mayTouch], which is false the moment a die is at rest,
 * and a correction that slipped through anyway would be counted in
 * [SimulationOutcome.postRestCorrections] — a number the device harness
 * asserts is zero (`docs/TODO.md`, Step 5.5).
 */
class RollLoop(
  private val spec: ThrowSpec,
  private val world: PhysicsWorld,
  private val layout: SpawnLayout,
  private val shake: ShakeDriver,
) {
  private val diceCount = spec.dice.size
  private val tracker = RestTracker(diceCount)
  private val rethrowCount = IntArray(diceCount)
  private val troubleFor = IntArray(diceCount)
  private val biased = BooleanArray(diceCount)
  private val forced = BooleanArray(diceCount)

  private var corrections = 0
  private var rethrows = 0
  private var postRestCorrections = 0

  /** Runs the throw to its end and reports what the dice did. */
  fun run(): SimulationOutcome {
    if (diceCount == 0) return SimulationOutcome(faces = emptyMap())

    var states = settle()
    // A die that came to rest cocked or on top of another is thrown again —
    // visibly, one die, while the others stay where they are — and then the
    // roll has to settle once more. Re-throws come out of the same step budget
    // as the rest of the roll: the 12-second cap is a cap on the throw, not on
    // each attempt at it.
    while (!tracker.capReached() && rethrowCocked(states)) {
      states = settle()
    }

    return SimulationOutcome(
      faces = readFaces(states),
      steps = tracker.stepsTaken,
      corrections = corrections,
      rethrows = rethrows,
      // Per die, not per way of failing: a die that was still moving when the
      // cap fired *and* was cocked when it was read is one die the simulation
      // had to finish for, not two.
      forcedSettles = forced.count { it },
      postRestCorrections = postRestCorrections,
    )
  }

  /** Steps until every die is at rest or the cap fires. */
  private fun settle(): List<DieState> {
    var states = world.readStates()
    while (!tracker.finished()) {
      val step = tracker.stepsTaken
      shake.advance(step)
      world.setGravity(shake.gravity)
      world.step(SettleRule.TIMESTEP_SECONDS)

      states = world.readStates()
      tracker.step(states.map(DieState::motion))
      correct(states, step)
    }

    if (tracker.capReached()) tracker.stillMoving().forEach { forced[it] = true }
    return states
  }

  /**
   * Rung 2: a small seeded bias for a die that is settling into trouble.
   *
   * The gate is [CorrectionLadder.mayTouch] and it is asked for every die,
   * every time, before anything else is considered. A die that is already at
   * rest fails it, and that is the only thing standing between this app and an
   * invisible hand.
   */
  private fun correct(
    states: List<DieState>,
    step: Int,
  ) {
    states.forEachIndexed { index, state ->
      if (!TroubleCheck.isInTrouble(state, spec.dice[index].die)) {
        troubleFor[index] = 0
        return@forEachIndexed
      }
      troubleFor[index]++

      // A tumbling die passes through plenty of orientations that would read
      // as cocked if it stopped in them, and it is not going to stop in them.
      // Waiting for trouble to *persist* is the difference between "heading
      // for a cocked orientation" and "happens to be at an angle".
      if (troubleFor[index] < TROUBLE_STEPS_BEFORE_BIAS) return@forEachIndexed
      // One nudge, not a hand on the die. If a bias of the order of the energy
      // the die still has does not shake it loose, more of them will not — that
      // is what rung 3 is for, and a die pushed every step for half a second is
      // a die being steered.
      if (biased[index]) return@forEachIndexed

      val atRest = tracker.isAtRest(index)
      // A die in trouble that may not be touched is left in trouble. That is
      // not a failure of the ladder, it is the ladder: rung 3 will throw it
      // again where the player can see, and nothing reaches it before then.
      if (!CorrectionLadder.mayTouch(state.motion, atRest)) return@forEachIndexed

      world.applyBias(index, CorrectionLadder.bias(spec.seed, index, step, state.motion))
      biased[index] = true
      // Counted per die, not per nudge: the budget in `CorrectionLadder` is a
      // share of the dice that needed correcting at all.
      corrections++
      // Unreachable while `mayTouch` keeps its promise, and here because that
      // promise lives in another module. If it is ever broken this is what
      // turns a silent invisible hand into a number the device harness fails
      // on (`docs/TODO.md`, Step 5.5).
      if (atRest) postRestCorrections++
    }
  }

  /**
   * Rung 3: throws the dice that finished cocked or stacked, and says whether
   * any were.
   *
   * Not a nudge and not a snap to the nearest face. The die is picked up and
   * dropped back on the table where the player can watch it happen, which is
   * what a player does and is the only correction that is still a roll.
   */
  private fun rethrowCocked(states: List<DieState>): Boolean {
    var thrown = false
    states.forEachIndexed { index, state ->
      val die = spec.dice[index].die
      val cocked = FaceReader.read(die, state.orientation) is Reading.Cocked
      if (!cocked && !state.supportedByDie) return@forEachIndexed
      if (rethrowCount[index] >= MAX_RETHROWS) return@forEachIndexed
      world.respawn(index, layout.rethrowPlacement(index, rethrowCount[index]))
      // The tracker still has it down as settled from a moment ago, and a die
      // in mid-air is not settled.
      tracker.rethrown(index)
      // A re-thrown die is a fresh throw and gets a fresh chance to be helped.
      troubleFor[index] = 0
      biased[index] = false
      rethrowCount[index]++
      rethrows++
      thrown = true
    }
    return thrown
  }

  /**
   * What each die says, now that it has stopped.
   *
   * A die that is still cocked here has been thrown again as often as it is
   * going to be and the roll has run out of its twelve seconds — which is the
   * safety valve firing, not the ladder working. It is counted as a forced
   * settle so the harness sees it, and it reports the face that came nearest,
   * because at that point the alternative is a roll with no answer at all.
   * Step 5 asserts this never happens.
   */
  private fun readFaces(states: List<DieState>): Map<Int, Int> =
    states.indices.associateWith { index ->
      when (val reading = FaceReader.read(spec.dice[index].die, states[index].orientation)) {
        is Reading.Face -> reading.index
        is Reading.Cocked -> {
          forced[index] = true
          reading.nearestIndex
        }
      }
    }

  companion object {
    /**
     * How often one die may be thrown again before the roll gives up on it.
     *
     * A die that has been re-thrown this many times is not unlucky, it is a
     * physics bug, and letting it loop would spend the whole step budget on
     * one die while the other seventy-nine sit there.
     */
    const val MAX_RETHROWS: Int = 3

    /**
     * How long a die has to stay in trouble before it is worth touching —
     * about a twentieth of a second.
     *
     * Short enough that the die still has the speed a bias is measured
     * against, long enough that a die merely tumbling through an awkward angle
     * is left alone.
     */
    const val TROUBLE_STEPS_BEFORE_BIAS: Int = 6
  }
}

/**
 * The two questions rung 2 acts on: is this die standing on another one, and
 * would it read as cocked if it stopped now.
 *
 * "Leaning on a wall", the third case the physics document names, is the one
 * where [DieState.touchingWall] and a cocked reading are both true — so it
 * needs no rule of its own, only the two below.
 */
internal object TroubleCheck {
  fun isInTrouble(
    state: DieState,
    die: Die,
  ): Boolean = state.supportedByDie || FaceReader.read(die, state.orientation) is Reading.Cocked
}
