package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.stats.FaceTally
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.DieStatsRow
import de.drehtuer.dinfinity.data.db.DieSummaryRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What every die has done, to read (`docs/statistics.md`, per die).
 *
 * The reading half of the statistics had no test of its own: three screens
 * each tested *their use* of it and nobody tested the thing, which is the gap
 * `.claude/CLAUDE.md` names — it looks exactly like the mechanical Compose
 * shortfall in a report and is not the same at all.
 */
@RunWith(RobolectricTestRunner::class)
class DieStatisticsRepositoryTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var reading: DieStatisticsRepository

  /** The session [face] writes into, which [inSession] moves for a moment. */
  private var session: String = SessionRepository.DEFAULT_ID

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
    reading = DieStatisticsRepository(database)
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `the die rolled most recently comes first, because that is what somebody came about`() =
    runTest {
      summary(dieId = "d6", lastRolledAt = 1_000)
      summary(dieId = "d20", lastRolledAt = 2_000)

      assertEquals(listOf("d20", "d6"), reading.dice.first().map { it.dieId })
    }

  @Test
  fun `a die's faces come back lowest value first`() =
    runTest {
      face(dieId = "d6", faceValue = 3, count = 1)
      face(dieId = "d6", faceValue = 1, count = 5)
      face(dieId = "d6", faceValue = 2, count = 3)

      assertEquals(listOf(1, 2, 3), reading.faces("builtin", "d6").first().map(FaceTally::faceValue))
    }

  @Test
  fun `one die's faces are its own, not every die's`() =
    runTest {
      face(dieId = "d6", faceValue = 1, count = 5)
      face(dieId = "d20", faceValue = 1, count = 9)

      assertEquals(listOf(5L), reading.faces("builtin", "d6").first().map(FaceTally::count))
    }

  @Test
  fun `all my d20s are summed across the sets that have one`() =
    runTest {
      // The roll-up (design option `5c`): the same face of the same kind of
      // die, added up wherever it came from.
      face(setId = "builtin", dieId = "d20", sides = 20, faceValue = 20, count = 3)
      face(setId = "brass", dieId = "d20", sides = 20, faceValue = 20, count = 4)

      val pooled = reading.facesForSides(20).first()

      assertEquals(listOf(7L), pooled.map(FaceTally::count))
      assertEquals("a pooled row belongs to no set", "", pooled.single().setId)
    }

  @Test
  fun `a roll-up of one kind does not gather another`() =
    runTest {
      face(dieId = "d20", sides = 20, faceValue = 1, count = 3)
      face(dieId = "d6", sides = 6, faceValue = 1, count = 4)

      assertEquals(listOf(3L), reading.facesForSides(20).first().map(FaceTally::count))
    }

  @Test
  fun `every face of every die comes back at once, for a file`() =
    runTest {
      face(setId = "builtin", dieId = "d6", sides = 6, faceValue = 1, count = 5)
      face(setId = "brass", dieId = "d20", sides = 20, faceValue = 20, count = 2)

      val everything = reading.allFaces()

      assertEquals(2, everything.size)
      assertTrue(
        "the set and die are what make a row identifiable in a file",
        everything.any { it.setId == "brass" && it.dieId == "d20" && it.count == 2L },
      )
    }

  @Test
  fun `the export reading is ordered, so two exports of the same record match`() =
    runTest {
      face(setId = "brass", dieId = "d6", sides = 6, faceValue = 2, count = 1)
      face(setId = "builtin", dieId = "d6", sides = 6, faceValue = 2, count = 1)
      face(setId = "builtin", dieId = "d6", sides = 6, faceValue = 1, count = 1)

      val rows = reading.allFaces().map { "${it.setId}/${it.dieId}/${it.faceValue}" }

      assertEquals(listOf("brass/d6/2", "builtin/d6/1", "builtin/d6/2"), rows)
    }

  @Test
  fun `nothing thrown yet is empty rather than a row of zeroes`() =
    runTest {
      assertEquals(emptyList<FaceTally>(), reading.allFaces())
      assertTrue(reading.dice.first().isEmpty())
    }

  @Test
  fun `a die thrown once has a mean and no variance to speak of`() =
    runTest {
      // One throw is a mean; a variance needs two to be between.
      summary(dieId = "d6", row = { copy(throws = 1, sum = 4, sumOfSquares = 16) })

      val die = reading.dice.first().single()

      assertEquals(4.0, die.mean!!, 1e-9)
      assertNull("one throw was given a variance", die.variance)
    }

  /**
   * One die's running summary.
   *
   * Takes the row rather than its fields: seven parameters is detekt's limit
   * doing its job, and a helper that lists every column of a table has stopped
   * helping and started restating it.
   */
  private fun summary(
    dieId: String,
    lastRolledAt: Long = 0,
    row: DieSummaryRow.() -> DieSummaryRow = { this },
  ) {
    runBlocking {
      database.dieSummary().upsert(
        DieSummaryRow(
          setId = "builtin",
          dieId = dieId,
          sides = 6,
          throws = 10,
          sum = 35,
          sumOfSquares = 150,
          lastRolledAtEpochMs = lastRolledAt,
        ).row(),
      )
    }
  }

  @Test
  fun `a session's counts are its own, and every session together is every throw`() =
    runTest {
      // The whole of what version 5 bought (`docs/statistics.md`, per
      // session): the same die, two campaigns, and each answer available
      // without recomputing the other from the history.
      face(dieId = "d20", sides = 20, faceValue = 20, count = 4)
      inSession(TUESDAY) { face(dieId = "d20", sides = 20, faceValue = 20, count = 3) }

      assertEquals(listOf(3L), reading.facesIn("builtin", "d20", TUESDAY).first().map(FaceTally::count))
      assertEquals(listOf(7L), reading.faces("builtin", "d20").first().map(FaceTally::count))
    }

  @Test
  fun `a session's summary is added up from its face counts`() =
    runTest {
      // Throws, sum and sum of squares add, which is why a session needs no
      // summary row of its own — and why `die_summary` could keep its streaks.
      inSession(TUESDAY) {
        face(dieId = "d6", faceValue = 6, count = 2)
        face(dieId = "d6", faceValue = 1, count = 1)
      }

      val die = reading.diceIn(TUESDAY).first().single()
      assertEquals(3L, die.throws)
      assertEquals(13L, die.sum)
      assertEquals("6² + 6² + 1²", 73L, die.sumOfSquares)
    }

  @Test
  fun `a session nobody rolled in has no dice in it`() =
    runTest {
      face(dieId = "d6", faceValue = 6, count = 2)

      assertEquals(emptyList<String>(), reading.diceIn(TUESDAY).first().map { it.dieId })
    }

  @Test
  fun `all my d20s can be cut to one session too`() =
    runTest {
      face(setId = "builtin", dieId = "d20", sides = 20, faceValue = 20, count = 3)
      inSession(TUESDAY) {
        face(setId = "builtin", dieId = "d20", sides = 20, faceValue = 20, count = 1)
        face(setId = "brass", dieId = "d20", sides = 20, faceValue = 20, count = 2)
      }

      assertEquals(listOf(3L), reading.facesForSidesIn(20, TUESDAY).first().map(FaceTally::count))
      assertEquals(listOf(6L), reading.facesForSides(20).first().map(FaceTally::count))
    }

  /**
   * One face count in the table.
   *
   * The session is given by [inSession] rather than by a sixth parameter: most
   * of what this file checks has nothing to do with sessions, and a parameter
   * would have to be defaulted at every one of those call sites.
   */
  private fun face(
    setId: String = "builtin",
    dieId: String,
    sides: Int = 6,
    faceValue: Int,
    count: Long,
  ) {
    runBlocking {
      database.dieStats().upsert(
        DieStatsRow(
          setId = setId,
          dieId = dieId,
          sessionId = session,
          sides = sides,
          faceValue = faceValue,
          count = count,
        ),
      )
    }
  }

  /** The session the faces written inside [block] belong to. */
  private fun inSession(
    sessionId: String,
    block: () -> Unit,
  ) {
    session = sessionId
    try {
      block()
    } finally {
      session = SessionRepository.DEFAULT_ID
    }
  }

  private companion object {
    const val TUESDAY = "tuesday"
  }
}
