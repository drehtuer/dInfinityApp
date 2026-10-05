package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.NotationErrorCode
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The formula behind a tab at the side (`design/dInfinity.dc.html`, option
 * 2a; `docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * The thing worth asserting is what the second device session asked for and
 * what it must not cost: the formula is **not on the table** until it is
 * asked for, and yet a formula that does not read still says so from outside
 * — otherwise a mistake is behind a door nobody has a reason to open.
 */
@RunWith(RobolectricTestRunner::class)
class FormulaDrawerTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `shut, the formula is not drawn at all`() {
    show(text = "3d6 + 4")

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.FORMULA_DRAWER).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()
  }

  @Test
  fun `the tab brings it in and takes it away again`() {
    show(text = "3d6 + 4")

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()

    compose.onNodeWithTag(RollTestTags.FORMULA_DRAWER).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.FORMULA).assertIsDisplayed()

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.waitForIdle()

    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()
  }

  @Test
  fun `what is typed reaches the caller on every keystroke`() {
    val typed = mutableListOf<String>()
    compose.setContent {
      FormulaDrawer(text = "", onChange = { typed += it }, open = true, onOpen = {})
    }

    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("2")

    assertEquals("the field did not report what was typed", listOf("2"), typed)
  }

  @Test
  fun `the tab says what is behind it, and that it is shut`() {
    // A chevron is a drawing and says none of it
    // (`docs/architecture.md`, "Accessibility").
    show(text = "3d6")

    compose
      .onNodeWithTag(RollTestTags.FORMULA_TAB)
      .assertContentDescriptionEquals("Formula")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Closed"))
  }

  @Test
  fun `and says so when the formula does not read`() {
    // The tab goes red, which is the badge `9c` asks for — and red is not
    // something a screen reader can say, so the state description says it.
    show(text = "3d6 +", error = unfinished())

    compose
      .onNodeWithTag(RollTestTags.FORMULA_TAB)
      .assert(
        SemanticsMatcher.expectValue(
          SemanticsProperties.StateDescription,
          "Closed, and the formula does not read",
        ),
      )
  }

  /**
   * The other half of the badge: a sighted player sees it as the tab turning
   * the accent, word and chevron both. The theme maps `error` onto the
   * system's one red, so that is the ink it must be drawn in — and only when
   * there is something wrong, or it is a tab that is always alarmed.
   */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `and is drawn in the accent when it does not read, and in the ink when it does`() {
    compose.setContent {
      MaterialTheme(colorScheme = lightColorScheme(error = ACCENT, onBackground = INK)) {
        Column {
          FormulaDrawer(text = "3d6 +", onChange = {}, open = false, onOpen = {}, error = unfinished())
          FormulaDrawer(text = "3d6", onChange = {}, open = false, onOpen = {})
        }
      }
    }
    val (wrong, right) =
      compose
        .onAllNodesWithTag(RollTestTags.FORMULA_TAB)
        .fetchSemanticsNodes()
        .indices
        .map { compose.onAllNodesWithTag(RollTestTags.FORMULA_TAB)[it].captureToImage().toPixelMap() }

    assertTrue("a formula that does not read left the tab in the ink", wrong.has(ACCENT))
    assertFalse("a formula that reads put the accent on the tab", right.has(ACCENT))
    assertTrue("a formula that reads left the tab with no ink on it", right.has(INK))
  }

  @Test
  fun `open, it says it is open`() {
    show(text = "3d6", open = true)

    compose
      .onNodeWithTag(RollTestTags.FORMULA_TAB)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Open"))
  }

  @Test
  fun `what is wrong is said inside, where it can be acted on`() {
    show(text = "3d6 +", error = unfinished(), open = true)

    compose.onNodeWithTag(RollTestTags.INVALID).assertIsDisplayed()
  }

  @Test
  fun `a drawer wired to nothing still draws and still works`() {
    // No error, no `wrong` and no `onDone`: the defaults are the branch
    // every other test here takes past.
    compose.setContent { FormulaDrawer(text = "3d6", onChange = {}, open = true, onOpen = {}) }

    compose.onNodeWithTag(RollTestTags.FORMULA).performImeAction()

    compose.onNodeWithTag(RollTestTags.FORMULA).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.INVALID).assertDoesNotExist()
  }

  @Test
  fun `the tab is worth pressing`() {
    show(text = "3d6")

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `the action key is offered to the caller rather than acted on here`() {
    // Closing is the screen's, not the drawer's: the screen is what knows the
    // drawer is open.
    var done = 0
    compose.setContent {
      FormulaDrawer(text = "1d20", onChange = {}, open = true, onOpen = {}, onDone = { done++ })
    }

    compose.onNodeWithTag(RollTestTags.FORMULA).performImeAction()

    assertEquals("the action key did not reach the screen", 1, done)
  }

  private fun PixelMap.has(colour: Color): Boolean =
    (0 until height).any { y -> (0 until width).any { x -> this[x, y] == colour } }

  /** `3d6 +` — the commonest way to get a squiggle, and the one 9c draws. */
  private fun unfinished() =
    NotationError(code = NotationErrorCode.UnexpectedEnd, message = "Nothing after the +", range = 4..5)

  private fun show(
    text: String,
    error: NotationError? = null,
    open: Boolean = false,
  ) {
    compose.setContent {
      var shown by remember { mutableStateOf(open) }
      FormulaDrawer(
        text = text,
        onChange = {},
        open = shown,
        onOpen = { shown = it },
        error = error,
      )
    }
    compose.waitForIdle()
  }

  private companion object {
    val ACCENT = Color(0xFFD62828)
    val INK = Color(0xFF14213D)
  }
}
