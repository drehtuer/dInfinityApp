package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
 * The first thing a new install shows (`design/dInfinity.dc.html`, option 9a).
 *
 * It exists to say **there is nothing to set up** and then get out of the way.
 * All four of its buttons are ways forward and none of them is a "skip": throw
 * a d20 now, go straight to the tray, bring saved rolls in, or add somebody
 * else's dice.
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
    compose.onNodeWithTag(RollTestTags.WELCOME_IMPORT).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS_ADD).assertIsDisplayed()
  }

  @Test
  fun `it counts what is actually there rather than claiming a number`() {
    // All three, and none of them written into the sentence: the line used to
    // say "0 saved rolls" whatever was saved, because there had been nowhere
    // to keep one when it was written (`docs/TODO.md`, 4.1).
    show(what = WhatIsThere(sets = 3, savedRolls = 7, sessions = 2))

    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("3 dice sets", substring = true)
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("7 saved rolls", substring = true)
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("2 sessions", substring = true)
  }

  @Test
  fun `one of each is one of each, not one sets`() {
    show(what = WhatIsThere(sets = 1, savedRolls = 1, sessions = 1))

    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("1 dice set ", substring = true)
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("1 saved roll ", substring = true)
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("1 session", substring = true)
  }

  @Test
  fun `a fresh install says so rather than saying nothing`() {
    show(what = WhatIsThere(sets = 1, savedRolls = 0, sessions = 0))

    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("0 saved rolls", substring = true)
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("0 sessions", substring = true)
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

  @Test
  fun `the two imports are ways in as well, and neither of them dismisses it`() {
    // Going to fetch something and coming back to a tray that has forgotten it
    // ever said hello would leave somebody wondering what to do next — and the
    // count line has something new to say when they return.
    val dismissed = mutableListOf<Unit>()
    val went = mutableListOf<String>()
    show(onDismiss = { dismissed += Unit }, onImport = { went += "import" }, onAddSets = { went += "sets" })

    compose.onNodeWithTag(RollTestTags.WELCOME_IMPORT).performClick()
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS_ADD).performClick()

    assertEquals(listOf("import", "sets"), went)
    assertEquals("a way in dismissed the welcome", 0, dismissed.size)
  }

  @Test
  fun `a recomposition around it that changes nothing leaves it alone`() {
    // Every parameter of a composable is a branch that says "nothing changed,
    // skip it", and a single-pass test only ever takes one side of it. One
    // that skipped wrongly would come back without its counts or without a
    // button (`docs/TODO.md`, Coverage).
    var tick by mutableStateOf(0)
    compose.setContent {
      Column {
        Text("tick $tick")
        Welcome(what = WhatIsThere(sets = 1, savedRolls = 2, sessions = 3), onRollNow = {}, onDismiss = {})
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("2 saved rolls", substring = true)
    compose.onNodeWithTag(RollTestTags.WELCOME_ROLL).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_IMPORT).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS_ADD).assertIsDisplayed()
  }

  @Test
  fun `a count that changes is a line that changes`() {
    // The other side of the same branch, and the reason the line is a count
    // rather than a sentence: importing saved rolls is one of the ways out of
    // this screen, and coming back should show what arrived.
    var what by mutableStateOf(WhatIsThere(sets = 1))
    compose.setContent { Welcome(what = what, onRollNow = {}, onDismiss = {}) }
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("0 saved rolls", substring = true)

    compose.runOnIdle { what = what.copy(savedRolls = 4) }

    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("4 saved rolls", substring = true)
  }

  @Test
  fun `a caller that wires neither import gets buttons that do nothing rather than a crash`() {
    // The two defaults. A welcome drawn by something that has nowhere to send
    // anybody — a preview, or a test of the screen around it — still draws
    // four buttons, and pressing the two it cannot honour changes nothing.
    compose.setContent { Welcome(what = WhatIsThere(sets = 1), onRollNow = {}, onDismiss = {}) }

    compose.onNodeWithTag(RollTestTags.WELCOME_IMPORT).performClick()
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS_ADD).performClick()

    compose.onNodeWithTag(RollTestTags.WELCOME).assertIsDisplayed()
  }

  private fun show(
    what: WhatIsThere = WhatIsThere(sets = 1),
    onRollNow: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onImport: () -> Unit = {},
    onAddSets: () -> Unit = {},
  ) {
    compose.setContent {
      Welcome(
        what = what,
        onRollNow = onRollNow,
        onDismiss = onDismiss,
        onImport = onImport,
        onAddSets = onAddSets,
      )
    }
  }
}
