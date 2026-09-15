package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.roundToLong

/**
 * Turns the phone's motion into the record a roll is driven by, and can be
 * replayed from (`docs/physics-and-rendering.md`, "Shake input").
 *
 * Two things happen here and both matter.
 *
 * The samples are **placed on simulation steps**, not on wall-clock moments.
 * Sensors arrive when they arrive and the simulation advances in fixed 1/120 s
 * steps; a record indexed by step feeds those steps identically in normal mode,
 * where it is consumed as it arrives, and in power-saving mode, where the whole
 * session is replayed as a batch afterwards. Same seed, same record, same roll.
 *
 * The samples are **quantised**. Recording raw sensor doubles would make a
 * roll unreproducible from its own record, because the record has to be
 * written, stored and read back; a snapped value survives that unchanged. The
 * grid is far finer than anything a die could notice.
 *
 * The acceleration is stored as the phone's, and applied to the *tray*
 * inverted when the roll runs — which is what makes a shake feel like a cupped
 * hand rather than like impulses fired at the dice.
 */
class ShakeRecorder {
  private val samples = mutableListOf<ShakeSample>()
  private var firstMillis: Long = -1

  /** Everything recorded so far, in step order, ready to drive a throw. */
  fun recorded(): List<ShakeSample> = samples.toList()

  /** How many moments were kept. */
  val size: Int get() = samples.size

  /** Forgets the session, at the start of a shake. */
  fun reset() {
    samples.clear()
    firstMillis = -1
  }

  /**
   * Records one moment.
   *
   * A moment that lands on a step already recorded replaces it, which happens
   * when the sensors outrun the simulation's 120 Hz. Usually they do not —
   * `SENSOR_DELAY_GAME` is about 50 Hz — so most steps get no sample at all,
   * and holding the last one across them is `ShakeDriver`'s job.
   *
   * Hands back the sample it stored, so a roll already in progress can be
   * given the same moment the record keeps — one value, quantised once, driving
   * the live roll and any replay of it alike.
   *
   * A moment past [ShakeSample.MAX_RECORDED] is handed back but not kept. A
   * shake session may run for thirty seconds and a roll may not run past
   * twelve, so that moment names a step nothing will ever take; the record
   * stops there rather than growing for as long as the hand does.
   */
  fun record(
    atMillis: Long,
    accelerationMmPerSecond2: Vector3,
    gravity: Vector3,
  ): ShakeSample {
    if (firstMillis < 0) firstMillis = atMillis
    val step = stepOf(atMillis)
    val sample =
      ShakeSample(
        stepIndex = step,
        accelerationMmPerSecond2 =
          quantise(accelerationMmPerSecond2, ShakeThresholds.ACCELERATION_QUANTUM_MM_PER_SECOND2),
        gravity = quantise(gravity, ShakeThresholds.DIRECTION_QUANTUM),
      )
    if (sample.drivesAStep) {
      val existing = samples.indexOfLast { it.stepIndex == step }
      if (existing >= 0) samples[existing] = sample else samples += sample
    }
    return sample
  }

  /** Which simulation step a moment belongs to, counted from the start of the shake. */
  fun stepOf(atMillis: Long): Int {
    val since = (atMillis - firstMillis).coerceAtLeast(0)
    return (since * ShakeThresholds.SAMPLE_RATE_HZ / MILLIS_PER_SECOND).toInt()
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1_000L

    /** A vector with every component snapped to a grid, so it survives a round trip. */
    fun quantise(
      vector: Vector3,
      quantum: Double,
    ): Vector3 =
      Vector3(
        snap(vector.x, quantum),
        snap(vector.y, quantum),
        snap(vector.z, quantum),
      )

    fun snap(
      value: Double,
      quantum: Double,
    ): Double = (value / quantum).roundToLong() * quantum
  }
}
