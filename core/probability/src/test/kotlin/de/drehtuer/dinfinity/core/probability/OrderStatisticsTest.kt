package de.drehtuer.dinfinity.core.probability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The edges of the keep/drop machinery, reached directly rather than through a
 * formula — the notation refuses some of them, and the code underneath still
 * has to mean something sensible if it is ever called another way.
 */
class OrderStatisticsTest {
  private val d6 = Pmf.uniformOver((1..6).toList())
  private val fudge = Pmf.uniformOver(listOf(-1, -1, 0, 0, 1, 1))

  @Test
  fun `keeping none of them keeps nothing`() {
    val nothing = OrderStatistics.keepHighest(d6, count = 4, keep = 0)
    assertEquals(0..0, nothing.support)
    assertEquals(1.0, nothing.probabilityOf(0))
  }

  @Test
  fun `keeping all of them is simply their sum`() {
    assertTrue(OrderStatistics.keepHighest(d6, count = 3, keep = 3).approximates(Convolution.repeated(d6, 3)))
    assertTrue(OrderStatistics.keepLowest(d6, count = 3, keep = 3).approximates(Convolution.repeated(d6, 3)))
  }

  @Test
  fun `keeping one of one is that one`() {
    assertTrue(OrderStatistics.keepHighest(d6, count = 1, keep = 1).approximates(d6))
    assertTrue(OrderStatistics.keepLowest(d6, count = 1, keep = 1).approximates(d6))
  }

  @Test
  fun `the highest of two d6 has the distribution it should`() {
    val best = OrderStatistics.keepHighest(d6, count = 2, keep = 1)
    (1..6).forEach { k -> assertEquals((2.0 * k - 1) / 36, best.probabilityOf(k), 1e-14, "P(max = $k)") }
  }

  @Test
  fun `the lowest of two d6 mirrors the highest`() {
    val worst = OrderStatistics.keepLowest(d6, count = 2, keep = 1)
    (1..6).forEach { k -> assertEquals((2.0 * (7 - k) - 1) / 36, worst.probabilityOf(k), 1e-14, "P(min = $k)") }
  }

  @Test
  fun `a die with negative faces ranks the same way round`() {
    val best = OrderStatistics.keepHighest(fudge, count = 2, keep = 1)
    assertEquals(-1..1, best.support)
    // The best of two is a minus only when both are: (1/3)² = 1/9.
    assertEquals(1.0 / 9, best.probabilityOf(-1), 1e-14)
    val worst = OrderStatistics.keepLowest(fudge, count = 2, keep = 1)
    assertEquals(1.0 / 9, worst.probabilityOf(1), 1e-14)
  }

  @Test
  fun `keeping some of many is still exact, and sums to one`() {
    val kept = OrderStatistics.keepHighest(d6, count = 8, keep = 3)
    assertEquals(3..18, kept.support)
    assertTrue(kotlin.math.abs(kept.total() - 1.0) < 1e-12)
    assertTrue(kept.mean > 3 * 3.5, "keeping the best three should beat the average")
  }

  @Test
  fun `keeping more than there are is a bug, not a distribution`() {
    assertFailsWith<IllegalArgumentException> { OrderStatistics.keepHighest(d6, count = 2, keep = 3) }
    assertFailsWith<IllegalArgumentException> { OrderStatistics.keepHighest(d6, count = 0, keep = 0) }
  }

  @Test
  fun `a chain with nothing done to it is just the die`() {
    assertTrue(ChainPmf.of(d6).approximates(d6))
    assertEquals(6, ChainPmf.explodingValue(d6))
  }

  @Test
  fun `a chain that cannot explode is not followed`() {
    val flat = Pmf.uniformOver(listOf(1, 1, 1))
    assertEquals(0.0, ChainPmf.truncatedMass(flat, explodesAt = 9))
    assertTrue(ChainPmf.of(flat, explodesAt = 9).approximates(flat))
  }

  @Test
  fun `a reroll nothing can trigger changes nothing`() {
    assertTrue(ChainPmf.of(d6, rerollAtOrBelow = 0).approximates(d6))
  }
}
