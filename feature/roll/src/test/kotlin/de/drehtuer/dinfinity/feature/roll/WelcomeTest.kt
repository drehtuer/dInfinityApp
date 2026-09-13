package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The first thing a new install shows (`design/dInfinity.dc.html`, option 9a).
 *
 * It exists to say **there is nothing to set up** and then get out of the way.
 * Both of its buttons are ways forward and neither is a "skip": throw a d20
 * now, or go straight to the tray.
 */
@RunWith(RobolectricTestRunner::class)
class WelcomeTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `it says there is nothing to set up`() {
    show()

    compose.onNodeWithTag(RollTestTags.WELCOME).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_ROLL).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).assertIsDisplayed()
  }

  @Test
  fun `it counts what is actually installed rather than claiming a number`() {
    show(sets = 3)

    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("3", substring = true)
  }

  @Test
  fun `one set is one set, not one sets`() {
    show(sets = 1)

    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("1 dice set ", substring = true)
  }

  @Test
  fun `throwing a d20 now is one press`() {
    val thrown = mutableListOf<Unit>()
    show(onRollNow = { thrown += Unit })

    compose.onNodeWithTag(RollTestTags.WELCOME_ROLL).performClick()

    assertEquals(1, thrown.size)
  }

  @Test
  fun `going straight to the tray is the other press`() {
    val dismissed = mutableListOf<Unit>()
    show(onDismiss = { dismissed += Unit })

    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()

    assertEquals(1, dismissed.size)
  }

  private fun show(
    sets: Int = 1,
    onRollNow: () -> Unit = {},
    onDismiss: () -> Unit = {},
  ) {
    compose.setContent { Welcome(sets = sets, onRollNow = onRollNow, onDismiss = onDismiss) }
  }
}
