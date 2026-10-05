package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The screen over an exploding die that has earned a throw
 * (`docs/dice-notation.md`, "Evaluation"; `design/dInfinityPhone.dc.html`, the
 * `earned` block).
 *
 * The machine's half — that a six earns a throw rather than taking one — is
 * `RollMachineTest`'s and `RollPresenterTest`'s, and the plate's words are
 * `TrayPlatesTest`'s. What only the whole screen can show is that the three
 * places a waiting chain is announced all appear together when it waits — the
 * plate over the felt, the prompt over the tray and the tray's own
 * description — and that the shake that answers them takes all three away
 * with a total. [ThrowAgainTest] is the same question for dice nobody could
 * read.
 */
@RunWith(RobolectricTestRunner::class)
class EarnedThrowTest {
  @get:Rule
  val compose = createComposeRule()

  @get:Rule
  val shaking = ShakingHand()

  @Test
  fun `a six that explodes asks for the throw it earned, and scores nothing yet`() {
    val rolls = PassingRolls(listOf(SIX, ONE))
    compose.setContent { RollScreen(presenter = rollPresenter(DirectTray(), rolls)) }

    typeFormula("1d6!")
    shake()

    compose.onNodeWithTag(RollTestTags.SHAKE_AGAIN).assertIsDisplayed()
    compose
      .onNodeWithTag(RollTestTags.SHAKE_PROMPT_TEXT, useUnmergedTree = true)
      .assertTextEquals("Shake to throw the earned die")
    compose
      .onNodeWithTag(RollTestTags.TRAY)
      .assertContentDescriptionEquals("Dice tray, 1 die down, shake again for the one it earned")
    compose.onNodeWithTag(RollTestTags.TOTAL).assertDoesNotExist()
    assertEquals("the app threw the earned die by itself", 1, rolls.started.size)
  }

  @Test
  fun `the shake throws the earned die alone, and the total is both of them`() {
    val rolls = PassingRolls(listOf(SIX, ONE))
    compose.setContent { RollScreen(presenter = rollPresenter(DirectTray(), rolls)) }
    typeFormula("1d6!")
    shake()

    assertTrue("the earned throw would not go into the air", shake())

    assertEquals(
      "the die that was down went back into the air",
      1,
      rolls.started
        .last()
        .dice.size,
    )
    compose.onNodeWithTag(RollTestTags.SHAKE_AGAIN).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.SHAKE_PROMPT).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertTextEquals("7")
  }

  private fun typeFormula(text: String) {
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(text)
  }

  private fun shake(): Boolean {
    val threw = compose.runOnUiThread { shaking.hand.shake() }
    compose.waitForIdle()
    return threw
  }

  private companion object {
    /**
     * The built-in d6 on its sixth face, which explodes, lying in the middle
     * of the tray — where it lies is what makes it a die that is down.
     */
    val SIX =
      SimulationOutcome(
        faces = mapOf(0 to 5),
        restingAt = mapOf(0 to RestingPlace(Vector3(0.0, 0.0, 8.0), Quaternion.Identity)),
      )

    /** And on its first, which does not. */
    val ONE = SimulationOutcome(faces = mapOf(0 to 0))
  }
}
