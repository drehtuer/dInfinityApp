package de.drehtuer.dinfinity.data

import androidx.room.withTransaction
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.stats.DieStatistics
import de.drehtuer.dinfinity.core.stats.DieSummary
import de.drehtuer.dinfinity.core.stats.FaceTally
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.DieStatsRow
import de.drehtuer.dinfinity.data.db.DieSummaryRow
import de.drehtuer.dinfinity.data.db.RollHistoryRow

/**
 * Writes a finished roll down (`docs/statistics.md`, "What is recorded").
 *
 * The history row, every face count and every summary go in **one
 * transaction**. A crash between them would leave a campaign whose
 * natural-20 count disagrees with its own history, and there is no way to tell
 * afterwards which of the two was right.
 *
 * Pruning happens here too, after the write rather than before it: the cap is
 * on the history and nothing else, so aggregates survive the rolls that
 * produced them.
 */
class StatisticsRepository(
  private val database: DInfinityDatabase,
  private val historyLimit: Int = MAX_HISTORY_ROWS,
) {
  /** Records one finished roll, history and aggregates together. */
  suspend fun record(roll: FinishedRoll): Long =
    database.withTransaction {
      val result = roll.result
      val id =
        database.rollHistoryWriting().insert(
          RollHistoryRow(
            timestamp = result.rolledAtEpochMs,
            sessionId = roll.context.sessionId,
            savedRollId = roll.context.savedRollId,
            groupId = roll.context.groupId,
            formula = result.formula,
            total = result.total,
            seed = roll.replay.seed,
            inputBlob = roll.replay.inputBlob,
            breakdownJson = roll.breakdownJson,
            anomalies = result.rethrows + result.forcedSettles,
          ),
        )
      result.dice.forEach { rolled ->
        roll.dice[rolled.instanceIndex]?.let { source -> count(source, rolled, result.rolledAtEpochMs) }
      }
      prune()
      id
    }

  /** Drops the oldest rolls once there are more than the cap allows. */
  suspend fun prune(): Int =
    if (database.rollHistory().count() > historyLimit) {
      database.rollHistoryWriting().pruneToNewest(historyLimit)
    } else {
      0
    }

  /** Forgets one die entirely, counts and streaks alike. */
  suspend fun resetDie(
    setId: String,
    dieId: String,
  ) = database.withTransaction {
    database.dieStats().reset(setId, dieId)
    database.dieSummary().reset(setId, dieId)
  }

  /** Forgets everything, which is the last of the four reset choices. */
  suspend fun resetEverything() =
    database.withTransaction {
      database.rollHistoryWriting().deleteAll()
      database.dieStats().deleteAll()
      database.dieSummary().deleteAll()
    }

  private suspend fun count(
    source: RolledDieSource,
    rolled: RolledDie,
    atEpochMs: Long,
  ) {
    val stats = database.dieStats()
    val existingTally = stats.find(source.setId, source.die.id, rolled.value)?.toTally()
    stats.upsert(
      DieStatistics
        .record(existingTally, source.die, rolled)
        .copy(setId = source.setId, dieId = source.die.id, sides = source.die.shape.faceCount)
        .toRow(),
    )
    val summaries = database.dieSummary()
    val existingSummary = summaries.find(source.setId, source.die.id)?.toSummary()
    summaries.upsert(
      DieStatistics
        .record(existingSummary, source.die, rolled, atEpochMs)
        .copy(setId = source.setId, dieId = source.die.id, sides = source.die.shape.faceCount)
        .toRow(),
    )
  }

  companion object {
    /** How many rolls the history keeps before the oldest go (`docs/statistics.md`). */
    const val MAX_HISTORY_ROWS: Int = 50_000
  }
}

/** Which die a breakdown line came from, and which set it came out of. */
data class RolledDieSource(
  val setId: String,
  val die: Die,
)

/**
 * A roll that has finished, with everything needed to write it down.
 *
 * A parameter object rather than eight arguments, and not a `data class`: it
 * carries a `ByteArray`, and generated equality over one compares by identity,
 * which is never what anybody means.
 *
 * @param dice the die each breakdown line came from, keyed by its position in
 *   the throw. The result knows which face came up; only the die knows whether
 *   that was its highest.
 * @param context the session, saved roll and group this belongs to.
 * @param replay the seed and inputs, kept for reproducing a roll when a bug
 *   report needs it and never shown or exported.
 */
class FinishedRoll(
  val result: RollResult,
  val dice: Map<Int, RolledDieSource>,
  val context: RollContext,
  val breakdownJson: String = "",
  val replay: RollReplay = RollReplay(),
)

/**
 * Where a roll happened: the session it belongs to and, if it came from a
 * saved roll, which one and in which group.
 *
 * All three are what the statistics screens filter and roll up by — "Thorin's
 * attack rolls this campaign" is one screen, and it is this
 * (`docs/statistics.md`).
 */
data class RollContext(
  val sessionId: String,
  val savedRollId: String? = null,
  val groupId: String? = null,
)

/**
 * What a roll can be reproduced from: its seed, and the quantised shake
 * samples or default throw parameters that drove it.
 *
 * It is a thing of its own rather than two more fields so that the rule about
 * it has somewhere to live. These are **internal**. No screen shows them, the
 * export leaves them out, and reproducing a stored roll is a developer action
 * rather than a feature of the app (`docs/statistics.md`, "Storage";
 * `docs/architecture.md`, decision 13).
 */
class RollReplay(
  val seed: Long = 0,
  val inputBlob: ByteArray? = null,
)

private fun DieStatsRow.toTally(): FaceTally =
  FaceTally(
    setId = setId,
    dieId = dieId,
    sides = sides,
    faceValue = faceValue,
    count = count,
    droppedCount = droppedCount,
  )

private fun FaceTally.toRow(): DieStatsRow =
  DieStatsRow(
    setId = setId,
    dieId = dieId,
    sides = sides,
    faceValue = faceValue,
    count = count,
    droppedCount = droppedCount,
  )

private fun DieSummaryRow.toSummary(): DieSummary =
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

private fun DieSummary.toRow(): DieSummaryRow =
  DieSummaryRow(
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
