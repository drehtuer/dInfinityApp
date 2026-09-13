package de.drehtuer.dinfinity.feature.settings

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
  fun `power saving is off unless it has been turned on`() {
    // A roll that silently stopped rendering because the battery dipped would
    // be a surprise in the middle of a game (decision 16).
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {})
    }

    compose.onNodeWithTag(SettingsTestTags.POWER_SAVING).assertIsOff()
  }

  @Test
  fun `power saving shows as on when it is`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(powerSaving = true), onAccentSelected = {})
    }

    compose.onNodeWithTag(SettingsTestTags.POWER_SAVING).assertIsOn()
  }

  @Test
  fun `turning power saving on reports it, and decides nothing itself`() {
    val asked = mutableListOf<Boolean>()
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {}, onPowerSavingChanged = asked::add)
    }

    compose.onNodeWithTag(SettingsTestTags.POWER_SAVING).performScrollTo().performClick()

    assertEquals(listOf(true), asked)
  }

  @Test
  fun `the whole row is the switch, not just the switch`() {
    // A 56 dp target at the right-hand edge of the screen is a target for a
    // right thumb and nobody else.
    compose.setContent {
      SettingsScreen(settings = AppSettings(powerSaving = true), onAccentSelected = {})
    }

    compose.onNodeWithTag(SettingsTestTags.POWER_SAVING).assertHasClickAction()
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
