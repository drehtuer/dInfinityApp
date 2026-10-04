package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.abs

/**
 * Takes out of a shake the pull every swing of the wrist adds, which points
 * at the hand whichever way the phone is shaken
 * (`docs/physics-and-rendering.md`, "Shake input", and decision 87).
 *
 * A hand does not only carry a phone back and forth; it swings it, and the
 * pivot — the wrist, the grip — is below the sensor. Anything turning about a
 * point it is not on is pulled towards that point, by `ω × (ω × p)` where `p`
 * runs from the pivot to the sensor. The pull goes with the *square* of the
 * rate of turn, so it does not reverse when the swing does: every stroke, left
 * or right, up or down, towards the player or away, adds a little more towards
 * the bottom of the phone. The linear-acceleration sensor reports it with the
 * rest, applied inverse it is a steady push towards the top of the tray, and
 * the dice gather at the top of the screen whichever way the phone is shaken,
 * which is what the owner saw on the Pixel 10a. It is not the tray being carried. The tray here does
 * not turn — `ShakeDriver` keeps it level — so the one part of a swing that
 * only a turning tray would feel has no business reaching it.
 *
 * Where the pivot is depends on the grip and is not known in advance, so it is
 * **estimated from the shake itself**: the gyroscope says how fast the phone
 * is turning, every acceleration sample is one equation `a ≈ M(ω)·p` with
 * `M(ω) = ωωᵀ − |ω|²I`, and `p` is their least-squares answer. The rest of
 * what a hand does — carrying the phone, and the tangential part of the swing
 * — reverses every stroke and averages out of the fit, so what it finds is the
 * pull that does not. That pull, for the rate of turn now, is subtracted.
 *
 * What it does not do is filter the shake. A high-pass that removed the mean
 * would also do it, and would answer the hand stopping with a kick the other
 * way, because a filter cannot tell a pull that ended from a pull that never
 * was. This one is tied to the rate of turn: a phone that has stopped turning
 * gets nothing taken away.
 *
 * One estimate per shake, started when the detector first sees hard motion
 * ([newShake]) — a grip is a grip for one shake, not for the next one. Each
 * sample counts for the time it stands for, so a phone whose sensors run at
 * 200 Hz is not four times as sure of itself as one at 50.
 */
class SwingCorrection {
  private var rate: Vector3 = Vector3.Zero

  // The normal equations Σ MᵀM and Σ Mᵀa, M symmetric. ΣMᵀM is kept as its
  // six distinct entries: xx, xy, xz, yy, yz, zz.
  private val normal = DoubleArray(SYMMETRIC_ENTRIES)
  private val moment = DoubleArray(AXES)
  private var lastMillis: Long = NO_SAMPLE

  /** Takes the latest rate of turn, in the tray's axes, in rad/s. */
  fun rotation(rateRadiansPerSecond: Vector3) {
    rate = rateRadiansPerSecond
  }

  /**
   * Forgets the estimate, at the start of a shake. The last rate of turn and
   * the last sample's time are kept: both are still true.
   */
  fun newShake() {
    normal.fill(0.0)
    moment.fill(0.0)
  }

  /**
   * Where the sensor is from the pivot it is being swung about, in mm and the
   * tray's axes, as far as the shake so far can tell. Zero until the phone has
   * turned enough to say.
   */
  fun pivotToSensorMm(): Vector3 {
    val n = normal
    val solved =
      solve(
        n[XX] + DAMPING,
        n[XY],
        n[XZ],
        n[YY] + DAMPING,
        n[YZ],
        n[ZZ] + DAMPING,
        Vector3(moment[0], moment[1], moment[2]),
      )
    val size = solved.length
    return if (size <= LONGEST_ARM_MM) solved else solved * (LONGEST_ARM_MM / size)
  }

  /**
   * Takes one acceleration sample into the estimate, and answers it with the
   * swing's pull taken out.
   *
   * @param atMillis the sample's own timestamp; how long since the last one is
   *   how much this one counts. The first sample counts for nothing, having no
   *   last one, and a gap longer than [LONGEST_GAP_SECONDS] counts as that.
   */
  fun correct(
    atMillis: Long,
    accelerationMmPerSecond2: Vector3,
  ): Vector3 {
    val previous = lastMillis
    lastMillis = atMillis
    val seconds = if (previous == NO_SAMPLE) 0.0 else secondsBetween(previous, atMillis)
    val w = rate
    val spin = w dot w
    // M·a = ω(ω·a) − |ω|²a, and MᵀM = M² = −|ω|²M.
    val a = accelerationMmPerSecond2
    val along = w dot a
    moment[0] += seconds * (w.x * along - spin * a.x)
    moment[1] += seconds * (w.y * along - spin * a.y)
    moment[2] += seconds * (w.z * along - spin * a.z)
    val weight = seconds * spin
    normal[XX] += weight * (spin - w.x * w.x)
    normal[XY] -= weight * w.x * w.y
    normal[XZ] -= weight * w.x * w.z
    normal[YY] += weight * (spin - w.y * w.y)
    normal[YZ] -= weight * w.y * w.z
    normal[ZZ] += weight * (spin - w.z * w.z)
    return accelerationMmPerSecond2 - pullOf(w, pivotToSensorMm())
  }

  internal companion object {
    private const val AXES = 3
    private const val SYMMETRIC_ENTRIES = 6
    private const val XX = 0
    private const val XY = 1
    private const val XZ = 2
    private const val YY = 3
    private const val YZ = 4
    private const val ZZ = 5

    /**
     * How much turning, in (rad/s)⁴·s, the estimate wants before it believes a
     * pivot: about a quarter of a second of a swing at 3 rad/s. Below it the
     * answer is pulled towards no pivot at all, so a phone carried rather than
     * swung is left alone and a single noisy reading cannot invent an arm.
     *
     * Measured rather than derived (decision 87): over 300 synthetic wrist
     * shakes in each direction, a tenth of this pushed the up-and-down shake's
     * dice towards the bottom, three times it left a sixth of the pull in and
     * thirty times two thirds of it; this was the one that left every
     * direction within a few hundredths of the middle.
     */
    const val DAMPING: Double = 20.0

    /** A gap longer than this is dropped samples, and counts as no more than this. */
    const val LONGEST_GAP_SECONDS: Double = 0.05

    private const val MILLIS_PER_SECOND = 1_000.0
    private const val NO_SAMPLE = Long.MIN_VALUE

    /**
     * No hand swings a phone from further than a forearm away. A fit that says
     * otherwise is being fooled, and this is as far as it is believed.
     */
    const val LONGEST_ARM_MM: Double = 300.0

    private const val SINGULAR = 1e-12

    /** How long one sample stands for: the time since the last, held to a sensible range. */
    fun secondsBetween(
      previousMillis: Long,
      atMillis: Long,
    ): Double = ((atMillis - previousMillis) / MILLIS_PER_SECOND).coerceIn(0.0, LONGEST_GAP_SECONDS)

    /** The pull towards the pivot a phone turning at [rate] feels: ω × (ω × p). */
    fun pullOf(
      rate: Vector3,
      pivotToSensor: Vector3,
    ): Vector3 = rate * (rate dot pivotToSensor) - pivotToSensor * (rate dot rate)

    /** Solves the symmetric 3×3 system [[a, b, c], [b, d, e], [c, e, f]]·p = r. */
    @Suppress("LongParameterList")
    fun solve(
      a: Double,
      b: Double,
      c: Double,
      d: Double,
      e: Double,
      f: Double,
      r: Vector3,
    ): Vector3 {
      val c00 = d * f - e * e
      val c01 = c * e - b * f
      val c02 = b * e - c * d
      val determinant = a * c00 + b * c01 + c * c02
      if (abs(determinant) < SINGULAR) return Vector3.Zero
      val c11 = a * f - c * c
      val c12 = b * c - a * e
      val c22 = a * d - b * b
      return Vector3(
        (c00 * r.x + c01 * r.y + c02 * r.z) / determinant,
        (c01 * r.x + c11 * r.y + c12 * r.z) / determinant,
        (c02 * r.x + c12 * r.y + c22 * r.z) / determinant,
      )
    }
  }
}
