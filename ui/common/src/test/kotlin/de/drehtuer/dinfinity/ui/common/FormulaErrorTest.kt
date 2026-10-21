package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.NotationErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The error under the formula (`design/dInfinity.dc.html`, options 6f and 9c).
 *
 * Two halves, tested apart. **Which characters are blamed** is arithmetic and
 * is asserted directly; *that a wave was drawn under them* is pixels, and is
 * read once off a rasterised canvas — whether it *looks* like a wave is still
 * the device suite's to say.
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

  @Test
  // Real text measurement, so that the line actually breaks where a phone
  // would break it.
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `a blame that runs over a line break is a wave on each line, not a strike through the middle`() {
    // The squiggle follows the glyphs when the line wraps: the first run
    // starts at the first blamed character and goes to the end of its line,
    // the middle ones span their lines, and the last stops after the last
    // blamed character.
    lateinit var layout: TextLayoutResult
    compose.setContent {
      Text(
        text = "2d6 + 1d7 - 4 + 3d8 + 2d10 + 1d12 - 1d4",
        onTextLayout = { layout = it },
        modifier = Modifier.width(NARROW),
      )
    }
    compose.waitForIdle()
    assertTrue("the text did not wrap onto three lines: ${layout.lineCount}", layout.lineCount >= 3)
    val first = 1
    val last = layout.getLineStart(2)

    val runs = underlinesOf(layout, under = first..last, clearance = 0f)

    assertEquals("one run for each line the blame covers", 3, runs.size)
    assertEquals(layout.getHorizontalPosition(first, true), runs[0].left, TOLERANCE)
    assertEquals(layout.getLineRight(0), runs[0].right, TOLERANCE)
    assertEquals(layout.getLineLeft(1), runs[1].left, TOLERANCE)
    assertEquals(layout.getLineRight(1), runs[1].right, TOLERANCE)
    assertEquals(layout.getLineLeft(2), runs[2].left, TOLERANCE)
    assertEquals(layout.getHorizontalPosition(last + 1, true), runs[2].right, TOLERANCE)
    assertEquals("the runs are not on lines of their own", 3, runs.map { it.bottom }.distinct().size)
  }

  /**
   * The wave, on a real canvas: there is ink of the accent's colour under the
   * blamed `1d7` and nowhere else on the line. The formula and the complaint
   * are printed in the text colour, so anything in the accent is the wave.
   */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Config(qualifiers = "xxhdpi")
  fun `the wave is drawn under the characters it blames and nowhere else`() {
    compose.setContent {
      MaterialTheme(colorScheme = lightColorScheme(primary = ACCENT, onBackground = Color.Black)) {
        FormulaError(
          formula = "2d6 + 1d7 - 4",
          error = NotationError(NotationErrorCode.UnknownDie, "this set has no d7", 6..8),
        )
      }
    }
    val node = compose.onNodeWithTag(FormulaTestTags.ERROR)
    val layouts = mutableListOf<TextLayoutResult>()
    node
      .fetchSemanticsNode()
      .config[SemanticsActions.GetTextLayoutResult]
      .action
      ?.invoke(layouts)
    val from = layouts.single().getHorizontalPosition(6, true)
    val to = layouts.single().getHorizontalPosition(9, true)

    val drawn = node.captureToImage().toPixelMap()
    val waved = (0 until drawn.width).filter { x -> (0 until drawn.height).any { y -> drawn[x, y].isNear(ACCENT) } }

    assertTrue("no wave was drawn", waved.isNotEmpty())
    val slack = with(compose.density) { INK_SLACK.toPx() }
    assertTrue("the wave starts before the blamed characters", waved.first() >= from - slack)
    assertTrue("the wave runs past the blamed characters", waved.last() <= to + slack)
  }

  /** Close enough to [colour] to be it under anti-aliasing, and neither the text nor the wash. */
  private fun Color.isNear(colour: Color): Boolean =
    abs(red - colour.red) + abs(green - colour.green) + abs(blue - colour.blue) < NEAR

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

    /** Narrow enough that the long formula above wraps onto three lines. */
    val NARROW = 60.dp

    /** The round cap and the anti-aliasing either side of the wave's ends. */
    val INK_SLACK = 2.dp

    val ACCENT = Color(0xFF0F7A50)
    const val NEAR = 0.15f
  }
}
