package de.drehtuer.dinfinity.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.NotationErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The error under the formula (`design/dInfinity.dc.html`, options 6f and 9c).
 *
 * Two halves, tested apart. **Which characters are blamed** is arithmetic and
 * is asserted directly; *that a wave was drawn under them* is pixels, and is
 * the device suite's to look at. The split is the same one the rest of the app
 * draws: the decision is testable, the drawing is not.
 */
@RunWith(RobolectricTestRunner::class)
class FormulaErrorTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the squiggle goes under the characters the parser blamed`() {
    assertEquals(6..8, squiggleOver("2d6 + 1d7 - 4", 6..8))
  }

  @Test
  fun `a formula blamed past its end is squiggled at its last character`() {
    // `3d6 +` stops in the middle of something, so the blame lands one past
    // the end — and a squiggle has to be drawn under a character that exists.
    assertEquals(4..4, squiggleOver("3d6 +", 5..5))
  }

  @Test
  fun `an empty formula has nothing to point at`() {
    assertNull(squiggleOver("", 0..0))
  }

  @Test
  fun `the error line says the formula and then what is wrong with it`() {
    show(NotationError(NotationErrorCode.UnknownDie, "this set has no d7", 6..8))

    compose
      .onNodeWithTag(FormulaTestTags.ERROR)
      .assertTextContains("2d6 + 1d7 - 4 — this set has no d7", substring = true)
  }

  @Test
  fun `a mistake with an obvious reading is offered as a fix`() {
    show(
      NotationError(NotationErrorCode.SpacedDice, "dice are written without spaces in them", 0..4, suggestion = "3d6"),
    )

    compose.onNodeWithTag(FormulaTestTags.SUGGESTION).assertIsDisplayed()
  }

  @Test
  fun `taking the fix hands back the formula, and does not apply it itself`() {
    // The error line decides nothing. It says what was pressed and the machine
    // re-reads the formula like any other keystroke.
    val taken = mutableListOf<String>()
    show(
      error = NotationError(NotationErrorCode.SpacedDice, "dice are written without spaces", 0..4, suggestion = "3d6"),
      onSuggestion = taken::add,
    )

    compose.onNodeWithTag(FormulaTestTags.SUGGESTION).performClick()

    assertEquals(listOf("3d6"), taken)
  }

  @Test
  fun `a mistake with no obvious reading is offered nothing`() {
    // A guess that is wrong costs more than no guess: it is one tap away from
    // replacing a formula somebody meant.
    show(NotationError(NotationErrorCode.UnknownDie, "this set has no d7", 6..8))

    compose.onNodeWithTag(FormulaTestTags.SUGGESTION).assertDoesNotExist()
  }

  @Test
  fun `the wave runs the width of the characters it blames and no further`() {
    val layout = laidOut("2d6 + 1d7 - 4")

    val runs = underlinesOf(layout, under = 6..8, clearance = 0f)

    assertEquals("one line, one run of squiggle", 1, runs.size)
    val run = runs.single()
    assertEquals(layout.getHorizontalPosition(6, true), run.left, TOLERANCE)
    assertEquals(layout.getHorizontalPosition(9, true), run.right, TOLERANCE)
    assertTrue("the squiggle covered the whole line", run.left > layout.getLineLeft(0))
  }

  @Test
  fun `the wave sits above the bottom of the line it belongs to`() {
    val layout = laidOut("2d6 + 1d7 - 4")

    val run = underlinesOf(layout, under = 6..8, clearance = CLEARANCE).single()

    assertEquals(layout.getLineBottom(0) - CLEARANCE, run.bottom, TOLERANCE)
  }

  @Test
  fun `a wave is a wave and not a line`() {
    // Half a period up, half down. A path whose bounds are as tall as the
    // amplitude on both sides of the baseline is the only evidence available
    // without a GPU that this is not a straight underline.
    val path = crestPath(Underline(left = 0f, right = 40f, bottom = 10f), wavelength = 4f, amplitude = 2f)

    val bounds = path.getBounds()
    assertEquals(0f, bounds.left, TOLERANCE)
    assertEquals(40f, bounds.right, TOLERANCE)
    assertTrue("the wave never went above the baseline", bounds.top < 10f)
    assertTrue("the wave never went below the baseline", bounds.bottom > 10f)
  }

  @Test
  fun `the wave stops where the characters do, mid-period or not`() {
    // A period and a half: the last one is cut short rather than overrunning
    // into the character after the one being blamed.
    val path = crestPath(Underline(left = 0f, right = 6f, bottom = 10f), wavelength = 4f, amplitude = 2f)

    assertEquals(6f, path.getBounds().right, TOLERANCE)
  }

  private fun laidOut(text: String): TextLayoutResult {
    lateinit var layout: TextLayoutResult
    compose.setContent {
      Text(text = text, onTextLayout = { layout = it })
    }
    compose.waitForIdle()
    return layout
  }

  private fun show(
    error: NotationError,
    onSuggestion: (String) -> Unit = {},
  ) {
    compose.setContent {
      FormulaError(formula = "2d6 + 1d7 - 4", error = error, onSuggestion = onSuggestion)
    }
  }

  private companion object {
    const val TOLERANCE = 0.5f
    const val CLEARANCE = 3f
  }
}
