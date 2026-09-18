package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import de.drehtuer.dinfinity.core.model.AccentChoice
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.designer.Ink
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
      // Scrolled to, like every other row on this screen: six swatches on a
      // wrapping row do not all fit above the fold of the phone the test
      // pretends to be.
      compose.onNodeWithTag(SettingsTestTags.accentSwatch(accent)).performScrollTo().assertIsDisplayed()
    }
  }

  @Test
  fun `the current accent is the selected one`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(accentColor = AccentColor.Pine), onAccentSelected = {})
    }
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Pine)).assertIsSelected()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.ModernistRed)).assertIsNotSelected()
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
    val chosen = mutableListOf<AccentChoice>()
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = { chosen += it })
    }
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Pine)).performScrollTo().performClick()
    assertEquals(listOf(AccentColor.Pine), chosen)
  }

  /**
   * The screen is stateless, so tapping must not change what it shows until
   * the caller feeds the new value back. Getting this wrong produces a picker
   * that looks like it worked while nothing was saved.
   */
  @Test
  fun `the screen does not select on its own`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(accentColor = AccentColor.ModernistRed), onAccentSelected = {})
    }
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Cobalt)).performScrollTo().performClick()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.Cobalt)).assertIsNotSelected()
    compose.onNodeWithTag(SettingsTestTags.accentSwatch(AccentColor.ModernistRed)).assertIsSelected()
  }

  /**
   * Four across, not five (`docs/architecture.md`, "Settings").
   *
   * Six swatches at the old size wrapped 5 + 1 and orphaned one; seven — the
   * six presets and the player's own — come out 4 + 3 on a four-column grid.
   * Measured rather than eyeballed: the fifth swatch is under the first, not
   * beside the fourth.
   */
  @Test
  fun `the swatches are four across`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {})
    }

    val first = bounds(SettingsTestTags.accentSwatch(AccentColor.LightBlue))
    val fourth = bounds(SettingsTestTags.accentSwatch(AccentColor.Cobalt))
    val fifth = bounds(SettingsTestTags.accentSwatch(AccentColor.Pine))
    assertTrue("the fourth swatch is not on the first row", fourth.top == first.top)
    assertTrue("the fourth swatch is not beside the first", fourth.left > first.left)
    assertEquals("the fifth swatch is not under the first", first.left, fifth.left)
    assertTrue("the fifth swatch is not on a second row", fifth.top > first.top)
  }

  /**
   * The chip is the 44 dp the design draws and the target around it is
   * Android's 48 dp floor, which is the one thing a drawing cannot say.
   */
  @Test
  fun `a swatch is big enough to hit`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {})
    }

    AccentColor.entries.forEach { accent ->
      compose.onNodeWithTag(SettingsTestTags.accentSwatch(accent)).assertHeightIsAtLeast(TOUCH_TARGET)
    }
    compose.onNodeWithTag(SettingsTestTags.ACCENT_CUSTOM).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  /**
   * The hex says what was **chosen**, not what will be painted.
   *
   * Light blue is 2.41:1 on paper and the theme deepens it before anything is
   * drawn in it, but a swatch that answers a tap with a different colour is a
   * control nobody can aim. The clamp is explained in the sentence above the
   * grid instead (`docs/architecture.md`, "Settings").
   */
  @Test
  fun `the hex is the colour that was chosen rather than the colour that is painted`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(accentColor = AccentColor.LightBlue), onAccentSelected = {})
    }

    compose.onNodeWithTag(SettingsTestTags.ACCENT_HEX).assertTextContains("#38A8DC")
  }

  @Test
  fun `a colour of your own shows its own hex and holds the seventh swatch`() {
    compose.setContent {
      SettingsScreen(
        settings = AppSettings(accentColor = AccentChoice.Custom(0xFF2B5AA8.toInt())),
        onAccentSelected = {},
      )
    }

    compose.onNodeWithTag(SettingsTestTags.ACCENT_HEX).assertTextContains("#2B5AA8")
    compose.onNodeWithTag(SettingsTestTags.ACCENT_CUSTOM).assertIsSelected()
    AccentColor.entries.forEach { accent ->
      compose.onNodeWithTag(SettingsTestTags.accentSwatch(accent)).assertIsNotSelected()
    }
  }

  @Test
  fun `the custom swatch opens a picker, and what it picks is reported once`() {
    val chosen = mutableListOf<AccentChoice>()
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = { chosen += it })
    }

    compose.onNodeWithTag(SettingsTestTags.ACCENT_PICKER).assertDoesNotExist()
    compose.onNodeWithTag(SettingsTestTags.ACCENT_CUSTOM).performScrollTo().performClick()
    compose.onNodeWithTag(SettingsTestTags.ACCENT_PICKER).assertExists()
    slide(SettingsTestTags.ACCENT_HUE, 150f)
    slide(SettingsTestTags.ACCENT_DEPTH, 0.6f)
    slide(SettingsTestTags.ACCENT_BRIGHTNESS, 0.8f)
    compose.onNodeWithTag(SettingsTestTags.ACCENT_PICKER_USE).performClick()

    assertEquals(listOf(AccentChoice.Custom(Ink.argb(150f, 0.6f, 0.8f))), chosen)
    compose.onNodeWithTag(SettingsTestTags.ACCENT_PICKER).assertDoesNotExist()
  }

  @Test
  fun `a picker that is cancelled changes nothing`() {
    val chosen = mutableListOf<AccentChoice>()
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = { chosen += it })
    }

    compose.onNodeWithTag(SettingsTestTags.ACCENT_CUSTOM).performScrollTo().performClick()
    slide(SettingsTestTags.ACCENT_HUE, 300f)
    compose.onNodeWithTag(SettingsTestTags.ACCENT_PICKER_CANCEL).performClick()

    assertTrue("a cancelled picker still chose something", chosen.isEmpty())
    compose.onNodeWithTag(SettingsTestTags.ACCENT_PICKER).assertDoesNotExist()
  }

  @Test
  fun `each accent has its own label`() {
    val labels = AccentColor.entries.map { it.labelRes() }
    assertEquals("two accents share a label resource", labels.size, labels.toSet().size)
  }

  @Test
  fun `the three appearances are offered, and the chosen one is chosen`() {
    compose.setContent {
      SettingsScreen(
        settings = AppSettings(appearance = Appearance.Dark),
        onAccentSelected = {},
      )
    }

    Appearance.entries.forEach { appearance ->
      compose.onNodeWithTag(SettingsTestTags.appearanceOf(appearance)).performScrollTo().assertExists()
    }
    compose.onNodeWithTag(SettingsTestTags.appearanceOf(Appearance.Dark)).assertIsSelected()
  }

  @Test
  fun `choosing an appearance says which`() {
    val chosen = mutableListOf<Appearance>()
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {}, onAppearanceSelected = chosen::add)
    }

    compose.onNodeWithTag(SettingsTestTags.appearanceOf(Appearance.Light)).performScrollTo().performClick()

    assertEquals(listOf(Appearance.Light), chosen)
  }

  @Test
  fun `shake is a switch, and it says which way it was moved`() {
    val changed = mutableListOf<Boolean>()
    compose.setContent {
      SettingsScreen(
        settings = AppSettings(shakeToRoll = true),
        onAccentSelected = {},
        onShakeChanged = changed::add,
      )
    }

    compose.onNodeWithTag(SettingsTestTags.SHAKE).performScrollTo().performClick()

    assertEquals(listOf(false), changed)
  }

  @Test
  fun `haptics and sound are two switches, each saying which way it was moved`() {
    val haptics = mutableListOf<Boolean>()
    val sound = mutableListOf<Boolean>()
    compose.setContent {
      SettingsScreen(
        settings = AppSettings(haptics = true, sound = false),
        onAccentSelected = {},
        onHapticsChanged = haptics::add,
        onSoundChanged = sound::add,
      )
    }

    compose
      .onNodeWithTag(SettingsTestTags.HAPTICS)
      .performScrollTo()
      .assertIsOn()
      .performClick()
    compose
      .onNodeWithTag(SettingsTestTags.SOUND)
      .performScrollTo()
      .assertIsOff()
      .performClick()

    assertEquals(listOf(false), haptics)
    assertEquals(listOf(true), sound)
  }

  @Test
  fun `moving one of the two leaves the other alone`() {
    val haptics = mutableListOf<Boolean>()
    val sound = mutableListOf<Boolean>()
    compose.setContent {
      SettingsScreen(
        settings = AppSettings(haptics = true, sound = true),
        onAccentSelected = {},
        onHapticsChanged = haptics::add,
        onSoundChanged = sound::add,
      )
    }

    compose.onNodeWithTag(SettingsTestTags.SOUND).performScrollTo().performClick()

    assertTrue("moving sound moved the haptics too", haptics.isEmpty())
    assertEquals(listOf(false), sound)
  }

  @Test
  fun `a recomposition around the screen that changes nothing leaves the switches where they were`() {
    // The other side of every skip branch the screen's parameters carry: drawn
    // once, something beside it changes, and the rows have to come back saying
    // the same thing rather than vanishing or flipping.
    var tick by mutableStateOf(0)
    compose.setContent {
      Column {
        Text("tick $tick")
        SettingsScreen(settings = AppSettings(haptics = true, sound = false), onAccentSelected = {})
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(SettingsTestTags.HAPTICS).performScrollTo().assertIsOn()
    compose.onNodeWithTag(SettingsTestTags.SOUND).performScrollTo().assertIsOff()
    compose.onNodeWithTag(SettingsTestTags.SHAKE).performScrollTo().assertIsOn()
  }

  @Test
  fun `the debugging tools are off, and the switch says so`() {
    // Off on every install, and the default of the type rather than of the
    // screen (`docs/physics-and-rendering.md`, "Debug tooling").
    compose.setContent { SettingsScreen(settings = AppSettings(), onAccentSelected = {}) }

    compose.onNodeWithTag(SettingsTestTags.DEVELOPER).performScrollTo().assertIsOff()
  }

  @Test
  fun `turning the debugging tools on says so once`() {
    val changed = mutableListOf<Boolean>()
    compose.setContent {
      SettingsScreen(
        settings = AppSettings(),
        onAccentSelected = {},
        onDeveloperToolsChanged = { changed += it },
      )
    }

    compose.onNodeWithTag(SettingsTestTags.DEVELOPER).performScrollTo().performClick()

    assertEquals(listOf(true), changed)
  }

  @Test
  fun `the three roundings are offered, and the chosen one is chosen`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(rounding = Rounding.Up), onAccentSelected = {})
    }

    Rounding.entries.forEach { rounding ->
      compose.onNodeWithTag(SettingsTestTags.roundingOf(rounding)).performScrollTo().assertExists()
    }
    compose.onNodeWithTag(SettingsTestTags.roundingOf(Rounding.Up)).assertIsSelected()
  }

  @Test
  fun `choosing a rounding says which`() {
    val chosen = mutableListOf<Rounding>()
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {}, onRoundingSelected = chosen::add)
    }

    compose.onNodeWithTag(SettingsTestTags.roundingOf(Rounding.Nearest)).performScrollTo().performClick()

    assertEquals(listOf(Rounding.Nearest), chosen)
  }

  @Test
  fun `both table views are offered, and the chosen one is chosen`() {
    compose.setContent {
      SettingsScreen(settings = AppSettings(tableView = TableView.Angled), onAccentSelected = {})
    }

    TableView.entries.forEach { view ->
      compose.onNodeWithTag(SettingsTestTags.tableViewOf(view)).performScrollTo().assertExists()
    }
    compose.onNodeWithTag(SettingsTestTags.tableViewOf(TableView.Angled)).assertIsSelected()
  }

  @Test
  fun `a fresh install shows the table straight down`() {
    // The default the design asks for, read off the row rather than off the
    // model: this is the screen saying it.
    compose.setContent { SettingsScreen(settings = AppSettings(), onAccentSelected = {}) }

    compose
      .onNodeWithTag(SettingsTestTags.tableViewOf(TableView.StraightDown))
      .performScrollTo()
      .assertIsSelected()
  }

  @Test
  fun `choosing a table view says which`() {
    val chosen = mutableListOf<TableView>()
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {}, onTableViewSelected = chosen::add)
    }

    compose.onNodeWithTag(SettingsTestTags.tableViewOf(TableView.Angled)).performScrollTo().performClick()

    assertEquals(listOf(TableView.Angled), chosen)
  }

  @Test
  fun `the version on screen is the one it was handed`() {
    // Read from the installed package rather than a generated constant, so it
    // is what is on the phone rather than what a build thought it was making.
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {}, version = "1.2.0")
    }

    compose
      .onNodeWithTag(SettingsTestTags.VERSION)
      .performScrollTo()
      .assertTextContains("1.2.0", substring = true)
  }

  @Test
  fun `the repository link asks to be opened rather than opening anything itself`() {
    var asked = false
    compose.setContent {
      SettingsScreen(settings = AppSettings(), onAccentSelected = {}, onRepository = { asked = true })
    }

    compose.onNodeWithTag(SettingsTestTags.REPOSITORY).performScrollTo().performClick()

    assertTrue(asked)
  }

  /** One of the picker's sliders, moved to [to]. */
  private fun slide(
    tag: String,
    to: Float,
  ) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(to) }

  /** Where a swatch sits, so "four across" is measured rather than assumed. */
  private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
}
