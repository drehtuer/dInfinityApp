package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.Release
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * A synthetic hand for the harness: a short shake that ends in a throw
 * (`docs/physics-and-rendering.md`, "The hand lets go"; decision 105).
 *
 * Four strokes along one line — back, forth, back, forth — each pushed and braked
 * as hard as a shake is ([PUSH_MM_PER_SECOND2], over the shake detector's
 * threshold), the last one going the way the roll's own seed says, and then
 * quiet for long enough that the hand lets go. The last stroke's peak is the
 * hand speed that [Release.of] turns into [speedMmPerSecond], so a run asked
 * for a toss at that speed gets one, give or take the rounding of a stroke to
 * whole steps.
 *
 * A sample on every step: this stands in for what `ShakeDriver` is handed,
 * which is one sample a step at most, not for the sensor's own 50 Hz.
 */
object HandStroke {
  /** The moments of a shake that ends in a toss at about [speedMmPerSecond], along a line drawn from [seed]. */
  fun of(
    speedMmPerSecond: Double,
    seed: Long,
  ): List<ShakeSample> {
    val angle = Seeds.stream(seed, 0, Seeds.RELEASE).nextDouble() * 2 * PI
    val line = Vector3(Exact.cos(angle), Exact.sin(angle), 0.0)
    val peak = speedMmPerSecond / Release.GAIN
    val half = (peak * STEPS_PER_SECOND / PUSH_MM_PER_SECOND2).roundToInt().coerceAtLeast(1)
    val moments = mutableListOf<ShakeSample>()

    fun push(along: Vector3) {
      repeat(half) { moments += ShakeSample(moments.size, along * PUSH_MM_PER_SECOND2, DOWN) }
      repeat(half) { moments += ShakeSample(moments.size, along * -PUSH_MM_PER_SECOND2, DOWN) }
    }
    push(-line)
    push(line)
    push(-line)
    push(line)
    repeat(QUIET_STEPS) { moments += ShakeSample(moments.size, Vector3.Zero, DOWN) }
    return moments
  }

  /** How hard each stroke pushes and brakes: twice the shake detector's threshold. */
  const val PUSH_MM_PER_SECOND2: Double = 12_000.0

  /** How long the hand is quiet after the last stroke: well past letting go. */
  const val QUIET_STEPS: Int = 60

  private const val STEPS_PER_SECOND: Int = 120

  private val DOWN = Vector3(0.0, 0.0, -1.0)
}
