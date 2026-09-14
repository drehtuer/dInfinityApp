package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.RollHistoryRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Past rolls, to read (`docs/statistics.md`, "History").
 *
 * Apart from [StatisticsRepository], which writes them, because they are two
 * different jobs with two different shapes: writing a roll is one transaction
 * across three tables, and reading them is a flow of one.
 *
 * What comes out of here is deliberately **less** than what is in the row. A
 * past roll is a record, not something to re-run: there is no replay action,
 * the seed is never shown, and the way to be sure of that is for the type the
 * screens see not to have one (`docs/architecture.md`, decision 13).
 */
class HistoryRepository(
  private val database: DInfinityDatabase,
) {
  /** The most recent rolls, newest first. */
  fun recent(limit: Int = PAGE): Flow<List<HistoryEntry>> =
    database.rollHistory().recent(limit).map { rows -> rows.map(RollHistoryRow::asEntry) }

  /** One session's rolls, newest first. */
  fun inSession(
    sessionId: String,
    limit: Int = PAGE,
  ): Flow<List<HistoryEntry>> =
    database.rollHistory().inSession(sessionId, limit).map { rows -> rows.map(RollHistoryRow::asEntry) }

  /** Every roll of one saved roll, which is what its own statistics are made of. */
  fun forSavedRoll(savedRollId: String): Flow<List<HistoryEntry>> =
    database.rollHistory().forSavedRoll(savedRollId).map { rows -> rows.map(RollHistoryRow::asEntry) }

  /**
   * A copy of the history, taken now (`docs/statistics.md`, "Export and reset").
   *
   * @param sessionId only this session's rolls, or null for every session.
   * @param savedRollId only this saved roll's throws, or null for all of them.
   * @param limit how many at most. The table is pruned to
   *   `StatisticsRepository.MAX_HISTORY_ROWS`, so asking for that many is
   *   asking for everything there can be.
   */
  suspend fun snapshot(
    sessionId: String? = null,
    savedRollId: String? = null,
    limit: Int = StatisticsRepository.MAX_HISTORY_ROWS,
  ): List<HistoryEntry> =
    database
      .rollHistory()
      .snapshot(sessionId = sessionId, savedRollId = savedRollId, limit = limit)
      .map(RollHistoryRow::asEntry)

  /**
   * Forgets every roll made in one session (`docs/statistics.md`, "Export and
   * reset").
   *
   * **The session itself stays**, and so does every per-die record. This
   * forgets the *history*, which is one of the two records the app keeps and
   * not the other — the same line `StatisticsRepository.resetDie` draws from
   * the other side, where forgetting a die's aggregate leaves its rolls in the
   * history. A screen that offers this has to say so.
   *
   * @return how many rolls were forgotten.
   */
  suspend fun forgetSession(sessionId: String): Int = database.rollHistoryWriting().forgetSession(sessionId)

  /**
   * Forgets every throw made through one saved roll.
   *
   * The saved roll itself stays; it is a formula somebody wrote down, and this
   * is its record rather than the thing.
   *
   * @return how many throws were forgotten.
   */
  suspend fun forgetSavedRoll(savedRollId: String): Int = database.rollHistoryWriting().forgetSavedRoll(savedRollId)

  companion object {
    /**
     * How many rolls a history screen asks for.
     *
     * A limit rather than everything, because the cap on the table is fifty
     * thousand and nobody scrolls that far. The screens that need more than
     * this are the aggregates, and they are counted in SQL rather than read.
     */
    const val PAGE: Int = 200
  }
}

/**
 * One past roll, as a screen sees it.
 *
 * No seed and no inputs: they are in the row and they stay there. Reproducing
 * a stored roll is a developer action rather than a feature, and a type that
 * cannot carry them is a better guarantee of that than a rule somebody has to
 * remember (`docs/statistics.md`, "Storage").
 *
 * @param groups the breakdown as it was stored — the labels the faces carried,
 *   which dice were dropped, which showed a natural maximum. It is read out of
 *   the row rather than looked up, so a past roll means what it meant then even
 *   after the set that threw it is uninstalled.
 * @param anomalies corrections, re-throws and forced settles. Zero for almost
 *   every roll; a number that climbs is a physics bug
 *   (`docs/physics-and-rendering.md`).
 */
data class HistoryEntry(
  val id: Long,
  val atEpochMs: Long,
  val sessionId: String,
  val savedRollId: String?,
  val groupId: String?,
  val formula: String,
  val total: Long,
  val groups: List<StoredGroup>,
  val anomalies: Int = 0,
) {
  /** True when some die that counted showed its highest face — painted in the accent. */
  val hasNaturalMax: Boolean get() = groups.any { group -> group.kept.any(StoredDie::naturalMax) }

  /** True when there is a breakdown to open at all: `4 + 4` has none. */
  val hasBreakdown: Boolean get() = groups.isNotEmpty()
}

private fun RollHistoryRow.asEntry(): HistoryEntry =
  HistoryEntry(
    id = id,
    atEpochMs = timestamp,
    sessionId = sessionId,
    savedRollId = savedRollId,
    groupId = groupId,
    formula = formula,
    total = total,
    groups = Breakdown.read(breakdownJson),
    anomalies = anomalies,
  )
