package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
    // The editor and the graph put it on screen with everything else, and a
    // keyboard that appears uninvited is a keyboard covering what somebody
    // came to read.
    compose.setContent { FormulaField(text = "", onChange = {}, hint = "3d6") }

    compose.onNodeWithTag(FormulaTestTags.FIELD).assertIsNotFocused()
  }

  @Test
  fun `and does when it has been asked for`() {
    // The tray's editor appears because somebody tapped the formula, so the
    // keyboard should be up without a second tap
    // (`design/dInfinity.dc.html`, option 2a).
    compose.setContent { FormulaField(text = "", onChange = {}, hint = "3d6", takeFocus = true) }

    compose.onNodeWithTag(FormulaTestTags.FIELD).assertIsFocused()
  }

  @Test
  fun `a field with somewhere to submit to says so on the keyboard`() {
    val submitted = mutableListOf<Unit>()
    compose.setContent { FormulaField(text = "3d6", onChange = {}, onSubmit = { submitted += Unit }) }

    compose.onNodeWithTag(FormulaTestTags.FIELD).performImeAction()

    assertEquals(1, submitted.size)
  }

  @Test
  fun `and one with nowhere just puts the keyboard away`() {
    // The editor and the graph have nothing for an action key to do. Pressing
    // it is not an error and does not change the text.
    compose.setContent { FormulaField(text = "3d6", onChange = { error("the text changed") }) }

    compose.onNodeWithTag(FormulaTestTags.FIELD).performImeAction()

    compose.onNodeWithTag(FormulaTestTags.FIELD).assertTextContains("3d6")
  }

  @Test
  fun `a recomposition around it that changes nothing leaves it alone`() {
    // Every parameter is a branch that says "nothing changed, skip it", and
    // three screens share this one (`docs/TODO.md`, Coverage).
    var tick by mutableStateOf(0)
    compose.setContent {
      Column {
        Text("tick $tick")
        FormulaField(text = "3d6", onChange = {}, hint = "3d6", error = noSuchDie())
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(FormulaTestTags.FIELD).assertIsDisplayed()
    compose.onNodeWithTag(FormulaTestTags.ERROR, useUnmergedTree = true).assertIsDisplayed()
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
