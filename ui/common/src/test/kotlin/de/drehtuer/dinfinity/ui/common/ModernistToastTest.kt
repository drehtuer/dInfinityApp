package de.drehtuer.dinfinity.ui.common

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.milliseconds

/**
 * A toast says one thing and takes itself away.
 *
 * Whether it *looks* inverted is the device suite's to say; what is asserted
 * here is the half a screen cannot get wrong by accident — that the words are
 * there, that nothing has to press anything to be rid of them, and that the
 * caller is told when they have gone. A toast that stayed would be a notice
 * sitting over a tray for the rest of the session.
 */
@RunWith(RobolectricTestRunner::class)
class ModernistToastTest {
  @get:Rule
  val compose = createComposeRule()

  private var dismissed = 0

  private fun show(after: Long = 2_600) {
    // By hand: with the clock advancing on its own the delay below would be
    // stepped through before the first assertion.
    compose.mainClock.autoAdvance = false
    compose.setContent {
      ModernistToast(
        text = WORDS,
        onDismissed = { dismissed++ },
        dismissAfter = after.milliseconds,
      )
    }
  }

  @Test
  fun `it says its words`() {
    show()

    compose.onNodeWithTag(ToastTestTags.TOAST).assertIsDisplayed()
    compose.onNodeWithTag(ToastTestTags.TOAST).assertTextEquals(WORDS)
  }

  @Test
  fun `it takes itself away after its time, and not before`() {
    show()

    compose.mainClock.advanceTimeBy(2_500)
    compose.waitForIdle()
    assertEquals("still inside its time", 0, dismissed)

    compose.mainClock.advanceTimeBy(200)
    compose.waitForIdle()
    assertEquals(1, dismissed)
  }

  @Test
  fun `its time is the caller's to set`() {
    show(after = 100)

    compose.mainClock.advanceTimeBy(150)
    compose.waitForIdle()

    assertEquals(1, dismissed)
  }

  @Test
  fun `it is a notice rather than a control`() {
    // Nothing on it can be pressed: everything it says is already true of a
    // control the player is holding.
    show()

    compose.onNodeWithTag(ToastTestTags.TOAST).assertHasNoClickAction()
  }

  private companion object {
    const val WORDS = "Back again to leave dInfinity"
  }
}
