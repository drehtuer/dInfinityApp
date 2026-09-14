package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.core.stats.TotalBar

/**
 * One rectangle of the observed-against-expected chart
 * (`design/dInfinity.dc.html`, option `8b`).
 *
 * @param isMark true for the red line showing what the distribution says, false
 *   for the ink bar showing what was rolled.
 */
data class ChartShape(
  val left: Float,
  val top: Float,
  val width: Float,
  val height: Float,
  val isMark: Boolean,
)

/**
 * Where every rectangle of the chart goes.
 *
 * Arithmetic rather than drawing, and separate for the reason `FaceHistogram`
 * is separate: a `Canvas` draw lambda is the one place a test cannot reach, so
 * everything that can be *wrong* is kept out of it. What is left in the
 * composable is `drawRect`.
 *
 * **Both are scaled to the same tallest share**, which is the whole point of
 * the picture: a mark that sits above its bar means the total came up less
 * often than it should, and that reading only holds if the two use one scale.
 */
object ChartShapes {
  /**
   * The bars and marks for [bars], laid out across [width] by [height].
   *
   * A bar of zero still produces a rectangle of zero height rather than being
   * skipped, so the shapes line up one-to-one with the bars — a caller that
   * wanted to label them can count on that.
   *
   * A mark is produced only where the distribution says something. An expected
   * share of zero is not a claim that the total is impossible — it is the
   * absence of a claim (`RolledAgainstExpected.observed`) — and drawing a line
   * along the floor would look like one.
   */
  fun of(
    bars: List<TotalBar>,
    width: Float,
    height: Float,
  ): List<ChartShape> {
    if (bars.isEmpty() || width <= 0f || height <= 0f) return emptyList()
    val tallest = bars.maxOf { maxOf(it.observed, it.expected) }
    if (tallest <= 0.0) return emptyList()

    val step = width / bars.size
    val barWidth = step * BAR_SHARE
    return bars.flatMapIndexed { index, bar ->
      val left = index * step + (step - barWidth) / 2
      val barHeight = (bar.observed / tallest * height).toFloat()
      val ink =
        ChartShape(
          left = left,
          top = height - barHeight,
          width = barWidth,
          height = barHeight,
          isMark = false,
        )
      if (bar.expected <= 0.0) {
        listOf(ink)
      } else {
        val markTop = height - (bar.expected / tallest * height).toFloat()
        listOf(ink, ChartShape(left = left, top = markTop, width = barWidth, height = MARK_THICKNESS, isMark = true))
      }
    }
  }

  /** How much of each column the bar fills, leaving a gap between them. */
  private const val BAR_SHARE = 0.7f

  /** How thick the expected mark is drawn, in pixels. */
  private const val MARK_THICKNESS = 2f
}
