package de.drehtuer.dinfinity.feature.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * The distribution, drawn (`design/dInfinity.dc.html`, option 1k).
 *
 * Discrete bars, because the distribution is discrete: `2d6` has eleven
 * outcomes and no others, and a smooth curve over them would be a picture of a
 * different kind of thing. Behind them the ±1σ band, over them the dashed mean
 * line, and — when this chart is still about the roll that opened it — a solid
 * line in the accent at the total that actually came up.
 *
 * Everything it draws was decided before it was called: the bar heights, the
 * fractions the lines sit at, whether there is a mark at all. This file turns
 * those into pixels and turns a tap back into a total, and does not do
 * arithmetic about probability anywhere (`docs/probability.md`).
 */
@Composable
internal fun GraphChart(
  bars: List<GraphBar>,
  stats: GraphStats,
  modifier: Modifier = Modifier,
  picked: Reading? = null,
  rolled: Reading? = null,
  onPick: (Int) -> Unit = {},
) {
  // The prototype's four inks, and no others: a bar inside ±1σ is the text
  // colour, one outside it `--color-neutral-500`, the one that was tapped the
  // accent, and the band behind them the surface
  // (`design/dInfinityPhone.dc.html`: `bg: isPick ? accent : inS ? text :
  // neutral-500`). `surfaceVariant` and `onSurfaceVariant`, which this drew
  // in before, are roles the theme does not fill in — so two of the four were
  // Material's baseline lavender.
  val ink = MaterialTheme.colorScheme.onBackground
  val bar = faint
  val chosen = MaterialTheme.colorScheme.primary
  val band = MaterialTheme.colorScheme.surface
  val mark = MaterialTheme.colorScheme.primary

  // A `Canvas` hands a screen reader an empty rectangle, and this one is the
  // whole point of the screen. What shape the distribution is is said in words
  // (`docs/architecture.md`, "Accessibility").
  val reading = ChartReading.of(bars, picked, rolled)?.spoken()

  Canvas(
    modifier =
      modifier
        .testTag(GraphTestTags.CHART)
        .then(reading?.let { said -> Modifier.semantics { contentDescription = said } } ?: Modifier)
        .pointerInput(bars) {
          detectTapGestures { at ->
            if (size.width > 0) barAt(bars, at.x / size.width)?.let { onPick(it.from) }
          }
        },
  ) {
    if (bars.isEmpty()) return@Canvas

    // The band first, so the bars stand on it rather than under it.
    val deviation = stats.deviationBand
    drawRect(
      color = band,
      topLeft = Offset(x = (deviation.start * size.width).toFloat(), y = 0f),
      size = Size(width = ((deviation.endInclusive - deviation.start) * size.width).toFloat(), height = size.height),
    )

    val width = size.width / bars.size
    bars.forEachIndexed { index, drawn ->
      val height = (drawn.share * size.height).toFloat()
      drawRect(
        color =
          when {
            picked != null && picked.value in drawn -> chosen
            // Inside the band it stands on, the bar is the full ink; outside
            // it, the grey. The band is a shape the eye reads *through* the
            // bars, which is why the bars say where it ends as well.
            stats.within(drawn) -> ink
            else -> bar
          },
        topLeft = Offset(x = index * width, y = size.height - height),
        size = Size(width = maxOf(width - GAP.toPx(), MIN_BAR.toPx()), height = height),
      )
    }

    // Dashed, and behind the roll's line: the mean is where the dice tend, and
    // what actually happened is the thing being looked at.
    upright(stats.meanShare, ink, LINE, dashed = true)
    // Heavier than the mean's line and solid, as the prototype draws it: 3 px
    // against 2, because this one is about the player rather than the formula.
    if (rolled != null) upright(stats.share(rolled.value.toDouble()), mark, MARK, dashed = false)

    drawLine(
      color = ink,
      start = Offset(0f, size.height),
      end = Offset(size.width, size.height),
      strokeWidth = AXIS.toPx(),
    )
  }
}

/**
 * The chart's shape as a sentence or three.
 *
 * Built from parts rather than from one format string with optional halves: a
 * mark is there or it is not, and a translator should not have to guess what a
 * trailing fragment attaches to.
 */
@Composable
private fun ChartReading.spoken(): String =
  listOfNotNull(
    pluralStringResource(R.plurals.graph_chart_shape, bars, bars),
    stringResource(R.string.graph_chart_range, lowest, highest),
    stringResource(R.string.graph_chart_likeliest, likeliest),
    rolled?.let { stringResource(R.string.graph_chart_rolled, it) },
    picked?.let { stringResource(R.string.graph_chart_picked, it) },
  ).joinToString(separator = " ")

/** A line straight up the chart at [share] of the way along it. */
private fun DrawScope.upright(
  share: Double,
  colour: Color,
  thickness: Dp,
  dashed: Boolean,
) {
  val x = (share * size.width).toFloat()
  drawLine(
    color = colour,
    start = Offset(x, 0f),
    end = Offset(x, size.height),
    strokeWidth = thickness.toPx(),
    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)) else null,
  )
}

/**
 * The bar [fraction] of the way across the chart, or `null` for a tap outside
 * it.
 *
 * Its own function so that "which bar did they mean" can be asserted without
 * drawing anything. The bars share the width evenly, which is what lets a tap
 * be a division rather than a search.
 */
internal fun barAt(
  bars: List<GraphBar>,
  fraction: Float,
): GraphBar? {
  if (bars.isEmpty() || fraction < 0f || fraction > 1f) return null
  return bars.getOrNull((fraction * bars.size).toInt().coerceAtMost(bars.size - 1))
}

/** `gap:1px` between two bars, and the least a bar may be and still be one. */
private val GAP: Dp = Modernist.hairline
private val MIN_BAR: Dp = Modernist.hairline

/** The mean's dashed line, and the axis the bars stand on: both 2 px rules. */
private val LINE: Dp = Modernist.rule
private val AXIS: Dp = Modernist.rule

/** The roll's own line: `border-left:3px solid var(--color-accent)`. */
private val MARK: Dp = Modernist.mark
private const val DASH = 8f
