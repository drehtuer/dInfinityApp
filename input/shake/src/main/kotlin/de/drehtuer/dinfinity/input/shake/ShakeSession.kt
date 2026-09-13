package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * One shake, from the first hard movement to the release: the detector, the
 * gravity tracker and the recorder wired together
 * (`docs/physics-and-rendering.md`, "Shake input").
 *
 * It is deliberately free of Android. `SensorShakeSource` turns
 * `SensorManager` callbacks into calls on this, and everything that decides
 * anything happens here — so a whole shake, including a thirty-second one and
 * a phone turned upside down, plays through a unit test without an emulator.
 */
class ShakeSession {
  private val detector = ShakeDetector()
  private val gravity = GravityTracker()
  private val recorder = ShakeRecorder()

  /**
   * The moment last recorded, or null when nothing was — before a shake is
   * confirmed, and after it has ended.
   *
   * This is what drives a roll that is *already running*: the dice are spawned
   * when the shake begins, so every sample after that has to reach them as it
   * arrives rather than waiting for the hand to stop
   * (`docs/physics-and-rendering.md`, "Shake input").
   */
  var latest: ShakeSample? = null
    private set

  /** True while the player is shaking. */
  val shaking: Boolean get() =
    detector.state == ShakeDetector.State.Shaking ||
      detector.state == ShakeDetector.State.Stopping

  /** What has been recorded so far, ready to drive a throw. */
  fun recorded(): List<ShakeSample> = recorder.recorded()

  /** Which way down currently points. */
  fun gravity(): Vector3 = gravity.gravity()

  /** Throws away the session, for a roll that was cancelled or has been used. */
  fun reset() {
    detector.reset()
    gravity.reset()
    recorder.reset()
    latest = null
  }

  /**
   * Takes one linear-acceleration sample, with gravity already removed by the
   * sensor, and says whether the shake started or ended.
   *
   * Recording begins the moment a shake is confirmed and not before: the dice
   * are spawned then, and a record of the seconds before they existed would
   * drive steps that never ran.
   */
  fun acceleration(
    atMillis: Long,
    accelerationMmPerSecond2: Vector3,
  ): ShakeDetector.Event {
    val event = detector.sample(atMillis, accelerationMmPerSecond2.length)
    if (event == ShakeDetector.Event.Started) {
      recorder.reset()
      gravity.reset()
    }
    latest = if (shaking) recorder.record(atMillis, accelerationMmPerSecond2, gravity.gravity()) else null
    return event
  }

  /** Takes one gyroscope sample, which is what tilts gravity. */
  fun rotation(
    atNanos: Long,
    rateRadiansPerSecond: Vector3,
  ) {
    gravity.sample(atNanos, rateRadiansPerSecond)
  }
}
