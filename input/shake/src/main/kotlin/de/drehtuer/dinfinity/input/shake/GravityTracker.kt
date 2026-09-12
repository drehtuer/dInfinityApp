package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * Which way is down, as the phone turns.
 *
 * Tilt the phone and the dice slide; hold it flat and they settle
 * (`docs/physics-and-rendering.md`). That is done by rotating the *gravity
 * vector* rather than the tray, so the tray stays the screen and the dice
 * behave the way objects in a box in a tilted hand behave.
 *
 * The gyroscope reports how fast the phone is turning, so the orientation is
 * the integral of it. Integrating drifts — every gyroscope does — but a roll
 * lasts a couple of seconds and drift over a couple of seconds is far below
 * anything a die would notice. It is reset at the start of every shake rather
 * than carried between them, which is what keeps the drift bounded.
 */
class GravityTracker(
  private var direction: Vector3 = DOWN,
) {
  private var lastSampleNanos: Long = 0
  private var hasPrevious: Boolean = false

  /** Which way down currently points, in the tray's coordinates. */
  fun gravity(): Vector3 = direction

  /** Forgets everything, at the start of a shake. */
  fun reset() {
    direction = DOWN
    lastSampleNanos = 0
    hasPrevious = false
  }

  /**
   * Takes one gyroscope sample.
   *
   * @param atNanos the sample's own timestamp, from the sensor.
   * @param rateRadiansPerSecond how fast the phone is turning about each of
   *   its own axes.
   */
  fun sample(
    atNanos: Long,
    rateRadiansPerSecond: Vector3,
  ) {
    val previous = lastSampleNanos
    val first = !hasPrevious
    lastSampleNanos = atNanos
    hasPrevious = true
    // A zero timestamp is a timestamp. Using it as "no sample yet" would mean
    // the first one never established a baseline, and on a device where the
    // sensor clock starts at boot that is not a hypothetical.
    if (first || atNanos <= previous) return
    val seconds = (atNanos - previous) / NANOS_PER_SECOND
    if (seconds > LONGEST_GAP_SECONDS) return
    val angle = rateRadiansPerSecond.length * seconds
    if (angle < SMALLEST_ANGLE) return
    // The phone turning one way is the world turning the other.
    direction = Quaternion.about(rateRadiansPerSecond, -angle).rotate(direction).normalised()
  }

  private companion object {
    val DOWN = Vector3(0.0, 0.0, -1.0)
    const val NANOS_PER_SECOND = 1_000_000_000.0

    /**
     * A gap longer than this means samples were dropped — the app was
     * backgrounded, or the sensor stalled — and integrating across it would
     * invent a rotation that never happened.
     */
    const val LONGEST_GAP_SECONDS = 0.1

    /** Below this the sample is sensor noise, and rotating by it only adds drift. */
    const val SMALLEST_ANGLE = 1e-7
  }
}
