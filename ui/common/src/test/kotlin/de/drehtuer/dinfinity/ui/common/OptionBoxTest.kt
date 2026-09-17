package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One option of a wrapping set: what it says it is, and how big it is.
 *
 * What a test can hold is the behaviour and the geometry. Whether the chosen
 * one is filled in the accent is the theme's business and `InkTest`'s; what
 * matters here is that a tap reports the option, that a screen reader is told
 * the right thing about a set that can be emptied, and that every option is
 * big enough to press — which is the one thing `FilterChip` used to supply for
 * free and a hand-drawn box does not.
 */
@RunWith(RobolectricTestRunner::class)
class OptionBoxTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a tap reports the option it is on, and the chosen one says so`() {
    var picked: String? = null
    compose.setContent {
      Row {
        listOf("Thorin", "Ezren").forEach { name ->
          OptionBox(
            text = name,
            selected = name == "Thorin",
            onClick = { picked = name },
            modifier = Modifier.testTag(name),
          )
        }
      }
    }

    compose.onNodeWithTag("Thorin").assertIsSelected()
    compose.onNodeWithTag("Ezren").assertIsNotSelected()
    compose.onNodeWithTag("Ezren").performClick()

    compose.runOnIdle { assertEquals("Ezren", picked) }
  }

  @Test
  fun `an option is at least a touch target, whatever its text`() {
    // A single character is the case that breaks: a box that fits "7" is a box
    // the width of a 7. Material's chip carried a minimum of its own, and a
    // square-cornered box drawn by hand carries whatever it is given.
    compose.setContent {
      Row {
        OptionBox(text = "7", selected = false, onClick = {}, modifier = Modifier.testTag("narrow"))
        OptionBox(text = "🎲", selected = false, onClick = {}, square = true, modifier = Modifier.testTag("mark"))
      }
    }

    compose.onNodeWithTag("narrow").assertHeightIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag("mark").assertWidthIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag("mark").assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `a mark is named, because an emoji is not a name`() {
    compose.setContent {
      OptionBox(
        text = "🎲",
        selected = false,
        onClick = {},
        square = true,
        contentDescription = "Dice",
        role = Role.Checkbox,
      )
    }

    compose.onNodeWithContentDescription("Dice").assertExists()
  }

  @Test
  fun `square options are all one size, however wide their marks are`() {
    // The point of `square`: a row of single characters whose boxes were each
    // the width of their own glyph would read as a ransom note, and an emoji's
    // advance width is not something a screen can predict.
    compose.setContent {
      Row {
        OptionBox(
          text = "\u2694\ufe0f",
          selected = false,
          onClick = {},
          square = true,
          modifier = Modifier.testTag("a"),
        )
        OptionBox(text = "!", selected = false, onClick = {}, square = true, modifier = Modifier.testTag("b"))
      }
    }

    val wide = compose.onNodeWithTag("a").fetchSemanticsNode().size
    val narrow = compose.onNodeWithTag("b").fetchSemanticsNode().size
    assertEquals("two marks came out different sizes", wide, narrow)
  }

  @Test
  fun `a mark inverts to the ink, and the set it is in still reports one choice`() {
    // The other fill, and the other half of `selected` on a square: a chosen
    // mark is filled with the *text* colour rather than the accent, because
    // the mark itself is what carries the accent.
    var cleared = false
    compose.setContent {
      Row {
        OptionBox(
          text = "\u2694\ufe0f",
          selected = true,
          onClick = { cleared = true },
          fill = OptionFill.Ink,
          square = true,
          contentDescription = "Crossed swords",
          role = Role.Checkbox,
          modifier = Modifier.testTag("chosen"),
        )
        OptionBox(
          text = "\ud83c\udff9",
          selected = false,
          onClick = {},
          fill = OptionFill.Ink,
          square = true,
          contentDescription = "Bow and arrow",
          role = Role.Checkbox,
          modifier = Modifier.testTag("other"),
        )
      }
    }

    compose.onNodeWithTag("chosen").assertIsSelected()
    compose.onNodeWithTag("other").assertIsNotSelected()
    // Tapping the chosen one is how a mark is taken off again, so the click
    // has to reach it even while it is already selected.
    compose.onNodeWithTag("chosen").performClick()

    compose.runOnIdle { assertEquals(true, cleared) }
  }
}
