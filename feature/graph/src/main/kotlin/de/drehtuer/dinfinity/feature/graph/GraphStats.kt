package de.drehtuer.dinfinity.feature.graph

import de.drehtuer.dinfinity.core.probability.Pmf

/**
 * The numbers beside the chart (`design/dInfinity.dc.html`, option 1k).
 *
 * Six of them, and each is there because a picture cannot say it: the mean and
 * σ place the shape, the range says what is possible at all, and the two
 * extreme probabilities are the ones people actually argue about — how often
 * `4d6dl1` gives an 18, how often `1d20` gives a 1.
 *
 * Taken from the exact distribution, not measured off the bars: a bucketed
 * chart would give a mean that depends on how wide the phone is.
 */
data class GraphStats(
  val mean: Double,
  val standardDeviation: Double,
  val lowest: Int,
  val highest: Int,
  val chanceOfLowest: Double,
  val chanceOfHighest: Double,
  /** How many dice the formula throws, which is what a table has to hold. */
  val dice: Int,
) {
  /**
   * How far along the chart the mean sits, `0` to `1`.
   *
   * Measured against the whole range rather than against the bars, so the
   * dashed line lands in the same place whether or not the bars were gathered.
   */
  val meanShare: Double get() = share(mean)

  /** Where the ±1σ band starts and ends, clamped to the chart. */
  val deviationBand: ClosedFloatingPointRange<Double>
    get() {
      val from = share(mean - standardDeviation).coerceIn(0.0, 1.0)
      val to = share(mean + standardDeviation).coerceIn(0.0, 1.0)
      return from..maxOf(from, to)
    }

  /**
   * Whether [bar] falls inside the ±1σ band, and so is drawn in the full ink
   * rather than in the grey (`design/dInfinityPhone.dc.html`: `inS = x.k2 >=
   * mean - sd && x.k <= mean + sd`).
   *
   * A bar that only *overlaps* the band counts as inside it. A bucketed bar
   * stands for several totals, and one that is half inside the band is not a
   * bar the chart has any way to draw half of.
   */
  fun within(bar: GraphBar): Boolean = bar.to >= mean - standardDeviation && bar.from <= mean + standardDeviation

  /** How far along the chart [total] sits, `0` to `1`. */
  fun share(total: Double): Double {
    val span = (highest - lowest).toDouble()
    return if (span <= 0.0) HALFWAY else (total - lowest) / span
  }

  companion object {
    /** A distribution with one outcome has its whole story in the middle. */
    private const val HALFWAY = 0.5

    fun of(
      pmf: Pmf,
      dice: Int,
    ): GraphStats =
      GraphStats(
        mean = pmf.mean,
        standardDeviation = pmf.standardDeviation,
        lowest = pmf.min,
        highest = pmf.max,
        chanceOfLowest = pmf.probabilityOf(pmf.min),
        chanceOfHighest = pmf.probabilityOf(pmf.max),
        dice = dice,
      )
  }
}
