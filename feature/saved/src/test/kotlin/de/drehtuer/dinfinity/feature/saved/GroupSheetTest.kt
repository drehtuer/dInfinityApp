package de.drehtuer.dinfinity.feature.saved

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
 * Making and renaming groups from the saved-rolls screen
 * (`design/dInfinity.dc.html`, option 1p).
 *
 * The sheet is drawn over the list rather than pushed as a destination, so
 * these are tests of the screen that owns it.
 */
@RunWith(RobolectricTestRunner::class)
class GroupSheetTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var repository: SavedRollRepository
  private val scope = CoroutineScope(Dispatchers.Unconfined)

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
    repository = SavedRollRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `the switcher offers a way to make a group, which the list had no way to do`() {
    show()

    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()
    compose.onNodeWithTag(GroupTestTags.NEW).performClick()

    compose.onNodeWithTag(GroupTestTags.SHEET).assertIsDisplayed()
  }

  @Test
  fun `a group typed in is written and appears in the switcher`() {
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()
    compose.onNodeWithTag(GroupTestTags.NEW).performClick()

    compose.onNodeWithTag(GroupTestTags.NAME).performTextInput("Curse of Strahd")
    compose.onNodeWithTag(GroupTestTags.SAVE).performClick()

    compose.waitUntil(PATIENCE) { names().contains("Curse of Strahd") }
    compose.onNodeWithTag(GroupTestTags.SHEET).assertDoesNotExist()
  }

  @Test
  fun `Save is refused, and says whose name it is, while the name is taken`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()
    compose.onNodeWithTag(GroupTestTags.NEW).performClick()

    compose.onNodeWithTag(GroupTestTags.NAME).performTextInput("D&D")

    compose.onNodeWithTag(GroupTestTags.CLASH, useUnmergedTree = true).assertTextContains(
      "D&D",
      substring = true,
    )
    compose.onNodeWithTag(GroupTestTags.SAVE).assertIsNotEnabled()
  }

  @Test
  fun `Save is refused while there is no name at all`() {
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()
    compose.onNodeWithTag(GroupTestTags.NEW).performClick()

    compose.onNodeWithTag(GroupTestTags.SAVE).assertIsNotEnabled()
  }

  @Test
  fun `an existing group opens for renaming, and offers to be deleted`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()

    compose.onNodeWithTag(GroupTestTags.editOf("dnd")).performClick()

    compose.onNodeWithTag(GroupTestTags.NAME).assertTextContains("D&D", substring = true)
    compose.onNodeWithTag(GroupTestTags.SAVE).assertIsEnabled()
    compose.onNodeWithTag(GroupTestTags.DELETE).assertIsDisplayed()
  }

  @Test
  fun `Unfiled is offered no way to delete itself`() {
    // It is where a deleted group's rolls go, so it has to be there to go to.
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()

    compose.onNodeWithTag(GroupTestTags.editOf(SavedRollGroup.UNFILED_ID)).performClick()

    compose.onNodeWithTag(GroupTestTags.DELETE).assertDoesNotExist()
  }

  @Test
  fun `deleting a group says first how many rolls will move`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    runBlocking { repository.save(SavedRoll(id = "axe", groupId = "dnd", name = "Axe", formula = "1d12")) }
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()

    compose.onNodeWithTag(GroupTestTags.editOf("dnd")).performClick()

    compose.onNodeWithTag(GroupTestTags.MOVES, useUnmergedTree = true).assertTextContains(
      "1 roll",
      substring = true,
    )
  }

  @Test
  fun `a group that has groups inside it says why it cannot be moved`() {
    given(
      SavedRollGroup(id = "dnd", name = "D&D"),
      SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"),
    )
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()

    compose.onNodeWithTag(GroupTestTags.editOf("dnd")).performClick()

    // An empty chooser reads as a bug; a sentence reads as a rule.
    compose.onNodeWithTag(GroupTestTags.NO_NESTING, useUnmergedTree = true).assertIsDisplayed()
    compose.onNodeWithTag(GroupTestTags.parentOf(null)).assertDoesNotExist()
  }

  @Test
  fun `a group can be put inside a top-level one`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()
    compose.onNodeWithTag(GroupTestTags.NEW).performClick()
    compose.onNodeWithTag(GroupTestTags.NAME).performTextInput("Thorin")

    compose.onNodeWithTag(GroupTestTags.parentOf("dnd")).performClick()
    compose.onNodeWithTag(GroupTestTags.SAVE).performClick()

    compose.waitUntil(PATIENCE) { groups().any { it.name == "Thorin" && it.parentId == "dnd" } }
  }

  @Test
  fun `Cancel leaves the group list alone`() {
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()
    compose.onNodeWithTag(GroupTestTags.NEW).performClick()
    compose.onNodeWithTag(GroupTestTags.NAME).performTextInput("Thorin")

    compose.onNodeWithTag(GroupTestTags.CANCEL).performClick()

    compose.onNodeWithTag(GroupTestTags.SHEET).assertDoesNotExist()
    assertTrue(names().none { it == "Thorin" })
  }

  @Test
  fun `a mark chosen on the sheet is written with the group`() {
    show()
    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()
    compose.onNodeWithTag(GroupTestTags.NEW).performClick()
    compose.onNodeWithTag(GroupTestTags.NAME).performTextInput("Thorin")

    compose.onNodeWithTag(GroupTestTags.iconOf("🐉")).performClick()
    compose.onNodeWithTag(GroupTestTags.SAVE).performClick()

    compose.waitUntil(PATIENCE) { groups().any { it.name == "Thorin" && it.icon == "🐉" } }
  }

  @Test
  fun `the editor can file a roll in a group that does not exist yet`() {
    // Before this, writing a roll into a new group meant leaving the roll
    // half-written, making the group, and coming back.
    val presenter =
      EditorPresenter(
        repository = repository,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        ids = { "made-up" },
      )
    val groups = GroupPresenter(repository, scope, "Unfiled", ids = { "new-group" })
    compose.setContent { EditorScreen(presenter = presenter, groups = groups) }

    compose.onNodeWithTag(EditorTestTags.NEW_GROUP).performScrollTo().performClick()
    compose.onNodeWithTag(GroupTestTags.NAME).performTextInput("Thorin")
    compose.onNodeWithTag(GroupTestTags.SAVE).performClick()

    // Made, and chosen: asking for a group from here means filing the roll in it.
    compose.waitUntil(PATIENCE) { presenter.state.groupId == "new-group" }
    assertEquals("Thorin", groups().firstOrNull { it.id == "new-group" }?.name)
  }

  private fun show() {
    val presenter =
      SavedPresenter(
        repository = repository,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        unfiledName = "Unfiled",
      )
    val groups = GroupPresenter(repository, scope, "Unfiled", ids = { "made-up" })
    compose.setContent { SavedScreen(presenter = presenter, groups = groups) }
  }

  private fun given(vararg groups: SavedRollGroup) {
    runBlocking {
      repository.ensureUnfiled("Unfiled")
      groups.forEach { repository.save(it) }
    }
  }

  private fun groups(): List<SavedRollGroup> = runBlocking { repository.groups.first() }

  private fun names(): List<String> = groups().map { it.name }

  private companion object {
    const val PATIENCE = 2_000L
  }
}
