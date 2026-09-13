package de.drehtuer.dinfinity.simulation.api

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The clock that lets a roll be watched without letting the watcher change it
 * (`docs/physics-and-rendering.md`, "The simulation clock").
 *
 * The property worth asserting is not "it divides": it is that however the
 * frame times fall, the *same* steps come out, in the same order, in the same
 * total — because that is the only reason a rolled-on-screen throw and a
 * power-saving throw can be the same roll.
 */
class FrameClockTest {
  private val step = SettleRule.TIMESTEP_SECONDS

  @Test
  fun `a frame exactly one step long takes one step`() {
    val clock = FrameClock()

    assertEquals(1, clock.advance(step))
    assertClose(0.0, clock.interpolation, "nothing is left over, so nothing is between steps")
  }

  @Test
  fun `a frame shorter than a step takes none and moves the interpolation on`() {
    // A 240 Hz panel watching a 120 Hz simulation: half the frames draw the
    // same two states again, blended further along.
    val clock = FrameClock()

    assertEquals(0, clock.advance(step / 2))
    assertClose(0.5, clock.interpolation)
  }

  @Test
  fun `a 60 Hz frame takes two steps of a 120 Hz simulation`() {
    val clock = FrameClock()

    assertEquals(2, clock.advance(1.0 / 60.0))
  }

  @Test
  fun `time left over is carried rather than dropped`() {
    // Three frames of a step and a half are four steps and a half, not three:
    // the halves add up, and a clock that threw them away would run the
    // simulation a third slow.
    val clock = FrameClock()
    val taken = (1..3).sumOf { clock.advance(step * 1.5) }

    assertEquals(4, taken)
    assertClose(0.5, clock.interpolation)
  }

  @Test
  fun `however a second is cut up, it is the same number of steps`() {
    // The point of the whole class, stated as a property: the frame times may
    // be anything at all, and the simulation still sees exactly the steps one
    // second holds.
    val clock = FrameClock()
    val frames = listOf(0.001, 0.033, 0.007, 0.05, 0.016, 0.016, 0.016, 0.111, 0.25, 0.5)
    val taken = frames.sumOf { clock.advance(it) }

    assertClose(1.0, frames.sum(), "the frames do not add up to a second")
    assertClose(
      SettleRule.STEPS_PER_SECOND.toDouble(),
      taken + clock.droppedSteps + clock.interpolation,
      "a second of frames is a second of steps, taken or dropped",
    )
  }

  @Test
  fun `the catch-up cap is the published figure`() {
    // `docs/physics-and-rendering.md` says "up to 4 sub-steps per rendered
    // frame", and a document that says a number the code does not use is worse
    // than one that says nothing.
    assertEquals(4, FrameClock.MAX_STEPS_PER_FRAME)
  }

  @Test
  fun `a frame that ran long enough to owe more than the cap is not paid in full`() {
    // The app coming back from the background. Without the cap this one frame
    // is a hundred and twenty steps and a visible freeze.
    val clock = FrameClock(maxStepsPerFrame = 4)

    assertEquals(4, clock.advance(1.0))
    assertEquals(SettleRule.STEPS_PER_SECOND - 4, clock.droppedSteps)
  }

  @Test
  fun `the time behind a dropped step is dropped with it`() {
    // The spiral this exists to prevent: if the unpaid time were carried, the
    // next frame would owe more than this one did, and the one after that more
    // again, and a phone that fell behind once would never catch up.
    val clock = FrameClock(maxStepsPerFrame = 4)
    clock.advance(1.0)

    assertEquals(2, clock.advance(1.0 / 60.0), "the next ordinary frame is not an ordinary frame again")
  }

  @Test
  fun `the interpolation is always a moment between two steps`() {
    // It is handed straight to a render frame, which refuses anything else.
    val clock = FrameClock(maxStepsPerFrame = 2)
    listOf(0.0, step * 0.25, 1.0, step * 7.9, 0.004).forEach { elapsed ->
      clock.advance(elapsed)
      assertTrue(clock.interpolation in 0.0..1.0, "$elapsed left the clock at ${clock.interpolation}")
    }
  }

  @Test
  fun `a frame that took no time takes no step`() {
    val clock = FrameClock()

    assertEquals(0, clock.advance(0.0))
    assertEquals(0, clock.droppedSteps)
  }

  @Test
  fun `resetting forgets the carried time`() {
    val clock = FrameClock(maxStepsPerFrame = 1)
    clock.advance(step * 1.5)
    clock.reset()

    assertClose(0.0, clock.interpolation)
    assertEquals(0, clock.droppedSteps)
    assertEquals(0, clock.advance(step * 0.5), "the carried half step was not forgotten")
  }

  @Test
  fun `a frame cannot have taken a negative length of time`() {
    val clock = FrameClock()

    assertFailsWith<IllegalArgumentException> { clock.advance(-step) }
  }

  @Test
  fun `a clock that may take no steps is refused`() {
    assertFailsWith<IllegalArgumentException> { FrameClock(maxStepsPerFrame = 0) }
  }

  private fun assertClose(
    expected: Double,
    actual: Double,
    message: String = "expected $expected but was $actual",
  ) = assertTrue(abs(expected - actual) < EPSILON, message)

  private companion object {
    const val EPSILON = 1e-9
  }
}
