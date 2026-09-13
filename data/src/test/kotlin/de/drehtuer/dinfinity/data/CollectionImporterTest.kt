package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.collection.CollectionGroup
import de.drehtuer.dinfinity.core.collection.CollectionRoll
import de.drehtuer.dinfinity.core.collection.DiceCollection
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Writing an imported collection down
 * (`docs/dice-notation.md`, "Export and import"; decision 15).
 *
 * Against a real database, because the promise being tested is about a
 * transaction: either the whole collection arrived or none of it did, and a
 * fake would only be asserting that the fake keeps its own promise.
 */
@RunWith(RobolectricTestRunner::class)
class CollectionImporterTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var repository: SavedRollRepository
  private lateinit var importer: CollectionImporter
  private var next = 0

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        .build()
    repository = SavedRollRepository(database) { NOW }
    importer = CollectionImporter(database, clock = { NOW }, ids = { "id-${next++}" })
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `a collection arrives whole`() =
    runTest {
      val result = importer.import(thorin(), UNFILED)

      assertEquals(ImportResult.Imported(groups = 1, rolls = 2), result)
      assertEquals(listOf("Thorin"), groupNames())
      assertEquals(listOf("Fireball", "Longsword"), rollNames())
    }

  @Test
  fun `what was written is what the file said`() =
    runTest {
      importer.import(thorin(), UNFILED)

      val roll = repository.all.first().first { it.name == "Longsword" }
      assertEquals("1d20 + 7 [Attack]", roll.formula)
      assertEquals("🗡️", roll.icon)
      assertTrue(roll.favourite)
      assertEquals(NOW, roll.createdAtEpochMs)
    }

  @Test
  fun `a roll lands in the group the file filed it in`() =
    runTest {
      importer.import(
        DiceCollection(
          name = "Two",
          groups = listOf(group("a", "Alpha"), group("b", "Beta")),
          rolls = listOf(roll("b", "Only")),
        ),
        UNFILED,
      )

      val beta = repository.groups.first().first { it.name == "Beta" }
      assertEquals(listOf("Only"), repository.inGroup(beta.id).first().map { it.name })
    }

  @Test
  fun `one level of nesting survives the import`() =
    runTest {
      importer.import(
        DiceCollection(
          name = "Nested",
          groups = listOf(group("dnd", "D&D"), group("thorin", "Thorin", parent = "dnd")),
          rolls = emptyList(),
        ),
        UNFILED,
      )

      val groups = repository.groups.first()
      val parent = groups.first { it.name == "D&D" }
      assertEquals(parent.id, groups.first { it.name == "Thorin" }.parentId)
    }

  @Test
  fun `a group name that is already here refuses the import, naming the clash`() =
    runTest {
      // The whole rule, in one test: no merge, nothing deleted, and the name
      // said out loud so somebody can go and change it.
      repository.save(SavedRollGroup(id = "mine", name = "Thorin"))

      val result = importer.import(thorin(), UNFILED)

      assertEquals(ImportResult.Refused("Thorin"), result)
    }

  @Test
  fun `a refused import writes nothing at all`() =
    runTest {
      repository.save(SavedRollGroup(id = "mine", name = "Thorin"))
      repository.save(SavedRoll(id = "mine-roll", groupId = "mine", name = "Mine", formula = "1d4"))

      importer.import(thorin(), UNFILED)

      assertEquals(listOf("Thorin"), groupNames())
      assertEquals(listOf("Mine"), rollNames())
    }

  @Test
  fun `the clash ignores case, because two groups a capital apart are one group`() =
    runTest {
      repository.save(SavedRollGroup(id = "mine", name = "THORIN"))

      assertEquals(ImportResult.Refused("Thorin"), importer.import(thorin(), UNFILED))
    }

  @Test
  fun `the clash is named as the file spells it, which is the one to go and change`() =
    runTest {
      repository.save(SavedRollGroup(id = "mine", name = "thorin"))

      assertEquals(ImportResult.Refused("Thorin"), importer.import(thorin(), UNFILED))
    }

  @Test
  fun `a collection whose groups are all new imports beside what is here`() =
    runTest {
      repository.save(SavedRollGroup(id = "mine", name = "Pathfinder"))
      repository.save(SavedRoll(id = "mine-roll", groupId = "mine", name = "Mine", formula = "1d4"))

      importer.import(thorin(), UNFILED)

      assertEquals(listOf("Pathfinder", "Thorin"), groupNames().sorted())
      assertEquals(listOf("Fireball", "Longsword", "Mine"), rollNames().sorted())
    }

  @Test
  fun `imported groups land after what is already in the switcher`() =
    runTest {
      repository.save(SavedRollGroup(id = "mine", name = "Pathfinder", sortOrder = 7))

      importer.import(thorin(), UNFILED)

      val thorin = repository.groups.first().first { it.name == "Thorin" }
      assertTrue("it sorted above what was already there", thorin.sortOrder > 7)
    }

  @Test
  fun `the file's own ids are not reused, because they are a stranger's`() =
    runTest {
      // A slug is stable inside the file, which is what lets a person edit one
      // by hand. It says nothing about what this database already uses.
      repository.save(SavedRollGroup(id = "thorin", name = "Somebody else's"))

      importer.import(thorin(), UNFILED)

      assertEquals(
        "Somebody else's",
        repository.groups
          .first()
          .first { it.id == "thorin" }
          .name,
      )
      assertEquals(2, groupNames().size)
    }

  @Test
  fun `an import into a fresh install leaves it with an Unfiled to save into`() =
    runTest {
      // A collection may be the first thing anybody ever does with the app.
      importer.import(thorin(), UNFILED)

      assertNotNull(repository.groups.first().firstOrNull { it.id == SavedRollGroup.UNFILED_ID })
    }

  @Test
  fun `an Unfiled that is already there is left as it is`() =
    runTest {
      repository.ensureUnfiled("Loose ends")

      importer.import(thorin(), UNFILED)

      assertEquals(
        "Loose ends",
        repository.groups
          .first()
          .first { it.id == SavedRollGroup.UNFILED_ID }
          .name,
      )
    }

  @Test
  fun `a collection of groups and no rolls is a perfectly good collection`() =
    runTest {
      val result =
        importer.import(
          DiceCollection(name = "Empty folders", groups = listOf(group("a", "Alpha"))),
          UNFILED,
        )

      assertEquals(ImportResult.Imported(groups = 1, rolls = 0), result)
    }

  private suspend fun groupNames(): List<String> =
    repository.groups
      .first()
      .filterNot { it.id == SavedRollGroup.UNFILED_ID }
      .map { it.name }

  private suspend fun rollNames(): List<String> =
    repository.all
      .first()
      .map { it.name }
      .sorted()

  private fun thorin() =
    DiceCollection(
      name = "Thorin, level 5 fighter",
      groups = listOf(group("thorin", "Thorin", icon = "⚔️")),
      rolls =
        listOf(
          roll("thorin", "Longsword", formula = "1d20 + 7 [Attack]", icon = "🗡️", favourite = true),
          roll("thorin", "Fireball", formula = "8d6 [Fire]"),
        ),
    )

  private fun group(
    id: String,
    name: String,
    icon: String = "",
    parent: String? = null,
  ) = CollectionGroup(id = id, name = name, icon = icon, parent = parent)

  private fun roll(
    group: String,
    name: String,
    formula: String = "1d20",
    icon: String = "",
    favourite: Boolean = false,
  ) = CollectionRoll(group = group, name = name, formula = formula, icon = icon, favourite = favourite)

  private companion object {
    const val NOW = 1_000L
    const val UNFILED = "Unfiled"
  }
}
