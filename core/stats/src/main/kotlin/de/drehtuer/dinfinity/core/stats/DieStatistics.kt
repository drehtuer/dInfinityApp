package de.drehtuer.dinfinity.core.stats

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.RolledDie

/**
 * Folds one thrown die into what was already known about it.
 *
 * Pure, and deliberately so: streaks are the part of statistics that is easy
 * to get subtly wrong — a streak that survives a different die, or a highest
 * and a lowest both counting on a coin — and none of that should need a
 * database to find out about (`docs/statistics.md`).
 */
object DieStatistics {
  /**
   * [summary] with [rolled] added, or a fresh summary when this die has never
   * been thrown before.
   *
   * A die that is neither its highest nor its lowest breaks both streaks. A
   * die that is both — a one-faced die, or a coin whose faces read the same —
   * counts as its highest only, so the two streaks can never run together and
   * "natural highs" and "natural lows" cannot both be every throw.
   */
  fun record(
    summary: DieSummary?,
    die: Die,
    rolled: RolledDie,
    atEpochMs: Long,
  ): DieSummary {
    val previous = summary ?: DieSummary(setId = "", dieId = die.id, sides = die.shape.faceCount)
    val highest = rolled.value == die.maxValue
    val lowest = !highest && rolled.value == die.minValue
    val highStreak = if (highest) previous.highestStreak + 1 else 0
    val lowStreak = if (lowest) previous.lowestStreak + 1 else 0
    return previous.copy(
      throws = previous.throws + 1,
      sum = previous.sum + rolled.value,
      sumOfSquares = previous.sumOfSquares + rolled.value.toLong() * rolled.value,
      highestStreak = highStreak,
      highestStreakMax = maxOf(previous.highestStreakMax, highStreak),
      lowestStreak = lowStreak,
      lowestStreakMax = maxOf(previous.lowestStreakMax, lowStreak),
      lastRolledAtEpochMs = atEpochMs,
    )
  }

  /**
   * [tally] with one more throw of this face on it.
   *
   * A dropped die still counts. It was thrown, it landed on that face, and
   * pretending otherwise would make `4d6dl1` look like a d6 that never rolls
   * ones (`docs/statistics.md`).
   */
  fun record(
    tally: FaceTally?,
    die: Die,
    rolled: RolledDie,
  ): FaceTally {
    val previous =
      tally ?: FaceTally(setId = "", dieId = die.id, sides = die.shape.faceCount, faceValue = rolled.value)
    return previous.copy(
      count = previous.count + 1,
      droppedCount = previous.droppedCount + if (rolled.kept) 0 else 1,
    )
  }

  /**
   * The fair share of a die's throws each of its face *values* should get.
   *
   * The faint line the histogram is drawn against (`docs/statistics.md`,
   * "Overview"). It is per value rather than per face, so a d6 labelled
   * `1,2,3,1,2,3` is drawn against a third each rather than a sixth.
   */
  fun expectedShare(die: Die): Map<Int, Double> =
    die
      .values()
      .groupingBy { it }
      .eachCount()
      .mapValues { (_, faces) -> faces.toDouble() / die.faces.size }
}
