package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.PlannedGroup
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Writing a finished throw down
 * (`docs/statistics.md`, "What is recorded").
 *
 * Nothing has recorded a roll until now, so these are the tests that say what
 * "recorded" means: a history row, a face count for every die, a running
 * summary for each, and the breakdown stored whole beside them.
 */
@RunWith(RobolectricTestRunner::class)
class RollRecordingTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var recording: RollRecording

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
    recording = RollRecording(StatisticsRepository(database))
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `a throw becomes a row in the history`() =
    runTest {
      recording.record(result = result(), plan = plan())

      val row =
        database
          .rollHistory()
          .recent(10)
          .first()
          .single()
      assertEquals("2d6", row.formula)
      assertEquals(9L, row.total)
      assertEquals(NOW, row.timestamp)
    }

  @Test
  fun `the breakdown travels with it`() =
    runTest {
      recording.record(result = result(), plan = plan())

      val row =
        database
          .rollHistory()
          .recent(10)
          .first()
          .single()
      assertEquals(
        listOf(6, 3),
        Breakdown
          .read(row.breakdownJson)
          .groups
          .single()
          .dice
          .map { it.value },
      )
    }

  @Test
  fun `every die's faces are counted`() =
    runTest {
      recording.record(result = result(), plan = plan())

      val histogram = database.dieStats().histogram("builtin", "d6").first()
      assertEquals(mapOf(3 to 1L, 6 to 1L), histogram.associate { it.faceValue to it.count })
    }

  @Test
  fun `the running summary is kept, so a mean is never recomputed from the history`() =
    runTest {
      recording.record(result = result(), plan = plan())

      val summary = database.dieSummary().find("builtin", "d6")!!
      assertEquals(2L, summary.throws)
      assertEquals(9L, summary.sum)
      assertEquals(1, summary.highestStreakMax)
    }

  @Test
  fun `two throws add up rather than replacing each other`() =
    runTest {
      recording.record(result = result(), plan = plan())
      recording.record(result = result(), plan = plan())

      assertEquals(4L, database.dieSummary().find("builtin", "d6")!!.throws)
      assertEquals(
        2,
        database
          .rollHistory()
          .recent(10)
          .first()
          .size,
      )
    }

  @Test
  fun `a roll knows which session it belongs to, even before there are sessions`() =
    runTest {
      // Not an empty string: "" in a history is a value somebody will one day
      // have to guess the meaning of. Step 4.9 turns this into a real session
      // that can be renamed rather than a gap to migrate.
      recording.record(result = result(), plan = plan())

      assertEquals(
        RollRecording.NO_SESSION,
        database
          .rollHistory()
          .recent(10)
          .first()
          .single()
          .sessionId,
      )
    }

  @Test
  fun `the two ends of the default session id agree`() =
    runTest {
      // `core/model` cannot see `data`, so the string is written in both and
      // asserted equal here. If they ever drifted, every roll made before
      // somebody opened the sessions screen would point at a session that does
      // not exist.
      assertEquals(AppSettings.DEFAULT_SESSION_ID, RollRecording.NO_SESSION)
      assertEquals(SessionRepository.DEFAULT_ID, RollRecording.NO_SESSION)
    }

  @Test
  fun `the active session is asked for at each roll, not captured once`() =
    runTest {
      // A recorder that captured it once would file every roll of an evening
      // under whichever session was current when the screen opened.
      var session = "evening"
      val recorder = RollRecording(StatisticsRepository(database)) { session }

      recorder.record(result = result(), plan = plan())
      session = "the next evening"
      recorder.record(result = result(), plan = plan())

      assertEquals(
        listOf("the next evening", "evening"),
        database
          .rollHistory()
          .recent(10)
          .first()
          .map { it.sessionId },
      )
    }

  @Test
  fun `a roll from a saved roll says so, which is what makes it a query later`() =
    runTest {
      recording.record(result = result(), plan = plan(), savedRollId = "fireball", groupId = "thorin")

      val row =
        database
          .rollHistory()
          .recent(10)
          .first()
          .single()
      assertEquals("fireball", row.savedRollId)
      assertEquals("thorin", row.groupId)
    }

  @Test
  fun `the seed is stored and nothing shows it`() =
    runTest {
      // Kept for reproducing a roll when a bug report needs it; never shown,
      // never exported (decision 13). That it is *stored* is the part a test
      // can check.
      recording.record(result = result(), plan = plan(), seed = 4_242L)

      val row =
        database
          .rollHistory()
          .recent(10)
          .first()
          .single()
      assertEquals(4_242L, row.seed)
      assertTrue("seed" !in row.breakdownJson)
    }

  @Test
  fun `corrections and forced settles are counted as anomalies`() =
    runTest {
      // A number that climbs is a physics bug, and it cannot climb if nobody
      // writes it down (`docs/physics-and-rendering.md`).
      recording.record(result = result().copy(rethrows = 2, forcedSettles = 1), plan = plan())

      assertEquals(
        3,
        database
          .rollHistory()
          .recent(10)
          .first()
          .single()
          .anomalies,
      )
    }

  @Test
  fun `a die the plan does not know is not counted, rather than counted wrongly`() =
    runTest {
      // The result knows which face came up; only the plan knows which die it
      // was. A breakdown line with no plan entry is a bug upstream, and
      // inventing a die id here would hide it in the statistics.
      recording.record(result = result(), plan = RollPlan(formula = "2d6"))

      assertEquals(
        emptyList<Long>(),
        database
          .dieStats()
          .histogram("builtin", "d6")
          .first()
          .map { it.count },
      )
      // The history row is still written: the roll happened.
      assertEquals(
        1,
        database
          .rollHistory()
          .recent(10)
          .first()
          .size,
      )
    }

  private fun plan() =
    RollPlan(
      formula = "2d6",
      groups =
        listOf(
          PlannedGroup(
            id = 0,
            notation = "2d6",
            dice =
              listOf(
                DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = d6()),
                DieInstance(index = 1, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = d6()),
              ),
          ),
        ),
    )

  @Test
  fun `and so do the numbers the formula added`() =
    runTest {
      // The whole chain, so the history's breakdown adds up to its total the
      // way the result sheet's does (`docs/dice-notation.md`, "Evaluation").
      val withFour = result().copy(formula = "2d6 + 4", total = 13, adjustments = listOf(4L))

      recording.record(result = withFour, plan = plan())

      val row =
        database
          .rollHistory()
          .recent(10)
          .first()
          .single()
      val read = Breakdown.read(row.breakdownJson)
      assertEquals(listOf(4L), read.adjustments)
      assertEquals(
        "the rows in the history do not add up to the total beside them",
        row.total,
        read.groups.sumOf { it.subtotal } + read.adjustments.sum(),
      )
    }

  private fun result() =
    RollResult(
      formula = "2d6",
      total = 9,
      rolledAtEpochMs = NOW,
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "2d6",
            setId = "builtin",
            requestedSetId = "builtin",
            subtotal = 9,
            dice =
              listOf(
                RolledDie(instanceIndex = 0, dieId = "d6", value = 6, naturalMax = true),
                RolledDie(instanceIndex = 1, dieId = "d6", value = 3),
              ),
          ),
        ),
    )

  @Test
  fun `a roll is not filed under a session that is not there any more`() =
    runTest {
      // Which session is active is a preference, and a preference outlives the
      // thing it names. Delete the active session while another screen is in
      // front — or open the app with one already gone — and every throw would
      // otherwise be filed under an id nothing can find, which puts the roll in
      // the history and in the face counts and shows it in neither.
      val sessions = SessionRepository(database)
      sessions.ensureDefault("First rolls")
      val friday = sessions.create("Friday")
      var active = friday
      val recorder = RollRecording(StatisticsRepository(database), sessionOf = { active }, sessions = sessions)

      recorder.record(result = result(), plan = plan())
      sessions.delete(friday, "First rolls")
      recorder.record(result = result(), plan = plan())

      val filed =
        database
          .rollHistory()
          .recent(10)
          .first()
          .map { it.sessionId }
      assertEquals(
        "the throw after the session went should have gone to the first session",
        listOf(SessionRepository.DEFAULT_ID, SessionRepository.DEFAULT_ID),
        filed,
      )
    }

  @Test
  fun `a session that is still there keeps its rolls`() =
    runTest {
      // The other half: the check must not quietly move everything to the
      // first session.
      val sessions = SessionRepository(database)
      sessions.ensureDefault("First rolls")
      val friday = sessions.create("Friday")
      val recorder = RollRecording(StatisticsRepository(database), sessionOf = { friday }, sessions = sessions)

      recorder.record(result = result(), plan = plan())

      assertEquals(
        friday,
        database
          .rollHistory()
          .recent(10)
          .first()
          .single()
          .sessionId,
      )
    }

  @Test
  fun `a recorder with no sessions to check against files what it is told`() =
    runTest {
      // The null case is a real one: a caller that has no session table to ask
      // — a test, or any screen recording before sessions exist — still gets a
      // row, and the column is still correct.
      val recorder = RollRecording(StatisticsRepository(database), sessionOf = { "tuesday" }, sessions = null)

      recorder.record(result = result(), plan = plan())

      assertEquals(
        "tuesday",
        database
          .rollHistory()
          .recent(10)
          .first()
          .single()
          .sessionId,
      )
    }

  private fun d6() =
    Die(
      id = "d6",
      shape = DieShape.Cube,
      faces = (1..6).map { Face(index = it - 1, value = it, label = it.toString()) },
    )

  private companion object {
    const val NOW = 1_700_000_000_000L
  }
}
