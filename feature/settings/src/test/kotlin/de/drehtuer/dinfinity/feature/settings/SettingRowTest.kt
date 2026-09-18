package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two-column setting row, measured rather than eyeballed.
 *
 * The screen's own tests say that each setting reports what was chosen; these
 * say where the two halves of a row end up, which is the thing the design and
 * the app disagreed about (`docs/architecture.md`, "Settings").
 */
@RunWith(RobolectricTestRunner::class)
class SettingRowTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the control sits beside the text, centred against it`() {
    compose.setContent {
      Box(modifier = Modifier.width(WIDE)) {
        SettingRow(heading = NAME, explanation = SENTENCE) { Dial() }
      }
    }

    val text = compose.onNodeWithText(NAME).getUnclippedBoundsInRoot()
    val control = compose.onNodeWithTag(DIAL).getUnclippedBoundsInRoot()

    assertTrue("the control is not beside the text", control.left >= text.right)
    assertEquals(
      "the control is not centred against the text",
      ((text.top + text.bottom) / 2f).value.toDouble(),
      ((control.top + control.bottom) / 2f).value.toDouble(),
      A_PIXEL,
    )
  }

  @Test
  fun `the text keeps the whole of the room the control leaves`() {
    // The other half of "beside": the sentence is set in what is left rather
    // than in some fixed column, so a row is as wide as the screen is.
    compose.setContent {
      Box(modifier = Modifier.width(WIDE)) {
        SettingRow(heading = NAME, explanation = SENTENCE) { Dial() }
      }
    }

    val text = compose.onNodeWithText(NAME).getUnclippedBoundsInRoot()
    val control = compose.onNodeWithTag(DIAL).getUnclippedBoundsInRoot()

    assertEquals("the row does not start at the left edge", 0.dp, text.left)
    assertEquals("the control is not at the right edge of the row", WIDE, control.right)
  }

  /**
   * The answer the drawing does not have: a browser lets `flex: none` overflow
   * sideways and a phone cannot, so the control goes under the text instead of
   * either half being squeezed.
   *
   * Deliberately narrower than anything the control's own width could decide,
   * so the test says what the rule is rather than what a font measured to.
   */
  @Test
  fun `a control that would fill the row is asked what it wants instead`() {
    // The one that a fixed-size fake cannot catch, and the one a phone did: a
    // segmented control fills the width it is offered, because that is what
    // makes its options equal. Measured against the row's own constraints it
    // takes the whole row, the words get nothing, and every row on the screen
    // stacks — which is what shipped until this test existed.
    compose.setContent {
      Box(modifier = Modifier.width(WIDE)) {
        SettingRow(heading = NAME, explanation = SENTENCE) { GreedyDial() }
      }
    }

    val text = compose.onNodeWithText(NAME).getUnclippedBoundsInRoot()
    val control = compose.onNodeWithTag(DIAL).getUnclippedBoundsInRoot()

    assertTrue("a control that fills its width pushed the text off the row", control.left >= text.right)
  }

  @Test
  fun `a control with no room beside the text goes under it`() {
    compose.setContent {
      Box(modifier = Modifier.width(NARROW)) {
        SettingRow(heading = NAME, explanation = SENTENCE) { Dial() }
      }
    }

    val text = compose.onNodeWithText(NAME).getUnclippedBoundsInRoot()
    val control = compose.onNodeWithTag(DIAL).getUnclippedBoundsInRoot()

    assertTrue("the control is still beside the text", control.top >= text.bottom)
    assertEquals("the control did not go to the left edge", text.left, control.left)
  }

  @Test
  fun `a row is as tall as its taller half`() {
    compose.setContent {
      Box(modifier = Modifier.width(WIDE)) {
        SettingRow(
          heading = NAME,
          explanation = SENTENCE,
          modifier = Modifier.testTag(ROW),
        ) { Dial() }
      }
    }

    compose.onNodeWithTag(ROW).assertHeightIsAtLeast(CONTROL)
  }

  @Test
  fun `a name and its sentence are one thing to a screen reader`() {
    // TalkBack should say "Appearance, light dark or whatever the phone is
    // doing" as one stop. Two stops is what merging prevents — and on a row
    // whose control sits beside the text, the order between them is not even
    // top to bottom any more.
    compose.setContent {
      Box(modifier = Modifier.width(WIDE)) {
        SettingRow(heading = NAME, explanation = SENTENCE) { Dial() }
      }
    }

    compose.onNodeWithText(NAME).assertTextContains(SENTENCE)
  }

  @Test
  fun `a row inside a section that has explained itself is just its name`() {
    compose.setContent {
      Box(modifier = Modifier.width(WIDE)) {
        SettingRow(heading = NAME) { Dial() }
      }
    }

    compose.onNodeWithText(NAME).assertExists()
    compose.onNodeWithText(SENTENCE).assertDoesNotExist()
  }

  /** Something the size of a segmented control, with a handle on it. */
  @Composable
  private fun Dial() {
    Box(modifier = Modifier.size(CONTROL).testTag(DIAL))
  }

  /**
   * A control that fills whatever it is given, the way a segmented control
   * does, but whose content is [CONTROL] wide.
   */
  @Composable
  private fun GreedyDial() {
    Row(modifier = Modifier.fillMaxWidth().testTag(DIAL)) {
      Box(modifier = Modifier.size(CONTROL))
    }
  }
}

private const val NAME = "Appearance"
private const val SENTENCE = "Light, dark, or whatever the phone is doing."
private const val DIAL = "test:dial"
private const val ROW = "test:row"

/**
 * A phone's width, near enough: both halves fit.
 *
 * Inside Robolectric's own 320 dp screen, because `Modifier.width` is clamped
 * by what it is given and a row asked to be wider than the window would simply
 * be the window.
 */
private val WIDE: Dp = 300.dp

/** Narrower than the text column's own floor, so nothing fits beside it. */
private val NARROW: Dp = 140.dp

private val CONTROL: Dp = 100.dp

private const val A_PIXEL = 1.0
