package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import de.drehtuer.dinfinity.ui.common.FormulaTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The × in the formula drawer (`docs/dice-notation.md`, "Emptying the field").
 *
 * The owner's note from the Pixel 10a asked for a way to delete the formula in
 * one tap. What the cross itself looks like and says is `FormulaFieldTest`'s;
 * what is asked here is the thing that would go wrong on the tray: that it is
 * **not a second way to empty a formula**. It has to land on the same
 * `clear the field` edge of the state machine as deleting the text by hand,
 * dice off the board and all.
 */
@RunWith(RobolectricTestRunner::class)
class ClearTheFormulaTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the drawer's field carries the cross once something is typed`() {
    show()
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()

    compose.onNodeWithTag(FormulaTestTags.CLEAR).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("3d6")
    compose.onNodeWithTag(FormulaTestTags.CLEAR).assertIsDisplayed()
  }

  @Test
  fun `the cross empties the formula the way deleting it by hand does`() {
    // One path to an empty formula, not two: the presenter hears the same `""`
    // the last backspace would, so the waiting dice come off the board and
    // the roll is back to having nothing to throw.
    val presenter = show()
    val tray = presenter.tray as DirectTray
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("3d6")
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextReplacement("")
    val byHand = presenter.state to tray.boards.last()

    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("3d6")
    assertEquals("the dice were not put on the table", 3, tray.boards.last().size)
    compose.onNodeWithTag(FormulaTestTags.CLEAR).performClick()

    assertEquals(RollState.Empty, byHand.first)
    assertEquals("", presenter.text)
    assertEquals("the cross did something deleting by hand does not", byHand, presenter.state to tray.boards.last())
  }

  @Test
  fun `and the drawer stays open with the cursor in it for the next formula`() {
    val presenter = show()
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("8d6")

    compose.onNodeWithTag(FormulaTestTags.CLEAR).performClick()

    compose.onNodeWithTag(RollTestTags.FORMULA_DRAWER).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.FORMULA).assertIsFocused()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("1d20")
    assertEquals("1d20", presenter.text)
  }

  private fun show(): RollPresenter {
    val presenter = rollPresenter(DirectTray(), LandingRolls(mapOf(0 to 0)))
    compose.setContent { RollScreen(presenter = presenter) }
    return presenter
  }
}
