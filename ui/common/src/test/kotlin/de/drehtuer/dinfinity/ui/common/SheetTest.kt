package de.drehtuer.dinfinity.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A sheet comes up from the bottom, spans the phone, and puts its actions
 * where the thumb already is.
 *
 * Those three are the whole of what this component is *for* — the design
 * system's own `.dialog` is a centred card with its actions at the right, and
 * so is Material's `AlertDialog`, which is why nine of them looked close
 * enough to leave alone. Each one is asserted here rather than left to the
 * eye, because "close enough" is how they got there.
 */
@RunWith(RobolectricTestRunner::class)
class SheetTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a sheet shows its title, its content and its actions`() {
    compose.setContent {
      Sheet(
        title = "Export Thorin",
        onDismiss = {},
        actions = { ModernistButton(text = "Export", onClick = {}) },
      ) {
        Text("Rolls with their breakdown.")
      }
    }

    compose.onNodeWithText("Export Thorin").assertExists()
    compose.onNodeWithText("Rolls with their breakdown.").assertExists()
    compose.onNodeWithText("Export").assertExists()
  }

  @Test
  fun `the actions start at the sheet's own left edge, not at its right`() {
    // `.dialog-actions` is `justify-content: flex-end` in the design system
    // and `flex-start` on every sheet in the phone prototype. Material's
    // dialog does the former. The whole point of the component is the latter,
    // so this is the assertion that would fail if someone swapped it back.
    compose.setContent {
      Sheet(
        title = "Export Thorin",
        onDismiss = {},
        actions = { ModernistButton(text = "Export", onClick = {}, modifier = Modifier.testTag("action")) },
      ) {
        Text("Rolls with their breakdown.")
      }
    }

    // The sheet's own padding, and nothing more: an action pushed to the right
    // would start most of a phone's width in.
    compose.onNodeWithTag("action").assertLeftPositionInRootIsEqualTo(Modernist.x4)
  }

  @Test
  fun `a sheet sits on the bottom edge and spans the screen`() {
    compose.setContent {
      Sheet(title = "Export Thorin", onDismiss = {}, modifier = Modifier.testTag("sheet")) {
        Text("Rolls with their breakdown.")
      }
    }

    val sheet = compose.onNodeWithTag("sheet").fetchSemanticsNode()
    val root = sheet.root!!.semanticsOwner.rootSemanticsNode
    assertEquals("a sheet that does not span the phone is a dialog", root.size.width, sheet.size.width)
    assertTrue(
      "a sheet that does not reach the bottom edge is a dialog",
      sheet.boundsInRoot.bottom >= root.size.height - 1f,
    )
  }

  @Test
  fun `a tap outside puts the sheet away`() {
    var dismissed = false
    compose.setContent {
      Sheet(title = "Export Thorin", onDismiss = { dismissed = true }) {
        Text("Rolls with their breakdown.")
      }
    }

    compose.onNodeWithText("Export Thorin").assertExists()
    // The top-left corner, which is backdrop: the sheet is on the bottom edge
    // and spans only the width. There are two roots once a dialog is open —
    // the screen behind and the dialog's own window — and the one with a size
    // is the dialog's.
    compose
      .onAllNodes(isRoot())
      .filterToOne(SemanticsMatcher("has a size") { it.size.height > 0 })
      .performTouchInput { click(Offset(1f, 1f)) }

    compose.runOnIdle { assertTrue("a tap outside the sheet did not put it away", dismissed) }
  }

  @Test
  fun `a tap inside the sheet does not put it away`() {
    // The backdrop is one big click target; without the sheet swallowing taps,
    // every tap on its own content would close it.
    var dismissed = false
    compose.setContent {
      Sheet(title = "Export Thorin", onDismiss = { dismissed = true }) {
        Text("Rolls with their breakdown.", modifier = Modifier.testTag("body"))
      }
    }

    compose.onNodeWithTag("body", useUnmergedTree = true).performClick()

    compose.runOnIdle { assertEquals(false, dismissed) }
  }

  @Test
  fun `a sheet full of content still has its actions`() {
    // Measured, not assumed: before the content was made to yield, twenty
    // lines pushed the sheet to the full height of the screen and the button
    // was laid out at zero height — a sheet whose only way out was a tap on
    // the backdrop. A refusal report is easily a dozen lines, so this is
    // reachable in ordinary use rather than a contrived case.
    compose.setContent {
      Sheet(
        title = "Nothing was installed",
        onDismiss = {},
        actions = { ModernistButton(text = "Close", onClick = {}, modifier = Modifier.testTag("close")) },
      ) {
        repeat(LONG_REPORT) { line -> Text("diceset.toml:$line: error: a line the validator wrote") }
      }
    }

    val close = compose.onNodeWithTag("close").fetchSemanticsNode()
    assertTrue("the sheet's own way out was squeezed to nothing", close.size.height >= 1)
    compose.onNodeWithTag("close").assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `the words in a sheet are their own nodes, not one lump`() {
    // The sheet swallows taps so that tapping its own content does not close
    // it. Doing that with `Modifier.clickable` merged every descendant into a
    // single semantics node: a screen reader read the whole sheet as one
    // utterance, and no `Text` inside it could be found in the merged tree.
    compose.setContent {
      Sheet(title = "Export Thorin", onDismiss = {}) {
        Text("Rolls with their breakdown.")
        Text("Dice sets and tables are not included.")
      }
    }

    compose.onNodeWithText("Rolls with their breakdown.").assertExists()
    compose.onNodeWithText("Dice sets and tables are not included.").assertExists()
  }

  private companion object {
    /** Longer than a phone, which is the point of it. */
    const val LONG_REPORT = 20
  }
}
