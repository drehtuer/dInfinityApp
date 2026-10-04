package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ShakeSessionTest {
  private val session = ShakeSession()
  private val hard = Vector3(ShakeThresholds.START_MM_PER_SECOND2 + 100, 0.0, 0.0)
  private val quiet = Vector3(10.0, 0.0, 0.0)

  @Test
  fun `nothing is recorded before a shake is confirmed`() {
    session.acceleration(0, hard)
    assertFalse(session.shaking)
    assertTrue(session.recorded().isEmpty())
  }

  @Test
  fun `recording starts the moment the shake does, because that is when the dice exist`() {
    assertEquals(ShakeDetector.Event.Started, start())
    assertTrue(session.shaking)
    assertEquals(1, session.recorded().size)
    assertEquals(0, session.recorded().single().stepIndex)
  }

  @Test
  fun `moments land on simulation steps, not on wall-clock milliseconds`() {
    start()
    session.acceleration(ShakeThresholds.START_MILLIS + 100, hard)
    session.acceleration(ShakeThresholds.START_MILLIS + 200, hard)
    assertEquals(listOf(0, 12, 24), session.recorded().map(ShakeSample::stepIndex))
  }

  @Test
  fun `two moments in one step leave one, because the simulation has one step to give`() {
    start()
    session.acceleration(ShakeThresholds.START_MILLIS + 1, hard)
    session.acceleration(ShakeThresholds.START_MILLIS + 2, hard)
    assertEquals(1, session.recorded().size)
  }

  @Test
  fun `what is recorded is snapped to a grid, so it survives being written down`() {
    start()
    session.acceleration(ShakeThresholds.START_MILLIS + 100, Vector3(4_000.123_456, -7.9, 0.4))
    val recorded = session.recorded().last().accelerationMmPerSecond2
    assertEquals(4_000.0, recorded.x, 0.0)
    assertEquals(-8.0, recorded.y, 0.0)
    assertEquals(0.0, recorded.z, 0.0)
  }

  @Test
  fun `a recorded shake replays to exactly itself`() {
    start()
    (1..20).forEach { step ->
      session.acceleration(ShakeThresholds.START_MILLIS + step * 8L, Vector3(4_000.0 + step, step * 3.7, 0.0))
    }
    val once = session.recorded()
    // Written down and read back — which is the only thing quantising is for.
    val again = once.map { ShakeSample(it.stepIndex, it.accelerationMmPerSecond2, it.gravity) }
    assertEquals(once, again)
  }

  @Test
  fun `gravity starts pointing down`() {
    assertEquals(Vector3(0.0, 0.0, -1.0), session.gravity())
  }

  @Test
  fun `turning the phone turns gravity the other way`() {
    // A quarter turn about x, over a tenth of a second.
    session.rotation(0, Vector3.Zero)
    session.rotation(100_000_000L, Vector3((PI / 2) / 0.1, 0.0, 0.0))
    val gravity = session.gravity()
    assertEquals(1.0, gravity.length, 1e-9)
    assertNotEquals(Vector3(0.0, 0.0, -1.0), gravity)
    assertEquals(0.0, gravity.z, 1e-9)
  }

  @Test
  fun `a gap in the samples does not invent a rotation that never happened`() {
    session.rotation(0, Vector3.Zero)
    session.rotation(5_000_000_000L, Vector3(10.0, 0.0, 0.0))
    assertEquals(Vector3(0.0, 0.0, -1.0), session.gravity())
  }

  @Test
  fun `a sample stamped no later than the one before turns nothing`() {
    // Sensor batches can repeat a timestamp or deliver one out of order. There
    // is no time between them to integrate, and a negative interval would
    // turn gravity the wrong way.
    session.rotation(100_000_000L, Vector3.Zero)
    session.rotation(100_000_000L, Vector3(10.0, 0.0, 0.0))
    session.rotation(50_000_000L, Vector3(10.0, 0.0, 0.0))
    assertEquals(Vector3(0.0, 0.0, -1.0), session.gravity())
  }

  @Test
  fun `sensor noise does not accumulate into a tilt`() {
    session.rotation(0, Vector3.Zero)
    (1..10_000).forEach { step ->
      session.rotation(step * 8_000_000L, Vector3(1e-9, -1e-9, 1e-9))
    }
    assertEquals(Vector3(0.0, 0.0, -1.0), session.gravity())
  }

  @Test
  fun `gravity is recorded with the motion, so a replay tilts the same way`() {
    start()
    session.rotation(0, Vector3.Zero)
    session.rotation(100_000_000L, Vector3(3.0, 0.0, 0.0))
    session.acceleration(ShakeThresholds.START_MILLIS + 100, hard)
    val gravity = session.recorded().last().gravity
    assertEquals(1.0, gravity.length, 1e-3)
    assertNotEquals(Vector3(0.0, 0.0, -1.0), gravity)
  }

  @Test
  fun `a new shake forgets the one before it`() {
    start()
    session.acceleration(ShakeThresholds.START_MILLIS + 100, hard)
    end()
    session.acceleration(5_000, hard)
    session.acceleration(5_000 + ShakeThresholds.START_MILLIS, hard)
    assertEquals(1, session.recorded().size)
  }

  @Test
  fun `a shake keeps recording through the quiet part of a swing`() {
    start()
    session.acceleration(ShakeThresholds.START_MILLIS + 100, quiet)
    assertTrue(session.shaking)
    assertEquals(2, session.recorded().size)
  }

  @Test
  fun `a session with no gyroscope at all still records, pointing straight down`() {
    start()
    session.acceleration(ShakeThresholds.START_MILLIS + 100, hard)
    assertEquals(Vector3(0.0, 0.0, -1.0), session.recorded().last().gravity)
  }

  @Test
  fun `resetting throws the session away`() {
    start()
    session.reset()
    assertFalse(session.shaking)
    assertTrue(session.recorded().isEmpty())
    assertEquals(Vector3(0.0, 0.0, -1.0), session.gravity())
  }

  @Test
  fun `a second shake at dice already in the air goes on numbering from the first`() {
    // The bug this is about: the recorder's clock is the roll's clock, so a
    // second shake that restarted it numbered its moments from zero — naming
    // steps the running roll took a second ago, which `ShakeDriver` can never
    // reach. Shaking the phone at moving dice did nothing at all
    // (`docs/physics-and-rendering.md`, "Shake input").
    start()
    end()
    val before = session.recorded().last().stepIndex

    startAgain(atMillis = 2_000)

    assertTrue(
      "a continuing shake drives steps after the ones already driven",
      session.recorded().last().stepIndex > before,
    )
  }

  @Test
  fun `a continuing shake's moments land on the steps its wall time says`() {
    start()
    end()

    // Two seconds after the dice were spawned, at 120 steps a second.
    startAgain(atMillis = 2_000)

    assertEquals(240, session.recorded().last().stepIndex)
  }

  @Test
  fun `a shake with no roll in the air starts the clock over, as it always did`() {
    start()
    end()

    session.acceleration(2_000, hard)
    session.acceleration(2_000 + ShakeThresholds.START_MILLIS, hard)

    // A throw of its own: its first moment is step zero, because that is when
    // its dice are spawned.
    assertEquals(0, session.recorded().first().stepIndex)
  }

  @Test
  fun `a continuing shake keeps the gravity the roll was already being driven by`() {
    start()
    // A quarter turn about the tray's long axis, integrated into `down`.
    session.rotation(atNanos = 0, rateRadiansPerSecond = Vector3.Zero)
    session.rotation(atNanos = 500_000_000, rateRadiansPerSecond = Vector3(PI, 0.0, 0.0))
    val turned = session.gravity()
    end()

    startAgain(atMillis = 2_000)

    // Not re-anchored to straight down: it is the same roll, in the same frame.
    assertEquals(turned, session.gravity())
  }

  @Test
  fun `a phone swung on the wrist drives the dice no further up the tray than down it`() {
    // The owner's report from the Pixel 10a: the dice gathered at the top of
    // the screen whichever way the phone was shaken. A swing about the wrist
    // pulls the sensor towards the hand on every stroke, both ways, and that
    // pull applied inverse is a steady push up the tray (decision 87).
    val arm = Vector3(100.0, 0.0, 0.0)
    val frequency = 2 * PI * 3.0
    val amplitude = 0.4
    var pull = 0.0
    var millis = 0L
    while (millis <= 2_000) {
      val t = millis / 1_000.0
      val rate = Vector3(0.0, 0.0, amplitude * frequency * cos(frequency * t))
      val turning = -amplitude * frequency * frequency * sin(frequency * t)
      // The stroke, across the tray, and the pull towards the wrist.
      val stroke = Vector3(0.0, turning * arm.x, 0.0)
      val towardsTheWrist = SwingCorrection.pullOf(rate, arm)
      session.rotation(millis * 1_000_000, rate)
      session.acceleration(millis, stroke + towardsTheWrist)
      if (session.shaking) pull += towardsTheWrist.x
      millis += 20
    }
    val recorded = session.recorded()
    val heard = pull / recorded.size
    val driven = recorded.sumOf { it.accelerationMmPerSecond2.x } / recorded.size

    assertTrue("the swing pulled towards the wrist: $heard", heard < -2_000)
    assertEquals("what drives the dice along the tray", 0.0, driven, -0.05 * heard)
  }

  @Test
  fun `the swing does not change what counts as a shake`() {
    // The detector hears the hand as it is; only what is recorded is corrected.
    session.rotation(0, Vector3(0.0, 0.0, 10.0))
    assertEquals(ShakeDetector.Event.Started, start())
  }

  /** A shake that begins while a roll is already running, so it throws nothing. */
  private fun startAgain(atMillis: Long) {
    session.acceleration(atMillis, hard) { false }
    session.acceleration(atMillis + ShakeThresholds.START_MILLIS, hard) { false }
  }

  private fun start(): ShakeDetector.Event {
    session.acceleration(0, hard)
    return session.acceleration(ShakeThresholds.START_MILLIS, hard)
  }

  private fun end() {
    session.acceleration(1_000, quiet)
    session.acceleration(1_000 + ShakeThresholds.STOP_MILLIS, quiet)
  }
}
