package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the tray does frame by frame.
 *
 * None of this needs a device: a frame callback is a number of nanoseconds, a
 * surface is a stage that may or may not be there, and a roll is something
 * that can be advanced. What needs a device is the thread, the vsync and the
 * GPU, and that is [TrayDriver] — this is everything else
 * (`docs/architecture.md`, decision 40).
 */
class TrayLoopTest {
  private val geometry = TableGeometry.referenceDevice()
  private val look = TableLook(id = "plain", name = "Plain")

  @Test
  fun `the first frame of a roll is worth no time at all`() {
    // There is no frame before it to measure against. Measuring from zero
    // would hand the clock however long the device has been awake, spend the
    // whole catch-up budget on frame one, and start the roll already late.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.frame(SOME_LATE_UPTIME)

    assertEquals(listOf(0.0), roll.advanced)
  }

  @Test
  fun `a frame is worth the time since the frame before it`() {
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.frame(SOME_LATE_UPTIME)
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)
    loop.frame(SOME_LATE_UPTIME + 3 * SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals(3, roll.advanced.size)
    assertEquals(1.0 / 60.0, roll.advanced[1], EPSILON)
    assertEquals(2.0 / 60.0, roll.advanced[2], EPSILON)
  }

  @Test
  fun `a frame clock that jumped backwards is worth no time rather than negative time`() {
    // The frame clock would refuse a negative length of time outright, and
    // taking the app down because a counter wrapped is not a trade worth
    // making.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.frame(SOME_LATE_UPTIME)
    loop.frame(SOME_LATE_UPTIME - SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals(0.0, roll.advanced.last(), EPSILON)
  }

  @Test
  fun `a second throw replaces the first rather than landing on top of it`() {
    val loop = TrayLoop()
    val first = FakeRoll(steps = 100)
    val second = FakeRoll(steps = 100)
    loop.stage(FakeStage())

    loop.roll(first.start())
    loop.frame(SOME_LATE_UPTIME)
    loop.roll(second.start())
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)

    assertTrue("the roll being replaced was not closed", first.closed)
    assertEquals("the first roll was still being stepped", 1, first.advanced.size)
    assertEquals("the second roll's first frame was not its first", listOf(0.0), second.advanced)
  }

  @Test
  fun `a roll that has finished stops asking for frames`() {
    // The dice have stopped and nothing may touch them, so there is nothing
    // left to draw — and a frame callback that kept arriving would keep this
    // thread awake for as long as the screen was on.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 2)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    assertTrue(loop.frame(SOME_LATE_UPTIME))
    assertFalse("the loop asked for another frame after the dice stopped", loop.frame(SOME_LATE_UPTIME + 1))
    assertFalse(loop.rolling)
  }

  @Test
  fun `a frame with no roll to advance is not asked for again`() {
    val loop = TrayLoop()
    loop.stage(FakeStage())

    assertFalse(loop.frame(SOME_LATE_UPTIME))
  }

  @Test
  fun `losing the surface closes the stage and leaves the roll alone`() {
    // Backgrounding the app does not stop the dice. It stops the drawing.
    val loop = TrayLoop()
    val stage = FakeStage()
    val roll = FakeRoll(steps = 100)
    loop.stage(stage)
    loop.roll(roll.start())

    loop.surfaceLost()

    assertTrue("the stage was left holding a surface that has gone", stage.closed)
    assertFalse(roll.closed)
    assertTrue("the roll was ended with the surface", loop.rolling)
    assertFalse("the loop kept asking for frames with nowhere to draw", loop.wantsFrames)
  }

  @Test
  fun `a roll goes on while there is nowhere to draw it`() {
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 100)
    loop.stage(FakeStage())
    loop.roll(roll.start())
    loop.surfaceLost()

    loop.frame(SOME_LATE_UPTIME)
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals("a roll nobody is watching stopped being stepped", 2, roll.advanced.size)
  }

  @Test
  fun `a new stage closes the one it replaces`() {
    // Turning the phone. Filament fixes its viewport when a stage is made, so
    // this happens on every resize, and a stage left behind is an engine left
    // behind.
    val loop = TrayLoop()
    val first = FakeStage()
    loop.stage(first)

    loop.stage(FakeStage(width = 640, height = 320))

    assertTrue("the stage that was replaced was left open", first.closed)
  }

  @Test
  fun `clearing the tray ends the roll`() {
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 100)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.clear()

    assertTrue(roll.closed)
    assertFalse(loop.rolling)
  }

  @Test
  fun `closing the loop gives up the roll and the stage`() {
    val loop = TrayLoop()
    val stage = FakeStage()
    val roll = FakeRoll(steps = 100)
    loop.stage(stage)
    loop.roll(roll.start())

    loop.close()

    assertTrue("a physics world was left open", roll.closed)
    assertTrue("an engine was left open", stage.closed)
  }

  /**
   * A roll that finishes after a fixed number of frames and remembers what it
   * was handed. No physics: what this class decides is *when* a roll is
   * advanced and by how much, which is the same question whatever is
   * underneath.
   */
  private inner class FakeRoll(
    private val steps: Int,
  ) : WatchedRoll {
    val advanced = mutableListOf<Double>()
    var closed = false
      private set

    private var watcher: Renderer? = null

    override val running: Boolean get() = advanced.size < steps

    override fun advance(elapsedSeconds: Double): RenderFrame {
      advanced += elapsedSeconds
      val frame = frame()
      // Shown to the renderer exactly as a real roll shows it. A fake that
      // skipped this would let a loop that never drew anything pass.
      if (running) watcher?.show(frame) else watcher?.settled(frame)
      return frame
    }

    override fun close() {
      closed = true
    }

    /** The roll as the loop asks for it: begun on whichever renderer it has. */
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
        List(DICE) { index ->
          DieInstance(
            index = index,
            groupId = 0,
            setId = "builtin",
            requestedSetId = "builtin",
            die = StandardDice.d6,
          )
        },
      geometry = geometry,
      table = look,
      seed = 3L,
    )

  private fun frame(): RenderFrame =
    RenderFrame.still(
      List(DICE) { index ->
        BodyTransform(index = index, position = Vector3(index * 20.0, 0.0, 8.0), orientation = Quaternion.Identity)
      },
    )

  private companion object {
    const val DICE = 2
    const val EPSILON = 1e-9

    /** A phone that has been awake for a day, which is what a frame clock counts from. */
    const val SOME_LATE_UPTIME = 86_400_000_000_000L
    const val SIXTIETH_OF_A_SECOND_NANOS = 16_666_667L
  }
}
