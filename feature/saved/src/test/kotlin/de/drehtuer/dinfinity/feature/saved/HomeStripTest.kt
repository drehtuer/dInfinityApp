package de.drehtuer.dinfinity.feature.saved

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * The active group's saved rolls, on the tray
 * (`design/dInfinity.dc.html`, option 9a).
 *
 * The strip is handed to the roll screen as a slot, so these are tests of the
 * strip on its own — which is also the only way to test it without a GPU and a
 * physics engine in the room.
 */
@RunWith(RobolectricTestRunner::class)
class HomeStripTest {
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
  fun `the rolls of the active group are on the tray`() {
    given(roll("fireball", name = "Fireball", formula = "8d6 [Fire]"))
    show()

    compose.onNodeWithTag(HomeStripTestTags.tileOf("fireball")).assertTextContains("Fireball", substring = true)
    compose.onNodeWithTag(HomeStripTestTags.tileOf("fireball")).assertTextContains("8d6 [Fire]", substring = true)
  }

  @Test
  fun `a tap throws it, which is what a saved roll is for`() {
    // Different from the saved-rolls list, which only fills the field: this is
    // the one place the tray is already on screen to roll it on.
    given(roll("fireball", formula = "8d6"))
    val thrown = mutableListOf<Triple<String, String, String>>()
    show(onRoll = { formula, id, group -> thrown += Triple(formula, id, group) })

    compose.onNodeWithTag(HomeStripTestTags.tileOf("fireball")).performClick()

    assertEquals(listOf(Triple("8d6", "fireball", SavedRollGroup.UNFILED_ID)), thrown)
  }

  @Test
  fun `a tap says which roll it was, not only what to throw`() {
    // Without it every throw is recorded as belonging to nothing, and the
    // saved-roll statistics screen can never have anything on it
    // (`docs/statistics.md`, per saved roll and per group).
    runBlocking { repository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
    given(roll("fireball", groupId = "thorin", formula = "8d6"))
    val thrown = mutableListOf<Triple<String, String, String>>()
    show(
      onRoll = { formula, id, group -> thrown += Triple(formula, id, group) },
      activeGroupId = "thorin",
    )

    compose.onNodeWithTag(HomeStripTestTags.tileOf("fireball")).performClick()

    assertEquals(listOf(Triple("8d6", "fireball", "thorin")), thrown)
  }

  @Test
  fun `a tap counts as a use, so the strip reorders itself over time`() {
    given(roll("fireball"))
    show()

    compose.onNodeWithTag(HomeStripTestTags.tileOf("fireball")).performClick()

    compose.waitUntil(PATIENCE) { (runBlocking { repository.byId("fireball") }?.useCount ?: 0) == 1 }
  }

  @Test
  fun `a long press edits it, and is not also a throw`() {
    given(roll("fireball"))
    val thrown = mutableListOf<String>()
    val edited = mutableListOf<String>()
    show(onRoll = { formula, _, _ -> thrown += formula }, onEdit = edited::add)

    compose.onNodeWithTag(HomeStripTestTags.tileOf("fireball")).performTouchInput { longClick() }

    assertEquals(emptyList<String>(), thrown)
    assertEquals(listOf("fireball"), edited)
  }

  @Test
  fun `an empty group still invites the first save`() {
    // A strip that vanished when there was nothing in it would never tell
    // anybody saved rolls exist.
    show()

    compose.onNodeWithTag(HomeStripTestTags.NEW).assertIsDisplayed()
    compose.onNodeWithTag(HomeStripTestTags.NEW).assertTextContains("Save a roll", substring = true)
  }

  @Test
  fun `once there are rolls the invitation is just another way to add one`() {
    given(roll("fireball"))
    show()

    compose.onNodeWithTag(HomeStripTestTags.NEW).performScrollTo().assertTextContains("New", substring = true)
  }

  @Test
  fun `the invitation asks for a new roll`() {
    var asked = false
    show(onNew = { asked = true })

    compose.onNodeWithTag(HomeStripTestTags.NEW).performClick()

    assertTrue(asked)
  }

  @Test
  fun `only the active group is on the strip`() {
    runBlocking { repository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
    given(roll("axe", groupId = "thorin"), roll("loose"))
    show()

    // The screen opens on Unfiled, which is the active group until one is
    // chosen on the saved-rolls screen.
    compose.onNodeWithTag(HomeStripTestTags.tileOf("loose")).assertIsDisplayed()
    compose.onNodeWithTag(HomeStripTestTags.tileOf("axe")).assertDoesNotExist()
  }

  @Test
  fun `a roll whose dice are gone is marked rather than hidden`() {
    // Marked, not removed and not disabled: the set may be re-installed
    // tomorrow, and a tile that vanished would take the roll with it. Tapping
    // it puts the formula in the field, where the error is. It does not fall
    // back — a set reference gets no substitute (`SavedFormulaTest`).
    given(roll("brass", formula = "brass:1d20"))
    show()

    compose
      .onNodeWithTag(HomeStripTestTags.noteOf("brass"), useUnmergedTree = true)
      .assertTextContains("dice missing", substring = true)
  }

  @Test
  fun `nothing is drawn at all until the database has answered`() {
    // An empty strip that fills in a frame later is the tray jumping as it
    // opens, on the one screen where that is most visible.
    val presenter =
      SavedPresenter(
        repository = repository,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = CoroutineScope(Dispatchers.Unconfined),
        unfiledName = "Unfiled",
      )
    compose.setContent { if (!presenter.state.loaded) HomeStrip(presenter = presenter) }

    // Before the flow has answered there is no strip; the assertion is about
    // the guard, and the presenter is left un-awaited on purpose.
    compose.onNodeWithTag(HomeStripTestTags.STRIP).assertDoesNotExist()
  }

  private fun given(vararg rolls: SavedRoll) {
    runBlocking {
      repository.ensureUnfiled("Unfiled")
      rolls.forEach { repository.save(it) }
    }
  }

  private fun show(
    onRoll: (String, String, String) -> Unit = { _, _, _ -> },
    onEdit: (String) -> Unit = {},
    onNew: () -> Unit = {},
    activeGroupId: String = SavedRollGroup.UNFILED_ID,
  ) {
    val presenter =
      SavedPresenter(
        repository = repository,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        unfiledName = "Unfiled",
        activeGroupId = activeGroupId,
      )
    compose.setContent {
      HomeStrip(presenter = presenter, onRoll = onRoll, onEdit = onEdit, onNew = onNew)
    }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
  }

  private fun roll(
    id: String,
    groupId: String = SavedRollGroup.UNFILED_ID,
    name: String = id,
    formula: String = "1d20",
  ) = SavedRoll(id = id, groupId = groupId, name = name, formula = formula)

  private companion object {
    const val PATIENCE = 2_000L
  }
}
