package de.drehtuer.dinfinity.core.stats

import de.drehtuer.dinfinity.core.probability.Pmf
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One total a saved roll can come to, how often it has, and how often it should
 * have (`docs/statistics.md`; design options `8b` and `9e`).
 *
 * @param expected what fraction of throws the exact distribution puts here —
 *   the red mark the ink bar is drawn against.
 * @param observed what fraction of throws actually landed here.
 */
data class TotalBar(
  val total: Int,
  val count: Long,
  val observed: Double,
  val expected: Double,
) {
  /** True when this total has come up more often than the maths says it should. */
  val over: Boolean get() = observed > expected
}

/**
 * What a saved roll has actually done, against what it was supposed to do
 * (design options `8b` and `9e`).
 *
 * @param throws how many times it has been rolled.
 * @param mean the average total actually rolled, or null before there is one.
 * @param expectedMean what the distribution says the average is.
 * @param lowest the lowest total rolled, and [highest] the highest — the range,
 *   which is what a player compares against the distribution's own ends.
 */
data class RollComparison(
  val bars: List<TotalBar> = emptyList(),
  val throws: Long = 0,
  val mean: Double? = null,
  val expectedMean: Double = 0.0,
  val lowest: Int? = null,
  val highest: Int? = null,
  val possible: IntRange = IntRange.EMPTY,
  /**
   * σ/√n for these throws of this distribution: the scale a drift is judged
   * against. Zero before anything has been rolled.
   */
  val standardError: Double = 0.0,
) {
  /** How far the average rolled is from the average expected, or null before there is one. */
  val drift: Double? get() = mean?.let { it - expectedMean }

  /**
   * How far off the average is, counted in standard errors — or null when
   * there is nothing to say yet.
   *
   * The number that decides whether a drift is worth looking at. A mean two
   * tenths above expectation is remarkable after ten thousand throws and
   * nothing at all after four, and a screen that showed only the drift would
   * invite the second reading. Null below [ENOUGH_TO_JUDGE] throws for the same
   * reason: with one or two throws there is no scale to divide by.
   */
  val driftInErrors: Double? get() = errors()

  /**
   * True when the average rolled is far enough from expectation to be worth a
   * word.
   *
   * Two standard errors, which is the usual line and is a long way from proof.
   * The screen says "worth a look", not "loaded" — dice are not accused on the
   * strength of forty throws (`docs/statistics.md`).
   */
  val worthALook: Boolean get() = errors()?.let { abs(it) >= NOTEWORTHY_ERRORS } == true

  private fun errors(): Double? {
    if (throws < ENOUGH_TO_JUDGE || standardError <= 0.0) return null
    return drift?.div(standardError)
  }

  companion object {
    /** Below this many throws there is no scale to judge a drift against. */
    const val ENOUGH_TO_JUDGE: Long = 20

    /** How many standard errors counts as worth mentioning. */
    const val NOTEWORTHY_ERRORS: Double = 2.0
  }
}

/**
 * Comparing the totals somebody rolled with the distribution they were rolling
 * against (`docs/probability.md`; `docs/statistics.md`).
 *
 * The app's whole claim is that the result comes from physics rather than from
 * a generator, and this is the screen where that claim can be checked: the
 * exact distribution is computed from the formula by `core/probability`, and
 * the bars are what the dice actually did. Nothing here smooths, bins or fits —
 * a comparison that tidied the data would be answering a different question.
 *
 * Pure arithmetic over a list of totals and a [Pmf], so the part that can be
 * wrong is the part a JVM test can reach.
 */
object RolledAgainstExpected {
  /**
   * Every total the formula can reach, with what it did and what it should do.
   *
   * The bars span the **distribution's** support rather than what was rolled,
   * so a total that has never come up is a bar of zero next to its expected
   * mark. That is the interesting shape — "this roll has never once reached
   * 24" — and a chart drawn only over observed totals would hide it.
   *
   * A total outside the distribution is still counted. It should not happen,
   * and if it does — a formula edited after the rolls were made, a set whose
   * die changed — the honest thing is to show it rather than to drop it.
   */
  fun of(
    totals: List<Long>,
    expected: Pmf,
  ): RollComparison {
    val counted = totals.groupingBy { it.toInt() }.eachCount()
    val throws = totals.size.toLong()
    val values = (expected.support.toList() + counted.keys).distinct().sorted()
    val bars =
      values.map { total ->
        val count = counted[total]?.toLong() ?: 0L
        TotalBar(
          total = total,
          count = count,
          observed = if (throws == 0L) 0.0 else count.toDouble() / throws,
          expected = expected.probabilityOf(total),
        )
      }
    return RollComparison(
      bars = bars,
      throws = throws,
      mean = if (throws == 0L) null else totals.sum().toDouble() / throws,
      expectedMean = expected.mean,
      lowest = totals.minOrNull()?.toInt(),
      highest = totals.maxOrNull()?.toInt(),
      possible = expected.support,
      standardError = errorOf(expected, throws),
    )
  }

  /**
   * The bars alone, for a roll whose distribution cannot be computed.
   *
   * A saved roll's formula is stored as text and the set it names can be
   * uninstalled afterwards (`docs/dice-notation.md`), so a roll that graphed
   * last week may not today. What the dice did is still the player's record,
   * and hiding it because the formula stopped resolving would lose the only
   * copy of it — so the ink bars are drawn with no red marks to draw them
   * against, and the screen says why.
   *
   * Every expected share is zero, which is not a claim that the totals are
   * impossible. It is the absence of a claim, and [RollComparison.possible]
   * being empty is what says so.
   */
  fun observed(totals: List<Long>): RollComparison {
    val counted = totals.groupingBy { it.toInt() }.eachCount()
    val throws = totals.size.toLong()
    return RollComparison(
      bars =
        counted.keys.sorted().map { total ->
          val count = counted.getValue(total).toLong()
          TotalBar(total = total, count = count, observed = count.toDouble() / throws, expected = 0.0)
        },
      throws = throws,
      mean = if (throws == 0L) null else totals.sum().toDouble() / throws,
      lowest = totals.minOrNull()?.toInt(),
      highest = totals.maxOrNull()?.toInt(),
    )
  }

  /**
   * The standard error of the mean of [throws] throws of [expected].
   *
   * σ/√n. It is what turns a drift into a number that can be judged: the same
   * two tenths above expectation is noise after four throws and a great deal
   * after ten thousand.
   */
  private fun errorOf(
    expected: Pmf,
    throws: Long,
  ): Double = if (throws <= 0) 0.0 else expected.standardDeviation / sqrt(throws.toDouble())
}
