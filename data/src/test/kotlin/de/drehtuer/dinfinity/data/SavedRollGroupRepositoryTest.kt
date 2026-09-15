package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * The folders saved rolls live in (`docs/dice-notation.md`, "Saved rolls").
 *
 * Two rules live here rather than in a screen, because a screen is not the
 * only thing that writes — an import writes too, and a rule only one of them
 * follows is not a rule: **groups nest exactly one level**, and **a roll
 * always has somewhere to be**.
 */
@RunWith(RobolectricTestRunner::class)
class SavedRollGroupRepositoryTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var repository: SavedRollGroupRepository
  private lateinit var rolls: SavedRollRepository

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        // Queries and invalidation on the calling thread — see
        // `SavedRollRepositoryTest` for why.
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
    repository = SavedRollGroupRepository(database)
    rolls = SavedRollRepository(database) { 1_000L }
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `Unfiled is made once and then found`() =
    runTest {
      val first = repository.ensureUnfiled("Unfiled")
      val again = repository.ensureUnfiled("Ignored")

      assertEquals(first, again)
      assertEquals(1, repository.all.first().size)
    }

  @Test
  fun `one group can be asked for by its id, and an unknown one answers nothing`() =
    runTest {
      // What the table fallback asks at roll time: a roll names its group, and
      // the group may pin a table (`docs/tables.md`).
      repository.save(SavedRollGroup(id = "dnd", name = "D&D", tablePin = TablePin("brass", "oak")))

      assertEquals(TablePin("brass", "oak"), repository.byId("dnd")?.tablePin)
      assertNull(repository.byId("nowhere"))
    }

  @Test
  fun `a group's table pin survives the round trip, both halves or neither`() =
    runTest {
      repository.save(SavedRollGroup(id = "pinned", name = "Pinned", tablePin = TablePin("brass", "oak")))
      repository.save(SavedRollGroup(id = "loose", name = "Loose"))

      val groups = repository.all.first().associateBy(SavedRollGroup::id)
      assertEquals(TablePin("brass", "oak"), groups.getValue("pinned").tablePin)
      assertNull(groups.getValue("loose").tablePin)
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
        repository.all
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
      rolls.save(roll("fireball", groupId = "thorin"))

      repository.delete("thorin", unfiledName = "Unfiled")

      val left = rolls.all.first()
      assertEquals(1, left.size)
      assertEquals(SavedRollGroup.UNFILED_ID, left.single().groupId)
    }

  @Test
  fun `deleting a parent lifts its children rather than taking them with it`() =
    runTest {
      repository.save(SavedRollGroup(id = "dnd", name = "D&D"))
      repository.save(SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"))
      rolls.save(roll("axe", groupId = "thorin"))

      repository.delete("dnd", unfiledName = "Unfiled")

      val thorin = repository.all.first().first { it.id == "thorin" }
      assertNull("a child was left pointing at a group that is gone", thorin.parentId)
      assertEquals(
        "thorin",
        rolls.all
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
        repository.delete(SavedRollGroup.UNFILED_ID, unfiledName = "Unfiled")
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

  /** That [block] was refused rather than quietly accepted. */
  private suspend fun assertRefused(block: suspend () -> Unit) {
    val thrown = runCatching { block() }.exceptionOrNull()
    assertTrue("nothing was refused: $thrown", thrown is IllegalArgumentException)
  }

  private fun roll(
    id: String,
    groupId: String,
  ) = SavedRoll(
    id = id,
    groupId = groupId,
    name = id,
    formula = "1d20",
  )
}
