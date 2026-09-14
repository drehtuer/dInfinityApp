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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Saved rolls and their groups (`docs/dice-notation.md`, "Saved rolls").
 *
 * Two rules live here rather than in a screen, because a screen is not the
 * only thing that writes — an import writes too, and a rule only one of them
 * follows is not a rule: **groups nest exactly one level**, and **a roll
 * always has somewhere to be**.
 */
@RunWith(RobolectricTestRunner::class)
class SavedRollRepositoryTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var repository: SavedRollRepository
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
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `a roll written is a roll read back, whole`() =
    runTest {
      repository.ensureUnfiled("Unfiled")
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
      repository.ensureUnfiled("Unfiled")

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
  fun `Unfiled is made once and then found`() =
    runTest {
      val first = repository.ensureUnfiled("Unfiled")
      val again = repository.ensureUnfiled("Ignored")

      assertEquals(first, again)
      assertEquals(1, repository.groups.first().size)
    }

  @Test
  fun `groups nest one level, and a third is refused`() =
    runTest {
      repository.save(SavedRollGroup(id = "dnd", name = "D&D"))
      repository.save(SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"))

      assertRefused {
        repository.save(SavedRollGroup(id = "axe", name = "Axe", parentId = "thorin"))
      }
    }

  @Test
  fun `a group with groups inside it cannot itself be put inside another`() =
    runTest {
      // The other end of the same rule. Checking only the parent lets a
      // three-deep tree be built from the bottom: make the child, then move
      // its parent. A list that nests three deep is one the switcher cannot
      // draw (`docs/dice-notation.md`).
      repository.save(SavedRollGroup(id = "dnd", name = "D&D"))
      repository.save(SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"))
      repository.save(SavedRollGroup(id = "pf", name = "Pathfinder"))

      assertRefused {
        repository.save(SavedRollGroup(id = "dnd", name = "D&D", parentId = "pf"))
      }
    }

  @Test
  fun `a group with no groups inside it can still be moved`() =
    runTest {
      repository.save(SavedRollGroup(id = "dnd", name = "D&D"))
      repository.save(SavedRollGroup(id = "thorin", name = "Thorin"))

      repository.save(SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"))

      assertEquals(
        "dnd",
        repository.groups
          .first()
          .first { it.id == "thorin" }
          .parentId,
      )
    }

  @Test
  fun `a group cannot be put inside itself`() =
    runTest {
      assertRefused {
        repository.save(SavedRollGroup(id = "dnd", name = "D&D", parentId = "dnd"))
      }
    }

  @Test
  fun `a group cannot be put inside one that does not exist`() =
    runTest {
      assertRefused {
        repository.save(SavedRollGroup(id = "thorin", name = "Thorin", parentId = "nowhere"))
      }
    }

  @Test
  fun `deleting a group moves its rolls rather than deleting them`() =
    runTest {
      repository.save(SavedRollGroup(id = "thorin", name = "Thorin"))
      repository.save(roll("fireball", groupId = "thorin"))

      repository.deleteGroup("thorin", unfiledName = "Unfiled")

      val left = repository.all.first()
      assertEquals(1, left.size)
      assertEquals(SavedRollGroup.UNFILED_ID, left.single().groupId)
    }

  @Test
  fun `deleting a parent lifts its children rather than taking them with it`() =
    runTest {
      repository.save(SavedRollGroup(id = "dnd", name = "D&D"))
      repository.save(SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"))
      repository.save(roll("axe", groupId = "thorin"))

      repository.deleteGroup("dnd", unfiledName = "Unfiled")

      val thorin = repository.groups.first().first { it.id == "thorin" }
      assertNull("a child was left pointing at a group that is gone", thorin.parentId)
      assertEquals(
        "thorin",
        repository.all
          .first()
          .single()
          .groupId,
      )
    }

  @Test
  fun `Unfiled cannot be deleted, being where things go`() =
    runTest {
      repository.ensureUnfiled("Unfiled")

      assertRefused {
        repository.deleteGroup(SavedRollGroup.UNFILED_ID, unfiledName = "Unfiled")
      }
    }

  @Test
  fun `favourites come first, then the most recently used`() =
    runTest {
      repository.ensureUnfiled("Unfiled")
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
      repository.ensureUnfiled("Unfiled")
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
      repository.ensureUnfiled("Unfiled")

      repository.inGroup(SavedRollGroup.UNFILED_ID).test {
        assertEquals(emptyList<SavedRoll>(), awaitItem())

        repository.save(roll("fireball"))

        assertEquals(listOf("fireball"), awaitItem().map(SavedRoll::id))
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `a name already taken is known before it is used`() =
    runTest {
      repository.save(SavedRollGroup(id = "dnd", name = "Curse of Strahd"))

      assertTrue(repository.nameTaken("Curse of Strahd"))
      assertFalse("a group clashed with itself", repository.nameTaken("Curse of Strahd", exceptId = "dnd"))
      assertFalse(repository.nameTaken("Pathfinder"))
    }

  @Test
  fun `a table pin survives the round trip, both halves or neither`() =
    runTest {
      repository.ensureUnfiled("Unfiled")
      repository.save(roll("pinned").copy(tablePin = TablePin("brass", "oak")))
      repository.save(roll("loose"))

      val rolls = repository.all.first().associateBy(SavedRoll::id)
      assertEquals(TablePin("brass", "oak"), rolls.getValue("pinned").tablePin)
      assertNull(rolls.getValue("loose").tablePin)
    }

  /** That [block] was refused rather than quietly accepted. */
  private suspend fun assertRefused(block: suspend () -> Unit) {
    val thrown = runCatching { block() }.exceptionOrNull()
    assertTrue("nothing was refused: $thrown", thrown is IllegalArgumentException)
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
