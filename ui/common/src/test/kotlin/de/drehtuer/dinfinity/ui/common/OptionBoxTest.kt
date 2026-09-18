package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

  @Test
  fun `an option drawn as a picture is still an option, and still has a name`() {
    // The slot form: the border, the inversion, the target and the semantics
    // are the lettered form's, because an option drawn as an icon is the same
    // control. What changes is only what is inside the box — and a picture
    // has no words, so the name is not optional.
    compose.setContent {
      Row {
        OptionBox(
          contentDescription = "Eraser",
          selected = true,
          onClick = {},
          modifier = Modifier.testTag("eraser"),
        ) { Box(Modifier.size(GLYPH)) }
        OptionBox(
          contentDescription = "Fill",
          selected = false,
          onClick = {},
          modifier = Modifier.testTag("fill"),
        ) { Box(Modifier.size(GLYPH)) }
      }
    }

    compose.onNodeWithContentDescription("Eraser").assertIsSelected()
    compose.onNodeWithTag("fill").assertIsNotSelected()
    compose.onNodeWithTag("eraser").assertWidthIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag("eraser").assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `the ink the content is handed inverts with the box`() {
    // A glyph that decided its own colour would be invisible on the chosen
    // one, which is filled. The box has already made that decision, so it
    // hands it over rather than leaving the content to guess.
    val inks = mutableListOf<Color>()
    compose.setContent {
      Row {
        OptionBox(contentDescription = "Chosen", selected = true, onClick = {}) { inks += it }
        OptionBox(contentDescription = "Not", selected = false, onClick = {}) { inks += it }
      }
    }

    compose.runOnIdle { assertEquals("both were drawn in the same ink", 2, inks.distinct().size) }
  }

  @Test
  fun `an option this die has no use for is dead rather than absent`() {
    // The paste turn on a kite, whose cells have no turn. A set of options
    // that moved about under the finger choosing from it would be worse than
    // one with a dim member.
    var chosen = 0
    compose.setContent {
      OptionBox(
        text = "Turn 1/1",
        selected = false,
        onClick = { chosen++ },
        enabled = false,
        modifier = Modifier.testTag("turn"),
      )
    }

    compose.onNodeWithTag("turn").assertIsNotEnabled()
    compose.onNodeWithTag("turn").performClick()

    compose.runOnIdle { assertEquals("a disabled option was chosen", 0, chosen) }
  }

  @Test
  fun `a picture inverts to the ink too, and can be dead`() {
    // The slot form's other fill and its other state, for the same reasons
    // the lettered form has both: a set whose content carries the accent
    // inverts to the text colour, and an option this die has no use for stays
    // put and says it cannot be chosen.
    var chosen = 0
    compose.setContent {
      Row {
        OptionBox(
          contentDescription = "Mirror",
          selected = true,
          onClick = {},
          fill = OptionFill.Ink,
          role = Role.Checkbox,
          modifier = Modifier.testTag("mirror"),
        ) { Box(Modifier.size(GLYPH)) }
        OptionBox(
          contentDescription = "Turn",
          selected = false,
          onClick = { chosen++ },
          enabled = false,
          modifier = Modifier.testTag("turn"),
        ) { Box(Modifier.size(GLYPH)) }
      }
    }

    compose.onNodeWithTag("mirror").assertIsSelected()
    compose.onNodeWithTag("turn").assertIsNotEnabled()
    compose.onNodeWithTag("turn").performClick()

    compose.runOnIdle { assertEquals(0, chosen) }
  }

  @Test
  fun `an option survives a recomposition that changes nothing about it`() {
    // A set of these usually sits in a row that redraws for something else
    // entirely — a stroke on a canvas, a name typed into a field — so the
    // ordinary case is a recomposition with the same arguments.
    var tick by mutableStateOf(0)
    compose.setContent {
      Column {
        Text("tick $tick")
        OptionBox(text = "Fine", selected = true, onClick = {}, modifier = Modifier.testTag("lettered"))
        OptionBox(
          contentDescription = "Eraser",
          selected = false,
          onClick = {},
          modifier = Modifier.testTag("pictured"),
        ) { Box(Modifier.size(GLYPH)) }
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag("lettered").assertIsSelected()
    compose.onNodeWithTag("pictured").assertIsNotSelected()
  }

  @Test
  fun `an option follows the state it is drawn from, in both forms`() {
    // The other half of the recomposition case above: a set of options is
    // usually redrawn *because* the choice moved, so the arguments change
    // rather than staying put — the chosen one has to become the other one,
    // and a label that follows the state has to follow it.
    var chosen by mutableStateOf(true)
    compose.setContent {
      Column {
        OptionBox(
          text = if (chosen) "Show guide" else "Hide guide",
          selected = chosen,
          onClick = {},
          modifier = Modifier.testTag("lettered"),
        )
        OptionBox(
          contentDescription = if (chosen) "Show guide" else "Hide guide",
          selected = !chosen,
          onClick = {},
          enabled = chosen,
          modifier = Modifier.testTag("pictured"),
        ) { Box(Modifier.size(GLYPH)) }
      }
    }
    compose.onNodeWithTag("lettered").assertIsSelected()
    compose.onNodeWithText("Show guide").assertIsDisplayed()

    compose.runOnIdle { chosen = false }

    compose.onNodeWithTag("lettered").assertIsNotSelected()
    compose.onNodeWithTag("pictured").assertIsSelected()
    compose.onNodeWithTag("pictured").assertIsNotEnabled()
    compose.onNodeWithText("Hide guide").assertIsDisplayed()
  }

  @Test
  fun `both forms take every argument there is`() {
    // Every screen leaves most of these defaulted, so nothing else here shows
    // that the ones nobody passes still mean what they say when they are.
    compose.setContent {
      Row {
        OptionBox(
          text = "d18",
          selected = false,
          onClick = {},
          modifier = Modifier.testTag("lettered"),
          fill = OptionFill.Ink,
          square = false,
          contentDescription = "An eighteen-sided die",
          role = Role.Checkbox,
          enabled = true,
        )
        OptionBox(
          contentDescription = "Broad",
          selected = true,
          onClick = {},
          modifier = Modifier.testTag("pictured"),
          fill = OptionFill.Accent,
          enabled = true,
          role = Role.RadioButton,
        ) { Box(Modifier.size(GLYPH)) }
      }
    }

    compose.onNodeWithContentDescription("An eighteen-sided die").assertIsNotSelected()
    compose.onNodeWithTag("pictured").assertIsSelected()
  }

  private companion object {
    /** Something to put in the slot that is not a word. */
    val GLYPH = 22.dp
  }
}
