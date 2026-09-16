package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.simulation.api.ContactPoint
import de.drehtuer.dinfinity.simulation.api.CorrectionLadder
import de.drehtuer.dinfinity.simulation.api.DieDiagnostic
import de.drehtuer.dinfinity.simulation.api.FaceReader
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Reading
import de.drehtuer.dinfinity.simulation.api.RestTracker
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
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
  /**
   * What writes down where the dice hit something.
   *
   * A reading of the roll and never an input to it: it is handed the states
   * this loop has already read and is asked for nothing back, so the same seed
   * comes to the same faces with it listening and with it [ImpactRecorder.deaf]
   * (`docs/physics-and-rendering.md`, "Impacts").
   */
  private val recorder: ImpactRecorder =
    ImpactRecorder(spec.dice.map { it.die.material.sizeMm * spec.dieScale }),
) {
  private val diceCount = spec.dice.size
  private val tracker = RestTracker(diceCount)
  private val rethrowCount = IntArray(diceCount)
  private val forced = BooleanArray(diceCount)

  /** Which dice have been read and taken off the table. */
  private val counted = BooleanArray(diceCount)

  /** The face each counted die came to rest on, kept as it is counted. */
  private val countedFace = IntArray(diceCount) { NOT_YET }

  /** And where it was standing when it was, which the next throw is aimed around. */
  private val countedAt = arrayOfNulls<RestingPlace>(diceCount)

  private var rethrows = 0

  /**
   * How wide each die is at the scale the capacity rule threw it
   * (`docs/tables.md`).
   *
   * The footprint the debug overlay draws, and the same number the impact
   * recorder is built with — computed once here rather than per snapshot,
   * because it cannot change during a roll.
   */
  private val dieWidthsMm = spec.dice.map { it.die.material.sizeMm * spec.dieScale }

  private var states: List<DieState> = if (diceCount == 0) emptyList() else world.readStates()
  private var result: SimulationOutcome? = null

  /**
   * The dice as of the last step taken, in throw order.
   *
   * Read-only, and the only thing the loop lets out while a roll is in
   * progress. A renderer is shown these and can do nothing with them but draw
   * them (`docs/physics-and-rendering.md`).
   */
  val dice: List<DieState> get() = states

  /** How many fixed steps the roll has taken so far. */
  val stepsTaken: Int get() = tracker.stepsTaken

  /**
   * The shake that threw these dice, as the loop actually received it.
   *
   * Accumulated rather than taken from [spec] because for a shake-driven throw
   * the spec has nothing in it: the dice are spawned the moment the shake is
   * confirmed and every moment of it arrives afterwards, through [shake]. The
   * driver is where those moments land, so the driver is where the record of
   * the throw is ([ShakeDriver.recorded]).
   */
  val drivenBy: List<ShakeSample> get() = shake.recorded()

  /**
   * Everywhere the dice have hit something so far, in step order.
   *
   * The counterpart of [drivenBy]: that is what the hand did to the roll, this
   * is what the roll did back. Like the dice themselves it is read-only, and
   * whoever reads it can play it and nothing else.
   */
  val impacts: List<Impact> get() = recorder.recorded()

  /**
   * Takes one more moment of the shake that is throwing these dice.
   *
   * The only thing that reaches a roll in progress from outside, and it is the
   * player's hand rather than anything the app decided. It cannot touch a die:
   * a shake changes which way down is, for one step, and the solver does the
   * rest ([ShakeDriver]).
   */
  fun shake(sample: ShakeSample) {
    shake.add(sample)
  }

  /**
   * Which dice have been read and taken off the table, by index.
   *
   * What a renderer needs in order to stop drawing them: a counted die is out
   * of play and the floor it stood on is free, so a die thrown afterwards may
   * land exactly there. Drawing both would be two dice in one place
   * (`docs/TODO.md`, Step 5.5).
   */
  val countedOut: List<Boolean> get() = counted.toList()

  /** What the throw came to, once [advance] has said there is nothing left. */
  fun outcome(): SimulationOutcome = requireNotNull(result) { "the roll has not finished yet" }

  /**
   * The roll as a developer sees it, right now
   * (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * Built when somebody asks rather than kept up to date, so a roll nobody is
   * watching does none of this work — which is what lets the overlay be a
   * setting that is off on every install and costs nothing there.
   *
   * Every field is read off state the loop already keeps. Nothing is recorded
   * *for* this, nothing branches on whether it has been called, and it returns
   * a snapshot rather than a view onto the arrays — so the same seed comes to
   * the same faces with an overlay on it and with none
   * ([RollDiagnostics], and `RollDiagnosticsTest` beside this file).
   */
  fun diagnostics(): RollDiagnostics =
    RollDiagnostics(
      steps = tracker.stepsTaken,
      dice =
        states.mapIndexed { index, state ->
          DieDiagnostic(
            index = index,
            position = state.position,
            acrossMm = dieWidthsMm[index],
            stillForSteps = tracker.stillSteps(index),
            atRest = tracker.isAtRest(index),
            touchingFloor = state.touchingFloor,
            touchingWall = state.touchingWall,
            supportedByDie = state.supportedByDie,
            countedOut = counted[index],
            rethrows = rethrowCount[index],
          )
        },
      corrections = 0,
      rethrows = rethrows,
      forcedSettles = forced.count { it },
      postRestCorrections = 0,
      contacts = recentContacts,
    )

  /**
   * The most recent contacts, marked at the die that made them.
   *
   * The solver's contact manifold never crosses the bridge — a point and a
   * normal per contact per step is a wire format nobody needs for a roll
   * (decision 52) — so what is drawn is the die's own position at the step it
   * hit something, which is the mark a person hunting for stacking is looking
   * for anyway. A die that has moved since is marked where it is now, and that
   * is honest for the frame it is drawn on.
   */
  private val recentContacts: List<ContactPoint>
    get() =
      recorder
        .recorded()
        .takeLast(RollDiagnostics.MAX_CONTACTS)
        .mapNotNull { impact ->
          states.getOrNull(impact.dieIndex)?.let { state ->
            ContactPoint(
              stepIndex = impact.stepIndex,
              dieIndex = impact.dieIndex,
              position = state.position,
              struck = impact.struck,
              strength = impact.strength,
            )
          }
        }

  /** Runs the throw to its end and reports what the dice did. */
  fun run(): SimulationOutcome {
    @Suppress("ControlFlowWithEmptyBody")
    while (advance()) {
      // Every step is the same step; there is nothing to do between them.
    }
    return outcome()
  }

  /**
   * Moves the roll on by the smallest amount it moves by, and says whether
   * there is anything left to do.
   *
   * Usually that is one fixed step. Once every die is at rest it is instead
   * the end of a settle phase, where a die that finished cocked or stacked is
   * thrown again (rung 3) and the roll goes round once more — which takes no
   * simulated time, so [stepsTaken] does not move and a caller drawing the
   * result can tell the two apart.
   *
   * Splitting the roll this way is what lets it be stepped from a frame clock
   * ([de.drehtuer.dinfinity.simulation.api.FrameClock]) without changing it:
   * the steps, their order and everything decided between them are the same
   * whether a caller asks for them one at a time or all at once, which is the
   * whole of why power-saving mode is the same roll (`docs/architecture.md`,
   * goal 1).
   */
  fun advance(): Boolean {
    if (result != null) return false
    if (diceCount == 0 || nothingLeftToStep()) return closeOutOrRethrow()

    val step = tracker.stepsTaken
    shake.advance(step)
    world.setGravity(shake.gravity)
    world.step(SettleRule.TIMESTEP_SECONDS)

    states = world.readStates()
    // Listened to before anything is decided, and from the states that were
    // already read: an impact is a reading of the step just taken, not a thing
    // the step waits for.
    recorder.step(step, states, shake.gravity.length)
    tracker.step(states.map(DieState::motion))
    return true
  }

  /**
   * True when there is no step left to take.
   *
   * Two things have to agree and there is a third that overrules both. Dice
   * that look still while the phone is still being shaken are not a roll that
   * is over — they are a roll caught at the top of a swing — so the settle rule
   * is the dice's answer and [ShakeDriver.stillShaking] is the hand's
   * (`docs/physics-and-rendering.md`, "Shake input").
   *
   * The cap is asked first and on its own, because it is the safety valve
   * rather than an opinion about the throw. A hand holds a roll open; it does
   * not get to hold one open past twelve seconds, which is what a shake longer
   * than the roll would otherwise do — the steps would go on being counted and
   * the throw would end as an outcome `SimulationOutcome` refuses to describe.
   */
  private fun nothingLeftToStep(): Boolean =
    tracker.capReached() || (tracker.finished() && !shake.stillShaking(tracker.stepsTaken))

  /**
   * The end of a settle phase: either the roll is over, or a die has to be
   * thrown again and there is another phase to come.
   *
   * Re-throws come out of the same step budget as the rest of the roll — the
   * 12-second cap is a cap on the throw, not on each attempt at it.
   */
  private fun closeOutOrRethrow(): Boolean {
    if (tracker.capReached()) tracker.stillMoving().forEach { forced[it] = true }

    if (!tracker.capReached() && countAndClear(states)) {
      states = world.readStates()
      return true
    }

    // Whatever is left has run out of its throws or out of the roll's twelve
    // seconds, and is read where it lies. Step 5 asserts this never happens.
    readWhatIsLeft(states)

    result =
      SimulationOutcome(
        faces = countedFace.indices.associateWith { countedFace[it] },
        // Where they stopped, for the throw an explosion or a reroll adds
        // next: it is aimed at the floor this one left clear and drawn among
        // the dice standing on the rest of it, and neither is something the
        // screen could work out for itself
        // (`docs/physics-and-rendering.md`).
        restingAt =
          countedAt.indices.associateWith { index ->
            countedAt[index] ?: RestingPlace(states[index].position, states[index].orientation)
          },
        steps = tracker.stepsTaken,
        // Zero, and not by luck. There is no correction left in this loop to
        // count: a die is either read and lifted off or thrown again where the
        // player can watch, and neither is a hand on a die
        // (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice").
        corrections = 0,
        rethrows = rethrows,
        // Per die, not per way of failing: a die that was still moving when
        // the cap fired *and* was cocked when it was read is one die the
        // simulation had to finish for, not two.
        forcedSettles = forced.count { it },
        postRestCorrections = 0,
        // A die counted and taken off the table cannot be stood on, so this
        // counts only what was left standing on something when the roll ran
        // out of throws — which is the number Step 5.5 wants at zero.
        stackedAtRest = states.filterIndexed { index, _ -> !counted[index] }.count(DieState::supportedByDie),
        deepestDiePenetrationMm = world.deepestDiePenetrationMm,
      )
    return false
  }

  /**
   * Count what can be counted, take it off the table, throw the rest again —
   * and say whether anything was thrown.
   *
   * **This is the whole of how a heap is cleared, and there is no hand in it.**
   * A die that came to rest showing a face is read, and that reading is its
   * answer for the rest of the roll; it is then lifted off the table, which
   * leaves the floor it was standing on free for the dice that still have to
   * land. A die that finished cocked or standing on another one is thrown
   * again, visibly, onto a table that now has more room on it than it had —
   * which is what a player does when the dice land in a pile, and it is the
   * reason this converges instead of being tuned.
   *
   * Taking a counted die out of play is not *moving* it: its face has already
   * been read and nothing about it can change again. That is the line the
   * honest rule draws (`docs/physics-and-rendering.md`).
   */
  private fun countAndClear(states: List<DieState>): Boolean {
    var thrown = false
    states.forEachIndexed { index, state ->
      if (counted[index]) return@forEachIndexed
      val die = spec.dice[index].die
      val reading = FaceReader.read(die, state.orientation)

      // A die standing on another one is not counted even when its face is
      // perfectly readable: it is resting on something that is about to be
      // taken away, and a reading taken from a die that is about to fall is
      // not a reading of anything.
      if (reading is Reading.Face && !state.supportedByDie) {
        countedFace[index] = reading.index
        countedAt[index] = RestingPlace(state.position, state.orientation)
        counted[index] = true
        world.remove(index)
        return@forEachIndexed
      }

      if (rethrowCount[index] >= MAX_RETHROWS) return@forEachIndexed
      world.respawn(index, layout.rethrowPlacement(index, rethrowCount[index]))
      // The speed it has the moment after this is the re-throw rather than a
      // contact, and a sound for the app's own hand is the one noise a player
      // must never hear.
      recorder.rethrown(index)
      // The tracker still has it down as settled from a moment ago, and a die
      // in mid-air is not settled.
      tracker.rethrown(index)
      rethrowCount[index]++
      rethrows++
      thrown = true
    }
    return thrown
  }

  /**
   * Reads whatever never got counted, where it lies.
   *
   * A die reaching here has been thrown again as often as it is going to be,
   * or the roll has run out of its twelve seconds — which is the safety valve
   * firing rather than the mechanism working. It is counted as a forced settle
   * so the harness sees it, and it reports the face that came nearest, because
   * at that point the alternative is a roll with no answer at all. Step 5
   * asserts this never happens.
   */
  private fun readWhatIsLeft(states: List<DieState>) {
    states.forEachIndexed { index, state ->
      if (counted[index]) return@forEachIndexed
      countedFace[index] =
        when (val reading = FaceReader.read(spec.dice[index].die, state.orientation)) {
          is Reading.Face -> reading.index
          is Reading.Cocked -> {
            forced[index] = true
            reading.nearestIndex
          }
        }
    }
  }

  companion object {
    /**
     * How often one die may be thrown again before the roll gives up on it.
     *
     * It used to be three, sized for a table with every other die still on it:
     * letting one die loop would spend the whole step budget while the other
     * seventy-nine sat there. That is no longer the trade. A die thrown again
     * now lands on a table the counted dice have left, so the budget it spends
     * is spent on the only dice that still need it.
     *
     * Eight because five is the most that sixteen seeds of twenty dice under a
     * hard sideways shake ever needed — measured on the Pixel 10a — and the
     * real backstop is the twelve-second cap rather than this. It is here to
     * bound a die that is going nowhere, not to ration a roll.
     */
    const val MAX_RETHROWS: Int = 8

    /** A die that has not been read yet. Never reaches an outcome. */
    private const val NOT_YET = -1

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
