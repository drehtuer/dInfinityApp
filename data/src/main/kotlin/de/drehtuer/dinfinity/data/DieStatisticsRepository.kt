package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.stats.DieSummary
import de.drehtuer.dinfinity.core.stats.FaceTally
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.DieStatsRow
import de.drehtuer.dinfinity.data.db.DieSummaryRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What every die has done, to read (`docs/statistics.md`, per die).
 *
 * The reading half of [StatisticsRepository], apart from it for the same
 * reason [HistoryRepository] is: writing a roll is one transaction across
 * three tables, and reading the aggregates is a flow of one at a time.
 */
class DieStatisticsRepository(
  private val database: DInfinityDatabase,
) {
  /** Every die ever thrown, most recently used first. */
  val dice: Flow<List<DieSummary>> =
    database.dieSummary().all().map { rows -> rows.map(DieSummaryRow::asSummary) }

  /** One die's face counts, lowest value first. */
  fun faces(
    setId: String,
    dieId: String,
  ): Flow<List<FaceTally>> = database.dieStats().histogram(setId, dieId).map { rows -> rows.map(DieStatsRow::asTally) }

  /**
   * "All my d20s", rolled up across every set that has one
   * (`docs/statistics.md`, per standard die type; design option `5c`).
   *
   * Summed in SQL rather than in Kotlin, because it is a question about every
   * row rather than about the page a screen is showing.
   */
  fun facesForSides(sides: Int): Flow<List<FaceTally>> =
    database.dieStats().histogramForSides(sides).map { totals ->
      totals.map { total ->
        FaceTally(setId = "", dieId = "", sides = sides, faceValue = total.faceValue, count = total.total)
      }
    }
}

private fun DieSummaryRow.asSummary(): DieSummary =
  DieSummary(
    setId = setId,
    dieId = dieId,
    sides = sides,
    throws = throws,
    sum = sum,
    sumOfSquares = sumOfSquares,
    highestStreak = highestStreak,
    highestStreakMax = highestStreakMax,
    lowestStreak = lowestStreak,
    lowestStreakMax = lowestStreakMax,
    lastRolledAtEpochMs = lastRolledAtEpochMs,
  )

private fun DieStatsRow.asTally(): FaceTally =
  FaceTally(
    setId = setId,
    dieId = dieId,
    sides = sides,
    faceValue = faceValue,
    count = count,
    droppedCount = droppedCount,
  )
