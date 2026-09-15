package de.drehtuer.dinfinity.data

import androidx.room.withTransaction
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.SessionRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The buckets statistics are filtered by
 * (`docs/statistics.md`, per session; design option 6c).
 *
 * A session is not a thing that happens; it is a label somebody puts on a
 * stretch of rolls. Nothing starts or stops one — the app simply files what is
 * thrown under whichever is active — so there is no "end session" here and
 * nothing to forget to press.
 *
 * Two rules, both of which exist so a roll can never be stranded:
 *
 * - **The default session always exists.** Every history row has carried a
 *   session id since version 1, so the rolls made before sessions did already
 *   belong to one; the migration gives it a name rather than leaving a
 *   thousand rows pointing at nothing.
 * - **Deleting a session moves its rolls rather than deleting them.** The same
 *   rule a group follows, for the same reason: a label being removed should
 *   not remove what it was labelling.
 */
class SessionRepository(
  private val database: DInfinityDatabase,
  private val clock: () -> Long = System::currentTimeMillis,
  private val ids: () -> String = {
    java.util.UUID
      .randomUUID()
      .toString()
  },
) {
  /** Every session, newest first, with how much is in each. */
  val sessions: Flow<List<Session>> =
    combine(database.sessions().all(), database.sessions().tallies()) { rows, tallies ->
      val counted = tallies.associateBy { it.sessionId }
      rows.map { row ->
        Session(
          id = row.id,
          name = row.name,
          startedAtEpochMs = row.startedAtEpochMs,
          rolls = counted[row.id]?.rolls ?: 0,
          naturals = counted[row.id]?.naturals ?: 0,
        )
      }
    }

  /** Makes sure the session every roll can be filed under exists. */
  suspend fun ensureDefault(name: String): String {
    val existing = database.sessions().byId(DEFAULT_ID)
    if (existing == null) {
      database.sessions().upsert(SessionRow(id = DEFAULT_ID, name = name, startedAtEpochMs = 0))
    }
    return DEFAULT_ID
  }

  /** Whether there is still a session with this id. */
  suspend fun exists(sessionId: String): Boolean = database.sessions().byId(sessionId) != null

  /**
   * Starts one, and returns its id.
   *
   * "Starts" only in the sense that it exists from now on. Nothing is running.
   */
  suspend fun create(name: String): String {
    val id = ids()
    database.sessions().upsert(SessionRow(id = id, name = name, startedAtEpochMs = clock()))
    return id
  }

  /**
   * Renames one.
   *
   * The id does not move, so every roll already filed under it stays filed
   * under it — which is the whole point of renaming rather than making a new
   * one.
   */
  suspend fun rename(
    sessionId: String,
    name: String,
  ) {
    val existing = database.sessions().byId(sessionId) ?: return
    database.sessions().upsert(existing.copy(name = name))
  }

  /** True when another session already answers to [name], ignoring case. */
  suspend fun nameTaken(
    name: String,
    exceptId: String = "",
  ): Boolean = database.sessions().countNamed(name, exceptId) > 0

  /**
   * Takes a session away and moves its rolls to the default one.
   *
   * Never deletes a roll. All of it is one transaction, so there is no moment
   * where a roll belongs to a session that is gone.
   */
  suspend fun delete(
    sessionId: String,
    defaultName: String,
  ) {
    require(sessionId != DEFAULT_ID) { "The first session is where rolls go; it cannot be deleted" }
    database.withTransaction {
      ensureDefault(defaultName)
      database.rollHistoryWriting().moveSessionToUnfiled(sessionId, DEFAULT_ID)
      database.sessions().delete(sessionId)
    }
  }

  companion object {
    /**
     * The session every roll belongs to until somebody makes another.
     *
     * The same string `RollRecording` files rolls under before there are
     * sessions, which is what lets that placeholder become a real session the
     * moment this table exists rather than a gap to migrate.
     */
    const val DEFAULT_ID: String = "default"
  }
}

/** One session, with how much is in it (`design/dInfinity.dc.html`, option 6c). */
data class Session(
  val id: String,
  val name: String,
  val startedAtEpochMs: Long,
  val rolls: Long = 0,
  /** How many of those rolls had a die showing its highest face in them. */
  val naturals: Long = 0,
) {
  /** The first session cannot be deleted: it is where a deleted one's rolls go. */
  val deletable: Boolean get() = id != SessionRepository.DEFAULT_ID
}
