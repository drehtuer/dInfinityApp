package de.drehtuer.dinfinity.feature.graph

import de.drehtuer.dinfinity.core.probability.Pmf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where things sit on the chart (`design/dInfinity.dc.html`, option 1k).
 *
 * The dashed mean line, the ±1σ band behind the bars and the red line at the
 * rolled total are all a fraction of the way along the same axis, and the
 * fraction is arithmetic rather than drawing. Measured against the whole
 * **range** rather than against the bars, so a line lands in the same place
 * whether or not the bars were gathered into buckets.
 */
class GraphGeometryTest {
  @Test
  fun `the mean of a symmetric distribution sits in the middle`() {
    val stats = statsOf(Pmf.uniformOver(listOf(1, 2, 3, 4, 5, 6)))

    assertEquals(HALFWAY, stats.meanShare, 1e-12)
  }

  @Test
  fun `a distribution with one outcome puts its story in the middle`() {
    // Nothing to place it against: min and max are the same, and a fraction
    // with no span is a division by zero rather than a chart.
    val stats = statsOf(Pmf.certain(7))

    assertEquals(HALFWAY, stats.meanShare, 0.0)
    assertEquals(HALFWAY..HALFWAY, stats.deviationBand)
  }

  @Test
  fun `the sigma band is one deviation each side of the mean`() {
    val stats = statsOf(Pmf.uniformOver(listOf(1, 2, 3, 4, 5, 6)))

    val band = stats.deviationBand
    assertEquals(stats.meanShare - band.start, band.endInclusive - stats.meanShare, 1e-12)
    assertTrue("the band was wider than the chart", band.start >= 0.0 && band.endInclusive <= 1.0)
  }

  @Test
  fun `a sigma band wider than the chart is cut off at its edges`() {
    // `1d20` has a deviation of about 5.8 on a range of 19, so a band of ±1σ
    // fits — but a two-outcome distribution's does not, and a band drawn
    // outside the chart is a band drawn over the numbers beside it.
    val stats = statsOf(Pmf.uniformOver(listOf(0, 1)))

    val band = stats.deviationBand
    assertTrue(band.start >= 0.0)
    assertTrue(band.endInclusive <= 1.0)
  }

  @Test
  fun `a bar knows which totals it stands for`() {
    val bar = GraphBar(from = 10, to = 14, exact = 0.1, atLeast = 0.5)

    assertTrue(12 in bar)
    assertFalse(15 in bar)
    assertFalse("a gathered bar called itself a single total", bar.single)
  }

  @Test
  fun `a bar of one total is a bar of one total`() {
    val bar = GraphBar(from = 7, to = 7, exact = 0.1, atLeast = 0.5)

    assertTrue(bar.single)
    assertEquals(0.1, bar.value(GraphMode.Exact), 0.0)
    assertEquals(0.5, bar.value(GraphMode.AtLeast), 0.0)
  }

  @Test
  fun `a gathered bar's at-least is its lowest total's, not a sum`() {
    // Adding up the at-leasts inside a bucket would count the same outcomes
    // once per total in it, and the first bar would read as five hundred
    // per cent.
    val pmf = Pmf.uniformOver((1..6).toList())

    val bars = GraphBars.of(pmf, GraphMode.AtLeast)

    assertEquals(1.0, bars.first().atLeast, 1e-12)
  }

  @Test
  fun `a distribution that cannot happen still draws something`() {
    // Every bar zero would be a division by zero when they are scaled to the
    // tallest. A flat chart is the honest answer.
    val bars = GraphBars.of(Pmf.certain(0), GraphMode.Exact)

    assertTrue(bars.all { it.share in 0.0..1.0 })
  }

  @Test
  fun `a tap lands on the bar under the finger`() {
    val bars = GraphBars.of(Pmf.uniformOver((1..10).toList()), GraphMode.Exact)

    assertEquals(bars.first(), barAt(bars, 0f))
    assertEquals(bars.last(), barAt(bars, 1f))
    assertEquals(bars[5], barAt(bars, 0.55f))
  }

  @Test
  fun `a tap outside the chart lands on nothing`() {
    val bars = GraphBars.of(Pmf.uniformOver((1..10).toList()), GraphMode.Exact)

    assertNull(barAt(bars, -0.1f))
    assertNull(barAt(bars, 1.1f))
    assertNull(barAt(emptyList(), 0.5f))
  }

  @Test
  fun `a probability too small to print as a percentage is not printed as zero`() {
    // The one answer a screen about probability must not give.
    assertEquals("< 0.1 %", percent(1e-9))
    assertEquals("0 %", percent(0.0))
    assertEquals("16.7 %", percent(6.0 / 36.0))
  }

  private fun statsOf(pmf: Pmf): GraphStats = GraphStats.of(pmf, dice = 1)

  private companion object {
    const val HALFWAY = 0.5
  }
}
