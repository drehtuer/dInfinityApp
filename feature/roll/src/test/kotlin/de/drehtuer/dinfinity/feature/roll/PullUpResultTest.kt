package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The result as a sheet that comes up and can be pushed back down
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * Where it rests and what a drag does to that is [SheetSlideTest]'s, on the
 * JVM. What is left here is the half a screen is needed for: that the sheet
 * arrives with the total and the breakdown on it, that the handle is a control
 * a finger and a screen reader can both use, and — the promise this whole
 * change rests on — that **pushing it down never takes the total away**.
 */
@RunWith(RobolectricTestRunner::class)
class PullUpResultTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `it arrives carrying the total and the whole breakdown`() {
    show()

    compose.onNodeWithTag(RollTestTags.PULL_UP).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertTextEquals("15")
    compose.onNodeWithTag(RollTestTags.SHEET).assertIsDisplayed()
  }

  @Test
  fun `the handle pushes it down without a drag, and pulls it back up`() {
    // Every state of the sheet is reachable from a tap: a gesture is not an
    // interface (`docs/architecture.md`, "Accessibility").
    show()
    val top = { compose.onNodeWithTag(RollTestTags.PULL_UP).getUnclippedBoundsInRoot().top }
    val arrived = top()

    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).performClick()
    compose.waitForIdle()
    val pushed = top()

    assertTrue("the sheet did not move down: $arrived then $pushed", pushed > arrived)

    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).performClick()
    compose.waitForIdle()

    assertTrue("the sheet did not come back up", top() < pushed)
  }

  @Test
  fun `pushed all the way down it still says what the roll came to`() {
    // The rule the pull-up exists to keep: a result that vanished would be a
    // result nobody could get back without re-rolling.
    show()

    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).performClick()
    compose.waitForIdle()

    compose.onNodeWithTag(RollTestTags.TOTAL).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).assertIsDisplayed()
  }

  @Test
  fun `a drag down the grip pushes it out of the way`() {
    show()
    val top = { compose.onNodeWithTag(RollTestTags.PULL_UP).getUnclippedBoundsInRoot().top }
    val arrived = top()

    // Further than the sheet can go, so it is the clamp and the nearer rest
    // deciding rather than however fast the harness happened to move.
    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).performTouchInput {
      down(center)
      repeat(STEPS) { moveBy(Offset(x = 0f, y = FAR / STEPS)) }
      up()
    }
    compose.waitForIdle()

    assertTrue("a drag down left the sheet where it was", top() > arrived)
  }

  @Test
  fun `the handle is worth pressing and says which way it goes`() {
    show()

    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).assertHeightIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithContentDescription("Result sheet").assertIsDisplayed()
  }

  @Test
  fun `the grip reports its height, so nothing is left under a parked sheet`() {
    var parked = 0f
    compose.setContent {
      Box(modifier = Modifier.fillMaxSize()) {
        PullUpResult(
          result = fourD6DropLowest(),
          onParked = { parked = it },
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }
    compose.waitForIdle()

    assertTrue("the grip measured nothing", parked > 0f)
  }

  private fun show() {
    compose.setContent {
      Box(modifier = Modifier.fillMaxSize()) {
        PullUpResult(
          result = fourD6DropLowest(),
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }
    compose.waitForIdle()
  }

  private fun fourD6DropLowest(): RollResult =
    RollResult(
      formula = "4d6dl1",
      total = 15,
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "4d6dl1",
            setId = "builtin",
            requestedSetId = "builtin",
            subtotal = 15,
            dice =
              listOf(
                die(0, 5),
                die(1, 4),
                die(2, 6, naturalMax = true),
                die(3, 1, notes = setOf(DieNote.Dropped)),
              ),
          ),
        ),
    )

  private fun die(
    index: Int,
    value: Int,
    naturalMax: Boolean = false,
    notes: Set<DieNote> = emptySet(),
  ): RolledDie =
    RolledDie(
      instanceIndex = index,
      dieId = "d6",
      value = value,
      naturalMax = naturalMax,
      notes = notes,
    )

  private companion object {
    /** Further down than any sheet this test draws can travel. */
    const val FAR = 2000f

    /** In steps, because one jump of the whole distance is not a drag. */
    const val STEPS = 20
  }
}
