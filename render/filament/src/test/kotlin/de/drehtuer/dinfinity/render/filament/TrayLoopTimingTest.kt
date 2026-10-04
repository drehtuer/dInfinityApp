package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.DebugWatch
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame times a tray hands the debug overlay's frame-rate line
 * (`docs/physics-and-rendering.md`, "Debug tooling"; decision 72).
 *
 * Apart from `TrayLoopTest` because that class is already as large as one
 * class should be; what is asked here is only *which* frame times reach the
 * watcher, so the roll under it is the smallest one that runs for a while.
 */
class TrayLoopTimingTest {
  @Test
  fun `a tray being debugged is told how long each frame came after the last`() {
    val timed = TimingWatch()
    val loop = TrayLoop(debug = timed)
    loop.stage(FakeStage())
    loop.roll({ StillRunning(frames = 10) })

    loop.frame(START)
    loop.frame(START + SIXTIETH)
    loop.frame(START + 3 * SIXTIETH)

    // The first frame has nothing before it; the third came two vsyncs late.
    assertEquals(listOf(SIXTIETH, 2 * SIXTIETH), timed.intervals)
  }

  @Test
  fun `a pause in which the tray asked for no frame is not a slow frame`() {
    // The roll lands on its only frame, the tray asks for nothing more, and
    // the next frame comes whenever the player next does something. The time
    // in between is the phone sitting still, not stuttering.
    val timed = TimingWatch()
    val loop = TrayLoop(debug = timed)
    loop.roll({ StillRunning(frames = 1) })

    assertFalse("a finished roll with no surface wants no frame", loop.frame(START))
    loop.roll({ StillRunning(frames = 10) })
    loop.frame(START + 100 * SIXTIETH)

    assertTrue(timed.intervals.isEmpty())
  }

  @Test
  fun `a tray nobody is debugging still steps its roll without timing it`() {
    // `DebugWatch.NONE` says it is not watching and the loop asks first, so
    // there is no frame time anywhere to look at — only the roll going on.
    val roll = StillRunning(frames = 10)
    val loop = TrayLoop()
    loop.stage(FakeStage())
    loop.roll({ roll })

    loop.frame(START)
    assertTrue(loop.frame(START + SIXTIETH))
    assertEquals(2, roll.advanced)
  }

  /** A debug watcher that keeps the frame times it is handed and ignores the rest. */
  private class TimingWatch : DebugWatch {
    val intervals = mutableListOf<Long>()

    override fun saw(diagnostics: RollDiagnostics) = Unit

    override fun framed(intervalNanos: Long) {
      intervals += intervalNanos
    }
  }

  /** A roll that runs for [frames] frames and lands on nothing. */
  private class StillRunning(
    private val frames: Int,
  ) : WatchedRoll {
    var advanced = 0
      private set

    override val running: Boolean get() = advanced < frames
    override val outcome: SimulationOutcome? get() = if (running) null else SimulationOutcome(faces = mapOf(0 to 0))
    override val drivenBy: List<ShakeSample> get() = emptyList()
    override val impacts: List<Impact> get() = emptyList()

    override fun advance(elapsedSeconds: Double): RenderFrame {
      advanced++
      return RenderFrame.still(emptyList())
    }

    override fun shake(sample: ShakeSample) = Unit

    override fun close() = Unit
  }

  private companion object {
    const val START = 86_400_000_000_000L
    const val SIXTIETH = 16_666_667L
  }
}
