package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

/**
 * The tray for power-saving mode (`design/dInfinity.dc.html`, option 1z).
 *
 * The claim it has to keep is not "it is fast" but "**it is the same roll**".
 * The throw is opened the same way and stepped by the same loop in the same
 * order; the only difference is that nobody paces it. Nothing here creates a
 * graphics engine, and there is no second path to a number
 * (`docs/physics-and-rendering.md`, "Power-saving mode").
 */
class PowerSavingTrayTest {
  /** Runs the roll where the test stands, so a throw and its result are one act. */
  private val here = Executor(Runnable::run)

  @Test
  fun `it says it draws nothing, so the screen puts up no surface`() {
    assertFalse("a tray that draws nothing asked for somewhere to draw", PowerSavingTray(here).draws)
  }

  @Test
  fun `the dice are thrown and the result comes back`() {
    val roll = FakeRoll(steps = 12)
    val landed = mutableListOf<SimulationOutcome>()

    PowerSavingTray(here).roll(start = roll.start(), onSettled = { outcome, _ -> landed += outcome })

    assertEquals(1, landed.size)
    assertTrue("the roll was left open, and a world with it", roll.closed)
  }

  @Test
  fun `it steps the roll to the end rather than stopping part way`() {
    val roll = FakeRoll(steps = 30)

    PowerSavingTray(here).roll(start = roll.start(), onSettled = { _, _ -> })

    assertFalse("the roll was abandoned before the dice stopped", roll.running)
  }

  @Test
  fun `every ask is paid in full, so no step is dropped`() {
    // The clock refuses to take more than its catch-up limit in one go and
    // drops the time for the rest. Asking for exactly that much means nothing
    // is ever dropped — which is what makes this the same roll as a watched
    // one rather than one that gets there a different way.
    val roll = FakeRoll(steps = 8)

    PowerSavingTray(here).roll(start = roll.start(), onSettled = { _, _ -> })

    val asked = roll.advanced.distinct()
    assertEquals("the roll was paced, or asked for uneven helpings", 1, asked.size)
  }

  @Test
  fun `the renderer it watches with draws nothing`() {
    val roll = FakeRoll(steps = 4)

    PowerSavingTray(here).roll(start = roll.start(), onSettled = { _, _ -> })

    assertTrue("something other than the headless renderer watched the roll", roll.watcher is HeadlessRenderer)
  }

  @Test
  fun `a second throw replaces the first rather than landing on it`() {
    val first = FakeRoll(steps = 4)
    val second = FakeRoll(steps = 4)
    val tray = PowerSavingTray(here)

    tray.roll(start = first.start(), onSettled = { _, _ -> })
    tray.roll(start = second.start(), onSettled = { _, _ -> })

    assertTrue(first.closed)
    assertTrue(second.closed)
  }

  @Test
  fun `a shake reaches the roll it is throwing`() {
    // It usually arrives too late — the dice are down before the second sample
    // — but a shake that catches a roll still going drives it exactly as it
    // would on a tray being watched. Delivered from inside the first step, so
    // "while the roll is live" is a fact rather than a race.
    val tray = PowerSavingTray(here)
    lateinit var roll: FakeRoll
    roll = FakeRoll(steps = 4, onAdvance = { if (roll.advanced.size == 1) tray.shake(sample()) })

    tray.roll(start = roll.start(), onSettled = { _, _ -> })

    assertEquals(listOf(sample()), roll.shaken)
  }

  @Test
  fun `the shake that drove the roll comes back with what the dice came to`() {
    // Power-saving reports the same two things a watched tray does, because it
    // is the same roll. Read before the roll is closed, which is the only
    // moment it can be (`docs/physics-and-rendering.md`, "Shake input").
    val tray = PowerSavingTray(here)
    lateinit var roll: FakeRoll
    roll = FakeRoll(steps = 4, onAdvance = { if (roll.advanced.size == 1) tray.shake(sample()) })
    var drove: List<ShakeSample>? = null

    tray.roll(start = roll.start(), onSettled = { _, shake -> drove = shake })

    assertEquals(listOf(sample()), drove)
    assertTrue("the roll was read but never given up", roll.closed)
  }

  @Test
  fun `a shake with no roll to drive is dropped rather than kept`() {
    // A hand waved at a screen with nothing in the air is not a throw, and a
    // sample held for the next roll would arrive as a force nobody applied.
    PowerSavingTray(here).shake(sample())
  }

  @Test
  fun `leaving the screen gives the roll up, and nothing lands`() {
    val roll = FakeRoll(steps = 4)
    val tray = PowerSavingTray(here)
    tray.close()

    tray.roll(start = roll.start(), onSettled = { _, _ -> error("a roll nobody was waiting for was reported") })

    assertNull("a roll was opened after the screen was left", roll.watcher)
  }

  @Test
  fun `the things a tray is told about a picture are all no-ops`() {
    // Each of these is a screen telling a tray something about what is on
    // screen. There is nothing on screen, so there is nothing to say back —
    // but none of them may throw, because the screen says them regardless.
    val tray = PowerSavingTray(here)

    tray.table(TableGeometry.referenceDevice(), TableLook(id = "plain", name = "Plain"))
    tray.look(TrayView.Whole)
    tray.surfaceLost()
    tray.clear()
    tray.close()
  }

  private fun sample(): ShakeSample =
    ShakeSample(stepIndex = 0, accelerationMmPerSecond2 = Vector3.Zero, gravity = Vector3.Zero)

  /** A roll that finishes after a fixed number of asks. No physics. */
  private class FakeRoll(
    private val steps: Int,
    private val onAdvance: () -> Unit = {},
  ) : WatchedRoll {
    val advanced = mutableListOf<Double>()
    val shaken = mutableListOf<ShakeSample>()
    var closed = false
      private set
    var watcher: Renderer? = null
      private set

    override val running: Boolean get() = advanced.size < steps

    override val outcome: SimulationOutcome?
      get() = if (running) null else SimulationOutcome(faces = mapOf(0 to 0))

    override val drivenBy: List<ShakeSample> get() = shaken.toList()

    override fun advance(elapsedSeconds: Double): RenderFrame {
      advanced += elapsedSeconds
      onAdvance()
      return frame()
    }

    override fun shake(sample: ShakeSample) {
      shaken += sample
    }

    override fun close() {
      closed = true
    }

    fun start(): (Renderer) -> WatchedRoll =
      { renderer ->
        watcher = renderer
        this
      }

    private fun frame(): RenderFrame =
      RenderFrame.still(listOf(BodyTransform(index = 0, position = Vector3.Zero, orientation = Quaternion.Identity)))
  }
}
