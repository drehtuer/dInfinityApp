package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.core.stats.TotalBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the statistics screens say about the pictures they draw
 * (`docs/architecture.md`, "Accessibility").
 *
 * Both charts on these screens draw two kinds of rectangle over one another and
 * tell them apart by colour alone — the fair line under a face's bar, and the
 * exact distribution across the totals actually rolled. Neither reaches a
 * screen reader, and neither reaches anybody who cannot separate the two.
 */
class ReadingsTest {
  @Test
  fun `a share is a percentage to one decimal`() {
    assertEquals("50.0 %", percent(0.5))
    assertEquals("8.3 %", percent(1.0 / 12))
  }

  @Test
  fun `something that never happened is nought`() {
    assertEquals("0 %", percent(0.0))
  }

  @Test
  fun `something that did happen is never printed as nought`() {
    // A face that came up once in ten thousand throws printed as "0.0 %" is
    // the app saying it never happened, which on a screen about whether a die
    // is fair is the one answer that must not be wrong.
    assertEquals("< 0.1 %", percent(0.0001))
  }

  @Test
  fun `the chart says how many totals it draws and where they run`() {
    val reading = TotalsReading.of(rolled())

    assertEquals(3, reading?.bars)
    assertEquals(3, reading?.lowest)
    assertEquals(5, reading?.highest)
  }

  @Test
  fun `it counts the totals that came up more often than the maths says`() {
    // The one claim the picture makes: a bar above its mark.
    assertEquals(2, TotalsReading.of(rolled())?.above)
  }

  @Test
  fun `a total exactly where it should be is not counted as above`() {
    val level = listOf(TotalBar(total = 7, count = 10, observed = 0.5, expected = 0.5))

    assertEquals(0, TotalsReading.of(level)?.above)
  }

  @Test
  fun `nothing drawn is described as nothing`() {
    assertNull(TotalsReading.of(emptyList()))
  }

  private fun rolled(): List<TotalBar> =
    listOf(
      TotalBar(total = 3, count = 5, observed = 0.5, expected = 0.25),
      TotalBar(total = 4, count = 3, observed = 0.3, expected = 0.5),
      TotalBar(total = 5, count = 2, observed = 0.2, expected = 0.15),
    )
}
