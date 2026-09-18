package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A button whose label is a picture still has to be findable, pressable and
 * named.
 *
 * All three of those are what a glyph in a `TextButton` was getting from
 * Material for free, and what a hand-drawn box has to be given deliberately.
 */
@RunWith(RobolectricTestRunner::class)
class ModernistIconButtonTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `it is named by what it does, not by the picture on it`() {
    compose.setContent {
      ModernistIconButton(contentDescription = "Export", onClick = {}) { Text("⤴") }
    }

    compose.onNodeWithContentDescription("Export").assertHasClickAction()
  }

  @Test
  fun `the name and the press are the same node, not a button wrapping a label`() {
    // The reason the semantics sit on the button rather than on the glyph. If
    // the description lived on the picture inside, a screen reader would meet
    // a nameless button containing a named thing — two stops where there is
    // one control — and a test reaching for the button by name would land on
    // something that cannot be pressed.
    compose.setContent {
      ModernistIconButton(
        contentDescription = "Export",
        onClick = {},
        modifier = Modifier.testTag("export"),
      ) { Text("⤴") }
    }

    val named = compose.onNodeWithContentDescription("Export").fetchSemanticsNode()
    val tagged = compose.onNodeWithTag("export").fetchSemanticsNode()
    assertEquals("the name and the button are two different nodes", tagged.id, named.id)
  }

  @Test
  fun `it is findable on the merged tree, which is where a screen's tests look`() {
    // A row of these usually sits inside something that merges its
    // descendants. Material's `Surface` kept each button its own merged node;
    // a plain `Box` would not, and a test reaching for one by tag would stop
    // finding it.
    compose.setContent {
      ModernistIconButton(
        contentDescription = "More",
        onClick = {},
        modifier = Modifier.testTag("more"),
      ) { Text("…") }
    }

    compose.onNodeWithTag("more").assertHasClickAction()
  }

  @Test
  fun `a press reports it`() {
    var pressed = 0
    compose.setContent {
      ModernistIconButton(
        contentDescription = "More",
        onClick = { pressed++ },
        modifier = Modifier.testTag("more"),
      ) { Text("…") }
    }

    compose.onNodeWithTag("more").performClick()

    compose.runOnIdle { assertEquals(1, pressed) }
  }

  @Test
  fun `an action with nothing to do is dead rather than gone`() {
    // Undo on a face nobody has drawn on. A row whose buttons come and go as
    // the drawing changes is a row nobody learns, so it stays and says it
    // cannot be pressed — and it must actually not be pressed, not merely
    // look it.
    var pressed = 0
    compose.setContent {
      ModernistIconButton(
        contentDescription = "Undo",
        onClick = { pressed++ },
        enabled = false,
        modifier = Modifier.testTag("undo"),
      ) { Text("↶") }
    }

    compose.onNodeWithTag("undo").assertIsNotEnabled()
    compose.onNodeWithTag("undo").performClick()

    compose.runOnIdle { assertEquals("a disabled button ran its action", 0, pressed) }
  }

  @Test
  fun `it is pressable, whatever the design says its box is`() {
    // `.btn-icon` is 36 px and Android's minimum is 48. The drawn box is the
    // design's; the target around it is the platform's, and the platform wins
    // on the thing a finger has to hit.
    compose.setContent {
      ModernistIconButton(
        contentDescription = "More",
        onClick = {},
        modifier = Modifier.testTag("more"),
      ) { Text("…") }
    }

    compose.onNodeWithTag("more").assertWidthIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag("more").assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `it survives a recomposition that changes nothing about it`() {
    // A row of these sits inside a screen that redraws for something else
    // entirely — a stroke on a canvas — so the ordinary case is a
    // recomposition with the same arguments, and it must not lose the glyph.
    var tick by mutableStateOf(0)
    compose.setContent {
      Column {
        Text("tick $tick")
        ModernistIconButton(
          contentDescription = "Undo",
          onClick = {},
          modifier = Modifier.testTag("undo"),
        ) { Text("↶") }
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag("undo").assertHasClickAction()
  }

  @Test
  fun `it follows the state it is drawn from`() {
    // The other half: an action's name and whether there is anything to do
    // both change under it — undo becomes pressable the moment a line is
    // drawn, and the guide's button says the opposite thing once it is off.
    var drawn by mutableStateOf(false)
    compose.setContent {
      ModernistIconButton(
        contentDescription = if (drawn) "Undo the line" else "Undo",
        onClick = {},
        enabled = drawn,
        modifier = Modifier.testTag("undo"),
      ) { Text("↶") }
    }
    compose.onNodeWithTag("undo").assertIsNotEnabled()

    compose.runOnIdle { drawn = true }

    compose.onNodeWithContentDescription("Undo the line").assertHasClickAction()
  }
}
