package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.SavedRollSource
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.notation.AddedDice
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.DicePicker
import de.drehtuer.dinfinity.core.notation.Formula
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.ParseResult
import de.drehtuer.dinfinity.core.notation.PickableDie
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollEvaluator
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.core.notation.RunningScore
import de.drehtuer.dinfinity.core.notation.Scoring
import de.drehtuer.dinfinity.core.notation.ThrowOutcome
import de.drehtuer.dinfinity.simulation.api.CapacityVerdict
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.DieAtRest
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * What the roll screen is showing, and what typing or tapping does to it
 * (`docs/TODO.md`, Step 4.1).
 *
 * No Compose, no Android, no engine: a formula goes in, a throw comes out, a
 * throw's faces go back in and a result comes out. The screen is a rendering
 * of [state] and the two buttons that call [type] and [throwDice]
 * (`docs/architecture.md`, decision 40, one level up from the physics).
 *
 * The one thing it must never do is produce a number. Every total here comes
 * from faces the simulation reported; there is no branch that scores a roll
 * some other way, not for an invalid formula, not for a refused one, not when
 * the engine will not open (`.claude/CLAUDE.md`).
 *
 * The class carries a function-count suppression. Nine of its methods are the
 * things a screen can do to a formula — type it, tap a die onto it, throw it,
 * read it, round it again, put it away — and the other three are the one answer
 * [settled] gives, which is either a total or the next die to throw
 * ([Landed]). Folding any of them together would hide that split rather than
 * remove it.
 */
@Suppress("TooManyFunctions")
class RollMachine(
  private val catalog: DiceCatalog,
  /** The table every throw from here lands on, and the one the tray draws. */
  val geometry: TableGeometry,
  /**
   * What a pinned table looks like, and what the app's own default looks like
   * when nothing is pinned (`docs/tables.md`, "Selecting a table").
   *
   * A function of the pin rather than one fixed look, because which table a
   * throw lands on is a property of the *throw*: a saved roll can pin its own
   * and so can the group it lives in, and tapping one has to put its table
   * under the dice. Resolving a pin needs the installed sets, which is the
   * caller's to know and not this module's — `null` in, and out comes whatever
   * the app default resolves to.
   */
  private val look: (TablePin?) -> TableLook,
  /**
   * Which way division rounds when a throw lands
   * (`docs/dice-notation.md`, "Division rounding").
   *
   * The player's setting, read when the screen opens. The result sheet can
   * still re-round the throw in front of them from the same dice, and that
   * override is deliberately not remembered.
   */
  private val defaultRounding: Rounding = Rounding.Default,
  private val outside: Outside = Outside(),
) {
  private val seeds: () -> Long get() = outside.seeds
  private val clock: () -> Long get() = outside.clock

  /**
   * A formula that resolved and the plan it resolved to, held together because
   * neither is any use without the other. Non-null exactly while [state] is
   * [RollState.Ready] — which is what lets every guard below be a single
   * question rather than three that cannot all be answered.
   */
  private class Prepared(
    val formula: Formula,
    val plan: RollPlan,
    val scale: Double,
    val diceCount: Int,
  )

  /**
   * The same, plus the throw it was thrown as. Non-null exactly while rolling.
   *
   * The whole spec rather than its seed, because a throw that has landed is
   * described by the spec that would replay it, and that spec is this one with
   * the shake written back into it ([FinishedThrow.thrown]).
   */
  private class InFlight(
    val prepared: Prepared,
    val spec: ThrowSpec,
  ) {
    /**
     * Every die at rest in the tray, in the order they stopped.
     *
     * It grows as the roll adds to itself. What it is for is the two things
     * outside the solver that an added die needs: the clear floor to drop it
     * onto, and the picture to drop it into. No throw ever puts a body in the
     * world for one of these — a die that has come to rest is finished
     * (`docs/physics-and-rendering.md`).
     */
    val down: MutableList<DieAtRest> = mutableListOf()

    /** The faces of the dice the roll has added, in the order it asked for them. */
    val added: MutableList<Int> = mutableListOf()

    /** The die in the air now, or null while the first throw is the one in the air. */
    var adding: DieInstance? = null

    /** The faces of the first throw, which is the only throw the plan describes. */
    var faces: Map<Int, Int> = emptyMap()

    /** The shake that threw the first throw. An added die is thrown by nobody. */
    var drivenBy: List<ShakeSample> = emptyList()

    /** Counted over every throw the roll took, because they are all one roll. */
    var rethrows: Int = 0
    var forcedSettles: Int = 0

    /** Where the dice of one throw stopped, added to [down] in throw order. */
    fun cameToRest(
      thrown: List<DieInstance>,
      outcome: SimulationOutcome,
    ) {
      thrown.forEachIndexed { position, instance ->
        outcome.restingAt[position]?.let { place -> down += DieAtRest(instance.die, place) }
      }
      rethrows += outcome.rethrows
      forcedSettles += outcome.forcedSettles
    }

    /** Where everything already down is, which is all [ClearSpace] needs. */
    fun taken(): List<Vector3> = down.map { it.at.position }
  }

  private var prepared: Prepared? = null
  private var inFlight: InFlight? = null
  private var scored: Pair<Formula, RollResult>? = null

  /** What the screen draws. */
  var state: RollState = RollState.Empty
    private set

  /** The formula as typed, valid or not. */
  var text: String = ""
    private set

  /**
   * The saved roll [text] was put there by, or null when somebody typed it.
   *
   * Carried so a throw can be recorded as that roll's — otherwise every throw
   * belongs to nothing, and the saved-roll statistics screen has nothing to
   * show (`docs/statistics.md`, per saved roll and per group).
   */
  var cameFrom: SavedRollSource? = null
    private set

  /**
   * The look the dice land on, now.
   *
   * The pin the throw came with, and the app default when it came with none —
   * which is every throw somebody typed. It changes when [type] changes where
   * the formula came from, so the tray has to be told again rather than asked
   * once (`RollPresenter`).
   */
  val table: TableLook get() = look(cameFrom?.tablePin)

  /** Which set the picker row is offering, and what is on it ([Picker]). */
  private val picker = Picker(catalog)

  /** The dice the picker row offers (`design/dInfinity.dc.html`, option 1h). */
  val pickable: List<PickableDie> get() = picker.dice

  /** Which set they come from (`design/dInfinity.dc.html`, option 4a). */
  val pickingFrom: String get() = picker.from

  /** Every set that has dice to offer, for the chooser. */
  val choosableSets: List<DiceSet> get() = picker.sets

  /**
   * How many of each of [pickable] the formula is asking for.
   *
   * Recomputed with the formula rather than on demand: it is one parse for the
   * whole row, and it is read on every recomposition.
   */
  var counts: Map<PickableDie, Int> = emptyMap()
    private set

  /** How many dice sets are installed, which the first-launch screen counts. */
  val sets: Int get() = catalog.installed.size

  /**
   * The formula field changed.
   *
   * Validated on every keystroke, which is why `core/notation` has no storage
   * behind it (`docs/architecture.md`, decision 31). An error keeps its range
   * so the screen can put a squiggle under the part that is wrong rather than
   * under the whole field (`design/dInfinity.dc.html`, options 6f and 9c).
   */
  fun type(
    typed: String,
    from: SavedRollSource? = null,
  ) {
    text = typed
    // Any edit drops it, which is the point of the default: a formula that was
    // Fireball and has since been typed over, or had a die tapped onto it, is
    // not Fireball's throw any more (`docs/statistics.md`).
    cameFrom = from
    prepared = null
    inFlight = null
    scored = null

    state =
      when (val parsed = FormulaParser.parse(typed)) {
        is ParseResult.Failed -> if (typed.isBlank()) RollState.Empty else RollState.Invalid(parsed.error)
        is ParseResult.Parsed -> planned(parsed.formula)
      }
    counts = DicePicker.counts(typed, pickable)
  }

  /**
   * A tap on the picker row: one more of [die].
   *
   * Goes through [type], which is not a shortcut but the point. A tap is an
   * edit to the formula, so it has to do everything an edit does — re-validate,
   * re-check the table's capacity, and abandon a throw that is in the air
   * (`docs/architecture.md`, "Screens and the states behind them").
   */
  fun add(die: PickableDie) {
    type(DicePicker.add(text, die))
  }

  /** A long press on the picker row: one fewer of [die], or none at all. */
  fun remove(die: PickableDie) {
    type(DicePicker.remove(text, die))
  }

  /**
   * Offer the picker row a different set's dice (design option `4a`).
   *
   * The formula is left exactly as it is. What is already written was written
   * on purpose, and a chooser that rewrote `3d6` into `brass:3d6` because
   * somebody looked at another set would be editing a roll nobody asked it to
   * edit. What changes is what the *next* tap writes.
   *
   * The counts are recomputed, because the badges belong to the dice on the
   * row and the row has just changed.
   */
  fun pickFrom(setId: String) {
    if (picker.choose(setId)) counts = DicePicker.counts(text, pickable)
  }

  /**
   * Throws the dice, or says why it will not.
   *
   * Hands back the throw for whoever is going to run it, and `null` when there
   * is nothing to throw — an empty field, a formula that does not read, one the
   * table cannot hold, or a throw already in the air. A refusal happens
   * **before a single body is created**, which is what makes `500d6` a message
   * rather than a hang (`docs/tables.md`).
   *
   * @param shake the recorded motion of the phone, or empty for a tap.
   */
  fun throwDice(shake: List<ShakeSample> = emptyList()): ThrowSpec? {
    val ready = prepared ?: return null
    prepared = null

    val spec =
      ThrowSpec(
        dice = ready.plan.dice,
        geometry = geometry,
        table = table,
        seed = seeds(),
        dieScale = ready.scale,
        shake = shake,
      )
    inFlight = InFlight(ready, spec)
    state = RollState.Rolling(ready.diceCount)
    return spec
  }

  /**
   * The dice have stopped. [outcome] is what they came to, and this is where it
   * becomes a total — or where the roll asks for one more die.
   *
   * Faces nobody asked for are ignored: only a throw that was made can land,
   * and a screen one stray callback away from a total with no roll behind it
   * would not be worth the rest of this file.
   *
   * The faces are the simulation's, unexamined and unadjusted. Scoring is
   * arithmetic over them — keep, drop, explode, modifiers, rounding — and
   * nothing in it can change what a die landed on.
   *
   * **A roll is not always over when its dice stop.** An explosion and a reroll
   * each add a die, and how many they add is not knowable until the first ones
   * land. So this hands back either the finished throw or the *next* throw to
   * make, and whoever is throwing comes back here when it lands
   * ([Landed], `docs/dice-notation.md`, "Evaluation", step 5).
   *
   * @param drivenBy every moment of the shake that reached the roll, in step
   *   order. A tap-to-roll throw has none, and so has every added die: nobody
   *   shakes the phone at a die the app threw for them. It is taken here rather
   *   than remembered from [throwDice] because for a shake there is nothing to
   *   remember at that point: the dice are spawned when the shake is confirmed
   *   and the moments arrive afterwards, so only the roll itself knows what
   *   actually threw them (`docs/physics-and-rendering.md`, "Shake input").
   */
  fun settled(
    outcome: SimulationOutcome,
    drivenBy: List<ShakeSample> = emptyList(),
    rounding: Rounding = defaultRounding,
  ): Landed? {
    val flight = inFlight ?: return null

    when (val adding = flight.adding) {
      null -> {
        flight.faces = outcome.faces
        flight.drivenBy = drivenBy
        flight.cameToRest(flight.prepared.plan.dice, outcome)
      }
      else -> {
        flight.added +=
          requireNotNull(outcome.faces[0]) { "the added ${adding.die.id} was thrown and reported no face" }
        flight.cameToRest(listOf(adding), outcome)
      }
    }
    flight.adding = null

    val scoring =
      RunningScore.of(
        formula = flight.prepared.formula,
        plan = flight.prepared.plan,
        outcome =
          ThrowOutcome(
            faces = flight.faces,
            rethrows = flight.rethrows,
            forcedSettles = flight.forcedSettles,
            rolledAtEpochMs = clock(),
          ),
        rounding = rounding,
        added = AddedDice(faces = flight.added, room = { die -> roomForAnother(flight, die) }),
      )
    return when (scoring) {
      is Scoring.OneMoreDie -> Landed.OneMore(oneMore(flight, scoring))
      is Scoring.Scored -> Landed.Complete(complete(flight, scoring.result))
    }
  }

  /**
   * The same throw under a different rounding (`design/dInfinity.dc.html`,
   * option 6d).
   *
   * The dice do not move. Only the arithmetic around them is redone, from
   * subtotals that are already recorded — which is the only honest way to offer
   * this at all.
   */
  fun round(rounding: Rounding) {
    val (formula, result) = scored ?: return
    val rescored = RollEvaluator.rescore(formula, result, rounding)
    scored = formula to rescored
    state = RollState.Settled(rescored, divides = formula.divides)
  }

  /** Puts the result away, ready to throw the same formula again. */
  fun clear() {
    type(text)
  }

  /**
   * The throw that puts one more die on the table, for an explosion or a
   * reroll.
   *
   * It is a real simulation of one die, not a number from somewhere else.
   * `docs/architecture.md`'s first goal has no exception for the second die of
   * an exploding six, and a shortcut here would be exactly the shortcut the
   * whole app exists not to take.
   *
   * It carries the dice already down, which decide two things and no third: the
   * clear floor it is dropped onto, and the picture it is drawn into. **No body
   * is created for any of them.** A die that has come to rest is finished, its
   * face is read, and the throw that follows it cannot reach it — not because
   * the spawn was tuned to miss, but because there is nothing there to hit
   * (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll
   * adds").
   *
   * Seeded from the roll's own seed and the die's position after it, and thrown
   * at the roll's own scale, so a formula with explosions in it replays like
   * any other and its added dice are the size of the dice they joined.
   */
  private fun oneMore(
    flight: InFlight,
    needed: Scoring.OneMoreDie,
  ): ThrowSpec {
    // The die is one of the dice already in the throw, so which set it came
    // from is a lookup rather than a guess and the statistics stay attributed
    // to the right one.
    val came =
      flight.prepared.plan.dice
        .first { it.die == needed.die }
    val spec =
      ThrowSpec(
        dice = listOf(came.copy(index = 0)),
        geometry = geometry,
        table = table,
        // Not `seed + n`: two seeds that differ by one are not two independent
        // throws, so an exploding die used to be thrown by a stream related to
        // the one that set it off (`Seeds`).
        seed = Seeds.derived(flight.spec.seed, needed.ordinal + 1),
        dieScale = flight.prepared.scale,
        among = flight.down.toList(),
      )
    flight.adding = spec.dice.single()
    return spec
  }

  /** The roll is over: this is the total, and this is what is written down. */
  private fun complete(
    flight: InFlight,
    result: RollResult,
  ): FinishedThrow {
    inFlight = null
    scored = flight.prepared.formula to result
    state = RollState.Settled(result, divides = flight.prepared.formula.divides)
    // Handed out rather than written here: this module decides what a throw
    // came to, and nothing else. Re-rounding the same throw does not come
    // through here, which is why a roll is recorded once and not once per
    // rounding somebody tries.
    return FinishedThrow(
      result = result,
      plan = flight.prepared.plan,
      // The throw as it happened, rather than as it started: a spec and a list
      // of samples kept side by side are two halves somebody has to join up,
      // and this is the join. What comes out replays this roll exactly, which
      // is the only form of the record worth keeping. It is the *first* throw:
      // the dice an explosion added follow from it, seed and all.
      thrown = flight.spec.copy(shake = flight.drivenBy),
      savedRollId = cameFrom?.rollId,
      groupId = cameFrom?.groupId,
    )
  }

  /**
   * Whether the tray could take one more of [die].
   *
   * The end of a chain of explosions that the depth limit does not reach: an
   * added die is dropped into clear floor, and a tray with none left cannot
   * take one. The breakdown says which of the two stopped it
   * (`docs/dice-notation.md`, "Limits").
   */
  private fun roomForAnother(
    flight: InFlight,
    die: Die,
  ): Boolean =
    ClearSpace.roomForAnother(
      geometry = geometry,
      dieRadiusMm = ClearSpace.radiusOf(die, flight.prepared.scale),
      taken = flight.taken(),
    )

  /**
   * What a formula that parsed comes to: a plan the table can hold, a refusal
   * because it cannot, or dice that no installed set defines.
   *
   * The two halves were two methods and are one, because they were never asked
   * separately — and because a plan that fits leaves [prepared] behind, which
   * is the assignment that has to happen in the same breath as the state it
   * belongs to.
   */
  private fun planned(parsed: Formula): RollState {
    val plan =
      when (val planned = RollPlanner.plan(parsed, catalog)) {
        is PlanResult.Failed -> return RollState.Invalid(planned.error)
        is PlanResult.Planned -> planned.plan
      }
    return when (val room = TableCapacity.check(plan, geometry)) {
      is CapacityVerdict.Refused -> RollState.TooMany(room.diceCount, room.largestThatFits, room.reason)
      is CapacityVerdict.Fits -> {
        prepared = Prepared(parsed, plan, room.scale, room.diceCount)
        RollState.Ready(diceCount = room.diceCount, scale = room.scale)
      }
    }
  }
}

/**
 * What a throw that has landed came to: the roll, or the next throw it calls
 * for.
 *
 * A roll is not always over when its dice stop. `2d6!` throws two dice, and if
 * one of them shows a six it throws a third — into the same tray, among the
 * dice that set it off, and nobody knows there is a third until the first two
 * have landed (`docs/dice-notation.md`, "Evaluation", step 5).
 *
 * A sealed pair rather than a nullable result and a nullable spec, for the
 * reason [RollState] is sealed: "finished and also asking for another die" is a
 * state to make unwritable rather than one to remember not to reach.
 */
sealed interface Landed {
  /** Every die is down and read. [thrown] is what goes in the history. */
  data class Complete(
    val thrown: FinishedThrow,
  ) : Landed

  /**
   * One more die has to be thrown before there is a total.
   *
   * [spec] is a throw of that one die into the same tray, carrying the dice
   * already down so it can be dropped clear of them and drawn among them. It is
   * thrown exactly like any other throw — there is one path to a number
   * (`docs/architecture.md`, goal 1).
   */
  data class OneMore(
    val spec: ThrowSpec,
  ) : Landed
}

/**
 * The four things the roll screen can be showing, and nothing in between.
 *
 * A sealed set rather than a bag of nullable fields, because "rolling with an
 * error showing" and "a result for a formula that has since been edited" are
 * states that should be impossible to write down, not states to remember not
 * to reach (`design/dInfinity.dc.html`, options 1a–1j).
 */
sealed interface RollState {
  /** Nothing typed and nothing picked. The first thing a new install shows. */
  data object Empty : RollState

  /**
   * The formula does not read. [error] carries the range that is wrong, so the
   * squiggle goes under the offending part rather than the whole field.
   */
  data class Invalid(
    val error: NotationError,
  ) : RollState

  /**
   * The formula reads but the table cannot hold it. No body is ever created
   * for one of these.
   */
  data class TooMany(
    val diceCount: Int,
    val largestThatFits: Int,
    val reason: String,
  ) : RollState

  /**
   * Ready to throw.
   *
   * [scale] is how far the capacity rule shrank the dice to make them fit
   * (`docs/tables.md`). It is on screen because a player who asked for forty
   * dice and got small ones should be able to see why.
   */
  data class Ready(
    val diceCount: Int,
    val scale: Double,
  ) : RollState

  /** The dice are in the air. */
  data class Rolling(
    val diceCount: Int,
  ) : RollState

  /**
   * They have landed, and this is what they came to.
   *
   * @param divides whether the formula has a division in it, and so whether
   *   the rounding control is worth offering. `Down`, `Nearest` and `Up` all
   *   give the same answer to `3d6 + 4`, and three buttons that change nothing
   *   are worse than no buttons (`docs/dice-notation.md`).
   */
  data class Settled(
    val result: RollResult,
    val divides: Boolean = false,
  ) : RollState
}
