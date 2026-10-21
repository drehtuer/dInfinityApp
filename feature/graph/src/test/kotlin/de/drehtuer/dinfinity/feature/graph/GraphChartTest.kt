package de.drehtuer.dinfinity.feature.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.probability.Pmf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The chart, as pixels (`design/dInfinity.dc.html`, option 1k).
 *
 * [GraphInkTest] pins the one ink that does not follow the theme, and
 * [GraphGeometryTest] where each line sits as a fraction of the axis. Neither
 * says which ink a bar is actually *drawn* in, and that is the rule a reader
 * of the chart goes by: inside ±1σ the full ink, outside it the grey, the
 * tapped bar the accent, the band behind them the surface, and the roll that
 * opened the graph a line in the accent. Captured rather than asserted on
 * semantics, because a colour is the one thing the semantics tree cannot show.
 *
 * `2d6` on a chart 88 dp wide at three pixels to the dp — narrow enough to fit
 * the test's 320 px screen whole: eleven bars of 24 px each, the tallest (7)
 * full height, the mean's line down the middle at 132 px, and the band from
 * about 68 px to about 196 px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphChartTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a bar inside the band is the ink and one outside it is the grey`() {
    val pixels = draw()

    assertEquals("the bar at 7 is not in the ink", INK, pixels.barAt(SEVEN))
    assertEquals("the bar at 2 is not in the ramp's grey", BAR_GREY, pixels.lowDownAt(TWO))
  }

  @Test
  fun `the band is drawn behind the bars, one deviation either side of the mean`() {
    // Read above the bar at 5, which is two thirds the height of the chart, so
    // what is there is the band and nothing standing on it.
    val pixels = draw()

    assertEquals("there is no band behind the middle of the chart", BAND, pixels[ABOVE_FIVE, NEAR_TOP])
    assertEquals("the band reaches out past one deviation", GROUND, pixels[ABOVE_TWO, NEAR_TOP])
    assertEquals("a line was drawn for a roll there was not", GROUND, pixels[ROLLED_LINE, NEAR_TOP])
  }

  @Test
  fun `a tapped bar is the accent, and the mean's line is drawn over it`() {
    // The mean of `2d6` is 7, so its dashed line runs straight down the bar
    // that was tapped — and is still there to be seen.
    val pixels = draw(picked = Reading(value = 7, exact = 6 / 36.0, atLeast = 21 / 36.0))

    assertEquals("the tapped bar is not the accent", ACCENT, pixels.barAt(SEVEN))
    assertEquals("the mean's line is under the tapped bar", INK, pixels[MEAN_LINE, FIRST_DASH])
  }

  @Test
  fun `the roll that opened the graph is a line in the accent`() {
    // A roll of 3 is a tenth of the way along 2..12, and read above the short
    // bar at 3, where nothing else is drawn. Without it the same pixel is the
    // bare ground, which the band test reads.
    val pixels = draw(rolled = Reading(value = 3, exact = 2 / 36.0, atLeast = 34 / 36.0))

    assertEquals("the roll's line is not the accent", ACCENT, pixels[ROLLED_LINE, NEAR_TOP])
  }

  @Test
  fun `a tap is turned back into the total under it`() {
    val picked = mutableListOf<Int>()
    draw(onPick = picked::add)

    compose.onNodeWithTag(GraphTestTags.CHART).performTouchInput { click(Offset(px(SEVEN), height / 2f)) }

    assertEquals(listOf(7), picked)
  }

  @Test
  fun `a chart with nothing to draw says nothing and picks nothing`() {
    // A description of nothing is one more thing to swipe past
    // ([ChartReading.of]), and a tap on it has no bar to land on.
    val picked = mutableListOf<Int>()
    draw(bars = emptyList(), onPick = picked::add)

    val chart = compose.onNodeWithTag(GraphTestTags.CHART)
    chart.performTouchInput { click(center) }

    assertFalse(chart.fetchSemanticsNode().config.contains(SemanticsProperties.ContentDescription))
    assertTrue("a tap on an empty chart picked a total", picked.isEmpty())
  }

  /** Draws `2d6` under a theme whose four inks are all different, and captures it. */
  private fun draw(
    bars: List<GraphBar> = GraphBars.of(TWO_D6, GraphMode.Exact),
    picked: Reading? = null,
    rolled: Reading? = null,
    onPick: (Int) -> Unit = {},
  ): PixelMap {
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
        MaterialTheme(colorScheme = lightColorScheme(primary = ACCENT, onBackground = INK, surface = BAND)) {
          Box(modifier = Modifier.size(WIDTH, HEIGHT).background(GROUND)) {
            GraphChart(
              bars = bars,
              stats = GraphStats.of(TWO_D6, dice = 2),
              picked = picked,
              rolled = rolled,
              onPick = onPick,
              modifier = Modifier.fillMaxSize(),
            )
          }
        }
      }
    }
    return compose.onNodeWithTag(GraphTestTags.CHART).captureToImage().toPixelMap()
  }

  /** Halfway up the bar at [total] — clear of the mean's line, which is at the bar's right. */
  private fun PixelMap.barAt(total: Int): Color = this[px(total).toInt(), height / 2]

  /** Near the foot of the bar at [total], above the axis it stands on. */
  private fun PixelMap.lowDownAt(total: Int): Color = this[px(total).toInt(), height - LOW]

  /** A quarter of the way into the bar for [total]: away from both its gap and the mean's line. */
  private fun px(total: Int): Float = ((total - 2) * BAR + BAR / 4).toFloat()

  private companion object {
    val TWO_D6: Pmf =
      Pmf.uniformOver(
        (2..12).toList().flatMap { total ->
          List(6 - kotlin.math.abs(total - 7)) { total }
        },
      )

    const val DENSITY = 3f
    val WIDTH = 88.dp
    val HEIGHT = 100.dp

    /** 264 px across eleven bars. */
    const val BAR = 24

    const val TWO = 2
    const val SEVEN = 7

    /** 40 px from the foot: inside the 50 px bar at 2, clear of the 6 px axis. */
    const val LOW = 40
    const val NEAR_TOP = 10

    /** The first 8 px of the mean's dashes, at 132 px. */
    const val MEAN_LINE = 132
    const val FIRST_DASH = 4

    /** Over the bar at 5 (72–93 px), inside the band; and over the bar at 2, outside it. */
    const val ABOVE_FIVE = 80
    const val ABOVE_TWO = 12

    /** A tenth of 264 px: where a roll of 3 is marked, 9 px wide. */
    const val ROLLED_LINE = 26

    val ACCENT = Color(0xFFC0392B)
    val INK = Color(0xFF111111)
    val BAND = Color(0xFFDDE4EE)
    val GROUND = Color(0xFFFFFFFF)
  }
}
