package de.drehtuer.dinfinity.core.stats

import de.drehtuer.dinfinity.core.probability.Pmf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a saved roll actually did, against what it was supposed to do
 * (design options `8b` and `9e`; `docs/statistics.md`).
 *
 * This is the screen where the app's central claim can be checked — that the
 * result comes from physics rather than from a generator — so the tests are
 * mostly about *not* flattering the data: a total that never came up is still
 * a bar, a total that should be impossible is still counted, and a drift is
 * not called remarkable until there are enough throws to say so.
 */
class RolledAgainstExpectedTest {
  @Test
  fun `every total the formula can reach is a bar, even the ones never rolled`() {
    // "This roll has never once reached 12" is the interesting shape, and a
    // chart drawn only over what came up would hide it.
    val comparison = RolledAgainstExpected.of(totals = listOf(7L, 7L), expected = twoD6())

    assertEquals((2..12).toList(), comparison.bars.map(TotalBar::total))
    assertEquals(0L, comparison.bars.single { it.total == 12 }.count)
  }

  @Test
  fun `the expected marks are the exact distribution`() {
    val comparison = RolledAgainstExpected.of(totals = emptyList(), expected = twoD6())

    assertEquals(1.0 / 36, comparison.bars.single { it.total == 2 }.expected, TOLERANCE)
    assertEquals(6.0 / 36, comparison.bars.single { it.total == 7 }.expected, TOLERANCE)
    assertEquals(1.0, comparison.bars.sumOf(TotalBar::expected), TOLERANCE)
  }

  @Test
  fun `what was rolled is a share of the throws`() {
    val comparison = RolledAgainstExpected.of(totals = listOf(7L, 7L, 3L, 3L), expected = twoD6())

    assertEquals(0.5, comparison.bars.single { it.total == 7 }.observed, TOLERANCE)
    assertEquals(0.5, comparison.bars.single { it.total == 3 }.observed, TOLERANCE)
    assertEquals(0.0, comparison.bars.single { it.total == 12 }.observed, TOLERANCE)
    assertEquals(1.0, comparison.bars.sumOf(TotalBar::observed), TOLERANCE)
  }

  @Test
  fun `a total the formula cannot reach is shown rather than dropped`() {
    // It should not happen. A formula edited after the rolls were made, or a
    // set whose die changed, can make it happen — and hiding it would hide
    // exactly the thing somebody needs to see.
    val comparison = RolledAgainstExpected.of(totals = listOf(99L), expected = twoD6())

    val stray = comparison.bars.single { it.total == 99 }
    assertEquals(1L, stray.count)
    assertEquals(0.0, stray.expected, TOLERANCE)
    assertTrue(stray.over, "a total that cannot happen was not marked as over")
  }

  @Test
  fun `the mean, the range and what was expected`() {
    val comparison = RolledAgainstExpected.of(totals = listOf(4L, 6L, 8L), expected = twoD6())

    assertEquals(6.0, comparison.mean!!, TOLERANCE)
    assertEquals(7.0, comparison.expectedMean, TOLERANCE)
    assertEquals(-1.0, comparison.drift!!, TOLERANCE)
    assertEquals(4, comparison.lowest)
    assertEquals(8, comparison.highest)
    assertEquals(2..12, comparison.possible)
  }

  @Test
  fun `nothing rolled yet is empty rather than zero`() {
    // A mean of zero would be a claim. There is no mean.
    val comparison = RolledAgainstExpected.of(totals = emptyList(), expected = twoD6())

    assertNull(comparison.mean)
    assertNull(comparison.drift)
    assertNull(comparison.lowest)
    assertEquals(0L, comparison.throws)
    assertEquals(11, comparison.bars.size)
  }

  @Test
  fun `a drift is not judged until there are enough throws to judge it`() {
    // Four throws of 12 is a mean five above expectation and means nothing at
    // all. A screen that showed only the drift would invite the opposite
    // reading.
    val comparison = RolledAgainstExpected.of(totals = List(4) { 12L }, expected = twoD6())

    assertEquals(5.0, comparison.drift!!, TOLERANCE)
    assertNull(comparison.driftInErrors, "four throws were judged")
    assertFalse(comparison.worthALook)
  }

  @Test
  fun `the same drift is remarkable once there are throws behind it`() {
    // Every one of a hundred throws coming to 12 is not a coincidence.
    val comparison = RolledAgainstExpected.of(totals = List(100) { 12L }, expected = twoD6())

    assertTrue(comparison.worthALook, "a hundred natural twelves was not worth a look")
    assertTrue(comparison.driftInErrors!! > 2.0, "the drift was not counted in errors")
  }

  @Test
  fun `a roll that lands exactly where it should is not accused of anything`() {
    // The distribution itself, thrown in its own proportions: 36 throws of 2d6
    // in exactly the right shape.
    val totals = (2..12).flatMap { total -> List(6 - kotlin.math.abs(7 - total)) { total.toLong() } }

    val comparison = RolledAgainstExpected.of(totals, twoD6())

    assertEquals(36L, comparison.throws)
    assertEquals(7.0, comparison.mean!!, TOLERANCE)
    assertEquals(0.0, comparison.driftInErrors!!, TOLERANCE)
    assertFalse(comparison.worthALook)
  }

  @Test
  fun `the standard error shrinks as the throws pile up`() {
    // The reason a drift means different things at four throws and ten
    // thousand: it is divided by this.
    val few = RolledAgainstExpected.of(List(25) { 7L }, twoD6())
    val many = RolledAgainstExpected.of(List(2_500) { 7L }, twoD6())

    assertTrue(few.standardError > many.standardError)
    // A hundred times the throws is a tenth of the error.
    assertEquals(few.standardError / 10, many.standardError, TOLERANCE)
  }

  @Test
  fun `a distribution with one outcome has no error to divide by`() {
    // `1d1`, or a formula that is a constant. There is nothing to be off by,
    // so nothing is judged rather than dividing by zero.
    val certain = Pmf.certain(3)

    val comparison = RolledAgainstExpected.of(List(100) { 3L }, certain)

    assertEquals(0.0, comparison.standardError, TOLERANCE)
    assertNull(comparison.driftInErrors)
    assertFalse(comparison.worthALook)
  }

  @Test
  fun `with no distribution to compare against, the bars stand alone`() {
    // A saved roll whose formula no longer resolves. What the dice did is
    // still the player's record, and hiding it would lose the only copy.
    val comparison = RolledAgainstExpected.observed(listOf(3L, 5L, 5L))

    assertEquals(listOf(3, 5), comparison.bars.map(TotalBar::total))
    assertEquals(2L, comparison.bars.single { it.total == 5 }.count)
    assertEquals(2.0 / 3, comparison.bars.single { it.total == 5 }.observed, TOLERANCE)
    assertEquals(3L, comparison.throws)
    assertEquals(13.0 / 3, comparison.mean!!, TOLERANCE)
    assertEquals(3, comparison.lowest)
    assertEquals(5, comparison.highest)
  }

  @Test
  fun `standing alone is the absence of a claim, not a claim of impossibility`() {
    // Every expected share is zero. `possible` being empty is what says that
    // means "nobody knows" rather than "these totals cannot happen".
    val comparison = RolledAgainstExpected.observed(listOf(3L))

    assertTrue(comparison.bars.all { it.expected == 0.0 })
    assertTrue(comparison.possible.isEmpty(), "an unknown distribution claimed a range")
    assertEquals(0.0, comparison.standardError, TOLERANCE)
    assertNull(comparison.driftInErrors, "a roll with no expectation was judged against one")
    assertFalse(comparison.worthALook)
  }

  @Test
  fun `standing alone with nothing rolled is empty rather than zero`() {
    val comparison = RolledAgainstExpected.observed(emptyList())

    assertEquals(emptyList<TotalBar>(), comparison.bars)
    assertNull(comparison.mean)
    assertEquals(0L, comparison.throws)
  }

  /**
   * `2d6`, written out.
   *
   * Built here rather than convolved, because a test of a comparison should
   * say what it is comparing against in a form a reader can check: 1, 2, 3, 4,
   * 5, 6, 5, 4, 3, 2, 1 over thirty-six.
   */
  private fun twoD6(): Pmf =
    Pmf.of(offset = 2, weights = intArrayOf(1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1).map { it / 36.0 }.toDoubleArray())

  private companion object {
    const val TOLERANCE = 1e-9
  }
}
