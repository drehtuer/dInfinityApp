package de.drehtuer.dinfinity.feature.roll

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.simulation.api.ShakeSample

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
 * @param toTheScreen how work gets back to the thread Compose reads on.
 */
class RollPresenter(
  private val machine: RollMachine,
  private val driver: Tray,
  private val rolls: Rolls,
  private val toTheScreen: (() -> Unit) -> Unit = { MAIN.post(it) },
) {
  /** What the screen draws. */
  var state: RollState by mutableStateOf(machine.state)
    private set

  /** The formula as typed, valid or not. */
  var text: String by mutableStateOf(machine.text)
    private set

  /** The tray to hand a surface to. */
  val tray: Tray get() = driver

  /** The formula field changed. Re-validated on every keystroke. */
  fun type(typed: String) {
    machine.type(typed)
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
          machine.settled(outcome)
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
  }

  private companion object {
    val MAIN = Handler(Looper.getMainLooper())
  }
}
