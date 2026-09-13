package de.drehtuer.dinfinity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.feature.graph.GraphMachine
import de.drehtuer.dinfinity.feature.graph.GraphTestTags
import de.drehtuer.dinfinity.feature.settings.MenuTestTags
import de.drehtuer.dinfinity.feature.settings.SettingsTestTags
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.DInfinityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DInfinityAppTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `starts on the roll screen`() {
    compose.setContent {
      DInfinityTheme {
        DInfinityApp()
      }
    }
    compose.onNodeWithTag("screen:${Destination.Roll.route}").assertIsDisplayed()
    compose.onNodeWithText(Destination.Roll.title).assertIsDisplayed()
  }

  @Test
  fun `the menu reaches every screen the app has`() {
    // The graph has had all ten destinations from the first commit; what it
    // did not have was a way in to any of them.
    compose.setContent { DInfinityTheme { DInfinityApp() } }

    compose.onNodeWithTag(MenuTestTags.BUTTON).performClick()

    compose.onNodeWithTag(MenuTestTags.SCREEN).assertIsDisplayed()
    Destination.inTheMenu.forEach { destination ->
      compose.onNodeWithTag(MenuTestTags.entryOf(destination.route)).performScrollTo().assertIsDisplayed()
    }
  }

  @Test
  fun `settings can be reached from the menu and reports the accent chosen there`() {
    val chosen = mutableListOf<AccentColor>()
    compose.setContent {
      DInfinityTheme {
        DInfinityApp(settings = AppSettings(), onAccentSelected = { chosen += it })
      }
    }

    compose.onNodeWithTag(MenuTestTags.BUTTON).performClick()
    compose.onNodeWithTag(MenuTestTags.entryOf(Destination.Settings.route)).performScrollTo().performClick()

    compose.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Amber)).performScrollTo().performClick()
    assertEquals(listOf(AccentColor.Amber), chosen)
  }

  @Test
  fun `choosing a screen takes the menu off the way back`() {
    // A menu you have to press back through twice is a detour: back from what
    // the menu opened goes where the menu was opened from.
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme { DInfinityApp(navController = navigation) }
    }
    compose.onNodeWithTag(MenuTestTags.BUTTON).performClick()

    compose.onNodeWithTag(MenuTestTags.entryOf(Destination.Settings.route)).performScrollTo().performClick()
    compose.waitForIdle()

    assertEquals(Destination.Roll.route, compose.runOnIdle { navigation.previousBackStackEntry?.destination?.route })
  }

  @Test
  fun `choosing the screen you are on does not stack a second copy of it`() {
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme { DInfinityApp(navController = navigation) }
    }

    compose.onNodeWithTag(MenuTestTags.BUTTON).performClick()
    compose.onNodeWithTag(MenuTestTags.entryOf(Destination.Roll.route)).performScrollTo().performClick()
    compose.waitForIdle()

    assertNull(compose.runOnIdle { navigation.previousBackStackEntry })
  }

  @Test
  fun `the outcome graph opens on the formula it was asked for`() {
    val navigation = graphApp()

    compose.runOnIdle { navigation.navigate(graphRoute("2d6 + 1d20", total = null)) }

    compose.onNodeWithTag(GraphTestTags.FORMULA).assertTextContains("2d6 + 1d20")
    compose.onNodeWithTag(GraphTestTags.CHART).assertIsDisplayed()
  }

  @Test
  fun `a formula's punctuation survives the trip to the graph`() {
    // `+` and `/` are what a URI reserves. Unencoded, `3d6 + 4` arrives as
    // `3d6   4` and graphs a different roll — or none at all.
    val navigation = graphApp()

    compose.runOnIdle { navigation.navigate(graphRoute("(2d6 + 4) / 2", total = 5L)) }

    compose.onNodeWithTag(GraphTestTags.FORMULA).assertTextContains("(2d6 + 4) / 2")
    compose.onNodeWithTag(GraphTestTags.ROLLED).assertTextContains("5", substring = true)
  }

  @Test
  fun `a graph reached with no arguments is a graph with no formula`() {
    // Every argument is optional, so the menu can open this screen too
    // (`docs/TODO.md`, Step 4.10).
    val navigation = graphApp()

    compose.runOnIdle { navigation.navigate(Destination.Graph.route) }

    compose.onNodeWithTag(GraphTestTags.EMPTY).assertIsDisplayed()
  }

  /** The app with a graph behind it, and the controller that drives it. */
  private fun graphApp(): NavHostController {
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme {
        DInfinityApp(
          graphMachine = { GraphMachine(DiceCatalog.of(listOf(BuiltinDiceSet.set))) },
          navController = navigation,
        )
      }
    }
    return navigation
  }

  @Test
  fun `the settings screen shows the accent it was given`() {
    compose.setContent {
      DInfinityTheme {
        DInfinityApp(settings = AppSettings(accentColor = AccentColor.Violet))
      }
    }
    compose.onNodeWithTag(MenuTestTags.BUTTON).performClick()
    compose.onNodeWithTag(MenuTestTags.entryOf(Destination.Settings.route)).performScrollTo().performClick()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Violet)).performScrollTo().assertIsSelected()
  }

  @Test
  fun `renders in dark theme too`() {
    compose.setContent {
      DInfinityTheme(darkTheme = true) {
        DInfinityApp()
      }
    }
    compose.onNodeWithTag("screen:${Destination.Roll.route}").assertIsDisplayed()
  }
}
