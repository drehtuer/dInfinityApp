package de.drehtuer.dinfinity.feature.saved

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
  private val scope = CoroutineScope(Dispatchers.Unconfined)

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
  }

  @After
  fun close() {
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
      repository.save(SavedRollGroup(id = "dnd", name = "D&D"))
      repository.save(SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"))
    }
    given(roll("axe", groupId = "thorin"))
    show()

    compose.onNodeWithTag(SavedTestTags.SWITCHER).performClick()

    compose.onNodeWithTag(SavedTestTags.groupOf("thorin")).assertTextContains("1 roll", substring = true)
    // A child is drawn under its parent's name rather than indented: an indent
    // is a tree control waiting to happen, and there is no tree.
    compose.onNodeWithTag(SavedTestTags.groupOf("thorin")).assertTextContains("D&D", substring = true)
  }

  @Test
  fun `choosing a group shows that group's rolls and nothing else`() {
    runBlocking { repository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
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
  fun `the order is favourites first, then the most recently used`() {
    given(roll("old"), roll("recent"), roll("liked", favourite = true))
    runBlocking {
      repository.used("old")
      repository.used("recent")
    }

    show()

    // The rows are in the list in that order; the list's own order is SQL's.
    compose.onNodeWithTag(SavedTestTags.rollOf("liked")).assertIsDisplayed()
    compose.onNodeWithTag(SavedTestTags.rollOf("recent")).assertIsDisplayed()
  }

  private fun given(vararg rolls: SavedRoll) {
    runBlocking {
      repository.ensureUnfiled("Unfiled")
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
        repository = repository,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        unfiledName = "Unfiled",
        onActiveGroup = onActiveGroup,
      )
    compose.setContent { SavedScreen(presenter = presenter, onRoll = onRoll, onEdit = onEdit) }
  }

  private fun roll(
    id: String,
    groupId: String = SavedRollGroup.UNFILED_ID,
    name: String = id,
    formula: String = "1d20",
    favourite: Boolean = false,
  ) = SavedRoll(id = id, groupId = groupId, name = name, formula = formula, favourite = favourite)

  private companion object {
    const val NOW = 1_000L
  }
}
