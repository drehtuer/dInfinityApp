package de.drehtuer.dinfinity.feature.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the distribution is said to look like, without drawing it.
 *
 * The chart is a `Canvas`, and to a screen reader a `Canvas` is a rectangle
 * with nothing in it — on the one screen whose entire purpose is a picture
 * (`docs/architecture.md`, "Accessibility"). What is said about it is decided
 * here so that it can be asserted here.
 */
class ChartReadingTest {
  @Test
  fun `the reading is the shape of the distribution`() {
    val reading = ChartReading.of(twoD6(), picked = null, rolled = null)

    assertEquals(11, reading?.bars)
    assertEquals(2, reading?.lowest)
    assertEquals(12, reading?.highest)
    assertEquals(7, reading?.likeliest)
  }

  @Test
  fun `nothing drawn is described as nothing rather than as an empty chart`() {
    // A description of nothing is one more thing to swipe past.
    assertNull(ChartReading.of(emptyList(), picked = null, rolled = null))
  }

  @Test
  fun `the likeliest comes from the exact odds, not from the drawn heights`() {
    // Under `P(total ≥ k)` the tallest bar is always the leftmost, which says
    // nothing about the distribution. The reading must be the same either way.
    val asDrawnUnderAtLeast =
      twoD6().map { bar -> bar.copy(share = bar.atLeast) }

    assertEquals(7, ChartReading.of(asDrawnUnderAtLeast, picked = null, rolled = null)?.likeliest)
  }

  @Test
  fun `a bar that stands for several totals is read from end to end`() {
    val gathered =
      listOf(
        GraphBar(from = 1, to = 50, exact = 0.4, atLeast = 1.0),
        GraphBar(from = 51, to = 100, exact = 0.6, atLeast = 0.6),
      )

    val reading = ChartReading.of(gathered, picked = null, rolled = null)

    assertEquals(1, reading?.lowest)
    assertEquals(100, reading?.highest)
    assertEquals(51, reading?.likeliest)
  }

  @Test
  fun `the marks on the chart are part of what it says`() {
    val reading =
      ChartReading.of(
        twoD6(),
        picked = Reading(value = 4, exact = 0.083, atLeast = 0.917),
        rolled = Reading(value = 9, exact = 0.111, atLeast = 0.278),
      )

    assertEquals(4, reading?.picked)
    assertEquals(9, reading?.rolled)
  }

  @Test
  fun `an unmarked chart claims no marks`() {
    val reading = ChartReading.of(twoD6(), picked = null, rolled = null)

    assertNull(reading?.picked)
    assertNull(reading?.rolled)
  }

  /** `2d6`: eleven totals, likeliest 7. */
  private fun twoD6(): List<GraphBar> =
    (2..12).map { total ->
      val ways = 6 - kotlin.math.abs(total - 7)
      GraphBar(from = total, to = total, exact = ways / 36.0, atLeast = 0.0)
    }
}
