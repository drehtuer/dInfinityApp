package de.drehtuer.dinfinity.simulation.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The three figures Step 5 is stated in, and the rule that each of them is a
 * value some roll really produced.
 */
class DistributionTest {
  @Test
  fun `a sample of nothing has nothing in it`() {
    assertEquals(Distribution.Nothing, Distribution.of(emptyList()))
  }

  @Test
  fun `one roll is its own median, its own p99 and its own worst case`() {
    assertEquals(Distribution(7.0, 7.0, 7.0), Distribution.of(listOf(7.0)))
  }

  @Test
  fun `the sample is sorted first, so the order it arrived in cannot change it`() {
    val values = listOf(9.0, 1.0, 5.0, 3.0, 7.0)
    assertEquals(Distribution.of(values), Distribution.of(values.reversed()))
    assertEquals(5.0, Distribution.of(values).median)
  }

  @Test
  fun `an even sample takes the lower of the two middles rather than averaging them`() {
    // Nearest rank, never interpolated: 3.5 is a settle time no roll took.
    assertEquals(3.0, Distribution.of(listOf(1.0, 3.0, 4.0, 8.0)).median)
  }

  @Test
  fun `p99 of a hundred rolls is the ninety-ninth of them`() {
    val values = (1..100).map(Int::toDouble)
    val distribution = Distribution.of(values)

    assertEquals(50.0, distribution.median)
    assertEquals(99.0, distribution.p99)
    assertEquals(100.0, distribution.worst)
  }

  @Test
  fun `the worst case is kept because a p99 says nothing about the tail past it`() {
    val values = (1..99).map(Int::toDouble) + 4_000.0
    val distribution = Distribution.of(values)

    assertEquals(99.0, distribution.p99)
    assertEquals(4_000.0, distribution.worst)
  }

  @Test
  fun `a rank below the first value still lands on the first`() {
    assertEquals(1.0, Distribution.nearestRank(listOf(1.0, 2.0, 3.0), 0.0))
    assertEquals(1.0, Distribution.nearestRank(listOf(1.0, 2.0, 3.0), -1.0))
  }

  @Test
  fun `a rank past the last value still lands on the last`() {
    assertEquals(3.0, Distribution.nearestRank(listOf(1.0, 2.0, 3.0), 1.0))
    assertEquals(3.0, Distribution.nearestRank(listOf(1.0, 2.0, 3.0), 2.0))
  }

  @Test
  fun `there is no percentile of an empty sample, and saying so beats returning zero`() {
    assertFailsWith<IllegalArgumentException> { Distribution.nearestRank(emptyList(), 0.5) }
  }
}
