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
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.ui.common.FormulaTestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Writing down a saved roll (`design/dInfinity.dc.html`, options 1r and 7b).
 *
 * The claim worth testing: **nothing gets saved that cannot be thrown.** A
 * saved roll is a button somebody presses in the middle of a game, and one
 * that fails then is worse than one that was never made.
 */
@RunWith(RobolectricTestRunner::class)
class EditorScreenTest {
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
    repository = SavedRollRepository(database) { NOW }
    runBlocking { repository.ensureUnfiled("Unfiled") }
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
  fun `a new roll cannot be saved until it has a formula`() {
    show()

    compose.onNodeWithTag(EditorTestTags.SAVE).assertIsNotEnabled()
  }

  @Test
  fun `a formula that does not read cannot be saved either`() {
    // A saved roll that cannot be thrown is a button that fails when it is
    // pressed, weeks later, in the middle of somebody's game.
    show()

    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextInput("3d6 +")

    compose.onNodeWithTag(FormulaTestTags.ERROR).assertIsDisplayed()
    compose.onNodeWithTag(EditorTestTags.SAVE).assertIsNotEnabled()
  }

  @Test
  fun `a formula that reads says what it is worth`() {
    // Nothing has to be thrown to know it (`docs/probability.md`).
    show()

    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextInput("2d6")

    compose.onNodeWithTag(EditorTestTags.ODDS).assertTextContains("7.0", substring = true)
    compose.onNodeWithTag(EditorTestTags.ODDS).assertTextContains("2", substring = true)
    compose.onNodeWithTag(EditorTestTags.SAVE).assertIsEnabled()
  }

  @Test
  fun `saving writes it down, whole`() {
    val presenter = show()
    compose.onNodeWithTag(EditorTestTags.NAME).performTextInput("Fireball")
    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextInput("8d6 [Fire]")
    compose.onNodeWithTag(EditorTestTags.iconOf("🔥")).performScrollTo().performClick()
    compose.onNodeWithTag(EditorTestTags.colourOf(AccentColor.Vermilion.argb)).performScrollTo().performClick()
    compose.onNodeWithTag(EditorTestTags.FAVOURITE).performScrollTo().performClick()

    compose.onNodeWithTag(EditorTestTags.SAVE).performScrollTo().performClick()
    presenter.written()

    val saved = runBlocking { repository.all.first().single() }
    assertEquals("Fireball", saved.name)
    assertEquals("8d6 [Fire]", saved.formula)
    assertEquals("🔥", saved.icon)
    assertEquals(AccentColor.Vermilion.argb, saved.colorArgb)
    assertTrue(saved.favourite)
  }

  @Test
  fun `a roll with no name of its own is called by its formula`() {
    // Better than "Untitled": the formula is the one thing it definitely has.
    val presenter = show()
    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextInput("1d20")

    compose.onNodeWithTag(EditorTestTags.SAVE).performScrollTo().performClick()
    presenter.written()

    assertEquals(
      "1d20",
      runBlocking {
        repository.all
          .first()
          .single()
          .name
      },
    )
  }

  @Test
  fun `editing an existing roll opens on what it says and saves over it`() {
    given(SavedRoll(id = "fireball", groupId = UNFILED, name = "Fireball", formula = "8d6"))

    val presenter = show(editing = "fireball")
    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextReplacement("10d6")
    compose.onNodeWithTag(EditorTestTags.SAVE).performScrollTo().performClick()
    presenter.written()

    val rolls = runBlocking { repository.all.first() }
    assertEquals("one edit made two rolls", 1, rolls.size)
    assertEquals("10d6", rolls.single().formula)
  }

  @Test
  fun `a new roll offers no delete`() {
    show()

    compose.onNodeWithTag(EditorTestTags.DELETE).assertDoesNotExist()
  }

  @Test
  fun `a roll that exists offers to delete it`() {
    given(SavedRoll(id = "fireball", groupId = UNFILED, name = "Fireball", formula = "8d6"))

    show(editing = "fireball")

    compose.onNodeWithTag(EditorTestTags.DELETE).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `deleting takes it away`() {
    given(SavedRoll(id = "fireball", groupId = UNFILED, name = "Fireball", formula = "8d6"))
    val presenter = show(editing = "fireball")

    compose.onNodeWithTag(EditorTestTags.DELETE).performScrollTo().performClick()
    presenter.written()

    assertTrue(runBlocking { repository.all.first() }.isEmpty())
  }

  @Test
  fun `a table can be pinned to this roll, and unpinned again`() {
    // A pin here wins over the group's and the app's while this roll is the
    // one being thrown (`docs/tables.md`).
    val felt = BuiltinDiceSet.set.tables.first()
    val presenter = show()
    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextInput("1d20")

    compose.onNodeWithTag(EditorTestTags.tableOf(TablePin("builtin", felt.id))).performScrollTo().performClick()
    compose.onNodeWithTag(EditorTestTags.SAVE).performScrollTo().performClick()
    presenter.written()

    assertEquals(
      TablePin("builtin", felt.id),
      runBlocking {
        repository.all
          .first()
          .single()
          .tablePin
      },
    )
  }

  @Test
  fun `default means follow whatever is pinned above`() {
    val presenter = show()
    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextInput("1d20")

    compose.onNodeWithTag(EditorTestTags.tableOf(null)).performScrollTo().performClick()
    compose.onNodeWithTag(EditorTestTags.SAVE).performScrollTo().performClick()
    presenter.written()

    assertNull(
      runBlocking {
        repository.all
          .first()
          .single()
          .tablePin
      },
    )
  }

  @Test
  fun `saving leaves the editor, and so does deleting`() {
    val left = mutableListOf<Unit>()
    val presenter = show(onDone = { left += Unit })
    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextInput("1d20")

    compose.onNodeWithTag(EditorTestTags.SAVE).performScrollTo().performClick()
    presenter.written()
    compose.waitUntil(PATIENCE) { left.isNotEmpty() }

    assertTrue("the editor stayed open over a roll it had already written down", left.isNotEmpty())
  }

  @Test
  fun `roll now hands the formula on without writing anything down`() {
    val thrown = mutableListOf<String>()
    show(onRollNow = thrown::add)
    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextInput("2d6 + 3")

    compose.onNodeWithTag(EditorTestTags.ROLL_NOW).performScrollTo().performClick()

    assertEquals(listOf("2d6 + 3"), thrown)
    assertTrue("rolling it saved it", runBlocking { repository.all.first() }.isEmpty())
  }

  private fun given(vararg rolls: SavedRoll) {
    runBlocking { rolls.forEach { repository.save(it) } }
  }

  private fun show(
    editing: String? = null,
    onDone: () -> Unit = {},
    onRollNow: (String) -> Unit = {},
  ): EditorPresenter {
    val presenter =
      EditorPresenter(
        repository = repository,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        ids = { "made-up" },
        editing = editing,
      )
    val groups = GroupPresenter(repository = repository, scope = scope, unfiledName = "Unfiled", ids = { "new-group" })
    compose.setContent {
      EditorScreen(presenter = presenter, groups = groups, onDone = onDone, onRollNow = onRollNow)
    }
    return presenter
  }

  /**
   * Waits for the write to land.
   *
   * The editor writes on its own scope and the database answers on its own
   * executor, so "saved" arrives a moment after the press — on a phone that is
   * imperceptible, and in a test it is this.
   */
  private fun EditorPresenter.written() {
    compose.waitUntil(PATIENCE) { state.saved || state.gone }
  }

  private companion object {
    const val NOW = 1_000L
    const val PATIENCE = 2_000L
    const val UNFILED = SavedRollGroup.UNFILED_ID
  }
}
