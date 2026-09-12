package de.drehtuer.dinfinity.input.shake

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shake state machine, played through at the speeds a hand actually moves.
 *
 * The interesting cases are the ones a single threshold would get wrong: the
 * quiet moment at the top of every swing, and the jolt of putting the phone
 * down on a table.
 */
class ShakeDetectorTest {
  private val detector = ShakeDetector()
  private val hard = ShakeThresholds.START_MM_PER_SECOND2 + 1
  private val quiet = ShakeThresholds.STOP_MM_PER_SECOND2 - 1

  @Test
  fun `a still phone is not being shaken`() {
    assertEquals(ShakeDetector.Event.None, detector.sample(0, 0.0))
    assertEquals(ShakeDetector.State.Idle, detector.state)
  }

  @Test
  fun `a single jolt is not a shake`() {
    detector.sample(0, hard)
    assertEquals(ShakeDetector.Event.None, detector.sample(20, 0.0))
    assertEquals(ShakeDetector.State.Idle, detector.state)
  }

  @Test
  fun `moving hard for long enough is a shake`() {
    detector.sample(0, hard)
    assertEquals(ShakeDetector.Event.None, detector.sample(50, hard))
    assertEquals(ShakeDetector.Event.Started, detector.sample(ShakeThresholds.START_MILLIS, hard))
    assertEquals(ShakeDetector.State.Shaking, detector.state)
  }

  @Test
  fun `the quiet moment at the top of a swing does not end the shake`() {
    start()
    detector.sample(200, quiet)
    assertEquals(ShakeDetector.State.Stopping, detector.state)
    assertEquals(ShakeDetector.Event.None, detector.sample(300, hard))
    assertEquals(ShakeDetector.State.Shaking, detector.state)
  }

  @Test
  fun `going quiet for long enough ends the shake`() {
    start()
    detector.sample(200, quiet)
    assertEquals(ShakeDetector.Event.None, detector.sample(300, quiet))
    assertEquals(ShakeDetector.Event.Ended, detector.sample(200 + ShakeThresholds.STOP_MILLIS, quiet))
    assertEquals(ShakeDetector.State.Idle, detector.state)
  }

  @Test
  fun `a shake that never stops is ended anyway`() {
    start()
    var event = ShakeDetector.Event.None
    var at = ShakeThresholds.START_MILLIS
    while (event == ShakeDetector.Event.None && at < ShakeThresholds.MAX_SESSION_MILLIS * 2) {
      at += 100
      event = detector.sample(at, hard)
    }
    assertEquals(ShakeDetector.Event.Ended, event)
    assertEquals(ShakeDetector.State.Idle, detector.state)
  }

  @Test
  fun `a shake can start again after one has ended`() {
    start()
    detector.sample(1_000, quiet)
    detector.sample(1_000 + ShakeThresholds.STOP_MILLIS, quiet)
    detector.sample(2_000, hard)
    assertEquals(ShakeDetector.Event.Started, detector.sample(2_000 + ShakeThresholds.START_MILLIS, hard))
  }

  @Test
  fun `a movement between the two thresholds keeps whatever was happening`() {
    val middling = (ShakeThresholds.START_MM_PER_SECOND2 + ShakeThresholds.STOP_MM_PER_SECOND2) / 2
    assertEquals(ShakeDetector.Event.None, detector.sample(0, middling))
    assertEquals(ShakeDetector.State.Idle, detector.state)
    start()
    detector.sample(500, middling)
    assertEquals(ShakeDetector.State.Shaking, detector.state)
  }

  @Test
  fun `resetting abandons a shake in progress`() {
    start()
    detector.reset()
    assertEquals(ShakeDetector.State.Idle, detector.state)
  }

  @Test
  fun `a recorder counts what it kept`() {
    val recorder = ShakeRecorder()
    assertEquals(0, recorder.size)
    recorder.record(
      0,
      de.drehtuer.dinfinity.simulation.api.Vector3.Zero,
      de.drehtuer.dinfinity.simulation.api.Vector3.Up,
    )
    assertEquals(1, recorder.size)
    recorder.reset()
    assertEquals(0, recorder.size)
  }

  @Test
  fun `the published thresholds are these`() {
    assertEquals(80L, ShakeThresholds.START_MILLIS)
    assertEquals(400L, ShakeThresholds.STOP_MILLIS)
    assertEquals(120, ShakeThresholds.SAMPLE_RATE_HZ)
  }

  private fun start() {
    detector.sample(0, hard)
    detector.sample(ShakeThresholds.START_MILLIS, hard)
  }
}
