package de.drehtuer.dinfinity.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Past rolls, to read (`docs/statistics.md`, "History").
 *
 * Reading and changing are two interfaces over one table, split when this one
 * reached detekt's function ceiling. The line is not arbitrary: it is the same
 * one the repositories above already draw, where `HistoryRepository` reads and
 * `StatisticsRepository` writes. Raising the threshold would have hidden that
 * the split was overdue (`docs/TODO.md`, 4.3 says the same about
 * `SavedRollRepository`).
 */
@Dao
interface RollHistoryDao {
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

  /**
   * The same three questions the flows above answer, asked once.
   *
   * One query rather than three because what varies is which filters are on,
   * and a null means "not this one" — which is exactly what an export needs: a
   * copy taken at a moment, of whatever the screen was cut to. A flow would be
   * the wrong shape twice over, once because nobody is watching a file and
   * once because a screen that re-exported itself on every roll would be
   * opening share sheets.
   */
  @Query(
    """
    SELECT * FROM roll_history
    WHERE (:sessionId IS NULL OR session_id = :sessionId)
      AND (:savedRollId IS NULL OR saved_roll_id = :savedRollId)
    ORDER BY timestamp DESC, id DESC
    LIMIT :limit
    """,
  )
  suspend fun snapshot(
    sessionId: String?,
    savedRollId: String?,
    limit: Int,
  ): List<RollHistoryRow>

  @Query("SELECT COUNT(*) FROM roll_history")
  suspend fun count(): Long
}

/**
 * Past rolls, to change.
 *
 * Everything here either adds a roll or takes some away, which is why they are
 * together: a reader of this file can see every way the history can shrink in
 * one screenful, and there are four.
 */
@Dao
interface RollHistoryWritingDao {
  @Insert
  suspend fun insert(row: RollHistoryRow): Long

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

  /**
   * Forgets one session's rolls, or one saved roll's throws.
   *
   * Not the same act as deleting a session, which moves its rolls to the first
   * one rather than deleting them — this is the one that takes them away.
   *
   * Two queries rather than one with two nullable parameters, which is how
   * `snapshot` is written: a null there means "not filtered by this", and the
   * same shape on a DELETE would mean that passing nothing deletes everything.
   * A reading query with a footgun is a reading query; a deleting one is a bug
   * report.
   */
  @Query("DELETE FROM roll_history WHERE session_id = :sessionId")
  suspend fun forgetSession(sessionId: String): Int

  @Query("DELETE FROM roll_history WHERE saved_roll_id = :savedRollId")
  suspend fun forgetSavedRoll(savedRollId: String): Int

  /** Deleting a session moves its rolls to the first one rather than deleting them. */
  @Query("UPDATE roll_history SET session_id = :unfiled WHERE session_id = :sessionId")
  suspend fun moveSessionToUnfiled(
    sessionId: String,
    unfiled: String,
  ): Int

  @Query("DELETE FROM roll_history")
  suspend fun deleteAll(): Int
}

@Dao
interface DieStatsDao {
  @Upsert
  suspend fun upsert(row: DieStatsRow)

  @Query(
    "SELECT * FROM die_stats WHERE set_id = :setId AND die_id = :dieId " +
      "AND session_id = :sessionId AND face_value = :faceValue",
  )
  suspend fun find(
    setId: String,
    dieId: String,
    sessionId: String,
    faceValue: Int,
  ): DieStatsRow?

  /**
   * Every face of every die, for an export.
   *
   * One shot rather than a flow, and unfiltered: a file is a copy taken at a
   * moment, and the thing being copied is the whole record. One row per face
   * per die, so it is bounded by the dice that have been thrown rather than by
   * how often they were — the sessions are summed away, because a file of what
   * every die has done is not the place a player would look for a campaign.
   */
  @Query(
    "SELECT set_id, die_id, '' AS session_id, sides, face_value, " +
      "SUM(count) AS count, SUM(dropped_count) AS dropped_count FROM die_stats " +
      "GROUP BY set_id, die_id, face_value ORDER BY set_id, die_id, face_value",
  )
  suspend fun everything(): List<DieStatsRow>

  /**
   * One die's face counts over every session, lowest value first.
   *
   * Summed rather than selected, because the rows are per session since
   * version 5 and a die thrown in three campaigns has three rows per face.
   * The session column is not in the result: what comes back stands for all of
   * them, and a value there would have to be one session's or a lie.
   */
  @Query(
    "SELECT set_id, die_id, '' AS session_id, sides, face_value, " +
      "SUM(count) AS count, SUM(dropped_count) AS dropped_count FROM die_stats " +
      "WHERE set_id = :setId AND die_id = :dieId GROUP BY face_value ORDER BY face_value",
  )
  fun histogram(
    setId: String,
    dieId: String,
  ): Flow<List<DieStatsRow>>

  /** The same die, cut to the one session (`docs/statistics.md`, per session). */
  @Query(
    "SELECT * FROM die_stats WHERE set_id = :setId AND die_id = :dieId " +
      "AND session_id = :sessionId ORDER BY face_value",
  )
  fun histogramInSession(
    setId: String,
    dieId: String,
    sessionId: String,
  ): Flow<List<DieStatsRow>>

  /** "All my d20s", rolled up across every set that has one. */
  @Query(
    "SELECT face_value, SUM(count) AS total FROM die_stats " +
      "WHERE sides = :sides GROUP BY face_value ORDER BY face_value",
  )
  fun histogramForSides(sides: Int): Flow<List<FaceTotal>>

  /** And the same roll-up in one session. */
  @Query(
    "SELECT face_value, SUM(count) AS total FROM die_stats " +
      "WHERE sides = :sides AND session_id = :sessionId GROUP BY face_value ORDER BY face_value",
  )
  fun histogramForSidesInSession(
    sides: Int,
    sessionId: String,
  ): Flow<List<FaceTotal>>

  /**
   * Every die that was thrown in one session, and what it did there.
   *
   * The list the statistics screen draws when it is cut to a session. It
   * cannot come from `die_summary`, which has no session and never will
   * ([DieSummaryRow]) — so it is added up from the face counts, where throws,
   * sum and sum of squares are exactly what they would have been had they been
   * counted separately all along.
   *
   * `die_summary` is joined anyway, for the one thing face counts cannot say:
   * *when*. The order is the die's own last throw rather than its last throw in
   * this session, because that is the only timestamp there is and because it is
   * still the right answer to what the order is for — a player comes to this
   * screen about a die they have just been rolling.
   */
  @Query(
    "SELECT d.set_id AS set_id, d.die_id AS die_id, d.sides AS sides, " +
      "SUM(d.count) AS throws, SUM(d.face_value * d.count) AS sum, " +
      "SUM(d.face_value * d.face_value * d.count) AS sum_sq " +
      "FROM die_stats d LEFT JOIN die_summary m " +
      "ON m.set_id = d.set_id AND m.die_id = d.die_id " +
      "WHERE d.session_id = :sessionId " +
      "GROUP BY d.set_id, d.die_id ORDER BY m.last_rolled_at DESC",
  )
  fun diceInSession(sessionId: String): Flow<List<SessionTotal>>

  @Query("DELETE FROM die_stats WHERE set_id = :setId AND die_id = :dieId")
  suspend fun reset(
    setId: String,
    dieId: String,
  ): Int

  @Query("DELETE FROM die_stats")
  suspend fun deleteAll(): Int
}

/**
 * What one die did in one session: added up from its face counts.
 *
 * The three numbers a [DieSummaryRow] carries that *add*, and deliberately not
 * the streaks, which do not — see [DieSummaryRow].
 */
data class SessionTotal(
  @androidx.room.ColumnInfo(name = "set_id")
  val setId: String,
  @androidx.room.ColumnInfo(name = "die_id")
  val dieId: String,
  val sides: Int,
  val throws: Long,
  val sum: Long,
  @androidx.room.ColumnInfo(name = "sum_sq")
  val sumOfSquares: Long,
)

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

  /**
   * Every group's name.
   *
   * Read in one go rather than asked per group: an import checks up to fifty
   * names against what is here, and fifty queries to answer one question is
   * fifty chances for the answer to change halfway through.
   */
  @Query("SELECT name FROM saved_roll_group")
  suspend fun allNames(): List<String>

  /** The bottom of the switcher, so imported groups land after what is there. */
  @Query("SELECT MAX(sort_order) FROM saved_roll_group")
  suspend fun maxSortOrder(): Int?

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
   * The rolls of one group, in the order the player dragged them into.
   *
   * The ordering is in SQL rather than in Kotlin because it is what the list
   * *is*. Nothing about use or recency comes into it: a list that reorders
   * itself between two fights is a list nobody can point at
   * (`docs/dice-notation.md`, "Saved rolls"). `name` only settles two rolls
   * that somehow share a place, which an import or a restore can produce.
   */
  @Query(
    """
    SELECT * FROM saved_roll WHERE group_id = :groupId
    ORDER BY sort_order, name
    """,
  )
  fun inGroup(groupId: String): Flow<List<SavedRollRow>>

  @Query("SELECT * FROM saved_roll ORDER BY sort_order, name")
  fun all(): Flow<List<SavedRollRow>>

  /** The bottom of a group's list, so a new roll lands under what is there. */
  @Query("SELECT MAX(sort_order) FROM saved_roll WHERE group_id = :groupId")
  suspend fun maxSortOrder(groupId: String): Int?

  /** Where one roll sits, written after a drag has ended. */
  @Query("UPDATE saved_roll SET sort_order = :order WHERE id = :id")
  suspend fun setSortOrder(
    id: String,
    order: Int,
  )

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

/** Sessions, the buckets statistics are filtered by (`docs/statistics.md`). */
@Dao
interface SessionDao {
  /** Every session, newest first. A session is not renamed to the top. */
  @Query("SELECT * FROM session ORDER BY started_at DESC, name")
  fun all(): Flow<List<SessionRow>>

  @Query("SELECT * FROM session WHERE id = :id")
  suspend fun byId(id: String): SessionRow?

  /**
   * Whether a name is taken, ignoring case.
   *
   * `COLLATE NOCASE` rather than lower-casing in Kotlin: SQLite's `=` is
   * case-sensitive, and two sessions a capital apart are one session to a
   * person.
   */
  @Query("SELECT COUNT(*) FROM session WHERE name = :name COLLATE NOCASE AND id <> :exceptId")
  suspend fun countNamed(
    name: String,
    exceptId: String = "",
  ): Int

  @Upsert
  suspend fun upsert(row: SessionRow)

  @Query("DELETE FROM session WHERE id = :id")
  suspend fun delete(id: String)

  /**
   * How many rolls each session holds, and how many of them showed a die's
   * highest face.
   *
   * Counted in SQL rather than by reading the rolls: a session of a thousand
   * throws is a thousand rows nobody wants on the way to a number, and the
   * list shows both figures for every session at once.
   *
   * The natural-max count reads the stored breakdown rather than joining
   * anything, because a breakdown means what it meant then — the set that
   * threw it may be long uninstalled (`docs/statistics.md`, "Storage").
   */
  @Query(
    """
    SELECT session_id AS sessionId,
           COUNT(*) AS rolls,
           SUM(CASE WHEN breakdown_json LIKE '%"max":true%' THEN 1 ELSE 0 END) AS naturals
    FROM roll_history GROUP BY session_id
    """,
  )
  fun tallies(): Flow<List<SessionTally>>
}

/** How much is in one session. */
data class SessionTally(
  val sessionId: String,
  val rolls: Long,
  val naturals: Long,
)

/**
 * The installed-set registry (`docs/dice-sets.md`, design `5a`).
 *
 * Small on purpose. Everything about what a package *is* comes from the folder
 * it lives in; this is only what the player has said about it.
 */
@Dao
interface InstalledSetDao {
  /**
   * Every opinion on record. Sets with no row are enabled by default.
   *
   * A one-shot read rather than a `Flow`, because nothing observes it: the
   * dice-set screen reads the `dicesets/` folder when it opens and after
   * anything that changes it, and the opinion is read in the same breath. A
   * flow here would emit on a schedule that had nothing to do with when the
   * disk was last looked at.
   */
  @Query("SELECT * FROM installed_set")
  suspend fun all(): List<InstalledSetRow>

  @Query("SELECT * FROM installed_set WHERE id = :id")
  suspend fun byId(id: String): InstalledSetRow?

  @Upsert
  suspend fun upsert(row: InstalledSetRow)

  @Query("DELETE FROM installed_set WHERE id = :id")
  suspend fun delete(id: String)

  /**
   * Forgets every set that is no longer on disk.
   *
   * A folder can vanish without the app being asked — a restore, a file
   * manager, a failed update — and a row left behind would switch a *new* set
   * off the moment somebody installed one under the same id.
   */
  @Query("DELETE FROM installed_set WHERE id NOT IN (:present)")
  suspend fun keepOnly(present: List<String>)
}
