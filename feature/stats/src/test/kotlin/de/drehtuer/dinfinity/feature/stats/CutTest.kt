package de.drehtuer.dinfinity.feature.stats

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One cut of a list, which Statistics and History both narrow with.
 *
 * It used to be written twice, once per screen, and each screen's tests
 * covered it by accident on the way to testing something else. Now it is one
 * composable, so it gets the test it always needed — and the assertion that
 * matters is the **`selected` semantics**, because the accent and the bold
 * that say which cut is on are marks only an eye can read.
 */
@RunWith(RobolectricTestRunner::class)
class CutTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the chosen cut says so in the semantics tree, not only in the accent`() {
    compose.setContent {
      Row {
        Cut(label = "All sets", chosen = true, tag = "all", onChoose = {})
        Cut(label = "brass", chosen = false, tag = "brass", onChoose = {})
      }
    }

    compose.onNodeWithTag("all").assertIsSelected()
    compose.onNodeWithTag("brass").assertIsNotSelected()
  }

  @Test
  fun `choosing a cut reports which one`() {
    var chose: String? = null
    compose.setContent {
      Row {
        Cut(label = "brass", chosen = false, tag = "brass", onChoose = { chose = "brass" })
      }
    }

    compose.onNodeWithTag("brass").performClick()

    compose.runOnIdle { assertEquals("brass", chose) }
  }

  @Test
  fun `a cut follows the choice as it moves along the row`() {
    // The row is one control: choosing a cut unchooses the one before it, and
    // both have to redraw. This is also what exercises the skip path a
    // `@Composable` is compiled with — a cut that held its first state would
    // leave two of them looking chosen.
    val chosen = mutableStateOf("all")
    compose.setContent {
      Row {
        listOf("all", "brass").forEach { id ->
          Cut(label = id, chosen = chosen.value == id, tag = id, onChoose = { chosen.value = id })
        }
      }
    }

    compose.onNodeWithTag("all").assertIsSelected()

    compose.onNodeWithTag("brass").performClick()

    compose.onNodeWithTag("brass").assertIsSelected()
    compose.onNodeWithTag("all").assertIsNotSelected()
  }

  @Test
  fun `a cut is pressable, however short its name`() {
    // A set's id can be three characters. Without the minimum, the target
    // would be the width of the word.
    compose.setContent { Cut(label = "d6", chosen = false, tag = "d6", onChoose = {}) }

    compose.onNodeWithTag("d6").assertWidthIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag("d6").assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `a cut prints the name it was given`() {
    compose.setContent { Cut(label = "Curse of Strahd", chosen = false, tag = "s", onChoose = {}) }

    compose.onNodeWithText("Curse of Strahd").assertExists()
  }
}
