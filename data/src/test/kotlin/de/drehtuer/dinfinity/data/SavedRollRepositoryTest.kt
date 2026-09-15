package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The saved rolls themselves (`docs/dice-notation.md`, "Saved rolls").
 *
 * The folders they live in are [SavedRollGroupRepositoryTest]'s. Unfiled is
 * made here all the same, because a roll has a group in the schema as well as
 * in the story and a roll with nowhere to be does not insert.
 */
@RunWith(RobolectricTestRunner::class)
class SavedRollRepositoryTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var repository: SavedRollRepository
  private lateinit var groups: SavedRollGroupRepository
  private var now = 1_000L

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
    repository = SavedRollRepository(database) { now }
    groups = SavedRollGroupRepository(database)
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `a roll written is a roll read back, whole`() =
    runTest {
      groups.ensureUnfiled("Unfiled")
      val fireball =
        SavedRoll(
          id = "fireball",
          groupId = SavedRollGroup.UNFILED_ID,
          name = "Fireball",
          formula = "8d6 [Fire]",
          icon = "🔥",
          colorArgb = 0x00FF0000,
          favourite = true,
          tablePin = TablePin(setId = "builtin", tableId = "felt-green"),
        )

      repository.save(fireball)

      assertEquals(listOf(fireball.copy(createdAtEpochMs = now)), repository.all.first())
    }

  @Test
  fun `a roll saved with no creation time gets the time it was saved`() =
    runTest {
      groups.ensureUnfiled("Unfiled")

      repository.save(roll("fireball"))

      assertEquals(
        now,
        repository.all
          .first()
          .single()
          .createdAtEpochMs,
      )
    }

  @Test
  fun `one roll can be asked for by its id, and an unknown one answers nothing`() =
    runTest {
      groups.ensureUnfiled("Unfiled")
      repository.save(roll("fireball"))

      assertEquals("fireball", repository.byId("fireball")?.id)
      assertNull(repository.byId("nowhere"))
    }

  @Test
  fun `a roll deleted is gone`() =
    runTest {
      groups.ensureUnfiled("Unfiled")
      repository.save(roll("fireball"))

      repository.delete("fireball")

      assertEquals(emptyList<SavedRoll>(), repository.all.first())
    }

  @Test
  fun `favourites come first, then the most recently used`() =
    runTest {
      groups.ensureUnfiled("Unfiled")
      repository.save(roll("old"))
      repository.save(roll("recent"))
      repository.save(roll("liked", favourite = true))
      now = 2_000L
      repository.used("old")
      now = 3_000L
      repository.used("recent")

      assertEquals(
        listOf("liked", "recent", "old"),
        repository.inGroup(SavedRollGroup.UNFILED_ID).first().map(SavedRoll::id),
      )
    }

  @Test
  fun `using a roll counts up rather than overwriting a count`() =
    runTest {
      groups.ensureUnfiled("Unfiled")
      repository.save(roll("fireball"))

      repository.used("fireball")
      repository.used("fireball")

      val fireball = repository.all.first().single()
      assertEquals(2, fireball.useCount)
      assertEquals(now, fireball.lastUsedAtEpochMs)
    }

  @Test
  fun `the list redraws itself when a roll is saved`() =
    runTest {
      groups.ensureUnfiled("Unfiled")

      repository.inGroup(SavedRollGroup.UNFILED_ID).test {
        assertEquals(emptyList<SavedRoll>(), awaitItem())

        repository.save(roll("fireball"))

        assertEquals(listOf("fireball"), awaitItem().map(SavedRoll::id))
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `a table pin survives the round trip, both halves or neither`() =
    runTest {
      groups.ensureUnfiled("Unfiled")
      repository.save(roll("pinned").copy(tablePin = TablePin("brass", "oak")))
      repository.save(roll("loose"))

      val rolls = repository.all.first().associateBy(SavedRoll::id)
      assertEquals(TablePin("brass", "oak"), rolls.getValue("pinned").tablePin)
      assertNull(rolls.getValue("loose").tablePin)
    }

  private fun roll(
    id: String,
    groupId: String = SavedRollGroup.UNFILED_ID,
    favourite: Boolean = false,
  ) = SavedRoll(
    id = id,
    groupId = groupId,
    name = id,
    formula = "1d20",
    favourite = favourite,
  )
}
