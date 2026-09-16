package de.drehtuer.dinfinity.feature.graph

/**
 * What the chart contains, in the numbers a screen reader can say
 * (`docs/architecture.md`, "Accessibility").
 *
 * The distribution is a `Canvas`: to a reader it is a rectangle with nothing in
 * it, and a chart nobody can read is the worst case of the lot — this is the
 * one screen whose whole purpose is a picture. The numbers beside it say the
 * mean and the deviation; what they do not say is the *shape*, and the shape is
 * what somebody looks at a distribution for.
 *
 * So the reading is the shape in words: how many totals there are, where they
 * run from and to, which is likeliest, and which of them the chart has marked.
 * Not every bar — a `d100` has a hundred, and a hundred spoken percentages is a
 * minute of speech nobody would sit through. The one bar somebody asked about
 * is already said in full under the chart, in text.
 *
 * @param likeliest the total the distribution puts most weight on. Taken from
 *   the exact probabilities rather than the drawn heights, so it is the same
 *   answer under either question the chart can be asked — under `P(total ≥ k)`
 *   the tallest bar is always the leftmost one, which says nothing.
 */
internal data class ChartReading(
  val bars: Int,
  val lowest: Int,
  val highest: Int,
  val likeliest: Int,
  val picked: Int?,
  val rolled: Int?,
) {
  companion object {
    /**
     * The reading of [bars], or `null` when there is nothing drawn.
     *
     * A chart with no bars is not described as an empty chart, because a
     * description of nothing is one more thing to swipe past.
     */
    fun of(
      bars: List<GraphBar>,
      picked: Reading?,
      rolled: Reading?,
    ): ChartReading? {
      val tallest = bars.maxByOrNull { it.exact } ?: return null
      return ChartReading(
        bars = bars.size,
        lowest = bars.first().from,
        highest = bars.last().to,
        likeliest = tallest.from,
        picked = picked?.value,
        rolled = rolled?.value,
      )
    }
  }
}
