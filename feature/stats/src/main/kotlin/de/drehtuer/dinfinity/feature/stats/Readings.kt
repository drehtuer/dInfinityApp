package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.core.stats.TotalBar

/**
 * A share as a percentage, to one decimal — and never as `0.0 %` when it
 * happened.
 *
 * A face that came up once in ten thousand throws printed as zero is the app
 * saying it never happened, which on a screen about whether a die is fair is
 * the one answer that must not be wrong. The same rule the outcome graph
 * follows, because it is the same claim.
 */
internal fun percent(share: Double): String {
  val shown = share * PER_CENT
  return when {
    share <= 0.0 -> "0 %"
    shown < SMALLEST -> "< $SMALLEST %"
    else -> "%.1f %%".format(shown)
  }
}

private const val PER_CENT = 100.0
private const val SMALLEST = 0.1

/**
 * What the observed-against-expected chart contains, in words
 * (`docs/architecture.md`, "Accessibility").
 *
 * The chart is a `Canvas` that draws each total's ink bar and lays the exact
 * distribution across it as a mark in the error colour. Which of the two a
 * rectangle is is carried by nothing but that colour — so to a screen reader,
 * and to anybody who cannot separate the two reds, the picture says nothing at
 * all.
 *
 * What it claims is one thing and one thing only: how often what was rolled ran
 * ahead of what the maths says. That is a count, and a count can be spoken.
 *
 * @param above how many totals came up more often than the distribution
 *   expects. [TotalBar.over] decides it, and is `core/stats`' to decide.
 */
internal data class TotalsReading(
  val bars: Int,
  val lowest: Int,
  val highest: Int,
  val above: Int,
) {
  companion object {
    /** The reading of [bars], or `null` when there is nothing drawn. */
    fun of(bars: List<TotalBar>): TotalsReading? {
      if (bars.isEmpty()) return null
      return TotalsReading(
        bars = bars.size,
        lowest = bars.minOf { it.total },
        highest = bars.maxOf { it.total },
        above = bars.count { it.over },
      )
    }
  }
}
