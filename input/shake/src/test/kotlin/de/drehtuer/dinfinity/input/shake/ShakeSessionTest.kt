package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

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

  private fun start(): ShakeDetector.Event {
    session.acceleration(0, hard)
    return session.acceleration(ShakeThresholds.START_MILLIS, hard)
  }

  private fun end() {
    session.acceleration(1_000, quiet)
    session.acceleration(1_000 + ShakeThresholds.STOP_MILLIS, quiet)
  }
}
