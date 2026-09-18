package de.drehtuer.dinfinity.feature.saved

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.SavedRollGroupRepository
import de.drehtuer.dinfinity.data.SavedRollLibrary
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Named formulas, rolled with one tap
 * (`design/dInfinity.dc.html`, options 1o, 9b and 9d).
 *
 * Against a real database rather than a fake repository: the ordering — the
 * thing a player notices most about this screen — is SQL, and a fake would be
 * asserting that the test's own `sortedBy` works.
 */
@RunWith(RobolectricTestRunner::class)
class SavedScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var repository: SavedRollRepository
  private lateinit var groupRepository: SavedRollGroupRepository
  private lateinit var library: SavedRollLibrary
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
    repository = SavedRollRepository(database) { NOW }
    groupRepository = SavedRollGroupRepository(database)
    library = SavedRollLibrary(repository, groupRepository)
  }

  @After
  fun close() {
    // The presenter watches the database for as long as its scope lives, so
    // the scope has to go first: a collector left running against a closed
    // connection fails the *next* test, which is a long way from here.
    scope.cancel()
    database.close()
  }

  @Test
  fun `a group with nothing in it says so, and offers the way out of that`() {
    show()

    compose.onNodeWithTag(SavedTestTags.EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(SavedTestTags.LIST).assertDoesNotExist()
  }

  @Test
  fun `every roll in the group is listed with its formula`() {
    given(roll("fireball", name = "Fireball", formula = "8d6 [Fire]"))

    show()

    compose.onNodeWithTag(SavedTestTags.rollOf("fireball")).assertTextContains("Fireball", substring = true)
    compose.onNodeWithTag(SavedTestTags.rollOf("fireball")).assertTextContains("8d6 [Fire]", substring = true)
  }

  @Test
  fun `no row wears a star, because there is no pinning left to show`() {
    // The design took the favourite flag out in the pass of 2026-09-17: a
    // favourite and a roll dragged to the top were solving the same problem
    // twice (`docs/dice-notation.md`, "Saved rolls").
    given(roll("fireball", name = "Fireball"), roll("plain", name = "Magic missile"))

    show()

    compose.onNodeWithTag(SavedTestTags.rollOf("fireball")).assertTextContains("Fireball", substring = true)
    compose.onNodeWithTag(SavedTestTags.rollOf("fireball")).assert(hasText("★", substring = true).not())
  }

  @Test
  fun `every row carries a grip, and it is a target a thumb can hit`() {
    given(roll("fireball", name = "Fireball"))

    show()

    val grip = compose.onNodeWithTag(SavedTestTags.gripOf("fireball"), useUnmergedTree = true)
    grip.assertContentDescriptionEquals("Reorder Fireball")
    grip.assertWidthIsAtLeast(TOUCH_TARGET)
    grip.assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `the grip moves a row without a drag, for somebody who cannot drag one`() {
    // The same move by the route a screen reader has. A list that can only be
    // ordered by dragging is a list TalkBack cannot order at all
    // (`docs/architecture.md`, "Accessibility").
    given(roll("first"), roll("second"), roll("third"))
    show()

    compose
      .onNodeWithTag(SavedTestTags.gripOf("third"), useUnmergedTree = true)
      .fetchSemanticsNode()
      .config[SemanticsActions.CustomActions]
      .first { action -> action.label == "Move up" }
      .action()
    compose.waitForIdle()

    assertEquals(
      listOf("first", "third", "second"),
      runBlocking { repository.inGroup(SavedRollGroup.UNFILED_ID).first() }.map { it.id },
    )
  }

  @Test
  fun `a drag down the grip reorders the list and writes it down`() {
    given(roll("first"), roll("second"), roll("third"))
    show()
    val row =
      compose
        .onNodeWithTag(SavedTestTags.rollOf("first"))
        .fetchSemanticsNode()
        .size.height
        .toFloat()

    compose.onNodeWithTag(SavedTestTags.gripOf("first"), useUnmergedTree = true).performTouchInput {
      down(center)
      // Two rows down, in steps, the way a finger arrives rather than teleports.
      moveBy(Offset(0f, row))
      moveBy(Offset(0f, row))
      up()
    }

    assertEquals(
      listOf("second", "third", "first"),
      runBlocking { repository.inGroup(SavedRollGroup.UNFILED_ID).first() }.map { it.id },
    )
  }

  @Test
  fun `a tap rolls it`() {
    given(roll("fireball"))
    val rolled = mutableListOf<String>()
    show(onRoll = { rolled += it.roll.id })

    compose.onNodeWithTag(SavedTestTags.rollOf("fireball")).performClick()

    assertEquals(listOf("fireball"), rolled)
  }

  @Test
  fun `a long press edits it, and is not also a tap`() {
    // The two do different things, so a press that counted as both would edit
    // a roll and throw it at the same time.
    given(roll("fireball"))
    val rolled = mutableListOf<String>()
    val edited = mutableListOf<String>()
    show(onRoll = { rolled += it.roll.id }, onEdit = { edited += it.roll.id })

    compose.onNodeWithTag(SavedTestTags.rollOf("fireball")).performTouchInput { longClick() }

    assertEquals(emptyList<String>(), rolled)
    assertEquals(listOf("fireball"), edited)
  }

  @Test
  fun `a roll whose dice are gone says so rather than failing when thrown`() {
    // The set may be installed again tomorrow, so the roll is neither deleted
    // nor rewritten (`docs/dice-notation.md`).
    given(roll("brass", formula = "brass:1d20"))

    show()

    compose.onNodeWithTag(SavedTestTags.brokenOf("brass"), useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `a roll that still resolves says nothing about its dice`() {
    given(roll("fireball", formula = "8d6"))

    show()

    compose.onNodeWithTag(SavedTestTags.brokenOf("fireball"), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `the switcher lists every group with how much is in it`() {
    runBlocking {
      groupRepository.save(SavedRollGroup(id = "dnd", name = "D&D"))
      groupRepository.save(SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"))
    }
    given(roll("axe", groupId = "thorin"))
    show()

    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()

    compose.onNodeWithTag(SavedTestTags.groupOf("thorin")).assertTextContains("1 roll", substring = true)
    // A child is drawn under its parent's name rather than indented: an indent
    // is a tree control waiting to happen, and there is no tree.
    compose.onNodeWithTag(SavedTestTags.groupOf("thorin")).assertTextContains("D&D", substring = true)
  }

  /**
   * The export mark and the group's **…** are single glyphs: a picture to a
   * screen reader, and a target the size of one character to a thumb
   * (`docs/architecture.md`, "Accessibility").
   */
  @Test
  fun `the controls drawn as one character are labelled and big enough to press`() {
    runBlocking { groupRepository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
    given(roll("axe", groupId = "thorin"))
    show()

    compose.onNodeWithTag(ExportTestTags.OPEN).assertContentDescriptionEquals("Collections: export or import")
    compose.onNodeWithTag(ExportTestTags.OPEN).assertWidthIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag(ExportTestTags.OPEN).assertHeightIsAtLeast(TOUCH_TARGET)

    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()

    compose.onNodeWithTag(GroupTestTags.editOf("thorin")).assertContentDescriptionEquals("Edit the group Thorin")
    compose.onNodeWithTag(GroupTestTags.editOf("thorin")).assertWidthIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag(GroupTestTags.editOf("thorin")).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `choosing a group shows that group's rolls and nothing else`() {
    runBlocking { groupRepository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
    given(roll("axe", groupId = "thorin"), roll("loose"))
    val opened = mutableListOf<String>()
    show(onActiveGroup = opened::add)

    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()
    compose.onNodeWithTag(SavedTestTags.groupOf("thorin")).performClick()

    compose.onNodeWithTag(SavedTestTags.rollOf("axe")).assertIsDisplayed()
    compose.onNodeWithTag(SavedTestTags.rollOf("loose")).assertDoesNotExist()
    assertEquals(listOf("thorin"), opened)
  }

  @Test
  fun `rolling something does not move it, because the order is the player's`() {
    given(roll("first"), roll("second"), roll("third"))
    show()

    compose.onNodeWithTag(SavedTestTags.rollOf("third")).performClick()

    assertEquals(
      listOf("first", "second", "third"),
      runBlocking { repository.inGroup(SavedRollGroup.UNFILED_ID).first() }.map { it.id },
    )
  }

  private fun given(vararg rolls: SavedRoll) {
    runBlocking {
      groupRepository.ensureUnfiled("Unfiled")
      rolls.forEach { repository.save(it) }
    }
  }

  private fun show(
    onRoll: (SavedEntry) -> Unit = {},
    onEdit: (SavedEntry) -> Unit = {},
    onActiveGroup: (String) -> Unit = {},
  ) {
    val presenter =
      SavedPresenter(
        library = library,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        unfiledName = "Unfiled",
        onActiveGroup = onActiveGroup,
      )
    val groups =
      GroupPresenter(
        library = library,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        unfiledName = "Unfiled",
      )
    compose.setContent {
      SavedScreen(presenter = presenter, groups = groups, onRoll = onRoll, onEdit = onEdit)
    }
  }

  private fun roll(
    id: String,
    groupId: String = SavedRollGroup.UNFILED_ID,
    name: String = id,
    formula: String = "1d20",
  ) = SavedRoll(id = id, groupId = groupId, name = name, formula = formula)

  private companion object {
    const val NOW = 1_000L
  }
}
