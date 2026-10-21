package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The dice pull-down at the top of the table ([DiceMenu]).
 *
 * Its own file rather than another corner of `RollScreenTest`, which the rest
 * of the screen already fills: the pull-down is a control of its own, like
 * the picker row inside it, and `PickerRowTest` is next door.
 *
 * What it checks is that the dice are *put away* — that is the whole point of
 * the thing. With the straight-down table view, a plate over the tray is a
 * place a die can land and not be seen
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 */
@RunWith(RobolectricTestRunner::class)
class DiceMenuTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the dice are put away until the pull-down is opened`() {
    // The picker was the third of four plates along the bottom edge, which on
    // a phone with the straight-down table view covered the felt a die may
    // well have landed on (`docs/physics-and-rendering.md`, "What is drawn
    // over the table").
    show()

    compose.onNodeWithTag(RollTestTags.DICE_MENU).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.PICKER).assertDoesNotExist()
  }

  @Test
  fun `opening it brings the dice out, and closing it puts them back`() {
    show()

    openDice()
    compose.onNodeWithTag(RollTestTags.PICKER).assertIsDisplayed()

    compose.onNodeWithTag(RollTestTags.DICE_MENU).performClick()
    compose.onNodeWithTag(RollTestTags.PICKER).assertDoesNotExist()
  }

  @Test
  fun `the shut menu still says how many dice are in the throw`() {
    // Otherwise putting the dice away would hide the one thing tapping them
    // did, and a player would have to open it again to check.
    show()
    typeFormula("4d6 + 1d20")

    compose.onNodeWithTag(RollTestTags.DICE_MENU_COUNT, useUnmergedTree = true).assertTextEquals("5")
  }

  @Test
  fun `opening the dice puts the formula editor away, and the other way round`() {
    // One comes down and one comes in from the side, and two open at once is
    // the whole top half of the table covered — which is the thing this
    // layout exists to stop.
    show()

    typeFormula("1d20")
    compose.onNodeWithTag(RollTestTags.FORMULA).assertIsDisplayed()
    openDice()

    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.PICKER).assertIsDisplayed()

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()

    compose.onNodeWithTag(RollTestTags.PICKER).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.FORMULA).assertIsDisplayed()
  }

  /** Opens the pull-down the way a player does, through its head. */
  private fun openDice() {
    compose.onNodeWithTag(RollTestTags.DICE_MENU).performClick()
  }

  /** Types a formula the way a player does: bring the drawer in, then type. */
  private fun typeFormula(text: String) {
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(text)
  }

  private fun show() {
    compose.setContent { RollScreen(presenter = rollPresenter(DirectTray(), LandingRolls(mapOf(0 to 0)))) }
  }
}
