package de.drehtuer.dinfinity

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.DInfinityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A chevron climbs; it does not retrace (`docs/architecture.md`,
 * "Navigation").
 *
 * The interesting cases are the ones where climbing and going back are
 * different — the same screen reached two ways — because that is where a
 * `popBackStack` would look right and be wrong.
 */
@RunWith(RobolectricTestRunner::class)
class ClimbingTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var navController: NavHostController

  private fun start() {
    compose.setContent {
      navController = rememberNavController()
      DInfinityTheme { DInfinityApp(navController = navController) }
    }
  }

  private fun go(vararg route: Destination) {
    route.forEach { destination ->
      compose.runOnIdle { navController.navigate(destination.route) }
      compose.waitForIdle()
    }
  }

  private fun climbFrom(destination: Destination) {
    compose.runOnIdle { navController.climb(destination) }
    compose.waitForIdle()
  }

  private fun here() = navController.currentBackStackEntry?.destination?.route

  private fun under() = navController.previousBackStackEntry?.destination?.route

  @Test
  fun `a screen the menu lists climbs to the menu, with the tray under it`() {
    start()
    go(Destination.Menu, Destination.Settings)

    climbFrom(Destination.Settings)

    assertEquals(Destination.Menu.pattern, here())
    assertEquals(Destination.home.pattern, under())
  }

  @Test
  fun `the menu climbs to the tray, and leaves nothing under it`() {
    start()
    go(Destination.Menu)

    climbFrom(Destination.Menu)

    assertEquals(Destination.home.pattern, here())
    assertNull("the tray is where the app opens; nothing is under it", under())
  }

  @Test
  fun `a screen about one of something climbs to the list of them`() {
    start()
    go(Destination.DiceSets, Destination.SetDetail)

    climbFrom(Destination.SetDetail)

    assertEquals(Destination.DiceSets.pattern, here())
    assertEquals(Destination.home.pattern, under())
  }

  @Test
  fun `the editor lands on the saved rolls however it was opened`() {
    // Opened straight from the tray's strip, which is the case a
    // `popBackStack` gets wrong: it would go back to the tray, and the same
    // chevron pressed on the same screen would mean two different things.
    start()
    go(Destination.SavedRollEditor)

    climbFrom(Destination.SavedRollEditor)

    assertEquals(Destination.SavedRolls.pattern, here())
    assertEquals(Destination.home.pattern, under())
  }

  @Test
  fun `climbing twice from a detail screen reaches the tray`() {
    start()
    go(Destination.SavedRollEditor)

    climbFrom(Destination.SavedRollEditor)
    climbFrom(Destination.SavedRolls)
    climbFrom(Destination.Menu)

    assertEquals(Destination.home.pattern, here())
    assertNull(under())
  }

  @Test
  fun `there is nowhere up from the tray`() {
    // Leaving is system back's, twice ([TwoStageBack]) — not a chevron's.
    start()

    climbFrom(Destination.Roll)

    assertEquals(Destination.home.pattern, here())
    assertNull(under())
  }
}
