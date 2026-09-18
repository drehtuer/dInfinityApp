package de.drehtuer.dinfinity

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.theme.DInfinityTheme
import de.drehtuer.dinfinity.ui.common.ToastTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The words, the window and the way out, on the screen they belong to
 * (`docs/architecture.md`, "Navigation").
 *
 * The rule itself is proved in [LeavingTheAppTest], without a clock and
 * without a window. What is here is the half only a screen can answer: that a
 * press raises the toast, that a second one leaves, and that a press after the
 * window has closed does not.
 */
@RunWith(RobolectricTestRunner::class)
class TwoStageBackTest {
  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  /** The clock the composable reads, moved by hand rather than by waiting. */
  private var now = 0L

  private var left = 0

  private fun armIt() {
    // The clock is driven by hand throughout: the toast's own 2.6 s would
    // otherwise be advanced through by the first `waitForIdle`, and every
    // assertion below would be about a toast that had already gone.
    compose.mainClock.autoAdvance = false
    compose.setContent {
      DInfinityTheme {
        TwoStageBack(onLeave = { left++ }, now = { now })
      }
    }
    settle()
  }

  /**
   * A frame, and then whatever it set off.
   *
   * With the clock held still nothing reaches the screen on its own: frames
   * are what turn a state change into a layout, and the toast is only on
   * screen once it has been laid out.
   */
  private fun settle() {
    compose.waitForIdle()
    // Twice: with the clock held still, one pass turns the state change into a
    // composition and the next turns that composition into a layout — and a
    // toast that has not been laid out is not yet on screen.
    repeat(PASSES) {
      compose.mainClock.advanceTimeBy(A_FEW_FRAMES)
      compose.waitForIdle()
    }
  }

  /** System back, as the platform delivers it. */
  private fun back() {
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    settle()
  }

  @Test
  fun `the first press says another one would leave, and leaves nothing`() {
    armIt()

    back()

    compose.onNodeWithTag(ToastTestTags.TOAST).assertIsDisplayed()
    // Read from the resource rather than spelled out again here: a test that
    // repeats the sentence asserts the same typo twice.
    val words = ApplicationProvider.getApplicationContext<Context>().getString(R.string.back_again)
    compose.onNodeWithText(words).assertIsDisplayed()
    assertEquals("nothing should have left on one press", 0, left)
  }

  @Test
  fun `a second press inside the window leaves`() {
    armIt()

    back()
    now = 1_500
    back()

    assertEquals(1, left)
    compose.onNodeWithTag(ToastTestTags.TOAST).assertDoesNotExist()
  }

  @Test
  fun `a press after the window has closed arms again rather than leaving`() {
    armIt()

    back()
    now = 60_000
    back()

    assertEquals("a stray swipe a minute later must not close the app", 0, left)
    compose.onNodeWithTag(ToastTestTags.TOAST).assertIsDisplayed()
  }

  @Test
  fun `the toast takes itself away, and the arming with it`() {
    armIt()

    back()
    compose.onNodeWithTag(ToastTestTags.TOAST).assertIsDisplayed()
    // Past the toast's own 2.6 s, without waiting 2.6 s.
    compose.mainClock.advanceTimeBy(2_700)
    settle()

    compose.onNodeWithTag(ToastTestTags.TOAST).assertDoesNotExist()
  }

  private companion object {
    /**
     * Long enough for a state change to become something on screen, and far
     * short of the toast's own 2.6 s — two of these still leave two seconds of
     * it to run.
     */
    const val A_FEW_FRAMES = 100L

    /** Composition, then layout. */
    const val PASSES = 2
  }
}
