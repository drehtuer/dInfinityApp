package de.drehtuer.dinfinity.core.stats

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * "All my d20s" (`docs/statistics.md`, per standard die type; design `5c`).
 *
 * The pooled fair line is the whole of what is being tested, and the case it
 * exists for is dice that were not thrown equally often. A line drawn from the
 * value lists alone would weigh a die thrown twice the same as one thrown a
 * thousand times, and call the pool loaded.
 */
class FaceHistogramPoolTest {
  @Test
  fun `a pool of one die is exactly what one die gives`() {
    val values = (1..6).toList()
    val tallies = values.map { tally(it, count = 10) }

    val pooled = FaceHistogram.ofPool(listOf(PooledDie(values, throws = 60)), tallies)

    assertEquals(FaceHistogram.of(values, tallies), pooled)
  }

  @Test
  fun `two identical dice share the line evenly`() {
    val values = (1..6).toList()
    val tallies = values.map { tally(it, count = 20) }

    val bars = FaceHistogram.ofPool(listOf(PooledDie(values, 60), PooledDie(values, 60)), tallies)

    bars.forEach { bar -> assertEquals(1.0 / 6, bar.fairShare, TOLERANCE, "value ${bar.value}") }
  }

  @Test
  fun `a die thrown far more often dominates the line`() {
    // A fair d6 thrown a thousand times and a 1,2,3,1,2,3 thrown twice is very
    // nearly a fair d6. A line that counted the two value lists equally would
    // put a sixth of the weight on 1, 2 and 3 each — and call the pool loaded.
    val fair = PooledDie((1..6).toList(), throws = 1_000)
    val odd = PooledDie(listOf(1, 2, 3, 1, 2, 3), throws = 2)

    val bars = FaceHistogram.ofPool(listOf(fair, odd), tallies = emptyList())

    val expectedOnOne = (1_000.0 / 6 + 2.0 * 2 / 6) / 1_002
    assertEquals(expectedOnOne, bars.single { it.value == 1 }.fairShare, TOLERANCE)
    assertEquals((1_000.0 / 6) / 1_002, bars.single { it.value == 6 }.fairShare, TOLERANCE)
  }

  @Test
  fun `the fair shares add up to one`() {
    val bars =
      FaceHistogram.ofPool(
        listOf(PooledDie((1..20).toList(), 37), PooledDie(listOf(2, 4, 6, 8, 10, 12), 5)),
        tallies = emptyList(),
      )

    assertEquals(1.0, bars.sumOf { it.fairShare }, TOLERANCE)
  }

  @Test
  fun `a value only one die in the pool can show is still a bar`() {
    // "No d20 of mine has ever rolled a 20" is the most interesting thing a
    // histogram can say, and it is still true when only one of them has a 20.
    val bars =
      FaceHistogram.ofPool(
        listOf(PooledDie((1..6).toList(), 10), PooledDie(listOf(7, 8, 9, 10, 11, 12), 10)),
        tallies = emptyList(),
      )

    assertEquals((1..12).toList(), bars.map { it.value })
    assertEquals(0L, bars.single { it.value == 12 }.count)
  }

  @Test
  fun `what has been recorded is shared out over the whole pool`() {
    val values = (1..6).toList()
    val tallies = listOf(tally(1, count = 30), tally(2, count = 10))

    val bars = FaceHistogram.ofPool(listOf(PooledDie(values, 20), PooledDie(values, 20)), tallies)

    assertEquals(0.75, bars.single { it.value == 1 }.share, TOLERANCE)
    assertEquals(0.25, bars.single { it.value == 2 }.share, TOLERANCE)
    assertEquals(0.0, bars.single { it.value == 6 }.share, TOLERANCE)
  }

  @Test
  fun `a die nobody has thrown does not skew the line`() {
    // It is in the pool because it is installed, not because it has a record.
    // Weighting it by zero throws is what keeps it out of the arithmetic.
    val fair = PooledDie((1..6).toList(), throws = 100)
    val never = PooledDie(listOf(1, 1, 1, 1, 1, 1), throws = 0)

    val bars = FaceHistogram.ofPool(listOf(fair, never), tallies = emptyList())

    bars.forEach { bar -> assertEquals(1.0 / 6, bar.fairShare, TOLERANCE, "value ${bar.value}") }
  }

  @Test
  fun `a pool nobody has thrown at all falls back to the values alone`() {
    // Before anything has been rolled there is no weight to go on, and an
    // empty histogram would be worse than an even one.
    val values = (1..6).toList()

    val bars = FaceHistogram.ofPool(listOf(PooledDie(values, 0), PooledDie(values, 0)), tallies = emptyList())

    assertEquals(values, bars.map { it.value })
    bars.forEach { bar -> assertEquals(1.0 / 6, bar.fairShare, TOLERANCE) }
  }

  @Test
  fun `an empty pool draws nothing rather than dividing by zero`() {
    assertEquals(emptyList<FaceBar>(), FaceHistogram.ofPool(emptyList(), emptyList()))
  }

  private fun tally(
    value: Int,
    count: Long,
  ) = FaceTally(setId = "", dieId = "", sides = 6, faceValue = value, count = count)

  private companion object {
    const val TOLERANCE = 1e-9
  }
}
