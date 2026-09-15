package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.stats.DieSummary
import de.drehtuer.dinfinity.core.stats.FaceTally
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.DieStatsRow
import de.drehtuer.dinfinity.data.db.DieSummaryRow
import de.drehtuer.dinfinity.data.db.FaceTotal
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

  /**
   * Every die thrown in one session, and what it did there
   * (`docs/statistics.md`, per session).
   *
   * Added up from the face counts rather than read off `die_summary`, which
   * has no session and never will — a streak cannot be cut into buckets
   * without coming out short. Throws, sum and sum of squares add exactly, so
   * everything the screen computes from them — the mean, the spread, the fair
   * line — is the same number it would be had it been counted separately all
   * along. The streaks come back zero, and the screen says whose they are.
   */
  fun diceIn(sessionId: String): Flow<List<DieSummary>> =
    database.dieStats().diceInSession(sessionId).map { totals ->
      totals.map { total ->
        DieSummary(
          setId = total.setId,
          dieId = total.dieId,
          sides = total.sides,
          throws = total.throws,
          sum = total.sum,
          sumOfSquares = total.sumOfSquares,
        )
      }
    }

  /** One die's face counts, lowest value first. */
  fun faces(
    setId: String,
    dieId: String,
  ): Flow<List<FaceTally>> = tallies(database.dieStats().histogram(setId, dieId))

  /** The same die, cut to one session. */
  fun facesIn(
    setId: String,
    dieId: String,
    sessionId: String,
  ): Flow<List<FaceTally>> = tallies(database.dieStats().histogramInSession(setId, dieId, sessionId))

  /**
   * Every face of every die, for an export (`docs/statistics.md`, "Export and
   * reset").
   *
   * One shot rather than a flow: a file is a copy taken at a moment, and a
   * screen that re-exported itself every time a roll landed would be opening
   * share sheets.
   */
  suspend fun allFaces(): List<FaceTally> = database.dieStats().everything().map(DieStatsRow::asTally)

  /**
   * "All my d20s", rolled up across every set that has one
   * (`docs/statistics.md`, per standard die type; design option `5c`).
   *
   * Summed in SQL rather than in Kotlin, because it is a question about every
   * row rather than about the page a screen is showing.
   */
  fun facesForSides(sides: Int): Flow<List<FaceTally>> = pooled(sides, database.dieStats().histogramForSides(sides))

  /** The same roll-up, cut to one session. */
  fun facesForSidesIn(
    sides: Int,
    sessionId: String,
  ): Flow<List<FaceTally>> = pooled(sides, database.dieStats().histogramForSidesInSession(sides, sessionId))

  /**
   * Rows as tallies.
   *
   * One place rather than one per query, which is what a session filter would
   * otherwise have doubled: the all-time question and the per-session one
   * differ in their `WHERE`, not in what a row means.
   */
  private fun tallies(rows: Flow<List<DieStatsRow>>): Flow<List<FaceTally>> =
    rows.map { row -> row.map(DieStatsRow::asTally) }

  /** And the same for a roll-up, which has a kind of die but no die of its own. */
  private fun pooled(
    sides: Int,
    totals: Flow<List<FaceTotal>>,
  ): Flow<List<FaceTally>> =
    totals.map { total ->
      total.map { FaceTally(setId = "", dieId = "", sides = sides, faceValue = it.faceValue, count = it.total) }
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
