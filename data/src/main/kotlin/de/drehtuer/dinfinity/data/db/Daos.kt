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

/** Groups of saved rolls (`docs/dice-notation.md`, "Saved rolls"). */
@Dao
interface SavedRollGroupDao {
  /**
   * Every group, in the order the switcher shows them.
   *
   * Parents and children in one list rather than a tree: groups nest exactly
   * one level, so a list plus a parent id *is* the tree, and the screen that
   * draws it does not have to walk anything.
   */
  @Query("SELECT * FROM saved_roll_group ORDER BY sort_order, name")
  fun all(): Flow<List<SavedRollGroupRow>>

  @Query("SELECT * FROM saved_roll_group WHERE id = :id")
  suspend fun byId(id: String): SavedRollGroupRow?

  @Query("SELECT COUNT(*) FROM saved_roll_group WHERE name = :name AND id <> :exceptId")
  suspend fun countNamed(
    name: String,
    exceptId: String = "",
  ): Int

  /**
   * How many groups are inside this one.
   *
   * Asked before a group is given a parent of its own: a group with children
   * that moved inside another would be three levels deep, which is the one
   * shape `docs/dice-notation.md` says does not exist.
   */
  @Query("SELECT COUNT(*) FROM saved_roll_group WHERE parent_id = :id")
  suspend fun countChildren(id: String): Int

  @Upsert
  suspend fun upsert(row: SavedRollGroupRow)

  /**
   * Lifts the children of one group to the top level.
   *
   * What deleting a parent does to them. The alternative — deleting the
   * children too — would take rolls with it, and a folder being removed
   * should not remove what somebody put in it.
   */
  @Query("UPDATE saved_roll_group SET parent_id = NULL WHERE parent_id = :id")
  suspend fun detachChildren(id: String)

  @Query("DELETE FROM saved_roll_group WHERE id = :id")
  suspend fun delete(id: String)
}

/** Saved rolls (`docs/dice-notation.md`, "Saved rolls"). */
@Dao
interface SavedRollDao {
  /**
   * The rolls of one group, favourites first and then by recent use.
   *
   * The ordering is in SQL rather than in Kotlin because it is what the list
   * is: a roll used ten minutes ago belongs above one used last month, and a
   * favourite belongs above both. A roll that has never been used sorts last
   * among its kind, which is what `last_used_at IS NULL` does here.
   */
  @Query(
    """
    SELECT * FROM saved_roll WHERE group_id = :groupId
    ORDER BY favourite DESC, last_used_at IS NULL, last_used_at DESC, name
    """,
  )
  fun inGroup(groupId: String): Flow<List<SavedRollRow>>

  @Query("SELECT * FROM saved_roll ORDER BY favourite DESC, last_used_at IS NULL, last_used_at DESC, name")
  fun all(): Flow<List<SavedRollRow>>

  @Query("SELECT * FROM saved_roll WHERE id = :id")
  suspend fun byId(id: String): SavedRollRow?

  @Upsert
  suspend fun upsert(row: SavedRollRow)

  @Query("DELETE FROM saved_roll WHERE id = :id")
  suspend fun delete(id: String)

  /** Moves every roll of one group to another, which is what deleting a group does. */
  @Query("UPDATE saved_roll SET group_id = :toGroupId WHERE group_id = :fromGroupId")
  suspend fun move(
    fromGroupId: String,
    toGroupId: String,
  )

  /**
   * One more use of this roll, at [atEpochMs].
   *
   * An increment in SQL rather than a read and a write: two taps in quick
   * succession would otherwise both read the old count and both store the
   * same new one.
   */
  @Query("UPDATE saved_roll SET use_count = use_count + 1, last_used_at = :atEpochMs WHERE id = :id")
  suspend fun used(
    id: String,
    atEpochMs: Long,
  )
}
