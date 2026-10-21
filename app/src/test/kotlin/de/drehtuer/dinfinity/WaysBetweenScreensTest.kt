package de.drehtuer.dinfinity

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.data.SavedRollGroupRepository
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.feature.graph.GraphTestTags
import de.drehtuer.dinfinity.feature.roll.RollTestTags
import de.drehtuer.dinfinity.feature.roll.ShakeInput
import de.drehtuer.dinfinity.feature.roll.TestHand
import de.drehtuer.dinfinity.feature.saved.EditorTestTags
import de.drehtuer.dinfinity.feature.saved.ExportTestTags
import de.drehtuer.dinfinity.feature.saved.HomeStripTestTags
import de.drehtuer.dinfinity.feature.saved.SavedTestTags
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.navigation.EditorArgument
import de.drehtuer.dinfinity.navigation.GraphArgument
import de.drehtuer.dinfinity.theme.DInfinityTheme
import de.drehtuer.dinfinity.ui.common.UpTestTags
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
 * The ways from one screen to the next, pressed rather than navigated.
 *
 * Every screen hands a lambda out and the graph turns it into a route; a
 * feature module can test that it calls the lambda, and only here can it be
 * seen that the lambda goes where the button says — with the formula, the roll
 * or the total it was handed, and with the back stack the design asks for
 * (`docs/architecture.md`, "Navigation").
 */
@RunWith(RobolectricTestRunner::class)
class WaysBetweenScreensTest {
  @get:Rule
  val compose = createComposeRule()

  private val app = TestApp()

  /** A hand where the sensors would be, because a shake is the only way to roll. */
  private val hand = TestHand()

  private lateinit var navigation: NavHostController

  @Before
  fun open() {
    ShakeInput.current = hand
  }

  @After
  fun close() {
    ShakeInput.current = ShakeInput.SENSORS
    app.close()
  }

  @Test
  fun `the graph's Roll this puts its formula in the tray`() {
    start()
    go(graphRoute("2d6 + 3", total = null))

    compose.onNodeWithTag(GraphTestTags.ROLL_THIS).performScrollTo().performClick()

    arrivedAt(Destination.Roll)
    assertEquals("2d6 + 3", argument(GraphArgument.FORMULA))
  }

  @Test
  fun `the graph's Save as roll opens a new roll with its formula in it`() {
    start()
    go(graphRoute("2d6 + 3", total = null))

    compose.onNodeWithTag(GraphTestTags.SAVE_AS_ROLL).performScrollTo().performClick()

    arrivedAt(Destination.SavedRollEditor)
    assertEquals("2d6 + 3", argument(EditorArgument.FORMULA))
    assertEquals("", argument(EditorArgument.ROLL))
  }

  @Test
  fun `taking a saved roll puts its formula in the tray, with nothing left to go back through`() {
    // The tray is home, and a saved roll taken is the player going home with
    // a formula: a back stack of list, tray, list would be a detour.
    fireball()
    start()
    go(Destination.SavedRolls.route)

    compose.onNodeWithTag(SavedTestTags.rollOf(FIREBALL)).performClick()

    arrivedAt(Destination.Roll)
    assertEquals("8d6", argument(GraphArgument.FORMULA))
    assertNull("something is under the tray", compose.runOnIdle { navigation.previousBackStackEntry })
  }

  @Test
  fun `holding a saved roll opens it in the editor, and New opens a new one`() {
    fireball()
    start()
    go(Destination.SavedRolls.route)

    compose.onNodeWithTag(SavedTestTags.rollOf(FIREBALL)).performTouchInput { longClick() }
    arrivedAt(Destination.SavedRollEditor)
    assertEquals(FIREBALL, argument(EditorArgument.ROLL))

    compose.runOnIdle { navigation.popBackStack() }
    compose.onNodeWithTag(SavedTestTags.NEW).performClick()
    arrivedAt(Destination.SavedRollEditor)
    assertEquals("", argument(EditorArgument.ROLL))
  }

  @Test
  fun `the export sheet's way in opens the import screen`() {
    start()
    go(Destination.SavedRolls.route)

    compose.onNodeWithTag(ExportTestTags.OPEN).performClick()
    compose.onNodeWithTag(ExportTestTags.IMPORT).performScrollTo().performClick()

    arrivedAt(Destination.CollectionImport)
  }

  @Test
  fun `the editor's chevron climbs to the saved rolls, wherever it was opened from`() {
    start()
    go(editorRoute(null, "2d6"))

    compose.onNodeWithTag(UpTestTags.UP).performClick()

    arrivedAt(Destination.SavedRolls)
    assertEquals(Destination.home.pattern, compose.runOnIdle { navigation.previousBackStackEntry?.destination?.route })
  }

  @Test
  fun `the editor's Roll now puts the roll's formula in the tray`() {
    fireball()
    start()
    go(editorRoute(FIREBALL))

    compose.onNodeWithTag(EditorTestTags.ROLL_NOW).performScrollTo().performClick()

    arrivedAt(Destination.Roll)
    assertEquals("8d6", argument(GraphArgument.FORMULA))
    assertNull("something is under the tray", compose.runOnIdle { navigation.previousBackStackEntry })
  }

  @Test
  fun `the tray's strip opens a saved roll in the editor, and offers a new one`() {
    fireball()
    start()
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()
    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).performClick()
    compose.waitForIdle()

    compose.onNodeWithTag(HomeStripTestTags.tileOf(FIREBALL)).performTouchInput { longClick() }
    arrivedAt(Destination.SavedRollEditor)
    assertEquals(FIREBALL, argument(EditorArgument.ROLL))

    compose.runOnIdle { navigation.popBackStack() }
    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).performClick()
    compose.waitForIdle()
    compose.onNodeWithTag(HomeStripTestTags.NEW).performClick()
    arrivedAt(Destination.SavedRollEditor)
    assertEquals("", argument(EditorArgument.ROLL))
  }

  @Test
  fun `a roll that landed opens the odds on its formula and its total`() {
    start()
    go(rollRoute("1d6"))
    landed()

    compose.onNodeWithTag(RollTestTags.ODDS).performClick()

    arrivedAt(Destination.Graph)
    assertEquals("1d6", argument(GraphArgument.FORMULA))
    // The simulator here lands every die on its first face, which on the
    // bundled d6 is a 1.
    assertEquals("1", argument(GraphArgument.TOTAL))
  }

  @Test
  fun `a roll that landed can be saved, as a new roll with its formula in it`() {
    start()
    go(rollRoute("1d6"))
    landed()

    compose.onNodeWithTag(RollTestTags.SAVE_AS_ROLL).performClick()

    arrivedAt(Destination.SavedRollEditor)
    assertEquals("1d6", argument(EditorArgument.FORMULA))
    assertEquals("", argument(EditorArgument.ROLL))
  }

  /** Past the welcome, one shake, and the result sheet standing still. */
  private fun landed() {
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()
    compose.waitForIdle()
    // The tray listens once its visit is resumed, which is after the
    // navigation has settled rather than when the route was pushed.
    compose.waitUntil(PATIENCE) { compose.runOnUiThread { hand.heard } }
    assertTrue("the shake threw nothing", compose.runOnUiThread { hand.shake() })
    compose.waitUntil(PATIENCE) {
      compose.onAllNodesWithTag(RollTestTags.dieAt(0)).fetchSemanticsNodes().isNotEmpty()
    }
    compose.waitForIdle()
  }

  private fun fireball() =
    runBlocking {
      SavedRollGroupRepository(app.database).ensureUnfiled("Unfiled")
      SavedRollRepository(app.database).save(
        SavedRoll(id = FIREBALL, groupId = SavedRollGroup.UNFILED_ID, name = "Fireball", formula = "8d6"),
      )
    }

  private fun start() {
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme { DInfinityApp(navController = navigation, screens = app.presenters()) }
    }
  }

  private fun go(route: String) {
    compose.runOnIdle { navigation.navigate(route) }
    compose.waitForIdle()
  }

  private fun arrivedAt(destination: Destination) =
    compose.waitUntil(PATIENCE) {
      compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute) ==
        destination
    }

  private fun argument(name: String): String? =
    compose.runOnIdle { navigation.currentBackStackEntry?.arguments?.getString(name) }

  private companion object {
    const val FIREBALL = "fireball"

    /** Long enough for a database round trip, short enough to fail. */
    const val PATIENCE = 5_000L
  }
}
