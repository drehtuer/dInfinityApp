package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The design system's `.btn`, in its three variants.
 *
 * Whether it *looks* square is the device suite's to say. What is asserted here
 * is that it is a button — one press per press, disabled when it is disabled,
 * and big enough to hit — in every variant, because the variants differ by a
 * `when` and a variant nobody presses in a test is a variant nobody presses.
 */
@RunWith(RobolectricTestRunner::class)
class ModernistButtonTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `every variant says its word and reports one press per press`() {
    // All three at once rather than three test runs: they differ by a `when`
    // over the kind, and a variant nothing ever presses is a variant nothing
    // ever checks.
    val pressed = mutableListOf<ModernistButtonKind>()
    compose.setContent {
      Column {
        ModernistButtonKind.entries.forEach { kind ->
          ModernistButton(
            text = "Replay",
            onClick = { pressed += kind },
            kind = kind,
            modifier = Modifier.testTag(tagOf(kind)),
          )
        }
      }
    }

    ModernistButtonKind.entries.forEach { kind ->
      compose.onNodeWithTag(tagOf(kind)).assertTextEquals("Replay")
      compose.onNodeWithTag(tagOf(kind)).assertIsEnabled()
      compose.onNodeWithTag(tagOf(kind)).performClick()
    }

    assertEquals(ModernistButtonKind.entries.toList(), pressed)
  }

  @Test
  fun `a disabled button is disabled, and a press on it does nothing`() {
    val pressed = mutableListOf<Unit>()
    compose.setContent {
      ModernistButton(
        text = "Replay those dice from that seed",
        onClick = { pressed += Unit },
        kind = ModernistButtonKind.Primary,
        enabled = false,
        modifier = Modifier.testTag(TAG),
      )
    }

    compose.onNodeWithTag(TAG).assertIsNotEnabled()
    compose.onNodeWithTag(TAG).performClick()

    assertTrue("a disabled button reported a press", pressed.isEmpty())
  }

  @Test
  fun `it is as pressable as a control anywhere`() {
    // Android's own minimum, which Material's own buttons supply and a bare
    // `clickable` does not.
    compose.setContent {
      ModernistButton(text = "Share the log", onClick = {}, modifier = Modifier.testTag(TAG))
    }

    compose.onNodeWithTag(TAG).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  private companion object {
    const val TAG = "button"
    val TOUCH_TARGET = 48.dp

    fun tagOf(kind: ModernistButtonKind): String = "button:$kind"
  }
}
