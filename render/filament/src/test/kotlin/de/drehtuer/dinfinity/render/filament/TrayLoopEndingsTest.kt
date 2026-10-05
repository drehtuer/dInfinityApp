package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a watched tray hands on a roll that ends without an answer, and the one
 * thing a player can change between throws without throwing
 * (`docs/physics-and-rendering.md`).
 *
 * Beside [TrayLoopTest] rather than in it, which has grown as large as one
 * class should: these need a roll that can give up, which the fake there does
 * not model.
 */
class TrayLoopEndingsTest {
  private val geometry = TableGeometry.referenceDevice()
  private val look = TableLook(id = "plain", name = "Plain")

  @Test
  fun `a roll that gave up offers back the dice that never stopped, and no result`() {
    // There is no outcome and there is not going to be one. What reaches the
    // screen is which dice to offer the player again, never a number nobody
    // rolled.
    val loop = TrayLoop()
    val roll = EndingRoll(steps = 2, gaveUp = true, stillMoving = listOf(0, 1))
    val reported = mutableListOf<SimulationOutcome>()
    var offered: List<Int>? = null
    loop.stage(FakeStage())
    loop.roll(roll.start(), onStalled = { offered = it }) { outcome, _ -> reported += outcome }

    runOut(loop)

    assertEquals("the dice still moving were not offered back", listOf(0, 1), offered)
    assertTrue("a roll that gave up reported a result", reported.isEmpty())
    assertTrue("the roll that gave up was not closed", roll.closed)
  }

  @Test
  fun `a roll that landed offers nothing back`() {
    val loop = TrayLoop()
    val reported = mutableListOf<SimulationOutcome>()
    var offered: List<Int>? = null
    loop.stage(FakeStage())
    loop.roll(EndingRoll(steps = 2).start(), onStalled = { offered = it }) { outcome, _ -> reported += outcome }

    runOut(loop)

    assertNull("a roll that finished offered dice back", offered)
    assertEquals(1, reported.size)
  }

  @Test
  fun `a pinch between throws is drawn, though nothing on the table moved`() {
    // Only the camera moves. Between throws nothing else would produce a
    // frame, so the new view would not appear until something else drew.
    val stage = FakeStage()
    val loop = TrayLoop()
    loop.stage(stage)
    loop.table(geometry, look)
    loop.frame(SOME_LATE_UPTIME)
    val before = stage.shots.last()

    loop.look(TrayView(zoom = 2.0))

    assertTrue("a new view did not ask for a frame", loop.wantsFrames)
    assertFalse("the view kept asking for frames once drawn", loop.frame(SOME_LATE_UPTIME + 1))
    assertEquals(2, stage.frames)
    assertTrue("the camera was not moved to the new view", stage.shots.last() != before)
  }

  private fun runOut(loop: TrayLoop) {
    repeat(FRAMES) { loop.frame(SOME_LATE_UPTIME + it * SIXTIETH_OF_A_SECOND_NANOS) }
  }

  /** A roll of one die that is over after [steps] asks, with or without an answer. */
  private inner class EndingRoll(
    private val steps: Int,
    private val gaveUp: Boolean = false,
    private val stillMoving: List<Int> = emptyList(),
  ) : WatchedRoll {
    private var asked = 0
    private var watcher: Renderer? = null
    var closed = false
      private set

    override val running: Boolean get() = asked < steps
    override val outcome: SimulationOutcome?
      get() = if (running || gaveUp) null else SimulationOutcome(faces = mapOf(0 to 1))
    override val stalled: Boolean get() = gaveUp && !running
    override val unsettled: List<Int> get() = if (stalled) stillMoving else emptyList()
    override val drivenBy: List<ShakeSample> = emptyList()
    override val impacts: List<Impact> = emptyList()

    override fun advance(elapsedSeconds: Double): RenderFrame {
      asked++
      val frame = RenderFrame.still(listOf(BodyTransform(0, Vector3.Zero, Quaternion.Identity)))
      if (running) watcher?.show(frame) else watcher?.settled(frame)
      return frame
    }

    override fun shake(sample: ShakeSample) = Unit

    override fun close() {
      closed = true
    }

    fun start(): (Renderer) -> WatchedRoll =
      { renderer ->
        watcher = renderer
        renderer.begin(spec(), geometry, look)
        this
      }
  }

  private fun spec(): ThrowSpec =
    ThrowSpec(
      dice =
        listOf(
          DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = StandardDice.d6),
        ),
      geometry = geometry,
      table = look,
      seed = 3L,
    )

  private companion object {
    const val SOME_LATE_UPTIME = 86_400_000_000_000L
    const val SIXTIETH_OF_A_SECOND_NANOS = 16_666_667L

    /** Comfortably more than a two-step roll needs to land and be read. */
    const val FRAMES = 8
  }
}
