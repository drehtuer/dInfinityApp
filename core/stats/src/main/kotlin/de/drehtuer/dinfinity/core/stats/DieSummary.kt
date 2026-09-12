package de.drehtuer.dinfinity.core.stats

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Everything known about one die, in the few numbers it takes to answer the
 * questions players actually ask (`docs/statistics.md`).
 *
 * Every face is equally likely and nobody needs telling. What a player wants
 * is "how many natural 20s have I rolled this campaign" and "is this d20
 * cursed", and those are counts and streaks — so counts and streaks are what
 * is kept, rather than a table of every throw.
 *
 * The mean and the variance come out of [sum] and [sumOfSquares] rather than
 * out of a stored average, because those two add: a roll updates them by
 * addition, in the same transaction as the history row, with no read of the
 * old mean to get wrong.
 *
 * @param sides the die's face count, so "all my d20s" can be rolled up across
 *   sets without joining anything (`docs/statistics.md`, per standard type).
 * @param highestStreak how many times in a row it has just shown its highest
 *   face. Highest is the die's own highest *value*, so a d6 labelled
 *   `1,2,3,1,2,3` has a highest of 3.
 */
data class DieSummary(
  val setId: String,
  val dieId: String,
  val sides: Int,
  val throws: Long = 0,
  val sum: Long = 0,
  val sumOfSquares: Long = 0,
  val highestStreak: Int = 0,
  val highestStreakMax: Int = 0,
  val lowestStreak: Int = 0,
  val lowestStreakMax: Int = 0,
  val lastRolledAtEpochMs: Long = 0,
) {
  /** What this die has averaged, or `null` before it has ever been thrown. */
  val mean: Double? get() = if (throws == 0L) null else sum.toDouble() / throws

  /**
   * The variance of what it has shown, or `null` before there are two throws
   * to have a variance between.
   *
   * The sample variance — divided by `n − 1` — because these are throws that
   * happened, not the whole population of throws that could.
   */
  val variance: Double?
    get() {
      if (throws < 2) return null
      val average = sum.toDouble() / throws
      return max(0.0, (sumOfSquares - average * sum) / (throws - 1))
    }

  /** The spread, in the units the die scores in. */
  val standardDeviation: Double? get() = variance?.let(::sqrt)
}

/**
 * How often one face value of one die has come up
 * (`docs/statistics.md`, per die).
 *
 * Keyed by face *value*, not by face index: a d6 labelled `1,2,3,1,2,3` has
 * three tallies, not six, and its histogram is the d3 it really is.
 *
 * @param droppedCount how many of those were thrown away by `kh`, `dl` and
 *   friends. They still count — the die was thrown and it landed on that face
 *   — but the breakdown remembers which, so a player who wonders whether their
 *   advantage rolls are unlucky can be shown.
 */
data class FaceTally(
  val setId: String,
  val dieId: String,
  val sides: Int,
  val faceValue: Int,
  val count: Long = 0,
  val droppedCount: Long = 0,
)
