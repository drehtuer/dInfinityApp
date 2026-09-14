package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.RollHistoryRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The buckets statistics are filtered by
 * (`docs/statistics.md`, per session; design option 6c).
 *
 * Against a real database, because the two things worth being certain of are
 * both about rows other tables point at: that deleting a session moves its
 * rolls rather than deleting them, and that the counts on the list are SQL's.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var sessions: SessionRepository
  private var next = 0

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        // Queries and invalidation on the calling thread, so a `Flow` from a
        // `@Query` emits when the write happens rather than when a pool thread
        // gets to it. Without it the first wait in a class races Room's own
        // executors, which surfaces as an unrelated test failing now and then.
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
    sessions = SessionRepository(database, clock = { NOW }, ids = { "id-${next++}" })
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `the first session is made once and not again`() =
    runTest {
      sessions.ensureDefault("First rolls")
      sessions.ensureDefault("Ignored")

      assertEquals(listOf("First rolls"), names())
    }

  @Test
  fun `a session is made with a name and a time`() =
    runTest {
      val id = sessions.create("Tuesday campaign")

      val made = sessions.sessions.first().single { it.id == id }
      assertEquals("Tuesday campaign", made.name)
      assertEquals(NOW, made.startedAtEpochMs)
    }

  @Test
  fun `renaming keeps the id, so the rolls filed under it stay filed under it`() =
    runTest {
      val id = sessions.create("Tuesday")
      rolled(session = id)

      sessions.rename(id, "Curse of Strahd")

      assertEquals(
        "Curse of Strahd",
        sessions.sessions
          .first()
          .single { it.id == id }
          .name,
      )
      assertEquals(
        1,
        database
          .rollHistory()
          .inSession(id, 10)
          .first()
          .size,
      )
    }

  @Test
  fun `renaming a session nothing answers to does nothing rather than making one`() =
    runTest {
      sessions.rename("never-existed", "Ghost")

      assertEquals(emptyList<String>(), names())
    }

  @Test
  fun `a name another session has is a clash, ignoring case`() =
    runTest {
      sessions.create("Tuesday")

      assertTrue(sessions.nameTaken("tuesday"))
      assertFalse(sessions.nameTaken("Wednesday"))
    }

  @Test
  fun `a session does not clash with itself`() =
    runTest {
      val id = sessions.create("Tuesday")

      assertFalse(sessions.nameTaken("Tuesday", exceptId = id))
    }

  @Test
  fun `the newest session is first, because a session is not renamed to the top`() =
    runTest {
      sessions.ensureDefault("First rolls")
      val second = SessionRepository(database, clock = { NOW + 1 }, ids = { "later" })
      second.create("Later")

      assertEquals(listOf("Later", "First rolls"), names())
    }

  @Test
  fun `the list says how many rolls are in each session`() =
    runTest {
      val id = sessions.create("Tuesday")
      rolled(session = id)
      rolled(session = id)
      rolled(session = "elsewhere")

      assertEquals(
        2L,
        sessions.sessions
          .first()
          .single { it.id == id }
          .rolls,
      )
    }

  @Test
  fun `the list says how many of those had a natural maximum in them`() =
    runTest {
      // The number a player actually looks for. Counted from the stored
      // breakdown, because that means what it meant then.
      val id = sessions.create("Tuesday")
      rolled(session = id, naturalMax = true)
      rolled(session = id, naturalMax = false)

      assertEquals(
        1L,
        sessions.sessions
          .first()
          .single { it.id == id }
          .naturals,
      )
    }

  @Test
  fun `a session with nothing in it counts nothing rather than nothing at all`() =
    runTest {
      val id = sessions.create("Tuesday")

      val session = sessions.sessions.first().single { it.id == id }
      assertEquals(0L, session.rolls)
      assertEquals(0L, session.naturals)
    }

  @Test
  fun `deleting a session moves its rolls rather than deleting them`() =
    runTest {
      sessions.ensureDefault("First rolls")
      val id = sessions.create("Tuesday")
      rolled(session = id)

      sessions.delete(id, "First rolls")

      assertEquals(listOf("First rolls"), names())
      assertEquals(1L, database.rollHistory().count())
      assertEquals(
        1,
        database
          .rollHistory()
          .inSession(SessionRepository.DEFAULT_ID, 10)
          .first()
          .size,
      )
    }

  @Test
  fun `the first session cannot be deleted, because it is where rolls go`() =
    runTest {
      sessions.ensureDefault("First rolls")

      val refused = runCatching { sessions.delete(SessionRepository.DEFAULT_ID, "First rolls") }

      assertTrue("deleting the first session was allowed", refused.isFailure)
    }

  @Test
  fun `only the first session says it cannot be deleted`() =
    runTest {
      sessions.ensureDefault("First rolls")
      sessions.create("Tuesday")

      val all = sessions.sessions.first()
      assertFalse(all.single { it.id == SessionRepository.DEFAULT_ID }.deletable)
      assertTrue(all.single { it.name == "Tuesday" }.deletable)
    }

  private suspend fun names(): List<String> = sessions.sessions.first().map { it.name }

  private fun rolled(
    session: String,
    naturalMax: Boolean = false,
  ) {
    runTest {
      database.rollHistoryWriting().insert(
        RollHistoryRow(
          timestamp = NOW,
          sessionId = session,
          formula = "1d20",
          total = 20,
          seed = 0,
          breakdownJson = Breakdown.of(result(naturalMax)),
        ),
      )
    }
  }

  private fun result(naturalMax: Boolean) =
    RollResult(
      formula = "1d20",
      total = 20,
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "1d20",
            setId = "builtin",
            requestedSetId = "builtin",
            subtotal = 20,
            dice = listOf(RolledDie(instanceIndex = 0, dieId = "d20", value = 20, naturalMax = naturalMax)),
          ),
        ),
    )

  private companion object {
    const val NOW = 1_000L
  }
}
