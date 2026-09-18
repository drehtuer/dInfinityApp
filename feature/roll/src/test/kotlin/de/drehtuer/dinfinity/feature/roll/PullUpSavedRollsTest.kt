package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The saved rolls as a pull-up (`design/dInfinity.dc.html`, option 1c).
 *
 * Where it rests and what a drag does to that is [SheetSlideTest]'s, on the
 * JVM, and it is the same arithmetic the result sheet uses. What is asked
 * here is the half a screen is needed for: that it is **parked to start
 * with**, which is the whole point of the change, that the grip is a control
 * a finger and a screen reader can both use, and that pulling it up really
 * does reveal the rolls.
 */
@RunWith(RobolectricTestRunner::class)
class PullUpSavedRollsTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `it is parked to start with, so the whole table is shown`() {
    // The fault it fixes: the strip was the last plate standing across the
    // bottom of the felt, in every state, whether or not anybody wanted it.
    show()

    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).assertIsDisplayed()
    val screen = compose.onRoot().getUnclippedBoundsInRoot()
    val rolls = compose.onNodeWithTag(STRIP).getUnclippedBoundsInRoot()

    assertTrue("the rolls were on the table rather than below the edge", rolls.top >= screen.bottom)
    compose.onNodeWithTag(STRIP).assertIsNotDisplayed()
  }

  @Test
  fun `the handle pulls it up without a drag, and puts it back`() {
    // Every rest is reachable from a tap: a gesture is not an interface
    // (`docs/architecture.md`, "Accessibility").
    show()
    val top = { compose.onNodeWithTag(RollTestTags.SAVED_PULL_UP).getUnclippedBoundsInRoot().top }
    val parked = top()

    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).performClick()
    compose.waitForIdle()
    val pulled = top()

    assertTrue("the saved rolls did not come up: $parked then $pulled", pulled < parked)

    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).performClick()
    compose.waitForIdle()

    assertTrue("the saved rolls did not go back down", top() > pulled)
  }

  @Test
  fun `pulled up it shows the rolls`() {
    show()

    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).performClick()
    compose.waitForIdle()

    compose.onNodeWithTag(STRIP).assertIsDisplayed()
  }

  @Test
  fun `a drag up the grip brings them out`() {
    show()
    val top = { compose.onNodeWithTag(RollTestTags.SAVED_PULL_UP).getUnclippedBoundsInRoot().top }
    val parked = top()

    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).performTouchInput {
      down(center)
      repeat(STEPS) { moveBy(Offset(x = 0f, y = -FAR / STEPS)) }
      up()
    }
    compose.waitForIdle()

    assertTrue("a drag up left the saved rolls where they were", top() < parked)
  }

  @Test
  fun `the handle is worth pressing and says what is behind it`() {
    show()

    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).assertHeightIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithContentDescription("Saved rolls").assertIsDisplayed()
  }

  @Test
  fun `the grip reports its height, so nothing is left under a parked strip`() {
    var parked = 0f
    compose.setContent {
      Box(modifier = Modifier.fillMaxSize()) {
        PullUpSavedRolls(
          rest = SheetRest.Down,
          onRest = {},
          onParked = { parked = it },
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          Text(text = "4d6dl1", modifier = Modifier.testTag(STRIP))
        }
      }
    }
    compose.waitForIdle()

    assertTrue("the grip measured nothing", parked > 0f)
  }

  /** The strip with its rest held outside it, which is how the screen holds it. */
  private fun show() {
    compose.setContent {
      var rest by remember { mutableStateOf(SheetRest.Down) }
      Box(modifier = Modifier.fillMaxSize()) {
        PullUpSavedRolls(
          rest = rest,
          onRest = { rest = it },
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          Text(text = "4d6dl1", modifier = Modifier.testTag(STRIP))
        }
      }
    }
    compose.waitForIdle()
  }

  private companion object {
    const val STRIP = "test:strip"

    /** Further than any strip this test draws can travel. */
    const val FAR = 2000f

    /** In steps, because one jump of the whole distance is not a drag. */
    const val STEPS = 20
  }
}
