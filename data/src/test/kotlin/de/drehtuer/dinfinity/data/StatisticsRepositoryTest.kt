package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlinx.coroutines.flow.first
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
 * Writing a finished roll down (`docs/statistics.md`).
 *
 * Against a real Room database in memory rather than against a fake DAO: the
 * things worth testing here — that the history and the aggregates agree, that
 * pruning takes the history and leaves the counters — are things a fake would
 * simply agree with.
 */
@RunWith(RobolectricTestRunner::class)
class StatisticsRepositoryTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var repository: StatisticsRepository

  @Before
  fun open() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, DInfinityDatabase::class.java)
        .allowMainThreadQueries()
        // Queries and invalidation on the calling thread, so a `Flow` from a
        // `@Query` emits when the write happens rather than when a pool thread
        // gets to it. Without it the first wait in a class races Room's own
        // executors, which surfaces as an unrelated test failing now and then.
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
    repository = StatisticsRepository(database)
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `a finished roll becomes one history row`() =
    runTest {
      repository.record(roll(total = 17, values = listOf(17)))
      val history = database.rollHistory().recent(10).first()
      assertEquals(1, history.size)
      assertEquals(17L, history.single().total)
      assertEquals("1d20", history.single().formula)
      assertEquals("tuesday", history.single().sessionId)
    }

  @Test
  fun `the same roll updates the die's counters`() =
    runTest {
      repository.record(roll(total = 20, values = listOf(20)))
      val summary = database.dieSummary().find("builtin", "d20")!!
      assertEquals(1, summary.throws)
      assertEquals(20, summary.sum)
      assertEquals(1, summary.highestStreak)
      assertEquals(20, summary.sides)
    }

  @Test
  fun `and the die's histogram`() =
    runTest {
      repository.record(roll(total = 20, values = listOf(20)))
      repository.record(roll(total = 20, values = listOf(20)))
      repository.record(roll(total = 3, values = listOf(3)))
      val histogram = database.dieStats().histogram("builtin", "d20").first()
      assertEquals(2, histogram.size)
      assertEquals(2L, histogram.single { it.faceValue == 20 }.count)
      assertEquals(1L, histogram.single { it.faceValue == 3 }.count)
    }

  @Test
  fun `a streak survives across rolls, which is the whole point of it`() =
    runTest {
      repeat(3) { repository.record(roll(total = 20, values = listOf(20))) }
      assertEquals(3, database.dieSummary().find("builtin", "d20")!!.highestStreak)
      repository.record(roll(total = 4, values = listOf(4)))
      val summary = database.dieSummary().find("builtin", "d20")!!
      assertEquals(0, summary.highestStreak)
      assertEquals(3, summary.highestStreakMax)
    }

  @Test
  fun `every die of a throw is counted`() =
    runTest {
      repository.record(roll(total = 12, values = listOf(3, 4, 5)))
      assertEquals(3, database.dieSummary().find("builtin", "d20")!!.throws)
      assertEquals(
        3,
        database
          .dieStats()
          .histogram("builtin", "d20")
          .first()
          .size,
      )
    }

  @Test
  fun `a dropped die is counted, and remembered as dropped`() =
    runTest {
      val dropped =
        RolledDie(
          instanceIndex = 0,
          dieId = "d20",
          value = 1,
          notes = setOf(de.drehtuer.dinfinity.core.model.DieNote.Dropped),
        )
      repository.record(roll(total = 0, dice = listOf(dropped)))
      val row = database.dieStats().find("builtin", "d20", 1)!!
      assertEquals(1L, row.count)
      assertEquals(1L, row.droppedCount)
    }

  @Test
  fun `re-throws and forced settles are recorded as anomalies`() =
    runTest {
      val result = result(total = 9, dice = listOf(die(0, 9))).copy(rethrows = 2, forcedSettles = 1)
      repository.record(FinishedRoll(result, sources(1), RollContext("tuesday")))
      assertEquals(
        3,
        database
          .rollHistory()
          .recent(1)
          .first()
          .single()
          .anomalies,
      )
    }

  @Test
  fun `the seed is stored and no screen ever asks for it`() =
    runTest {
      repository.record(roll(total = 9, values = listOf(9)).let { it })
      // The column exists and is written; the history DAO has no query that
      // selects it on its own, and the export leaves it out entirely.
      assertEquals(
        0L,
        database
          .rollHistory()
          .recent(1)
          .first()
          .single()
          .seed,
      )
    }

  @Test
  fun `history is capped, and the newest survive`() =
    runTest {
      val capped = StatisticsRepository(database, historyLimit = 5)
      (1..8).forEach { capped.record(roll(total = it.toLong(), values = listOf(it))) }
      assertEquals(5L, database.rollHistory().count())
      assertEquals(
        listOf(8L, 7L, 6L, 5L, 4L),
        database
          .rollHistory()
          .recent(10)
          .first()
          .map { it.total },
      )
    }

  @Test
  fun `pruning takes the history and leaves the counters, because a campaign outlives its rolls`() =
    runTest {
      val capped = StatisticsRepository(database, historyLimit = 2)
      (1..6).forEach { capped.record(roll(total = 20, values = listOf(20))) }
      assertEquals(2L, database.rollHistory().count())
      assertEquals(6, database.dieSummary().find("builtin", "d20")!!.throws)
      assertEquals(6L, database.dieStats().find("builtin", "d20", 20)!!.count)
    }

  @Test
  fun `a session's rolls can be listed, and a deleted session moves them to unfiled`() =
    runTest {
      repository.record(roll(total = 1, values = listOf(1)))
      repository.record(FinishedRoll(result(2, listOf(die(0, 2))), sources(1), RollContext("friday")))
      assertEquals(
        1,
        database
          .rollHistory()
          .inSession("friday", 10)
          .first()
          .size,
      )
      database.rollHistoryWriting().moveSessionToUnfiled("friday", "unfiled")
      assertEquals(
        0,
        database
          .rollHistory()
          .inSession("friday", 10)
          .first()
          .size,
      )
      assertEquals(
        1,
        database
          .rollHistory()
          .inSession("unfiled", 10)
          .first()
          .size,
      )
    }

  @Test
  fun `a saved roll's own history can be listed`() =
    runTest {
      repository.record(
        FinishedRoll(result(8, listOf(die(0, 8))), sources(1), RollContext("tuesday", savedRollId = "fireball")),
      )
      assertEquals(
        1,
        database
          .rollHistory()
          .forSavedRoll("fireball")
          .first()
          .size,
      )
      assertEquals(
        0,
        database
          .rollHistory()
          .forSavedRoll("sneak-attack")
          .first()
          .size,
      )
    }

  @Test
  fun `all my d20s is one line, whatever set they came from`() =
    runTest {
      repository.record(roll(total = 20, values = listOf(20)))
      repository.record(FinishedRoll(result(20, listOf(die(0, 20))), sources(1, setId = "brass"), RollContext("t")))
      val rolled = database.dieStats().histogramForSides(20).first()
      assertEquals(1, rolled.size)
      assertEquals(2L, rolled.single().total)
    }

  @Test
  fun `resetting one die forgets it and leaves the rest alone`() =
    runTest {
      repository.record(roll(total = 20, values = listOf(20)))
      repository.record(FinishedRoll(result(20, listOf(die(0, 20))), sources(1, setId = "brass"), RollContext("t")))
      repository.resetDie("builtin", "d20")
      assertNull(database.dieSummary().find("builtin", "d20"))
      assertTrue(
        database
          .dieStats()
          .histogram("builtin", "d20")
          .first()
          .isEmpty(),
      )
      assertEquals(1, database.dieSummary().find("brass", "d20")!!.throws)
    }

  @Test
  fun `resetting everything leaves nothing`() =
    runTest {
      repository.record(roll(total = 20, values = listOf(20)))
      repository.resetEverything()
      assertEquals(0L, database.rollHistory().count())
      assertNull(database.dieSummary().find("builtin", "d20"))
      assertTrue(
        database
          .dieSummary()
          .all()
          .first()
          .isEmpty(),
      )
    }

  @Test
  fun `a die from a set that was uninstalled keeps its rows`() =
    runTest {
      repository.record(FinishedRoll(result(20, listOf(die(0, 20))), sources(1, setId = "gone"), RollContext("t")))
      // Uninstalling a set deletes its folder, not its history (docs/statistics.md).
      assertEquals(
        1,
        database
          .dieSummary()
          .forSet("gone")
          .first()
          .size,
      )
    }

  private fun roll(
    total: Long,
    values: List<Int> = emptyList(),
    dice: List<RolledDie> = values.mapIndexed(::die),
  ): FinishedRoll = FinishedRoll(result(total, dice), sources(dice.size), RollContext("tuesday"))

  private fun die(
    index: Int,
    value: Int,
  ): RolledDie = RolledDie(instanceIndex = index, dieId = "d20", value = value)

  private fun result(
    total: Long,
    dice: List<RolledDie>,
  ): RollResult =
    RollResult(
      formula = "1d20",
      total = total,
      groups = listOf(RolledGroup(0, "1d20", "builtin", "builtin", dice, total)),
      rolledAtEpochMs = 1_000 + total,
    )

  private fun sources(
    count: Int,
    setId: String = "builtin",
  ): Map<Int, RolledDieSource> = (0 until count).associateWith { RolledDieSource(setId, StandardDice.d20) }
}
