package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The way back to the face designer, for a throw that came from it
 * ([BackToDesigner]; `docs/face-designer.md`, "The way back").
 *
 * Its own file rather than another corner of `RollScreenTest`, for the reason
 * `DiceMenuTest` is: it is a control of its own, and it can be drawn without
 * a tray, a physics world or a formula — none of which it knows anything
 * about.
 *
 * That it is on the screen only when the visit came from the designer, and
 * where a press on it lands, are the graph's questions and are answered over
 * the whole app in `:app`'s `DInfinityScreensTest`.
 */
@RunWith(RobolectricTestRunner::class)
class BackToDesignerTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a press on it goes back`() {
    var went = 0
    compose.setContent { BackToDesigner(onBack = { went++ }) }

    compose.onNodeWithTag(RollTestTags.BACK_TO_DESIGNER).assertIsDisplayed().performClick()

    assertEquals("the way back did not go anywhere", 1, went)
  }

  @Test
  fun `it survives the tray around it recomposing`() {
    // Which it does constantly: the tray it sits over is redrawn on every
    // frame of a roll, and the banner is not about the roll. Nothing about
    // it changes, so Compose skips it — and skipping it has to leave it on
    // the screen rather than take it off.
    var tick by mutableIntStateOf(0)
    val onBack = {}
    compose.setContent {
      Column {
        Text("$tick")
        BackToDesigner(onBack = onBack)
      }
    }

    repeat(3) { compose.runOnIdle { tick++ } }

    compose.onNodeWithTag(RollTestTags.BACK_TO_DESIGNER).assertIsDisplayed()
  }

  @Test
  fun `it says what is being tested, and what a press will do`() {
    // Two lines, and the second of them is also what a screen reader is told
    // the banner does — so it has to read as an action rather than as a
    // caption under the first.
    compose.setContent { BackToDesigner(onBack = {}) }

    compose.onNodeWithText("Testing the die you drew").assertExists()
    compose
      .onNodeWithTag(RollTestTags.BACK_TO_DESIGNER)
      .assertContentDescriptionEquals("Back to the face designer")
  }
}
