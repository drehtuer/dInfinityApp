package de.drehtuer.dinfinity.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Past rolls (`docs/statistics.md`, "History"). */
@Dao
interface RollHistoryDao {
  @Insert
  suspend fun insert(row: RollHistoryRow): Long

  /**
   * The most recent rolls, newest first.
   *
   * A [Flow], so the history screen redraws itself when a roll lands rather
   * than when somebody remembers to refresh it.
   */
  @Query("SELECT * FROM roll_history ORDER BY timestamp DESC, id DESC LIMIT :limit")
  fun recent(limit: Int): Flow<List<RollHistoryRow>>

  @Query("SELECT * FROM roll_history WHERE session_id = :sessionId ORDER BY timestamp DESC, id DESC LIMIT :limit")
  fun inSession(
    sessionId: String,
    limit: Int,
  ): Flow<List<RollHistoryRow>>

  @Query("SELECT * FROM roll_history WHERE saved_roll_id = :savedRollId ORDER BY timestamp DESC, id DESC")
  fun forSavedRoll(savedRollId: String): Flow<List<RollHistoryRow>>

  @Query("SELECT COUNT(*) FROM roll_history")
  suspend fun count(): Long

  /**
   * Drops all but the newest [keep] rolls.
   *
   * Only the history is pruned; the aggregates are never touched, so a
   * campaign's natural-20 count survives the rolls that produced it
   * (`docs/statistics.md`).
   */
  @Query(
    "DELETE FROM roll_history WHERE id NOT IN " +
      "(SELECT id FROM roll_history ORDER BY timestamp DESC, id DESC LIMIT :keep)",
  )
  suspend fun pruneToNewest(keep: Int): Int

  /** Deleting a session moves its rolls to Unfiled rather than deleting them. */
  @Query("UPDATE roll_history SET session_id = :unfiled WHERE session_id = :sessionId")
  suspend fun moveSessionToUnfiled(
    sessionId: String,
    unfiled: String,
  ): Int

  @Query("DELETE FROM roll_history")
  suspend fun deleteAll(): Int
}

/** Per-face counts (`docs/statistics.md`, per die). */
@Dao
interface DieStatsDao {
  @Upsert
  suspend fun upsert(row: DieStatsRow)

  @Query("SELECT * FROM die_stats WHERE set_id = :setId AND die_id = :dieId AND face_value = :faceValue")
  suspend fun find(
    setId: String,
    dieId: String,
    faceValue: Int,
  ): DieStatsRow?

  @Query("SELECT * FROM die_stats WHERE set_id = :setId AND die_id = :dieId ORDER BY face_value")
  fun histogram(
    setId: String,
    dieId: String,
  ): Flow<List<DieStatsRow>>

  /** "All my d20s", rolled up across every set that has one. */
  @Query(
    "SELECT face_value, SUM(count) AS total FROM die_stats " +
      "WHERE sides = :sides GROUP BY face_value ORDER BY face_value",
  )
  fun histogramForSides(sides: Int): Flow<List<FaceTotal>>

  @Query("DELETE FROM die_stats WHERE set_id = :setId AND die_id = :dieId")
  suspend fun reset(
    setId: String,
    dieId: String,
  ): Int

  @Query("DELETE FROM die_stats")
  suspend fun deleteAll(): Int
}

/** One face value and how often it has come up, across sets. */
data class FaceTotal(
  @androidx.room.ColumnInfo(name = "face_value")
  val faceValue: Int,
  val total: Long,
)

/** Running totals and streaks (`docs/statistics.md`, per die). */
@Dao
interface DieSummaryDao {
  @Upsert
  suspend fun upsert(row: DieSummaryRow)

  @Query("SELECT * FROM die_summary WHERE set_id = :setId AND die_id = :dieId")
  suspend fun find(
    setId: String,
    dieId: String,
  ): DieSummaryRow?

  @Query("SELECT * FROM die_summary ORDER BY last_rolled_at DESC")
  fun all(): Flow<List<DieSummaryRow>>

  @Query("SELECT * FROM die_summary WHERE set_id = :setId ORDER BY sides")
  fun forSet(setId: String): Flow<List<DieSummaryRow>>

  @Query("DELETE FROM die_summary WHERE set_id = :setId AND die_id = :dieId")
  suspend fun reset(
    setId: String,
    dieId: String,
  ): Int

  @Query("DELETE FROM die_summary")
  suspend fun deleteAll(): Int
}
