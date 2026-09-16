package de.drehtuer.dinfinity.feature.roll

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.SavedRollSource
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.PickableDie
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.simulation.api.DeveloperLog
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * The roll screen's state, as Compose reads it.
 *
 * Three things meet here and nothing else does: [RollMachine], which decides
 * what a formula means and what faces come to; [Tray], which owns the
 * thread the dice are thrown and drawn on; and [Rolls], which is the one line
 * between this screen and a physics engine.
 *
 * The thread rule is the only subtle thing in the file. The machine belongs to
 * the main thread, the roll belongs to the roll thread, and a result crossing
 * between them goes through [toTheScreen] — so nothing here is ever touched
 * from two threads at once (`docs/architecture.md`, "Threading").
 *
 * @param recorder what writes a finished throw down. An interface rather than
 *   a repository, so this module cannot reach a database
 *   (`docs/architecture.md`, "Modules").
 * @param toTheScreen how work gets back to the thread Compose reads on.
 *
 * The class carries a function-count suppression for the same reason
 * [RollMachine] does: ten of its methods are one thing a screen can do each,
 * and the eleventh is the loop that hands the tray a throw and then the throw
 * after it, which a roll that adds dice to itself needs and which nothing else
 * can be folded into.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class RollPresenter(
  private val machine: RollMachine,
  private val driver: Tray,
  private val rolls: Rolls,
  private val recorder: ThrowRecorder = ThrowRecorder.NONE,
  private val toTheScreen: (() -> Unit) -> Unit = { MAIN.post(it) },
  /**
   * What the debug overlay is reading, or null when the developer toggle is
   * off — which is every install until somebody turns it on
   * (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * The same object the tray was built with. It is given to both because the
   * tray is what the roll thread reaches and this is what Compose reads, and
   * the relay is the one thing that crosses between them.
   */
  private val debug: DebugRelay? = null,
  /**
   * What the developer toggle remembers, or [DeveloperLog.NONE].
   *
   * Held above the visit rather than by this presenter, because it outlives
   * the screen: an anomaly is a bug report and the last throw is what replays
   * it, and both are wanted after the player has walked away from the tray.
   * Every throw is offered; which part of it is worth keeping is the log's to
   * decide.
   */
  private val developer: DeveloperLog = DeveloperLog.NONE,
) {
  /** What the screen draws. */
  var state: RollState by mutableStateOf(machine.state)
    private set

  /** The formula as typed, valid or not. */
  var text: String by mutableStateOf(machine.text)
    private set

  /** How many of each of [pickable] the formula is asking for. */
  var counts: Map<PickableDie, Int> by mutableStateOf(machine.counts)
    private set

  /** The dice the picker row offers (`design/dInfinity.dc.html`, option 1h). */
  var pickable: List<PickableDie> by mutableStateOf(machine.pickable)
    private set

  /** Which set those dice come from (`design/dInfinity.dc.html`, option 4a). */
  var pickingFrom: String by mutableStateOf(machine.pickingFrom)
    private set

  /** Every set that has dice to offer, for the chooser. Fixed for the visit. */
  val choosableSets: List<DiceSet> get() = machine.choosableSets

  /** The tray to hand a surface to. */
  val tray: Tray get() = driver

  /**
   * Whether this visit draws the debug overlay at all
   * (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * Read when the screen opens and not watched, exactly like power saving, the
   * shake, the haptics and the sound: an overlay appearing over a roll in
   * progress is not a setting taking effect (`docs/architecture.md`,
   * decision 16).
   */
  val showsDebug: Boolean get() = debug != null

  /**
   * What the overlay draws: the roll as it is this frame, or
   * [RollDiagnostics.NONE] when nothing is being watched.
   *
   * A Compose read through the relay rather than a copy kept here, so a
   * snapshot arriving on the screen's thread recomposes the overlay and
   * nothing else.
   */
  val diagnostics: RollDiagnostics get() = debug?.latest ?: RollDiagnostics.NONE

  /**
   * Whether this screen puts a tray on the screen at all.
   *
   * False in power-saving mode, where the dice are thrown and never drawn, so
   * there is nothing for a surface to be for
   * (`design/dInfinity.dc.html`, option 1z).
   */
  val draws: Boolean get() = driver.draws

  /** The table's shape, which the tray's gestures are measured against. */
  val geometry: TableGeometry get() = machine.geometry

  /** How many dice sets are installed, which first launch counts. */
  val sets: Int get() = machine.sets

  /**
   * The look the tray was last told about.
   *
   * Kept so the tray is told again only when the table actually changes.
   * Tapping a saved roll that pins black felt changes it; typing over that
   * formula changes it back; every other keystroke does not, and rebuilding a
   * scene on every keystroke is not a thing to do by accident
   * (`docs/tables.md`, "Selecting a table").
   */
  private var showing: TableLook? = null

  init {
    // Before anything is thrown there is still a table, and it is what the
    // screen opens on. Said here rather than at the first roll because a
    // player arriving at the screen has not rolled yet, and a black rectangle
    // is not what a dice tray looks like (`docs/TODO.md`, Step 4.1).
    driver.table(machine.geometry, machine.table)
    showing = machine.table
  }

  /** The formula field changed. Re-validated on every keystroke. */
  fun type(typed: String) {
    machine.type(typed)
    publish()
  }

  /**
   * A saved roll was tapped on the strip: its formula, and which roll it was.
   *
   * Which roll it was is carried so the throw can be recorded as that roll's.
   * A throw that belongs to nothing is a throw the saved-roll statistics can
   * never count (`docs/statistics.md`, per saved roll and per group).
   */
  fun typeSaved(
    formula: String,
    from: SavedRollSource,
  ) {
    machine.type(formula, from)
    publish()
  }

  /**
   * Throws the dice, if there are any to throw.
   *
   * Everything after this happens on the roll thread: the world is opened
   * there, stepped there and drawn there. What comes back is one call with
   * what the dice came to, and that is posted to the screen's own thread
   * before the machine is touched.
   *
   * @param shake what the phone did, or empty for a tap.
   * @return whether dice were actually thrown. False when there is nothing to
   *   throw — a formula that does not read, a throw the table cannot hold, or
   *   **a roll already in the air**, which is what a second shake at tumbling
   *   dice meets. The shake source needs the answer: a shake that threw
   *   nothing goes on numbering its moments on the running roll's clock rather
   *   than starting a new one (`docs/physics-and-rendering.md`, "Shake input").
   */
  fun roll(shake: List<ShakeSample> = emptyList()): Boolean {
    // A roll that has landed is a roll that is over. Throwing again is one act
    // — one press, one shake — not "put the total away" followed by "now
    // throw", which is what a shake could never have expressed anyway.
    if (state is RollState.Settled) machine.clear()

    val spec = machine.throwDice(shake) ?: return false
    publish()
    throwIt(spec)
    return true
  }

  /**
   * Hands one throw to the tray, and hands the tray the one after it.
   *
   * A roll is not always over when its dice stop: an explosion and a reroll
   * each add a die, into the same tray, among the dice that set it off. How
   * many they add is not knowable until the first ones land, so it is a loop
   * rather than a list — the machine says what the throw came to or what has to
   * be thrown next, and this throws it (`docs/dice-notation.md`, "Evaluation",
   * step 5).
   *
   * It is the same call for the first throw and for every die after it. There
   * is one way to throw dice in this app and one path to a number, and an added
   * die that took a different one would be an added die that could come out
   * differently (`docs/architecture.md`, goal 1).
   */
  private fun throwIt(spec: ThrowSpec) {
    driver.roll(
      start = { watcher -> rolls.start(spec, watcher) },
      onSettled = { outcome, drivenBy ->
        toTheScreen {
          // Written down on the screen's thread, where the result exists, and
          // handed to something that takes it away — a roll is finished when
          // the dice stop, not when a database says so.
          //
          // The shake comes back with the outcome because the roll is the only
          // thing that knows it: a shake-driven throw goes into the world with
          // an empty spec and is filled in as the hand moves. Here is where it
          // is joined back onto the spec that started it, and here is where it
          // stops — the recorder is given a `FinishedThrow` and takes the
          // result, the plan and the seed off it, and nothing downstream has
          // anywhere to put a shake (`docs/architecture.md`, decision 13).
          val landed = machine.settled(outcome, drivenBy)
          publish()
          when (landed) {
            is Landed.Complete -> {
              recorder.record(landed.thrown)
              // And offered to the developer's log, which keeps the throw for
              // a replay and the anomaly if there was one. It is the one place
              // a seed reaches anywhere a person can read it, and it is behind
              // the toggle for exactly that reason (`docs/statistics.md`).
              developer.landed(landed.thrown.thrown, outcome, landed.thrown.result.rolledAtEpochMs)
            }
            // Straight back round: the next die is thrown the moment the last
            // one has stopped, which is what a player does with an exploding
            // six.
            is Landed.OneMore -> throwIt(landed.spec)
            // Nobody is waiting for this throw any more — the formula was typed
            // over while it was in the air. Nothing landed as far as the screen
            // is concerned, and nothing follows it.
            null -> Unit
          }
        }
      },
    )
  }

  /**
   * One more moment of the shake that is throwing the dice now.
   *
   * The dice were spawned when the shake began, so the rest of it reaches them
   * while they are already in the air — which is what makes a shake look like
   * a hand rather than like a button pressed afterwards
   * (`docs/physics-and-rendering.md`, "Shake input").
   */
  fun shaking(sample: ShakeSample) {
    driver.shake(sample)
  }

  /** A tap on the picker row: one more of that die in the formula. */
  fun add(die: PickableDie) {
    machine.add(die)
    publish()
  }

  /** A long press on the picker row: one fewer, or the group gone. */
  fun remove(die: PickableDie) {
    machine.remove(die)
    publish()
  }

  /** Offer the picker row a different set's dice. The formula is left alone. */
  fun pickFrom(setId: String) {
    machine.pickFrom(setId)
    publish()
  }

  /** The same throw under a different rounding. The dice do not move. */
  fun round(rounding: Rounding) {
    machine.round(rounding)
    publish()
  }

  /** Puts the result away, ready to throw the same formula again. */
  fun clear() {
    machine.clear()
    publish()
  }

  private fun publish() {
    // The table comes first: a throw that is about to be made lands on it, and
    // a tray told afterwards would draw one table and simulate another.
    if (machine.table != showing) {
      showing = machine.table
      driver.table(machine.geometry, machine.table)
    }
    state = machine.state
    text = machine.text
    counts = machine.counts
    pickable = machine.pickable
    pickingFrom = machine.pickingFrom
  }

  private companion object {
    val MAIN = Handler(Looper.getMainLooper())
  }
}
