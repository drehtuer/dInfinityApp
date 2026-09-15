package de.drehtuer.dinfinity.feature.saved

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.collection.CollectionLimits
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.CollectionImporter
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CompletableDeferred
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
        // Queries and invalidation on the calling thread, so a `Flow` from a
        // `@Query` emits when the write happens rather than when a pool thread
        // gets to it. Without it the first wait in a class races Room's own
        // executors, which surfaces as an unrelated test failing now and then.
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
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

  private fun show(
    onDone: () -> Unit = {},
    download: suspend (String) -> Fetched = { Fetched.Failed("no downloader in this test") },
  ): ImportPresenter {
    val presenter =
      ImportPresenter(
        importer = importer,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        unfiledName = "Unfiled",
        download = download,
      )
    compose.setContent { ImportScreen(presenter = presenter, onDone = onDone) }
    return presenter
  }

  private fun given(vararg groups: SavedRollGroup) {
    runBlocking { groups.forEach { repository.save(it) } }
  }

  @Test
  fun `a collection fetched from a link is imported exactly as a file is`() {
    val asked = mutableListOf<String>()
    val presenter =
      show(download = { url ->
        asked += url
        Fetched.Text(thorin())
      })

    presenter.fetch("  https://example.test/thorin.json  ")

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Imported }
    assertEquals("the link was not trimmed before it was fetched", listOf("https://example.test/thorin.json"), asked)
    assertEquals(listOf("Fireball", "Longsword"), rollNames())
  }

  @Test
  fun `bytes from a link go through the reader, and a bad one is refused the same way`() {
    // The rule that matters more than the feature: there is one validator and
    // no path around it. A link is not a shortcut past the rules a file obeys.
    val presenter = show(download = { Fetched.Text("this is not a collection") })

    presenter.fetch("https://example.test/rubbish")

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Unreadable }
    compose.onNodeWithTag(ImportTestTags.UNREADABLE).assertIsDisplayed()
    assertEquals("something was written from an unreadable download", emptyList<String>(), rollNames())
  }

  @Test
  fun `a download that does not arrive is not a bad collection`() {
    val presenter = show(download = { Fetched.Failed("'http://example.test' is not an https link") })

    presenter.fetch("http://example.test/thorin.json")

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Unreachable }
    compose.onNodeWithTag(ImportTestTags.UNREACHABLE).assertIsDisplayed()
    compose.onNodeWithTag(ImportTestTags.UNREADABLE).assertDoesNotExist()
  }

  @Test
  fun `the fetch button does nothing until there is a link to fetch`() {
    val asked = mutableListOf<String>()
    val presenter =
      show(download = { url ->
        asked += url
        Fetched.Failed("no")
      })

    presenter.fetch("   ")

    assertEquals("a blank link was fetched", emptyList<String>(), asked)
    assertTrue("the screen left its opening state for a blank link", presenter.state is ImportState.Waiting)
  }

  @Test
  fun `a presenter given no downloader says so rather than doing nothing`() {
    val presenter = show()

    presenter.fetch("https://example.test/thorin.json")

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Unreachable }
  }

  @Test
  fun `the link is typed on the screen and the button is dead until it is`() {
    // The field and the button are the whole of the feature on screen, and a
    // download of nothing is a spinner that stops for no reason.
    val asked = mutableListOf<String>()
    show(download = { url ->
      asked += url
      Fetched.Text(thorin())
    })

    compose.onNodeWithTag(ImportTestTags.FETCH).assertIsNotEnabled()
    compose.onNodeWithTag(ImportTestTags.LINK).performTextInput("https://example.test/thorin.json")
    compose.onNodeWithTag(ImportTestTags.FETCH).assertIsEnabled().performClick()

    compose.waitUntil(PATIENCE) { asked.isNotEmpty() }
    assertEquals(listOf("https://example.test/thorin.json"), asked)
  }

  @Test
  fun `while a link is being fetched the screen says whose link it is waiting on`() {
    // The one wait in the app that is somebody else's speed, so it is named
    // rather than left as a bare spinner.
    val holding = CompletableDeferred<Fetched>()
    val presenter = show(download = { holding.await() })

    presenter.fetch("https://example.test/thorin.json")

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Fetching }
    compose.onNodeWithTag(ImportTestTags.FETCHING).assertIsDisplayed()
    compose.onNodeWithText("https://example.test/thorin.json", substring = true).assertIsDisplayed()
    holding.complete(Fetched.Text(thorin()))
    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Imported }
  }

  @Test
  fun `a refused download shows the link and the reason, and offers another go`() {
    val presenter = show(download = { Fetched.Failed("the server answered 404") })

    presenter.fetch("https://example.test/gone.json")

    compose.waitUntil(PATIENCE) { presenter.state is ImportState.Unreachable }
    compose.onNodeWithText("https://example.test/gone.json", substring = true).assertIsDisplayed()
    compose.onNodeWithText("404", substring = true).assertIsDisplayed()
    compose.onNodeWithTag(ImportTestTags.AGAIN).performScrollTo().performClick()
    compose.onNodeWithTag(ImportTestTags.LINK).assertIsDisplayed()
  }

  @Test
  fun `a recomposition around the screen that changes nothing leaves it alone`() {
    // The screen is a `when` over one state, so an ordinary recomposition has
    // to skip every branch of it. One that skipped wrongly would come back
    // without its field, which a single-pass test would never see.
    var tick by mutableStateOf(0)
    val presenter =
      ImportPresenter(
        importer = importer,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        unfiledName = "Unfiled",
        download = { Fetched.Text(thorin()) },
      )
    compose.setContent {
      Column {
        Text("tick $tick")
        ImportScreen(presenter = presenter)
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(ImportTestTags.LINK).assertIsDisplayed()
    compose.onNodeWithTag(ImportTestTags.CHOOSE).assertIsDisplayed()
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
