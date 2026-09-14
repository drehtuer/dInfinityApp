package de.drehtuer.dinfinity

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.CollectionImporter
import de.drehtuer.dinfinity.data.DieStatisticsRepository
import de.drehtuer.dinfinity.data.HistoryRepository
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.StatisticsRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.feature.saved.EditorPresenter
import de.drehtuer.dinfinity.feature.saved.EditorTestTags
import de.drehtuer.dinfinity.feature.saved.GroupPresenter
import de.drehtuer.dinfinity.feature.saved.ImportPresenter
import de.drehtuer.dinfinity.feature.saved.ImportTestTags
import de.drehtuer.dinfinity.feature.saved.SavedPresenter
import de.drehtuer.dinfinity.feature.saved.SavedTestTags
import de.drehtuer.dinfinity.feature.stats.HistoryPresenter
import de.drehtuer.dinfinity.feature.stats.HistoryTestTags
import de.drehtuer.dinfinity.feature.stats.StatsPresenter
import de.drehtuer.dinfinity.feature.stats.StatsTestTags
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.DInfinityTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The navigation graph with real screens behind it.
 *
 * `DInfinityAppTest` drives the graph with nothing plugged in, which is right
 * for the graph's own rules. This is the other half: every destination that
 * takes a presenter, built over a real database, so that "the menu reaches
 * every screen" means the screen it reaches *draws* rather than falls back to
 * a placeholder.
 *
 * It is the one place the whole app is assembled, which is also the only place
 * a wiring mistake between two feature modules can show up.
 */
@RunWith(RobolectricTestRunner::class)
class DInfinityScreensTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var saved: SavedRollRepository
  private val scope = CoroutineScope(Dispatchers.Unconfined)
  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        .build()
    saved = SavedRollRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `the saved-rolls screen draws when it has a presenter`() {
    val navigation = app()

    go(navigation, Destination.SavedRolls)

    compose.onNodeWithTag(SavedTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the editor draws, on a roll that exists`() {
    runBlocking {
      saved.ensureUnfiled("Unfiled")
      saved.save(SavedRoll(id = "fireball", groupId = SavedRollGroup.UNFILED_ID, name = "Fireball", formula = "8d6"))
    }
    val navigation = app()

    compose.runOnIdle { navigation.navigate(editorRoute("fireball")) }

    compose.onNodeWithTag(EditorTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the editor draws for a roll that does not exist yet`() {
    val navigation = app()

    compose.runOnIdle { navigation.navigate(editorRoute(null)) }

    compose.onNodeWithTag(EditorTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the import screen draws`() {
    val navigation = app()

    go(navigation, Destination.CollectionImport)

    compose.onNodeWithTag(ImportTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(ImportTestTags.CHOOSE).assertIsDisplayed()
  }

  @Test
  fun `the history screen draws`() {
    val navigation = app()

    go(navigation, Destination.History)

    compose.onNodeWithTag(HistoryTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the statistics screen draws`() {
    val navigation = app()

    go(navigation, Destination.Statistics)

    compose.onNodeWithTag(StatsTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `every destination in the graph answers to its own route still answers, rather than leaving the graph blank`() {
    // Every destination in the graph has a composable. A gate that failed to
    // match would leave a destination that navigates to nothing, which is the
    // one navigation failure that looks like the app has crashed.
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme { DInfinityApp(navController = navigation) }
    }

    Destination.entries.forEach { destination ->
      compose.runOnIdle { navigation.navigate(destination.route) }
      assertEquals(
        destination,
        compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute),
      )
    }
  }

  private fun go(
    navigation: NavHostController,
    destination: Destination,
  ) {
    compose.runOnIdle { navigation.navigate(destination.route) }
  }

  /** The app with every screen that takes a presenter actually given one. */
  private fun app(): NavHostController {
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme {
        DInfinityApp(
          navController = navigation,
          savedRolls = {
            SavedPresenter(
              repository = saved,
              catalog = catalog,
              scope = scope,
              unfiledName = "Unfiled",
            )
          },
          savedGroups = { GroupPresenter(saved, scope, "Unfiled") },
          savedRollEditor = { editing ->
            EditorPresenter(repository = saved, catalog = catalog, scope = scope, editing = editing)
          },
          collectionImport = {
            ImportPresenter(
              importer = CollectionImporter(database),
              catalog = catalog,
              scope = scope,
              unfiledName = "Unfiled",
            )
          },
          history = { HistoryPresenter(history = HistoryRepository(database), scope = scope) },
          statistics = {
            StatsPresenter(
              statistics = DieStatisticsRepository(database),
              writer = StatisticsRepository(database),
              catalog = catalog,
              scope = scope,
            )
          },
        )
      }
    }
    return navigation
  }
}
