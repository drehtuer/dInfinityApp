package de.drehtuer.dinfinity

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.data.HistoryEntry
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.feature.sets.SetLibrary
import de.drehtuer.dinfinity.feature.settings.DeveloperTestTags
import de.drehtuer.dinfinity.feature.settings.MenuTestTags
import de.drehtuer.dinfinity.feature.settings.SettingsTestTags
import de.drehtuer.dinfinity.feature.stats.ExportFormat
import de.drehtuer.dinfinity.feature.stats.HistoryExport
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.DInfinityTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/**
 * The developer toggle, from the switch to the screen
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * Two claims, and the second is the one that matters:
 *
 * - with it off nothing in the app mentions it, and with it on there is one
 *   more row in the menu and one more screen behind that row;
 * - **turning it on changes nothing about the app a player uses.** The history
 *   still has no replay and still never shows a seed, and the exports still
 *   have no column for one (`docs/architecture.md`, decisions 13 and 53).
 */
@RunWith(RobolectricTestRunner::class)
class DeveloperToggleTest {
  @get:Rule
  val compose = createComposeRule()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  private val temporary: File = Files.createTempDirectory("dinfinity-developer-sets").toFile()
  private lateinit var database: DInfinityDatabase

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        // On the calling thread, so a flow from a query emits when the write
        // happens rather than when a pool thread gets to it.
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `with the toggle off the menu has no developer row at all`() {
    app(AppSettings())

    compose.onNodeWithTag(MenuTestTags.BUTTON).performClick()

    assertEquals(
      "a debugging tool was offered to a player",
      0,
      compose.onAllNodesWithTag(MenuTestTags.entryOf(Destination.Developer.route)).fetchSemanticsNodes().size,
    )
  }

  @Test
  fun `with the toggle on the menu offers it, and it opens`() {
    val navigation = app(AppSettings(developerTools = true))

    compose.runOnIdle { navigation.navigate(Destination.Menu.route) }
    compose.onNodeWithTag(MenuTestTags.entryOf(Destination.Developer.route)).performScrollTo().performClick()

    compose.onNodeWithTag(DeveloperTestTags.SCREEN).assertIsDisplayed()
    // And it is a real screen rather than the placeholder an unplugged one
    // draws (`Presenters`).
    assertEquals(0, compose.onAllNodesWithTag(notBuiltTag(Destination.Developer)).fetchSemanticsNodes().size)
  }

  @Test
  fun `the switch is on the settings screen and reports what was pressed`() {
    val changed = mutableListOf<Boolean>()
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme {
        DInfinityApp(
          navController = navigation,
          settings = AppSettings(),
          onDeveloperToolsChanged = { changed += it },
          screens = testPresenters(database, scope, setLibrary()),
        )
      }
    }

    compose.runOnIdle { navigation.navigate(Destination.Settings.route) }
    compose.onNodeWithTag(SettingsTestTags.DEVELOPER).performScrollTo().performClick()

    assertEquals(listOf(true), changed)
  }

  @Test
  fun `the toggle cannot put a seed into the history, because the type has none`() {
    // Structural rather than remembered: there is no field for the developer
    // screen to unhide and no column for an export to gain. Asserted here as
    // well as in `ShakeStopsAtTheRollScreenTest`, because "the toggle does not
    // change this" is exactly the claim somebody would be tempted to weaken
    // when adding the next debugging tool.
    val fields = HistoryEntry::class.java.declaredFields.map { it.name.lowercase() }

    assertFalse("the history grew a seed", fields.any { it.contains("seed") })
    assertFalse("the history grew a throw to re-run", fields.any { it.contains("spec") })
    assertFalse("the history grew a shake", fields.any { it.contains("shake") })
  }

  @Test
  fun `the exports have no seed to gain from the toggle either`() {
    // The export is built from `HistoryEntry`, so the check above already
    // settles it — this says the same thing about the file that comes out,
    // because that is the artefact a person could paste anywhere.
    val entry =
      HistoryEntry(
        id = 1L,
        atEpochMs = 0L,
        sessionId = "default",
        savedRollId = null,
        groupId = null,
        formula = "2d6",
        total = 7L,
        groups = emptyList(),
      )

    val text =
      HistoryExport.of(listOf(entry), ExportFormat.Csv, called = "all").text +
        HistoryExport.of(listOf(entry), ExportFormat.Json, called = "all").text

    assertFalse("a seed reached an export", text.lowercase().contains("seed"))
    assertTrue(text.contains("2d6"))
  }

  private fun setLibrary() =
    SetLibrary(
      bundled = BuiltinDiceSet.set,
      installed = InstalledSets(File(temporary, "dicesets")),
      registry = InstalledSetRepository(database),
      io = Dispatchers.Unconfined,
      installer = PackageInstaller(File(temporary, "dicesets")),
      defaultSetId = { BuiltinDiceSet.set.id },
    )

  private fun app(settings: AppSettings): NavHostController {
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme {
        DInfinityApp(
          navController = navigation,
          settings = settings,
          screens = testPresenters(database, scope, setLibrary()),
          menuHeader = null,
        )
      }
    }
    return navigation
  }
}
