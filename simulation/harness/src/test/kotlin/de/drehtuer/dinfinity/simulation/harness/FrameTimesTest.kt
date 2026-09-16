package de.drehtuer.dinfinity.simulation.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a run is entitled to say about frames.
 *
 * The device times them and does nothing else with them; whether there is
 * anything to report, and what a report of nothing looks like, is decided here
 * (`docs/architecture.md`, decision 53).
 */
class FrameTimesTest {
  @Test
  fun `a run with no frames has no frame summary at all`() {
    assertNull(FrameTimes.Nothing.summary())
    assertNull(FrameTimes(millis = emptyList(), droppedSteps = 3, drawn = true).summary())
  }

  @Test
  fun `a run with frames reports the three figures over every one of them`() {
    val measured = requireNotNull(FrameTimes(millis = listOf(4.0, 1.0, 40.0), droppedSteps = 7, drawn = true).summary())

    assertEquals(3L, measured.frames)
    assertEquals(7L, measured.droppedSteps)
    assertEquals(4.0, measured.millis.median)
    assertEquals(40.0, measured.millis.worst)
    assertTrue(measured.drawn)
  }

  @Test
  fun `a frame that finished early waits out the rest of its sixtieth of a second`() {
    assertEquals(FrameTimes.FRAME_NANOS, FrameTimes.waitNanos(0))
    assertEquals(FrameTimes.FRAME_NANOS - 1_000_000L, FrameTimes.waitNanos(1_000_000L))
  }

  @Test
  fun `a frame that overran waits for nothing, because the next one is already late`() {
    assertEquals(0L, FrameTimes.waitNanos(FrameTimes.FRAME_NANOS))
    assertEquals(0L, FrameTimes.waitNanos(FrameTimes.FRAME_NANOS * 4))
  }

  @Test
  fun `a frame is a sixtieth of a second, in whichever unit the loop needs it`() {
    assertEquals(1.0 / FrameTimes.FRAMES_PER_SECOND, FrameTimes.FRAME_SECONDS)
    assertEquals(FrameTimes.FRAME_SECONDS, FrameTimes.FRAME_NANOS / 1_000_000_000.0, 1e-6)
  }
}
