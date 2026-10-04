package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * One shake, from the first hard movement to the release: the detector, the
 * gravity tracker, the swing correction and the recorder wired together
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
  private var swing = SwingCorrection()

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
    swing = SwingCorrection()
    latest = null
  }

  /**
   * Takes one linear-acceleration sample, with gravity already removed by the
   * sensor, and says whether the shake started or ended.
   *
   * What is recorded is the sample with the wrist's swing taken out
   * ([SwingCorrection]); what decides whether this is a shake is the sample
   * as it came.
   *
   * Recording begins the moment a shake is confirmed and not before: the dice
   * are spawned then, and a record of the seconds before they existed would
   * drive steps that never ran.
   *
   * @param threw called when a shake is confirmed, and answers whether dice
   *   were actually thrown for it. **That answer is what starts the clock
   *   over**, because a sample's step index is counted from the first moment
   *   the recorder saw and that moment is when the dice were spawned — so the
   *   recorder's clock *is* the roll's clock.
   *
   *   A second shake at dice still tumbling throws nothing and answers false.
   *   Numbered from zero its moments would name steps the running roll took a
   *   second ago, `ShakeDriver` would never reach them, and shaking a phone at
   *   moving dice would do nothing at all — which is what it used to do
   *   (`docs/physics-and-rendering.md`, "Shake input"). Answering false keeps
   *   the numbering on the running roll's clock, so the hand reaches it.
   */
  fun acceleration(
    atMillis: Long,
    accelerationMmPerSecond2: Vector3,
    threw: () -> Boolean = { true },
  ): ShakeDetector.Event {
    val wasIdle = detector.state == ShakeDetector.State.Idle
    // The detector hears the hand as it is, swing and all: its thresholds are
    // about how hard the phone is moving, and the swing is part of that.
    val event = detector.sample(atMillis, accelerationMmPerSecond2.length)
    // A new estimate of the wrist per shake, from its first hard moment rather
    // than from when it was confirmed, so the strokes before the dice existed
    // still teach it where the pivot is.
    if (wasIdle && detector.state != ShakeDetector.State.Idle) swing.newShake()
    val hand = swing.correct(atMillis, accelerationMmPerSecond2)
    if (event == ShakeDetector.Event.Started && threw()) {
      recorder.reset()
      gravity.reset()
    }
    latest = if (shaking) recorder.record(atMillis, hand, gravity.gravity()) else null
    return event
  }

  /**
   * Takes one gyroscope sample: what tilts gravity, and what says how much of
   * the next acceleration is only the wrist swinging the phone
   * ([SwingCorrection]).
   */
  fun rotation(
    atNanos: Long,
    rateRadiansPerSecond: Vector3,
  ) {
    gravity.sample(atNanos, rateRadiansPerSecond)
    swing.rotation(rateRadiansPerSecond)
  }
}
