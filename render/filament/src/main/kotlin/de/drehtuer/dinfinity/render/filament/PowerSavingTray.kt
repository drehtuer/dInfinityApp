package de.drehtuer.dinfinity.render.filament

import android.view.Surface
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.FrameClock
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The tray for power-saving mode: it throws the dice and never draws them
 * (`design/dInfinity.dc.html`, option 1z).
 *
 * **No graphics engine is created — not a hidden one, not an off-screen one.**
 * There is no Filament type in this file, nothing implements [Stage], and the
 * screen is told not to hand over a surface at all, so there is nothing for
 * one to be made from (`docs/architecture.md`, decision 38).
 *
 * It is nonetheless **the same roll**. The throw is opened the same way, by
 * the same `Rolls`, and stepped by the same loop in the same order; the only
 * difference is who asks for the steps. A frame callback paces them sixty
 * times a second so a player can watch; here they are asked for as fast as the
 * processor will give them, and the dice are on the table before the screen
 * has redrawn. Same seed, same faces (`docs/physics-and-rendering.md`,
 * "Power-saving mode").
 *
 * It lives beside [TrayDriver] because it is the other implementation of
 * [Tray], and [Tray] is where a `Surface` is named. What decides whether a
 * Filament engine exists is not which module a file is in — it is that nothing
 * here constructs one.
 *
 * @param on where a roll is stepped. Off the main thread by default, for the
 *   same reason a watched roll is: a hundred convex bodies settling is real
 *   work, and it being quick is not a reason to do it where the UI is drawn.
 */
class PowerSavingTray(
  private val on: Executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, THREAD_NAME) },
) : Tray {
  /** Nothing is drawn, so nothing needs a surface. */
  override val draws: Boolean = false

  @Volatile
  private var live: WatchedRoll? = null

  @Volatile
  private var closed = false

  override fun roll(
    start: (Renderer) -> WatchedRoll,
    onSettled: (SimulationOutcome) -> Unit,
  ) {
    on.execute {
      // A second throw replaces the first, exactly as it does on a tray being
      // watched — a roll nobody is waiting for any more is a roll to give up.
      endRoll()
      if (closed) return@execute
      val roll = start(HeadlessRenderer())
      live = roll
      val outcome = roll.use(::runOut)
      live = null
      // A roll given up because the screen was left reports nothing, because
      // nothing landed.
      if (!closed && outcome != null) onSettled(outcome)
    }
  }

  /**
   * One more moment of the shake.
   *
   * Kept rather than dropped, even though a power-saving roll is usually over
   * before the second sample arrives: a shake that reaches a roll still going
   * drives it the way it would on a watched tray, and a sample for a roll that
   * has finished is dropped by the roll itself.
   */
  override fun shake(sample: ShakeSample) {
    live?.shake(sample)
  }

  override fun close() {
    closed = true
    on.execute { endRoll() }
  }

  // Nothing to draw on, nothing to draw, nowhere to look from. Each of these
  // is a thing the screen says to a tray, and each of them is about a picture.
  override fun surfaceAvailable(
    surface: Surface,
    width: Int,
    height: Int,
  ) = Unit

  override fun surfaceLost() = Unit

  override fun table(
    geometry: TableGeometry,
    look: TableLook,
  ) = Unit

  override fun look(view: TrayView) = Unit

  override fun clear() = Unit

  /** Steps the roll until the last die stops, then says what they came to. */
  private fun runOut(roll: WatchedRoll): SimulationOutcome? {
    while (roll.running && !closed) roll.advance(A_HELPING)
    return roll.outcome
  }

  private fun endRoll() {
    live?.close()
    live = null
  }

  private companion object {
    const val THREAD_NAME = "dinfinity-power-saving"

    /**
     * How much simulated time to ask for at a time.
     *
     * Exactly the clock's catch-up limit, so every ask is paid in full and
     * none of it is dropped: asking for more would be asking for steps the
     * clock refuses to take in one go, and asking for less would be pacing the
     * roll for no reason.
     */
    const val A_HELPING: Double = FrameClock.MAX_STEPS_PER_FRAME * SettleRule.TIMESTEP_SECONDS
  }
}
