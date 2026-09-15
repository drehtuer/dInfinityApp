package de.drehtuer.dinfinity.feature.roll

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.PickableDie
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.TableGeometry

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
 */
class RollPresenter(
  private val machine: RollMachine,
  private val driver: Tray,
  private val rolls: Rolls,
  private val recorder: ThrowRecorder = ThrowRecorder.NONE,
  private val toTheScreen: (() -> Unit) -> Unit = { MAIN.post(it) },
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

  init {
    // Before anything is thrown there is still a table, and it is what the
    // screen opens on. Said here rather than at the first roll because a
    // player arriving at the screen has not rolled yet, and a black rectangle
    // is not what a dice tray looks like (`docs/TODO.md`, Step 4.1).
    driver.table(machine.geometry, machine.table)
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
   */
  fun roll(shake: List<ShakeSample> = emptyList()) {
    // A roll that has landed is a roll that is over. Throwing again is one act
    // — one press, one shake — not "put the total away" followed by "now
    // throw", which is what a shake could never have expressed anyway.
    if (state is RollState.Settled) machine.clear()

    val spec = machine.throwDice(shake) ?: return
    publish()

    driver.roll(
      start = { watcher -> rolls.start(spec, watcher) },
      onSettled = { outcome ->
        toTheScreen {
          // Written down on the screen's thread, where the result exists, and
          // handed to something that takes it away — a roll is finished when
          // the dice stop, not when a database says so.
          machine.settled(outcome)?.let(recorder::record)
          publish()
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
