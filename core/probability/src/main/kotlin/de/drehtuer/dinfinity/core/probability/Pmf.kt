package de.drehtuer.dinfinity.core.probability

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The exact probability of every total a formula can produce: a dense array of
 * probabilities with an [offset], one entry per integer in [support]
 * (`docs/probability.md`).
 *
 * Dense rather than a map because the outcome graph draws a bar per value and
 * wants them in order, and because a gap in the middle of a distribution — the
 * odd values of `2d6 * 2`, say — is information the chart should show as an
 * empty column rather than close up.
 *
 * It is exact, not a normal approximation. A bell curve is right for `20d6`
 * and visibly wrong for `2d6`, and flatly wrong for `1d20`, which is the shape
 * a player most wants to see (`docs/probability.md`).
 */
class Pmf private constructor(
  /** The value the first entry of the array stands for. */
  val offset: Int,
  private val weights: DoubleArray,
) {
  /** Every value this distribution can take, including any it takes with probability zero. */
  val support: IntRange get() = offset until offset + weights.size

  /** The lowest total, and the highest. */
  val min: Int get() = offset

  val max: Int get() = offset + weights.size - 1

  /** P(total = [value]); zero for anything outside [support]. */
  fun probabilityOf(value: Int): Double = weights.getOrElse(value - offset) { 0.0 }

  /** P(total ≥ [value]) — the question most game rules actually ask. */
  fun atLeast(value: Int): Double = sum(from = value, to = max)

  /** P(total ≤ [value]). */
  fun atMost(value: Int): Double = sum(from = min, to = value)

  /** The expected total. */
  val mean: Double get() = support.sumOf { it * probabilityOf(it) }

  /** The variance of the total. */
  val variance: Double
    get() {
      val centre = mean
      return support.sumOf { (it - centre) * (it - centre) * probabilityOf(it) }
    }

  /** How spread out the totals are, in the units of the total. */
  val standardDeviation: Double get() = sqrt(variance)

  /** Every value with its probability, lowest first — the bars of the chart. */
  fun outcomes(): List<Outcome> = support.map { Outcome(it, probabilityOf(it)) }

  /** The whole mass, which is 1 to within floating-point noise. */
  fun total(): Double = weights.sum()

  /** A copy of the raw probabilities, for a caller that wants to walk them itself. */
  fun probabilities(): DoubleArray = weights.copyOf()

  /** True when every value agrees with [other] to within [tolerance]. */
  fun approximates(
    other: Pmf,
    tolerance: Double = DEFAULT_TOLERANCE,
  ): Boolean {
    val values = minOf(min, other.min)..maxOf(max, other.max)
    return values.all { abs(probabilityOf(it) - other.probabilityOf(it)) <= tolerance }
  }

  override fun toString(): String = "Pmf($min..$max, mean=%.4f)".format(mean)

  private fun sum(
    from: Int,
    to: Int,
  ): Double {
    if (from > to) return 0.0
    return (maxOf(from, min)..minOf(to, max)).sumOf { probabilityOf(it) }
  }

  companion object {
    /** Close enough for a distribution built out of doubles. */
    const val DEFAULT_TOLERANCE: Double = 1e-12

    /** The distribution of a value that is always [value]. */
    fun certain(value: Int): Pmf = of(offset = value, weights = doubleArrayOf(1.0))

    /**
     * The distribution of a die whose faces carry [values] — one entry per
     * face, duplicates and all.
     *
     * This is the only place a die becomes a distribution, and it needs
     * nothing but the face values: a cube labelled `1,2,1,2,1,2` produces the
     * d2 distribution on its own, with no special case anywhere
     * (`docs/probability.md`).
     */
    fun uniformOver(values: List<Int>): Pmf {
      require(values.isNotEmpty()) { "a die with no faces has no distribution" }
      val low = values.min()
      val weights = DoubleArray(values.max() - low + 1)
      val share = 1.0 / values.size
      values.forEach { weights[it - low] += share }
      return of(low, weights)
    }

    /**
     * A distribution from raw weights, normalised so the mass is exactly 1 and
     * trimmed of the zeroes at either end.
     *
     * Normalising at every step is what keeps a chain of twenty convolutions
     * from drifting; trimming is what keeps `2d6 - 1d6` from carrying a
     * hundred impossible values around with it.
     */
    fun of(
      offset: Int,
      weights: DoubleArray,
    ): Pmf {
      val first = weights.indexOfFirst { it > 0.0 }
      require(first >= 0) { "a distribution needs at least one possible outcome" }
      val last = weights.indexOfLast { it > 0.0 }
      val mass = weights.sum()
      val trimmed = DoubleArray(last - first + 1) { weights[first + it] / mass }
      return Pmf(offset + first, trimmed)
    }
  }
}

/** One bar of the chart: a total, and how likely it is. */
data class Outcome(
  val value: Int,
  val probability: Double,
)
