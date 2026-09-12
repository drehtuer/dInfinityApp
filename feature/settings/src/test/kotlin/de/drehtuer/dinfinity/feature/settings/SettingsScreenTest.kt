package de.drehtuer.dinfinity.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `every accent is offered`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {})
    }
    compose.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()
    AccentColor.entries.forEach { accent ->
      compose.onNodeWithTag(SettingsTestTags.accentSwatch(accent)).assertIsDisplayed()
    }
  }

  @Test
  fun `the current accent is the selected one`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(accentColor = AccentColor.Moss), onAccentSelected = {})
    }
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Moss)).assertIsSelected()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Vermilion)).assertIsNotSelected()
  }

  @Test
  fun `choosing an accent reports it once`() {
    val chosen = mutableListOf<AccentColor>()
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = { chosen += it })
    }
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Sky)).performClick()
    assertEquals(listOf(AccentColor.Sky), chosen)
  }

  /**
   * The screen is stateless, so tapping must not change what it shows until
   * the caller feeds the new value back. Getting this wrong produces a picker
   * that looks like it worked while nothing was saved.
   */
  @Test
  fun `the screen does not select on its own`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(accentColor = AccentColor.Vermilion), onAccentSelected = {})
    }
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Violet)).performClick()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Violet)).assertIsNotSelected()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Vermilion)).assertIsSelected()
  }

  @Test
  fun `each accent has its own label`() {
    val labels = AccentColor.entries.map { it.labelRes() }
    assertEquals("two accents share a label resource", labels.size, labels.toSet().size)
  }
}
