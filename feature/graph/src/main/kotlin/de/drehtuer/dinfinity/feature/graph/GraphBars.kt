package de.drehtuer.dinfinity.feature.graph

import de.drehtuer.dinfinity.core.probability.Pmf

/**
 * The bars of the chart (`design/dInfinity.dc.html`, option 1k).
 *
 * One bar per total, until there are more totals than a phone has pixels to
 * put them on; past that, neighbours are gathered into buckets. **The bucket
 * is a sum, not a sample**: the probabilities inside it are added up, so the
 * chart still shows where all the probability is and the area under it is
 * still one. Drawing every hundredth total instead would be a chart of a
 * different distribution.
 *
 * The heights are relative to the tallest bar rather than to 1, because almost
 * every interesting distribution has its tallest bar well under a tenth and a
 * chart scaled to 1 is a chart of nothing.
 */
object GraphBars {
  /**
   * How many bars a phone can usefully show.
   *
   * A 360 dp screen with a little padding gives about 340 dp of chart, so a
   * hundred and ten bars is around three dp each — thin, and still a bar. More
   * than that and neighbouring bars cannot be told apart, which is the point
   * at which gathering them says more than drawing them.
   */
  const val MOST_BARS: Int = 110

  /** [pmf] as bars answering [mode]'s question. */
  fun of(
    pmf: Pmf,
    mode: GraphMode,
  ): List<GraphBar> {
    val totals = pmf.support.toList()
    val perBucket = ((totals.size + MOST_BARS - 1) / MOST_BARS).coerceAtLeast(1)
    val bars =
      totals.chunked(perBucket) { bucket ->
        GraphBar(
          from = bucket.first(),
          to = bucket.last(),
          exact = bucket.sumOf(pmf::probabilityOf),
          // The chance of *this much or more*, which is the chance of the
          // lowest total in the bucket or more — not the sum of the bucket's
          // own at-leasts, which would count the same outcomes many times.
          atLeast = pmf.atLeast(bucket.first()),
        )
      }
    val tallest = bars.maxOfOrNull { it.value(mode) } ?: 0.0
    return bars.map { bar -> bar.copy(share = if (tallest > 0.0) bar.value(mode) / tallest else 0.0) }
  }
}

/**
 * One bar: the totals it stands for, and what the distribution says about them.
 *
 * @param from the lowest total in the bar, and [to] the highest. The two are
 *   the same for every bar of a distribution small enough to draw one each.
 * @param share how tall to draw it, `0` to `1`, against the tallest bar of the
 *   chart as it is currently asked.
 */
data class GraphBar(
  val from: Int,
  val to: Int,
  val exact: Double,
  val atLeast: Double,
  val share: Double = 0.0,
) {
  /** True when this bar is one total rather than a gathering of several. */
  val single: Boolean get() = from == to

  /** What this bar is worth under [mode]. */
  fun value(mode: GraphMode): Double =
    when (mode) {
      GraphMode.Exact -> exact
      GraphMode.AtLeast -> atLeast
    }

  /** True when [total] falls inside this bar. */
  operator fun contains(total: Int): Boolean = total in from..to
}
