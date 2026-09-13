package de.drehtuer.dinfinity.feature.saved

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.collection.CollectionLimits
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.CollectionImporter
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Taking a collection in (`design/dInfinity.dc.html`, options 9f and 9g).
 *
 * Against a real database, because the refusal that matters most is about what
 * is already saved: a group name that is taken. A fake would be asserting that
 * the fake knows its own names.
 *
 * Choosing the file is `app`'s — a content URI is the application's business —
 * so these start where the screen does, with the text of a file.
 */
@RunWith(RobolectricTestRunner::class)
class ImportScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var repository: SavedRollRepository
  private lateinit var importer: CollectionImporter
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
    repository = SavedRollRepository(database)
    importer = CollectionImporter(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `it opens asking for a file, and says what will not happen to what is saved`() {
    show()

    compose.onNodeWithTag(ImportTestTags.CHOOSE).assertIsDisplayed()
    compose.onNodeWithText("refused", substring = true).assertIsDisplayed()
  }

  @Test
  fun `a collection goes in, and the screen says what arrived`() {
    val presenter = show()

    presenter.offer(thorin())

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Imported }
    compose.onNodeWithTag(ImportTestTags.DONE).assertTextContains("Thorin", substring = true)
    compose.onNodeWithTag(ImportTestTags.COUNTS).assertTextContains("1 group", substring = true)
    compose.onNodeWithTag(ImportTestTags.COUNTS).assertTextContains("2 rolls", substring = true)
    assertEquals(listOf("Fireball", "Longsword"), rollNames())
  }

  @Test
  fun `a group name that is already here refuses the import and names it`() {
    given(SavedRollGroup(id = "mine", name = "Thorin"))
    val presenter = show()

    presenter.offer(thorin())

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Clash }
    compose.onNodeWithTag(ImportTestTags.CLASH).assertTextContains("Thorin", substring = true)
    // Nothing merged, nothing deleted.
    assertEquals(emptyList<String>(), rollNames())
  }

  @Test
  fun `a refusal says what to do about it`() {
    given(SavedRollGroup(id = "mine", name = "Thorin"))
    val presenter = show()

    presenter.offer(thorin())

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Clash }
    compose.onNodeWithText("Rename", substring = true).assertIsDisplayed()
  }

  @Test
  fun `a file that is not a collection lists everything wrong with it`() {
    val presenter = show()

    presenter.offer(
      """
      {
        "format": 1, "name": "Broken",
        "groups": [{ "id": "a", "name": "A" }],
        "rolls": [
          { "group": "a", "name": "One", "formula": "3d" },
          { "group": "nowhere", "name": "Two", "formula": "1d20" }
        ]
      }
      """.trimIndent(),
    )

    compose.onNodeWithTag(ImportTestTags.UNREADABLE).assertTextContains("2 things", substring = true)
    compose.onNodeWithTag(ImportTestTags.PROBLEMS).assertIsDisplayed()
    compose.onNodeWithText("rolls[0].formula", substring = true).assertIsDisplayed()
    compose.onNodeWithText("rolls[1].group", substring = true).assertIsDisplayed()
  }

  @Test
  fun `something that is not a collection at all is refused without a crash`() {
    val presenter = show()

    presenter.offer("this is somebody's shopping list")

    compose.onNodeWithTag(ImportTestTags.UNREADABLE).assertIsDisplayed()
  }

  @Test
  fun `a file too big to be a collection is refused`() {
    val presenter = show()

    presenter.offer("{\"format\":1,\"name\":\"" + "x".repeat(CollectionLimits.MAX_BYTES) + "\"}")

    compose.onNodeWithTag(ImportTestTags.UNREADABLE).assertIsDisplayed()
  }

  @Test
  fun `a file that could not be opened says so, rather than looking like a bad collection`() {
    val presenter = show()

    presenter.unopenable("permission was withdrawn")

    compose.onNodeWithTag(ImportTestTags.UNOPENABLE).assertIsDisplayed()
    compose.onNodeWithText("permission", substring = true).assertIsDisplayed()
  }

  @Test
  fun `a roll whose dice are not installed comes in anyway, and is mentioned`() {
    // The set may be installed tomorrow; rewriting what somebody wrote would
    // be worse than carrying it as written (`docs/dice-notation.md`).
    val presenter = show()

    presenter.offer(
      """
      {
        "format": 1, "name": "Brass",
        "groups": [{ "id": "a", "name": "A" }],
        "rolls": [{ "group": "a", "name": "Odd", "formula": "brass:1d20" }]
      }
      """.trimIndent(),
    )

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Imported }
    compose.onNodeWithTag(ImportTestTags.WARNINGS).assertIsDisplayed()
    compose.onNodeWithText("brass", substring = true).assertIsDisplayed()
    assertEquals(listOf("Odd"), rollNames())
  }

  @Test
  fun `after a refusal, another file can be chosen`() {
    val presenter = show()
    presenter.offer("not a collection")

    compose.onNodeWithTag(ImportTestTags.AGAIN).performClick()

    compose.onNodeWithTag(ImportTestTags.CHOOSE).assertIsDisplayed()
  }

  @Test
  fun `the way on from a finished import is the list it went into`() {
    var done = false
    val presenter = show(onDone = { done = true })
    presenter.offer(thorin())

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Imported }
    compose.onNodeWithTag(ImportTestTags.SEE).performClick()

    assertEquals(true, done)
  }

  private fun show(onDone: () -> Unit = {}): ImportPresenter {
    val presenter =
      ImportPresenter(
        importer = importer,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        unfiledName = "Unfiled",
      )
    compose.setContent { ImportScreen(presenter = presenter, onDone = onDone) }
    return presenter
  }

  private fun given(vararg groups: SavedRollGroup) {
    runBlocking { groups.forEach { repository.save(it) } }
  }

  private fun rollNames(): List<String> = runBlocking { repository.all.first() }.map { it.name }.sorted()

  private fun thorin() =
    """
    {
      "format": 1,
      "name": "Thorin, level 5 fighter",
      "groups": [{ "id": "thorin", "name": "Thorin", "icon": "⚔️", "parent": null }],
      "rolls": [
        { "group": "thorin", "name": "Longsword", "formula": "1d20 + 7 [Attack]" },
        { "group": "thorin", "name": "Fireball", "formula": "8d6 [Fire]" }
      ]
    }
    """.trimIndent()

  private companion object {
    const val PATIENCE = 2_000L
  }
}
