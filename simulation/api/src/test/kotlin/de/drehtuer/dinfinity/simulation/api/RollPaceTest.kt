package de.drehtuer.dinfinity.simulation.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much of a frame a roll is worth (`docs/physics-and-rendering.md`, "The
 * simulation clock").
 *
 * The whole of the pace is one function of two arguments, so the whole of it
 * is checked here — including the part that matters most, which is that a roll
 * with a hand on it is not slowed by a millisecond.
 */
class RollPaceTest {
  @Test
  fun `a roll being driven is worth every bit of the frame it took`() {
    // The one thing pacing must never touch. A player shaking the phone is
    // driving the dice, and dice that answer a hand a beat late are the only
    // way this mechanism could make the app worse.
    assertEquals(FRAME_SECONDS, RollPace.secondsFor(FRAME_SECONDS, driven = true), 0.0)
  }

  @Test
  fun `a roll being watched is worth less, so the dice can be seen to land`() {
    assertEquals(FRAME_SECONDS * RollPace.WATCHED, RollPace.secondsFor(FRAME_SECONDS, driven = false), TOLERANCE)
  }

  @Test
  fun `the pace slows a roll down and never speeds one up`() {
    // Faster than real time would overshoot a shake sample waiting on a step
    // the world had not reached yet, and would be the app deciding a roll was
    // over before the hand had finished throwing it (`ShakeDriver`).
    assertTrue(RollPace.WATCHED <= 1.0, "a watched roll ran faster than the clock on the wall")
    assertTrue(RollPace.WATCHED > 0.0, "a watched roll would never finish")
  }

  @Test
  fun `a frame worth no time is worth no time at whatever pace`() {
    // The first frame of a roll, which has nothing before it to measure
    // against (`TrayLoop`).
    assertEquals(0.0, RollPace.secondsFor(0.0, driven = false), 0.0)
    assertEquals(0.0, RollPace.secondsFor(0.0, driven = true), 0.0)
  }

  @Test
  fun `the pace is a scale, so twice the frame is twice the simulated time`() {
    // What keeps a paced roll the same roll: the same steps in the same order,
    // asked for over more wall clock. A pace that was not linear in the frame
    // time would make a stuttering phone roll differently from a smooth one.
    val one = RollPace.secondsFor(FRAME_SECONDS, driven = false)
    val two = RollPace.secondsFor(FRAME_SECONDS * 2, driven = false)

    assertEquals(one * 2, two, TOLERANCE)
  }

  @Test
  fun `the pace is flat, so letting go is the only place the speed changes`() {
    // Deliberately not an easing curve. A pace that changed while the dice
    // were in view would be indistinguishable from a phone dropping frames,
    // and would make the roll's own smoothness unmeasurable.
    val early = RollPace.secondsFor(FRAME_SECONDS, driven = false)
    val late = RollPace.secondsFor(FRAME_SECONDS, driven = false)

    assertEquals(early, late, 0.0)
  }

  @Test
  fun `a watched roll takes about twice the wall clock the physics takes`() {
    // The figure a person judges on the phone: 20d20 settle in a measured
    // 0.81 s of simulated time, and this is what that becomes on the screen.
    val wallClock = MEASURED_SETTLE_SECONDS / RollPace.WATCHED

    assertTrue(wallClock > MEASURED_SETTLE_SECONDS, "a roll still over before the eye reached it")
    assertEquals(EXPECTED_WATCHED_SECONDS, wallClock, 0.01)
  }

  @Test
  fun `a roll that will not land stops being watched and starts being waited for`() {
    // `100d4` runs the twelve-second cap out, and the cap counts simulated
    // time: at a flat half that is twenty-four seconds of watching dice that
    // were never going to stop, on the one formula a device session already
    // reported as stuck.
    val late = RollPace.WATCHED_STEPS
    assertEquals(FRAME_SECONDS, RollPace.secondsFor(FRAME_SECONDS, driven = false, stepsTaken = late), TOLERANCE)
    assertEquals(FRAME_SECONDS, RollPace.secondsFor(FRAME_SECONDS, driven = false, stepsTaken = late + 1), TOLERANCE)
  }

  @Test
  fun `and every roll that behaves is paced from the first step to the last`() {
    // The measured ninety-ninth percentile for 20d20 is 1.47 s, so the bound
    // has to sit well clear of it or it would be pacing the ordinary roll
    // differently at the end than at the start.
    val p99 = (MEASURED_P99_SECONDS * SettleRule.STEPS_PER_SECOND).toInt()
    assertTrue(
      p99 < RollPace.WATCHED_STEPS,
      "the bound falls inside the rolls that land: $p99 steps of ${RollPace.WATCHED_STEPS}",
    )
    assertEquals(
      FRAME_SECONDS * RollPace.WATCHED,
      RollPace.secondsFor(FRAME_SECONDS, driven = false, stepsTaken = p99),
      TOLERANCE,
    )
  }

  @Test
  fun `a hand still beats the bound, because a shake is answered now or not at all`() {
    assertEquals(
      FRAME_SECONDS,
      RollPace.secondsFor(FRAME_SECONDS, driven = true, stepsTaken = 0),
      TOLERANCE,
    )
  }

  private companion object {
    /** One frame of a 60 Hz panel. */
    const val FRAME_SECONDS = 1.0 / 60.0

    /** The median settle of 20d20 on the reference device, over 200 rolls. */
    const val MEASURED_SETTLE_SECONDS = 0.81

    /** And what the player waits for it, at the pace chosen here. */
    const val EXPECTED_WATCHED_SECONDS = 1.62

    /** And the ninety-ninth, which the pace has to cover whole. */
    const val MEASURED_P99_SECONDS = 1.47

    const val TOLERANCE = 1e-12
  }
}
