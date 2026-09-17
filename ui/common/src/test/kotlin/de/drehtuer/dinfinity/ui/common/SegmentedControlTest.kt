package de.drehtuer.dinfinity.ui.common

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
 * The design system's `.seg`, which every row of the Settings screen is built
 * from (`design/dInfinity.dc.html`, options 1y and 2d).
 *
 * What a picture cannot be tested for is what it looks like. What can be is the
 * part the screens depend on: that it offers what it was given, selects nothing
 * on its own, and that its two modes differ in exactly the way they are
 * documented to.
 */
@RunWith(RobolectricTestRunner::class)
class SegmentedControlTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `every option is offered, and the chosen one is chosen`() {
    show(selected = "Light")

    OPTIONS.forEach { option -> compose.onNodeWithTag(tagOf(option)).assertIsDisplayed() }
    compose.onNodeWithTag(tagOf("Light")).assertIsSelected()
    compose.onNodeWithTag(tagOf("System")).assertIsNotSelected()
  }

  @Test
  fun `choosing an option reports it once`() {
    val chosen = mutableListOf<String>()
    show(selected = "System", onSelect = chosen::add)

    compose.onNodeWithTag(tagOf("Dark")).performClick()

    assertEquals(listOf("Dark"), chosen)
  }

  /**
   * It is stateless, like the screens that hold it. A control that moved its
   * own selection would look like it had saved something it had not.
   */
  @Test
  fun `it does not select on its own`() {
    show(selected = "System")

    compose.onNodeWithTag(tagOf("Dark")).performClick()

    compose.onNodeWithTag(tagOf("Dark")).assertIsNotSelected()
    compose.onNodeWithTag(tagOf("System")).assertIsSelected()
  }

  @Test
  fun `an option is as pressable as a control anywhere`() {
    // Android's own minimum. `FilterChip` used to supply it; a bare
    // `selectable` does not, so the control asks for it itself.
    show(selected = "System")

    OPTIONS.forEach { option ->
      compose.onNodeWithTag(tagOf(option)).assertHeightIsAtLeast(TOUCH_TARGET)
    }
  }

  /**
   * Without a callback it is the read-out beside a switch row: the row owns the
   * tap and the row is the one thing TalkBack reads. Two more targets and two
   * more words inside a row that is already a switch would be three things to
   * choose from where there is one.
   */
  @Test
  fun `with no callback it is a read-out rather than two buttons`() {
    compose.setContent {
      SegmentedControl(options = listOf("Off", "On"), selected = "On", label = { it })
    }

    compose.onAllNodes(matcher = hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
    compose.onNodeWithText("On").assertDoesNotExist()
    compose.onNodeWithText("Off").assertDoesNotExist()
  }

  private fun show(
    selected: String,
    onSelect: (String) -> Unit = {},
  ) {
    compose.setContent {
      SegmentedControl(
        options = OPTIONS,
        selected = selected,
        label = { it },
        onSelect = onSelect,
        tagOf = ::tagOf,
      )
    }
  }

  private companion object {
    val OPTIONS = listOf("System", "Light", "Dark")
    val TOUCH_TARGET = 48.dp

    fun tagOf(option: String): String = "seg:$option"
  }
}
