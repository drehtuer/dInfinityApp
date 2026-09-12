package de.drehtuer.dinfinity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.DInfinityTheme
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
  fun `renders in dark theme too`() {
    compose.setContent {
      DInfinityTheme(darkTheme = true) {
        DInfinityApp()
      }
    }
    compose.onNodeWithTag("screen:${Destination.Roll.route}").assertIsDisplayed()
  }
}
