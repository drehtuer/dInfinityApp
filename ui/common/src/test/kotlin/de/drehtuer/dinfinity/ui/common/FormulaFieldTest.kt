package de.drehtuer.dinfinity.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.NotationErrorCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The formula field three screens share
 * (`design/dInfinity.dc.html`, options 2a, 6f and 9c).
 *
 * It had no tests of its own: the tray, the editor and the graph each tested
 * it through themselves, which meant three tests of *their* use of it and none
 * of the thing itself. A piece of furniture that exists so three screens agree
 * about a mistake should be asserted once, here, rather than three times in
 * three different words.
 */
@RunWith(RobolectricTestRunner::class)
class FormulaFieldTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `what is typed is handed straight out`() {
    val typed = mutableListOf<String>()
    compose.setContent { FormulaField(text = "", onChange = typed::add) }

    compose.onNodeWithTag(FormulaTestTags.FIELD).performTextInput("2d6")

    assertEquals(listOf("2d6"), typed)
  }

  @Test
  fun `the field shows the formula it was given`() {
    compose.setContent { FormulaField(text = "3d6 + 1d20 - 4", onChange = {}) }

    compose.onNodeWithTag(FormulaTestTags.FIELD).assertTextContains("3d6 + 1d20 - 4")
  }

  @Test
  fun `a formula with nothing wrong with it says nothing`() {
    compose.setContent { FormulaField(text = "2d6", onChange = {}) }

    compose.onNodeWithTag(FormulaTestTags.ERROR, useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `a mistake is printed under the formula, with the formula repeated`() {
    // Repeated rather than marked up in the field, because a squiggle that
    // moves under the cursor while somebody types is a squiggle that fights
    // them.
    compose.setContent { FormulaField(text = "2d6 + 1d7", onChange = {}, error = noSuchDie()) }

    compose
      .onNodeWithTag(FormulaTestTags.ERROR, useUnmergedTree = true)
      .assertTextContains("2d6 + 1d7", substring = true)
    compose
      .onNodeWithTag(FormulaTestTags.ERROR, useUnmergedTree = true)
      .assertTextContains("this set has no d7", substring = true)
  }

  @Test
  fun `an obvious correction is one tap, and arrives as text to be typed`() {
    // The fix goes back through the same path a keystroke does, so a suggested
    // formula is validated exactly like one somebody wrote.
    val taken = mutableListOf<String>()
    compose.setContent {
      FormulaField(text = "2d6 + 1d7", onChange = {}, error = noSuchDie(), onSuggestion = taken::add)
    }

    compose.onNodeWithTag(FormulaTestTags.SUGGESTION).performClick()

    assertEquals(listOf("2d6 + 1d8"), taken)
  }

  @Test
  fun `a mistake with no obvious reading is offered no fix`() {
    compose.setContent { FormulaField(text = "2d6 +", onChange = {}, error = unfinished()) }

    compose.onNodeWithTag(FormulaTestTags.ERROR, useUnmergedTree = true).assertIsDisplayed()
    compose.onNodeWithTag(FormulaTestTags.SUGGESTION).assertDoesNotExist()
  }

  @Test
  fun `taking a correction goes to onChange when nobody said otherwise`() {
    // The default: a screen that does not care where a fix comes from gets it
    // through the same callback as a keystroke, which is the honest default.
    val typed = mutableListOf<String>()
    compose.setContent { FormulaField(text = "2d6 + 1d7", onChange = typed::add, error = noSuchDie()) }

    compose.onNodeWithTag(FormulaTestTags.SUGGESTION).performClick()

    assertEquals(listOf("2d6 + 1d8"), typed)
  }

  @Test
  fun `a label is shown when there is one`() {
    compose.setContent { FormulaField(text = "", onChange = {}, label = "Formula") }

    compose.onNodeWithText("Formula").assertIsDisplayed()
  }

  @Test
  fun `a hint is shown while the field is empty`() {
    compose.setContent { FormulaField(text = "", onChange = {}, hint = "8d6 [Fire]") }

    compose.onNodeWithText("8d6 [Fire]").assertIsDisplayed()
  }

  @Test
  fun `a hint is not shown once something has been typed`() {
    compose.setContent { FormulaField(text = "2d6", onChange = {}, hint = "8d6 [Fire]") }

    compose.onNodeWithText("8d6 [Fire]").assertDoesNotExist()
  }

  @Test
  fun `a field with neither label nor hint still draws`() {
    // The graph opens with a formula already in it and wants neither.
    compose.setContent { FormulaField(text = "2d6", onChange = {}) }

    compose.onNodeWithTag(FormulaTestTags.FIELD).assertIsDisplayed()
  }

  @Test
  fun `a formula can be marked wrong without a mistake to point at`() {
    // What a throw the table cannot hold looks like: the formula reads
    // perfectly well, the field is marked, and what is wrong is said where the
    // total goes rather than under the field.
    compose.setContent { FormulaField(text = "500d6", onChange = {}, error = null, wrong = true) }

    compose.onNodeWithTag(FormulaTestTags.ERROR, useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithTag(FormulaTestTags.FIELD).assertIsDisplayed()
  }

  @Test
  fun `the field does not take focus by itself`() {
    // It is on the roll screen from the moment it opens, and a keyboard that
    // appears over the tray uninvited is a keyboard covering the dice.
    compose.setContent { FormulaField(text = "", onChange = {}, hint = "3d6") }

    compose.onNodeWithTag(FormulaTestTags.FIELD).assertIsNotFocused()
  }

  private fun noSuchDie() =
    NotationError(
      code = NotationErrorCode.UnknownDie,
      message = "this set has no d7",
      range = 6..8,
      suggestion = "2d6 + 1d8",
    )

  private fun unfinished() =
    NotationError(
      code = NotationErrorCode.UnexpectedEnd,
      message = "the formula ends after a +",
      range = 4..4,
    )
}
