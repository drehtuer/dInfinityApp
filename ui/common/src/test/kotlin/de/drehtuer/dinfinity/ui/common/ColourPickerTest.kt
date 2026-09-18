package de.drehtuer.dinfinity.ui.common

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Hsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The one colour picker three screens open — the face designer's ink,
 * Settings' accent and a saved roll's tag.
 *
 * What is asserted here is what each of those three used to assert for
 * itself, once: that the sliders make the colour the arithmetic says they do,
 * that **Cancel** reports nothing, and that a sheet made almost entirely of
 * pictures says what it is to a screen reader. The arithmetic itself is
 * `core/model`'s `HsvTest`, which needs no screen.
 */
@RunWith(RobolectricTestRunner::class)
class ColourPickerTest {
  @get:Rule
  val compose = createComposeRule()

  private val tags = ColourPickerTags("test:picker")

  @Test
  fun `the three sliders make the colour the arithmetic says they make`() {
    val chosen = mutableListOf<Int>()
    show(onChosen = chosen::add)

    slide(tags.hue, 150f)
    slide(tags.depth, 0.6f)
    slide(tags.brightness, 0.8f)
    compose.onNodeWithTag(tags.use).performClick()

    assertEquals(listOf(Hsv(150f, 0.6f, 0.8f).argb), chosen)
  }

  @Test
  fun `it opens on the colour it was given rather than on one nobody chose`() {
    // Otherwise every visit starts by undoing a colour the picker invented.
    show(start = 0xFF2B5AA8.toInt())

    compose.onNodeWithTag(tags.patch).assertContentDescriptionEquals("#2B5AA8")
  }

  @Test
  fun `the patch follows the sliders, as chosen and not as anything will paint it`() {
    show(start = 0xFF000000.toInt())

    slide(tags.hue, 0f)
    slide(tags.depth, 1f)
    slide(tags.brightness, 1f)

    compose.onNodeWithTag(tags.patch).assertContentDescriptionEquals("#FF0000")
  }

  @Test
  fun `cancel reports nothing at all, however far the sliders were dragged`() {
    val chosen = mutableListOf<Int>()
    var dismissed = 0
    show(onChosen = chosen::add, onDismiss = { dismissed++ })

    slide(tags.hue, 300f)
    compose.onNodeWithTag(tags.cancel).performClick()

    assertTrue("a cancelled picker still chose something", chosen.isEmpty())
    assertEquals(1, dismissed)
  }

  @Test
  fun `the sheet is called what the caller called it`() {
    // The title is the caller's word for the thing being coloured — "Pick a
    // colour" in the designer, "Your own colour" in Settings — because this
    // file does not know what it is colouring.
    show(title = "Your own colour")

    compose.onNodeWithText("Your own colour").assertExists()
  }

  @Test
  fun `each of the three sliders says which one it is`() {
    // Three unlabelled sliders in a row is a control a screen reader cannot
    // describe, and this sheet is nothing but sliders and a patch.
    show()

    listOf("Hue", "Depth", "Brightness").forEach {
      compose.onNodeWithContentDescription(it).assertExists()
    }
  }

  @Test
  fun `the patch of colour has words, because a colour is not one`() {
    // The hex, which is the one description of a colour that is exact.
    show(start = 0xFFFFFFFF.toInt())

    compose.onNodeWithTag(tags.patch).assertContentDescriptionEquals("#FFFFFF")
  }

  @Test
  fun `the patch and both buttons are big enough to hit`() {
    // Android's floor for anything pressable, and WCAG 2.2's 2.5.8 at AA.
    show()

    compose.onNodeWithTag(tags.patch).assertHeightIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag(tags.use).assertHeightIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag(tags.cancel).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `the three sliders are Material's own 44 dp, which is four short of the floor`() {
    // The one control in this sheet that does not reach `TOUCH_TARGET`, said
    // out loud rather than left out of the test above. `Slider` clamps its
    // own height and neither `heightIn` nor `minimumInteractiveComponentSize`
    // moves the node the label is attached to, so this is Material's figure
    // and not a choice made here. What softens it is the shape: a slider's
    // target is its whole width, which is the width of the sheet. If Material
    // ever grows it, this test is what notices.
    show()

    listOf(tags.hue, tags.depth, tags.brightness).forEach {
      compose.onNodeWithTag(it).assertHeightIsAtLeast(MATERIAL_SLIDER)
      compose.onNodeWithTag(it).assertWidthIsAtLeast(TOUCH_TARGET)
    }
  }

  @Test
  fun `a tag set names the sheet, and every control in it follows from that name`() {
    // One string rather than seven, so there is nowhere for a suffix to be
    // spelled two ways across three screens.
    val derived = ColourPickerTags("editor:colour:picker")

    assertEquals("editor:colour:picker", derived.sheet)
    assertEquals("editor:colour:picker:patch", derived.patch)
    assertEquals("editor:colour:picker:use", derived.use)
    assertEquals("editor:colour:picker:cancel", derived.cancel)
    assertEquals("editor:colour:picker:hue", derived.hue)
    assertEquals("editor:colour:picker:depth", derived.depth)
    assertEquals("editor:colour:picker:brightness", derived.brightness)
  }

  private fun slide(
    tag: String,
    to: Float,
  ) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(to) }

  private fun show(
    start: Int = 0xFFEC3013.toInt(),
    title: String = "Pick a colour",
    onDismiss: () -> Unit = {},
    onChosen: (Int) -> Unit = {},
  ) {
    compose.setContent {
      ColourPicker(
        start = start,
        title = title,
        tags = tags,
        onDismiss = onDismiss,
        onChosen = onChosen,
      )
    }
  }
}

/** What Material lays a `Slider` out at, and clamps it to. */
private val MATERIAL_SLIDER = 44.dp
