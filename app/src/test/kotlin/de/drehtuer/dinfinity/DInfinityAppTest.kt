package de.drehtuer.dinfinity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.feature.settings.SettingsTestTags
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.DInfinityTheme
import org.junit.Assert.assertEquals
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
  fun `settings can be reached and reports the accent chosen there`() {
    val chosen = mutableListOf<AccentColor>()
    compose.setContent {
      DInfinityTheme {
        DInfinityApp(settings = AppSettings(), onAccentSelected = { chosen += it })
      }
    }
    compose.onNodeWithTag("placeholder:open-settings").performClick()
    compose.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Amber)).performClick()
    assertEquals(listOf(AccentColor.Amber), chosen)
  }

  @Test
  fun `the settings screen shows the accent it was given`() {
    compose.setContent {
      DInfinityTheme {
        DInfinityApp(settings = AppSettings(accentColor = AccentColor.Violet))
      }
    }
    compose.onNodeWithTag("placeholder:open-settings").performClick()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Violet)).assertIsSelected()
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
