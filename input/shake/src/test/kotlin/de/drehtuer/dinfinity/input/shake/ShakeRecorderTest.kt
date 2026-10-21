package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The record a shake leaves behind (`docs/physics-and-rendering.md`, "Shake
 * input").
 *
 * [ShakeSession] covers what a whole shake does; this is the one thing the
 * recorder decides on its own and that a session is a clumsy way to ask about
 * — how long a record is allowed to get when the hand does not stop.
 */
class ShakeRecorderTest {
  @Test
  fun `a moment is placed on the simulation step it belongs to`() {
    val recorder = ShakeRecorder()

    recorder.record(atMillis = 0, accelerationMmPerSecond2 = shove, gravity = down)
    recorder.record(atMillis = 100, accelerationMmPerSecond2 = shove, gravity = down)

    assertEquals(listOf(0, 12), recorder.recorded().map(ShakeSample::stepIndex))
  }

  @Test
  fun `a shake that outlasts the roll stops being recorded`() {
    // A session may run for thirty seconds and a roll is force-settled at
    // twelve, so everything after that names a step nothing will ever take. The
    // record stops there rather than growing for as long as the arm does.
    val recorder = ShakeRecorder()

    var millis = 0L
    while (millis < ShakeThresholds.MAX_SESSION_MILLIS) {
      recorder.record(atMillis = millis, accelerationMmPerSecond2 = shove, gravity = down)
      millis += A_SENSOR_GAP_MILLIS
    }

    assertTrue("the record ran past the last step a roll can take", recorder.size <= ShakeSample.MAX_RECORDED)
    assertTrue(
      "a moment past the cap was kept",
      recorder.recorded().all(ShakeSample::drivesAStep),
    )
  }

  @Test
  fun `a moment past the cap still reaches a roll that is somehow still going`() {
    // Handed back rather than swallowed: what this refuses to do is *keep* it.
    // Whether it drives anything is the driver's to decide, and it will not,
    // but the two decisions stay in the places that own them.
    val recorder = ShakeRecorder()
    recorder.record(atMillis = 0, accelerationMmPerSecond2 = shove, gravity = down)

    val late = recorder.record(atMillis = PAST_THE_CAP_MILLIS, accelerationMmPerSecond2 = shove, gravity = down)

    assertTrue("a moment past the cap was not handed back", late.stepIndex >= ShakeSample.MAX_RECORDED)
    assertEquals("and it was kept anyway", 1, recorder.size)
  }

  @Test
  fun `resetting forgets the record and starts the step clock again`() {
    // A new shake is numbered from its own first moment. Keeping the old
    // origin would file the new shake's first moment seconds into a roll
    // that has not taken a step yet.
    val recorder = ShakeRecorder()
    recorder.record(atMillis = 0, accelerationMmPerSecond2 = shove, gravity = down)
    recorder.record(atMillis = 100, accelerationMmPerSecond2 = shove, gravity = down)

    recorder.reset()
    assertEquals(0, recorder.size)

    recorder.record(atMillis = 5_000, accelerationMmPerSecond2 = shove, gravity = down)
    assertEquals(listOf(0), recorder.recorded().map(ShakeSample::stepIndex))
  }

  private val shove = Vector3(5_000.0, 0.0, 0.0)
  private val down = Vector3(0.0, 0.0, -1.0)

  private companion object {
    /** About `SENSOR_DELAY_GAME`, which is what the sensors actually deliver. */
    const val A_SENSOR_GAP_MILLIS = 20L

    /** Comfortably past the twelve-second cap. */
    const val PAST_THE_CAP_MILLIS = 20_000L
  }
}
