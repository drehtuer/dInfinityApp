package de.drehtuer.dinfinity

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import de.drehtuer.dinfinity.feature.settings.MenuTestTags
import de.drehtuer.dinfinity.feature.settings.NotationTestTags
import de.drehtuer.dinfinity.feature.settings.SettingsTestTags
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.DInfinityTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every screen starts below the status bar, and exactly one does not
 * (`docs/architecture.md`, "One safe area, applied once").
 *
 * **This is the test the bug went past.** Each screen used to inset itself,
 * Settings never did, and nothing noticed until a Pixel 10a printed its title
 * under the clock — because every screen's own test drew that screen in a
 * window with no status bar at all, where inset and not-inset look the same.
 * So the inset is handed in here, the way a phone hands one in, and the
 * question asked of the *graph* rather than of a screen: does what the graph
 * drew begin below it.
 *
 * Nothing here is in pixels. The inset is a `Dp` and so is the answer.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenInsetsTest {
  @get:Rule
  val compose = createComposeRule()

  /** A Pixel 10a's status bar, which is what found this. */
  private val statusBar = 58.dp

  private lateinit var navController: NavHostController

  /** The app, with a status bar over it, opened on the tray as it always is. */
  private fun start() {
    compose.setContent {
      navController = rememberNavController()
      DInfinityTheme {
        // No presenters: every destination draws its placeholder, which is the
        // screen as far as the graph is concerned and carries the same tag.
        DInfinityApp(
          insets = WindowInsets(top = statusBar),
          navController = navController,
        )
      }
    }
  }

  private fun open(destination: Destination) {
    if (destination == Destination.home) return
    compose.runOnIdle { navController.navigate(destination.route) }
    compose.waitForIdle()
  }

  /**
   * What the screen at [destination] is tagged with.
   *
   * Three destinations are real screens even with no presenters — the menu,
   * settings and the notation reference need nothing built — and each of those
   * carries its own module's tag. Every other route draws the placeholder,
   * which carries the graph's.
   */
  private fun tagOf(destination: Destination) =
    when (destination) {
      Destination.Menu -> MenuTestTags.SCREEN
      Destination.Settings -> SettingsTestTags.SCREEN
      Destination.Notation -> NotationTestTags.SCREEN
      else -> "screen:${destination.route}"
    }

  private fun topOf(destination: Destination) = compose.onNodeWithTag(tagOf(destination)).getUnclippedBoundsInRoot().top

  @Test
  fun `a screen the menu opens starts below the status bar`() {
    start()

    open(Destination.Settings)

    assertEquals(statusBar, topOf(Destination.Settings))
  }

  @Test
  fun `and so does every other screen the graph draws`() {
    // One at a time, all of them: the bug was one screen out of fourteen, and
    // a test that checks the one that was fixed is a test that will miss the
    // fifteenth.
    start()

    Destination.entries.filterNot { it.fullBleed }.forEach { destination ->
      open(destination)
      assertEquals("${destination.route} starts under the status bar", statusBar, topOf(destination))
    }
  }

  @Test
  fun `the tray is the one screen drawn to the edge`() {
    // One full-bleed table with everything floating on it: felt inset by 58 dp
    // would be a grey stripe across the top of the screen. Its controls inset
    // themselves instead.
    start()

    assertEquals(0.dp, topOf(Destination.Roll))
  }
}
