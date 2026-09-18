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
import de.drehtuer.dinfinity.simulation.api.Tumble

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

  /** Which dice have been read. */
  private val counted = BooleanArray(diceCount)

  /**
   * Which dice have been taken off the table, which is not the same list.
   *
   * A die is read when the table has settled and lifted off only when that
   * same pass is about to throw something again — so a roll that needed no
   * re-throw lifts nothing and leaves every die where it landed
   * ([countAndClear]).
   */
  private val lifted = BooleanArray(diceCount)

  /** The face each counted die came to rest on, kept as it is counted. */
  private val countedFace = IntArray(diceCount) { NOT_YET }

  /** And where it was standing when it was, which the next throw is aimed around. */
  private val countedAt = arrayOfNulls<RestingPlace>(diceCount)

  private var rethrows = 0

  /**
   * How far each die turns once it is on the table.
   *
   * A reading, like [RollDiagnostics]: nothing here reaches the solver, and
   * the roll comes to the same faces whether or not it is counted
   * ([Tumble]).
   */
  private val tumble = Tumble(diceCount)

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

  /** True once the roll has taken longer than a roll should ([SettleRule.HARD_CAP_SECONDS]). */
  val outOfTime: Boolean get() = tracker.outOfTime()

  /**
   * True when the roll gave up: it ran too long and the dice never stopped.
   *
   * **There is no outcome, and that is the point.** The dice are not read off
   * whatever face they were nearest and handed back as a result — that is
   * making a number up, and it is what the twelve-second cap used to do. The
   * roll says it could not finish and the player is offered the throw again
   * (`docs/physics-and-rendering.md`).
   */
  var stalled: Boolean = false
    private set

  /** The dice that never came to rest and were never read. */
  val unsettled: List<Int> get() = counted.indices.filterNot { counted[it] }

  /** Which dice have been read, by index. */
  val countedOut: List<Boolean> get() = counted.toList()

  /**
   * Which dice have been taken off the table, by index.
   *
   * What a renderer needs in order to stop drawing them, and it is a shorter
   * list than [countedOut]. A lifted die is out of play and the floor it stood
   * on is free, so a die thrown afterwards may land exactly there and drawing
   * both would be two dice in one place. Nothing is lifted unless something is
   * about to be thrown again, so a roll that settled first time keeps every
   * die on the table for the player to look at
   * (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice").
   */
  val liftedOut: List<Boolean> get() = lifted.toList()

  /**
   * The faces read so far, by die index.
   *
   * What the screen follows while a roll is going: dice leave the table as
   * they are counted, so the running total is the only thing left to watch
   * (`docs/TODO.md`, Step 5.5). A die that has not been counted is not in it.
   */
  val countedSoFar: Map<Int, Int>
    get() = counted.indices.filter { counted[it] }.associateWith { countedFace[it] }

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

  /**
   * Runs the throw to its end and reports what the dice did.
   *
   * **This is the backstop for the runs nobody is watching**, and the only one
   * left. A roll on screen is stepped a frame at a time and runs until its
   * dice have stopped, however long that takes — a player tired of waiting
   * leaves the screen, and that gives it up. Nothing here has a screen to
   * leave: the harness throws thousands of these and a roll that never settled
   * would hang the run rather than fail it.
   *
   * It **fails** rather than answering. A roll whose dice never stopped has no
   * faces to report, and reading them off whatever they were nearest is making
   * a number up — which used to be exactly what the twelve-second cap did
   * (`.claude/CLAUDE.md`, and [SettleRule.HARD_CAP_SECONDS]).
   */
  fun run(): SimulationOutcome =
    runOrGiveUp() ?: error(
      "the dice had not settled after ${SettleRule.HARD_CAP_SECONDS} s, so there is no roll to report",
    )

  /**
   * The same, for a caller that would rather be told than thrown at.
   *
   * Null when the roll gave up, with [unsettled] saying which dice never
   * stopped. What the screen uses, and what a measurement uses: "this throw
   * did not settle" is a result worth counting, and a test that crashed on it
   * could not count it.
   */
  fun runOrGiveUp(): SimulationOutcome? {
    @Suppress("ControlFlowWithEmptyBody")
    while (advance()) {
      // Every step is the same step; there is nothing to do between them.
    }
    return if (stalled) null else outcome()
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
   *
   * Four ways out, and each is a different thing that can be true of a roll:
   * it is over, it is finishing now, it has run too long, or it took a step.
   * Folding any two together would hide which one happened.
   */
  @Suppress("ReturnCount")
  fun advance(): Boolean {
    if (result != null || stalled) return false
    // The close-out is asked first, so a roll that would have finished on this
    // very step is allowed to — and the backstop second, because a hand holds a
    // roll open for as long as it shakes, which is what a shake is for, but not
    // for ever and not past what `SimulationOutcome` will describe.
    if (diceCount == 0 || nothingLeftToStep()) return closeOutOrRethrow()
    if (outOfTime) return giveUp()

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
    states.forEachIndexed { index, state ->
      tumble.step(
        index = index,
        orientation = state.orientation,
        touching = state.touchingFloor || state.touchingWall || state.supportedByDie,
      )
    }
    return true
  }

  /**
   * Gives the roll up, and says there is nothing more to step.
   *
   * What could be read is read first: a die at rest showing a face is an
   * answer the roll has, and giving up on it would throw away a die that did
   * settle along with the ones that did not. What is left is offered back to
   * whoever is watching (`docs/physics-and-rendering.md`).
   */
  private fun giveUp(): Boolean {
    countAndClear(states, throwTheRest = false)
    stalled = true
    return false
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
   * There is no third thing any more. A roll used to end when it ran out of
   * time as well, and every die still moving was then read off whatever face
   * it was nearest — a made-up answer to a throw that had not finished. A roll
   * runs until its dice have stopped now, and one that cannot be finished is
   * given up rather than answered ([SettleRule.HARD_CAP_SECONDS]).
   */
  private fun nothingLeftToStep(): Boolean = tracker.finished() && !shake.stillShaking(tracker.stepsTaken)

  /**
   * The end of a settle phase: either the roll is over, or dice have to be
   * thrown again and there is another phase to come.
   *
   * A die is thrown again as often as it takes. There used to be a budget of
   * three, sized for a table with every other die still on it; a die thrown
   * again now lands on a table the counted dice have left, so the budget was
   * rationing the only dice that still needed the room.
   */
  private fun closeOutOrRethrow(): Boolean {
    if (countAndClear(states)) {
      states = world.readStates()
      return true
    }

    result =
      SimulationOutcome(
        faces = countedFace.indices.associateWith { countedFace[it] },
        // Where they stopped, for the throw an explosion or a reroll adds
        // next: it is aimed at the floor this one left clear and drawn among
        // the dice standing on the rest of it, and neither is something the
        // screen could work out for itself
        // (`docs/physics-and-rendering.md`).
        //
        // **The dice that were lifted off are not in it**, because they are
        // not on the table any more. A die is lifted exactly to free the
        // floor it stood on for a die being thrown again, so that floor is
        // where the re-thrown die may well have landed — and handing the
        // lifted one on would tell the next throw two untrue things at once:
        // draw a die where another die is standing, and treat as taken the
        // room the lift made. That is what put two dice in one place when an
        // exploding roll came back for its next die ([liftedOut]).
        restingAt =
          countedAt.indices
            .filterNot { lifted[it] }
            .associateWith { index ->
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
        // What says the dice rolled rather than were placed: the middle die's
        // turns after it first touched the table ([Tumble]).
        medianTurnsAfterLanding = tumble.medianTurns,
        // A die counted and taken off the table cannot be stood on, so this
        // counts only what was left standing on something when the roll ran
        // out of throws — which is the number Step 5.5 wants at zero.
        stackedAtRest = states.filterIndexed { index, _ -> !counted[index] }.count(DieState::supportedByDie),
        deepestDiePenetrationMm = world.deepestDiePenetrationMm,
      )
    return false
  }

  /**
   * Count what can be counted, throw the rest again — taking the counted dice
   * off the table only if there is a rest to throw — and say whether anything
   * was thrown.
   *
   * **This is the whole of how a heap is cleared, and there is no hand in it.**
   * A die that came to rest showing a face is read, and that reading is its
   * answer for the rest of the roll. A die that finished cocked or standing on
   * another one is thrown again, visibly, onto a table the counted dice have
   * left — which is what a player does when the dice land in a pile, and it is
   * the reason this converges instead of being tuned.
   *
   * **The lift is the price of a re-throw, not of being counted.** Reading a
   * die and taking it off the table used to be one act, which meant a roll
   * that settled first time cleared itself off the screen: the dice were read,
   * lifted, and the player was left looking at empty felt. They are two acts
   * now. Every die is read first; only if that same pass is going to throw
   * something again does anything come off, and then all of it comes off at
   * once, before a single placement is aimed at the floor it freed.
   *
   * That ordering is what makes leaving them safe. Counting happens when the
   * table has settled ([closeOutOrRethrow]), so no die is ever read while
   * another is still moving and nothing can knock a reading out of date. The
   * one thing that could put a second die where a counted one stands is a
   * re-throw, and a re-throw is exactly what lifts them.
   *
   * Taking a counted die out of play is not *moving* it: its face has already
   * been read and nothing about it can change again. That is the line the
   * honest rule draws (`docs/physics-and-rendering.md`).
   */
  private fun countAndClear(
    states: List<DieState>,
    throwTheRest: Boolean = true,
  ): Boolean {
    val throwAgain = mutableListOf<Int>()
    states.forEachIndexed { index, state ->
      if (counted[index]) return@forEachIndexed
      val die = spec.dice[index].die
      val reading = FaceReader.read(die, state.orientation)

      // A die standing on another one is not counted even when its face is
      // perfectly readable: it is resting on something that is about to be
      // taken away, and a reading taken from a die that is about to fall is
      // not a reading of anything.
      // At rest as well as readable. A die in mid-air can be showing a face
      // perfectly squarely and is not showing it to anybody — that only
      // matters when the roll gives up, because every other path here waits
      // until the dice have stopped.
      if (reading is Reading.Face && !state.supportedByDie && tracker.isAtRest(index)) {
        countedFace[index] = reading.index
        countedAt[index] = RestingPlace(state.position, state.orientation)
        counted[index] = true
        // Its turning is a fact about the throw that was read; the rest of
        // the roll belongs to the dice still going.
        tumble.settled(index)
        return@forEachIndexed
      }

      if (throwTheRest) throwAgain += index
    }
    if (throwAgain.isEmpty()) return false

    // Everything that has been read comes off before anything is aimed, so a
    // re-thrown die is placed against the table the old one measured: the
    // floor the counted dice were standing on, free.
    counted.indices.forEach { index ->
      if (counted[index] && !lifted[index]) {
        lifted[index] = true
        world.remove(index)
      }
    }
    throwAgain.forEach { index ->
      world.respawn(index, layout.rethrowPlacement(index, rethrowCount[index]))
      // The speed it has the moment after this is the re-throw rather than a
      // contact, and a sound for the app's own hand is the one noise a player
      // must never hear.
      recorder.rethrown(index)
      // The tracker still has it down as settled from a moment ago, and a die
      // in mid-air is not settled.
      tracker.rethrown(index)
      // The turns it made on the throw nobody will read are not this roll's.
      tumble.rethrown(index)
      rethrowCount[index]++
      rethrows++
    }
    return true
  }

  companion object {
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
