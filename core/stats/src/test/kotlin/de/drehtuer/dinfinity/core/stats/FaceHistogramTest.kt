package de.drehtuer.dinfinity.core.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Turning tallies into the bars a screen draws
 * (`docs/statistics.md`, "Screens").
 *
 * The interesting cases are all about the fair line, which is the thing that
 * makes a histogram an answer rather than a picture.
 */
class FaceHistogramTest {
  @Test
  fun `a fair d6 is six bars of a sixth`() {
    val bars = FaceHistogram.of(values = (1..6).toList(), tallies = (1..6).map { tally(it, 10) })

    assertEquals(6, bars.size)
    bars.forEach { bar ->
      assertEquals(1.0 / 6, bar.fairShare, 1e-9)
      assertEquals(1.0 / 6, bar.share, 1e-9)
    }
  }

  @Test
  fun `a value that has never come up is a bar of zero, not a gap`() {
    // "This d20 has never rolled a 20" is the single most interesting thing a
    // histogram can say, and a missing bar does not say it.
    val bars = FaceHistogram.of(values = (1..20).toList(), tallies = listOf(tally(1, 5)))

    assertEquals(20, bars.size)
    assertEquals(0L, bars.last().count)
  }

  @Test
  fun `a die whose faces repeat has a fair line per value, not per face`() {
    // A d6 labelled 1,2,3,1,2,3 is a d3. Drawn against a sixth it would look
    // twice as lucky as it is, on every value.
    val bars = FaceHistogram.of(values = listOf(1, 2, 3, 1, 2, 3), tallies = (1..3).map { tally(it, 10) })

    assertEquals(listOf(1, 2, 3), bars.map { it.value })
    bars.forEach { assertEquals(1.0 / 3, it.fairShare, 1e-9) }
  }

  @Test
  fun `an uneven die has an uneven fair line`() {
    // Two faces of "1" and one each of "2" and "3": a fair throw lands on 1
    // half the time.
    val bars = FaceHistogram.of(values = listOf(1, 1, 2, 3), tallies = emptyList())

    assertEquals(0.5, bars.first { it.value == 1 }.fairShare, 1e-9)
    assertEquals(0.25, bars.first { it.value == 2 }.fairShare, 1e-9)
  }

  @Test
  fun `a die that has never been thrown has bars of nothing rather than a division by zero`() {
    val bars = FaceHistogram.of(values = (1..6).toList(), tallies = emptyList())

    assertEquals(6, bars.size)
    bars.forEach { assertEquals(0.0, it.share, 0.0) }
  }

  @Test
  fun `a die nobody can describe has no bars at all`() {
    assertEquals(emptyList(), FaceHistogram.of(values = emptyList(), tallies = listOf(tally(1, 3))))
  }

  @Test
  fun `a face that came up more often than a fair die would is marked`() {
    val bars = FaceHistogram.of(values = (1..2).toList(), tallies = listOf(tally(1, 7), tally(2, 3)))

    assertTrue(bars.first { it.value == 1 }.over)
    assertFalse(bars.first { it.value == 2 }.over)
  }

  @Test
  fun `dropped throws are carried, because they were still throws`() {
    val bars = FaceHistogram.of(values = (1..2).toList(), tallies = listOf(tally(1, 7, dropped = 2)))

    assertEquals(2L, bars.first().droppedCount)
    // They still count towards the share: the die was thrown and it landed there.
    assertEquals(1.0, bars.first().share, 1e-9)
  }

  @Test
  fun `the natural highs and lows are the numbers a player asks for`() {
    val extremes =
      FaceHistogram.extremes(
        values = (1..20).toList(),
        tallies = listOf(tally(20, 7), tally(1, 3), tally(11, 40)),
      )

    assertEquals(20, extremes.highestValue)
    assertEquals(7L, extremes.highs)
    assertEquals(1, extremes.lowestValue)
    assertEquals(3L, extremes.lows)
  }

  @Test
  fun `highest means the die's own highest value, not twenty`() {
    // A d6 labelled 1,2,3,1,2,3 has a highest of 3.
    val extremes = FaceHistogram.extremes(values = listOf(1, 2, 3, 1, 2, 3), tallies = listOf(tally(3, 5)))

    assertEquals(3, extremes.highestValue)
    assertEquals(5L, extremes.highs)
  }

  @Test
  fun `a die that has never shown its highest face has none of them`() {
    val extremes = FaceHistogram.extremes(values = (1..20).toList(), tallies = listOf(tally(11, 40)))

    assertEquals(0L, extremes.highs)
    assertEquals(20, extremes.highestValue)
  }

  @Test
  fun `a die nobody can describe has no extremes to report`() {
    assertEquals(Extremes(), FaceHistogram.extremes(values = emptyList(), tallies = listOf(tally(1, 3))))
  }

  private fun tally(
    value: Int,
    count: Long,
    dropped: Long = 0,
  ) = FaceTally(setId = "builtin", dieId = "d6", sides = 6, faceValue = value, count = count, droppedCount = dropped)
}
