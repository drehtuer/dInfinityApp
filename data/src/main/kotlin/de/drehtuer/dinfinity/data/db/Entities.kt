package de.drehtuer.dinfinity.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed roll (`docs/statistics.md`, "Storage").
 *
 * A roll that was cancelled — the app closed mid-tumble — never gets a row:
 * these are throws that finished and were read.
 *
 * @param seed and [inputBlob] are kept so a roll can be reproduced exactly
 *   when a bug report needs it, and are **internal**. No screen shows them and
 *   the export leaves them out. Reproducing a stored roll is a developer
 *   action, not a feature: a past roll is a record, not something to re-run
 *   (`docs/architecture.md`, decision 13).
 * @param breakdownJson the per-die breakdown, stored as written rather than
 *   normalised into rows. It is only ever read back whole, to draw one line of
 *   history, and a roll's breakdown means what it meant *then* — normalising
 *   it would make a set uninstalled last week quietly rewrite last week's
 *   rolls.
 */
@Entity(
  tableName = "roll_history",
  indices = [
    Index("timestamp"),
    Index("session_id"),
    Index("saved_roll_id"),
  ],
)
data class RollHistoryRow(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  @ColumnInfo(name = "timestamp")
  val timestamp: Long,
  @ColumnInfo(name = "session_id")
  val sessionId: String,
  @ColumnInfo(name = "saved_roll_id")
  val savedRollId: String? = null,
  @ColumnInfo(name = "group_id")
  val groupId: String? = null,
  val formula: String,
  val total: Long,
  val seed: Long,
  @ColumnInfo(name = "input_blob", typeAffinity = ColumnInfo.BLOB)
  val inputBlob: ByteArray? = null,
  @ColumnInfo(name = "breakdown_json")
  val breakdownJson: String,
  /** Corrections, re-throws and forced settles, behind the developer toggle. */
  val anomalies: Int = 0,
) {
  // A data class with a ByteArray gets equals and hashCode that compare the
  // array by identity, which is never what anyone means. Room reads rows back
  // as new objects, so identity equality would make every row unequal to
  // itself the moment it made a round trip.
  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is RollHistoryRow && id == other.id && sameContent(other))

  override fun hashCode(): Int = id.hashCode() * HASH_FACTOR + (inputBlob?.contentHashCode() ?: 0)

  private fun sameContent(other: RollHistoryRow): Boolean =
    timestamp == other.timestamp &&
      sessionId == other.sessionId &&
      savedRollId == other.savedRollId &&
      groupId == other.groupId &&
      formula == other.formula &&
      total == other.total &&
      seed == other.seed &&
      breakdownJson == other.breakdownJson &&
      anomalies == other.anomalies &&
      inputBlob.contentEquals(other.inputBlob)

  private companion object {
    const val HASH_FACTOR = 31
  }
}

/**
 * How often one face *value* of one die has come up
 * (`docs/statistics.md`, per die).
 *
 * Keyed by value rather than by face index, so a d6 labelled `1,2,3,1,2,3` has
 * three rows and its histogram is the d3 it really is.
 */
@Entity(
  tableName = "die_stats",
  primaryKeys = ["set_id", "die_id", "face_value"],
  indices = [Index("sides")],
)
data class DieStatsRow(
  @ColumnInfo(name = "set_id")
  val setId: String,
  @ColumnInfo(name = "die_id")
  val dieId: String,
  val sides: Int,
  @ColumnInfo(name = "face_value")
  val faceValue: Int,
  val count: Long = 0,
  @ColumnInfo(name = "dropped_count")
  val droppedCount: Long = 0,
)

/**
 * The running totals and streaks for one die
 * (`docs/statistics.md`, per die).
 *
 * Sums rather than a stored mean, because sums add: a roll updates this by
 * addition, in the same transaction as the history row, with no read of the
 * old average to get wrong.
 */
@Entity(
  tableName = "die_summary",
  primaryKeys = ["set_id", "die_id"],
  indices = [Index("sides"), Index("last_rolled_at")],
)
data class DieSummaryRow(
  @ColumnInfo(name = "set_id")
  val setId: String,
  @ColumnInfo(name = "die_id")
  val dieId: String,
  val sides: Int,
  val throws: Long = 0,
  val sum: Long = 0,
  @ColumnInfo(name = "sum_sq")
  val sumOfSquares: Long = 0,
  @ColumnInfo(name = "hi_streak")
  val highestStreak: Int = 0,
  @ColumnInfo(name = "hi_streak_max")
  val highestStreakMax: Int = 0,
  @ColumnInfo(name = "lo_streak")
  val lowestStreak: Int = 0,
  @ColumnInfo(name = "lo_streak_max")
  val lowestStreakMax: Int = 0,
  @ColumnInfo(name = "last_rolled_at")
  val lastRolledAtEpochMs: Long = 0,
)
