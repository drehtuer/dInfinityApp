package de.drehtuer.dinfinity.feature.saved

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.collection.CollectionReader
import de.drehtuer.dinfinity.core.collection.CollectionResult
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exporting from the saved-rolls screen
 * (`docs/dice-notation.md`, "Export and import").
 *
 * The screen's half: press the control, and assert that what it hands up is a
 * collection this app would read back. Handing that file to another app is
 * `app`'s, because the provider that does it is declared in the application's
 * manifest — `CollectionSharingTest` is the other half.
 */
@RunWith(RobolectricTestRunner::class)
class ExportSheetTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var repository: SavedRollRepository
  private val scope = CoroutineScope(Dispatchers.Unconfined)
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Before
  fun open() {
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
    repository = SavedRollRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `the screen offers a way to export, which it had none of`() {
    show()

    compose.onNodeWithTag(ExportTestTags.OPEN).performClick()

    compose.onNodeWithTag(ExportTestTags.SHEET).assertIsDisplayed()
  }

  @Test
  fun `both choices are offered - this group, and everything`() {
    show()

    compose.onNodeWithTag(ExportTestTags.OPEN).performClick()

    compose.onNodeWithTag(ExportTestTags.GROUP).assertIsDisplayed()
    compose.onNodeWithTag(ExportTestTags.EVERYTHING).assertIsDisplayed()
  }

  @Test
  fun `exporting everything hands up a file this app would read back`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    given(roll("axe", "dnd", "Axe"), roll("loose", SavedRollGroup.UNFILED_ID, "Loose"))
    show()

    compose.onNodeWithTag(ExportTestTags.OPEN).performClick()
    compose.onNodeWithTag(ExportTestTags.EVERYTHING).performClick()

    val read = CollectionReader.read(exported().json)
    assertTrue("the exported file did not read back: $read", read is CollectionResult.Loaded)
    assertEquals(listOf("Axe", "Loose"), (read as CollectionResult.Loaded).collection.rolls.map { it.name })
  }

  @Test
  fun `exporting one group leaves the other groups' rolls behind`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    given(roll("axe", "dnd", "Axe"), roll("loose", SavedRollGroup.UNFILED_ID, "Loose"))
    show()

    // Unfiled is the group the screen opens on, so that is the one exported.
    compose.onNodeWithTag(ExportTestTags.OPEN).performClick()
    compose.onNodeWithTag(ExportTestTags.GROUP).performClick()

    val read = CollectionReader.read(exported().json) as CollectionResult.Loaded
    assertEquals(listOf("Loose"), read.collection.rolls.map { it.name })
  }

  @Test
  fun `the file is named after what is in it`() {
    given(roll("axe", SavedRollGroup.UNFILED_ID, "Axe"))
    show()

    compose.onNodeWithTag(ExportTestTags.OPEN).performClick()
    compose.onNodeWithTag(ExportTestTags.GROUP).performClick()

    assertEquals("unfiled.dinfinity.json", exported().name)
  }

  @Test
  fun `the sheet closes once a choice is made`() {
    given(roll("axe", SavedRollGroup.UNFILED_ID, "Axe"))
    show()

    compose.onNodeWithTag(ExportTestTags.OPEN).performClick()
    compose.onNodeWithTag(ExportTestTags.EVERYTHING).performClick()

    compose.onNodeWithTag(ExportTestTags.SHEET).assertDoesNotExist()
  }

  @Test
  fun `the same sheet is the way in as well as the way out`() {
    // A file arriving and a file leaving are one idea to a player, and the
    // alternative is a second control on a bar that has a group name in it.
    show()

    compose.onNodeWithTag(ExportTestTags.OPEN).performClick()
    compose.onNodeWithTag(ExportTestTags.IMPORT).performClick()

    assertTrue("the import screen was never asked for", importing)
    compose.onNodeWithTag(ExportTestTags.SHEET).assertDoesNotExist()
  }

  @Test
  fun `Cancel exports nothing`() {
    given(roll("axe", SavedRollGroup.UNFILED_ID, "Axe"))
    show()

    compose.onNodeWithTag(ExportTestTags.OPEN).performClick()
    compose.onNodeWithTag(ExportTestTags.CANCEL).performClick()

    compose.onNodeWithTag(ExportTestTags.SHEET).assertDoesNotExist()
    assertTrue(exported == null)
  }

  private fun exported(): CollectionFile = exported ?: error("nothing was handed up to export")

  private fun given(vararg groups: SavedRollGroup) {
    runBlocking {
      repository.ensureUnfiled("Unfiled")
      groups.forEach { repository.save(it) }
    }
  }

  private fun given(vararg rolls: SavedRoll) {
    runBlocking {
      repository.ensureUnfiled("Unfiled")
      rolls.forEach { repository.save(it) }
    }
  }

  private var exported: CollectionFile? = null
  private var importing = false

  private fun show() {
    val presenter =
      SavedPresenter(
        repository = repository,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        unfiledName = "Unfiled",
      )
    val groups = GroupPresenter(repository, scope, "Unfiled")
    compose.setContent {
      SavedScreen(
        presenter = presenter,
        groups = groups,
        onExport = { exported = it },
        onImport = { importing = true },
      )
    }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
  }

  private fun roll(
    id: String,
    groupId: String,
    name: String,
  ) = SavedRoll(id = id, groupId = groupId, name = name, formula = "1d20")

  private companion object {
    const val PATIENCE = 2_000L
  }
}
