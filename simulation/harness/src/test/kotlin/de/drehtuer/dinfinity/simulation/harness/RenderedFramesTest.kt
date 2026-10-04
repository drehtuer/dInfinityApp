package de.drehtuer.dinfinity.simulation.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the rendered harness makes of the frames a device drew.
 *
 * The device only hands frames over; which of them count, and what they add up
 * to, is pinned here (`docs/architecture.md`, decision 80).
 */
class RenderedFramesTest {
  @Test
  fun `a run that drew no frame of a roll has nothing to summarise`() {
    val frames = RenderedFrames()
    frames.skippedDraw()
    frames.gpu(frameId = 1, durationNanos = MS)

    assertNull(frames.summary())
    assertTrue(frames.times().millis.isEmpty())
  }

  @Test
  fun `each frame's work is kept in milliseconds and the run is marked drawn`() {
    val frames = RenderedFrames()
    frames.framed(beginNanos = 0, workNanos = 4 * MS)
    frames.framed(beginNanos = 16 * MS, workNanos = 2 * MS)

    val times = frames.times()
    assertEquals(listOf(4.0, 2.0), times.millis)
    assertTrue(times.drawn)
    assertEquals(2L, requireNotNull(frames.summary()).frames.frames)
  }

  @Test
  fun `the rate is over intervals within a pass and never across two`() {
    val frames = RenderedFrames()
    frames.framed(beginNanos = 0, workNanos = MS)
    frames.framed(beginNanos = 10 * MS, workNanos = MS)
    frames.passEnded(droppedSteps = 0)
    // A second later: the scene for the next throw was being built, which is
    // a pause between throws and not a slow frame.
    frames.framed(beginNanos = 1_000 * MS, workNanos = MS)
    frames.framed(beginNanos = 1_010 * MS, workNanos = MS)

    val rate = requireNotNull(requireNotNull(frames.summary()).rate)
    assertEquals(2, rate.frames)
    assertEquals(10.0, rate.p99Millis, 1e-9)
    assertEquals(100.0, rate.framesPerSecond, 1e-9)
  }

  @Test
  fun `a frame starting when the last one did is not an interval of no time`() {
    val frames = RenderedFrames()
    frames.framed(beginNanos = 5 * MS, workNanos = MS)
    frames.framed(beginNanos = 5 * MS, workNanos = MS)

    assertNull(requireNotNull(frames.summary()).rate)
  }

  @Test
  fun `dropped steps add up over every pass`() {
    val frames = RenderedFrames()
    frames.framed(beginNanos = 0, workNanos = MS)
    frames.passEnded(droppedSteps = 2)
    frames.passEnded(droppedSteps = 3)

    assertEquals(5L, requireNotNull(frames.summary()).frames.droppedSteps)
  }

  @Test
  fun `a GPU frame read twice from the history is kept once, and the first reading wins`() {
    val frames = RenderedFrames()
    frames.framed(beginNanos = 0, workNanos = MS)
    frames.gpu(frameId = 7, durationNanos = 3 * MS)
    frames.gpu(frameId = 7, durationNanos = 9 * MS)
    frames.gpu(frameId = 8, durationNanos = 5 * MS)

    val summary = requireNotNull(frames.summary())
    assertEquals(2, summary.gpuFrames)
    assertEquals(5.0, requireNotNull(summary.gpuMillis).worst)
    assertEquals(3.0, requireNotNull(summary.gpuMillis).median)
  }

  @Test
  fun `Filament's invalid and pending durations are not measurements`() {
    val frames = RenderedFrames()
    frames.framed(beginNanos = 0, workNanos = MS)
    frames.gpu(frameId = 1, durationNanos = INVALID)
    frames.gpu(frameId = 2, durationNanos = PENDING)
    frames.gpu(frameId = 3, durationNanos = 0)

    val summary = requireNotNull(frames.summary())
    assertNull(summary.gpuMillis)
    assertEquals(0, summary.gpuFrames)

    // Pending now, finished on the next read: the second reading is the one kept.
    frames.gpu(frameId = 2, durationNanos = 4 * MS)
    assertEquals(4.0, assertNotNull(requireNotNull(frames.summary()).gpuMillis).p99)
  }

  @Test
  fun `skipped draws are counted`() {
    val frames = RenderedFrames()
    frames.framed(beginNanos = 0, workNanos = MS)
    frames.skippedDraw()
    frames.skippedDraw()

    assertEquals(2L, frames.skippedDraws)
    assertEquals(2L, requireNotNull(frames.summary()).skippedDraws)
  }

  @Test
  fun `a negative frame or a negative drop is refused rather than averaged in`() {
    val frames = RenderedFrames()

    assertFailsWith<IllegalArgumentException> { frames.framed(beginNanos = 0, workNanos = -1) }
    assertFailsWith<IllegalArgumentException> { frames.passEnded(droppedSteps = -1) }
  }

  private companion object {
    const val MS = 1_000_000L
    const val INVALID = -1L
    const val PENDING = -2L
  }
}
