package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import de.drehtuer.dinfinity.render.filament.TrayPick
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The table, cleared of the controls over it (`docs/architecture.md`,
 * decision 83; `docs/physics-and-rendering.md`, "Clearing the table").
 *
 * Two of the owner's asks from the Pixel 10a, on one screen with a roll going
 * on: a shake folds the top away so nothing stands over the dice it threw,
 * and a double tap anywhere on the tray takes every pull-up, pull-down and
 * tab off it and the next puts them back. What the four facts are and how
 * they move is [ControlsTest]'s, on the JVM; what is asked here is that the
 * screen draws them, and that a double tap is never a pick.
 */
@RunWith(RobolectricTestRunner::class)
class ClearViewTest {
  @get:Rule
  val compose = createComposeRule()

  @get:Rule
  val shaking = ShakingHand()

  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `a shake folds the formula into the shut dice pull-down`() {
    show()
    typeFormula("3d6")
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()

    shake()

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.DICE_MENU).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.PICKER).assertDoesNotExist()
  }

  @Test
  fun `a shake shuts whichever menu was open`() {
    show()
    typeFormula("3d6")
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA_DRAWER).assertIsDisplayed()

    shake()

    compose.onNodeWithTag(RollTestTags.FORMULA_DRAWER).assertDoesNotExist()

    compose.onNodeWithTag(RollTestTags.DICE_MENU).performClick()
    compose.onNodeWithTag(RollTestTags.PICKER).assertIsDisplayed()
    shake()
    compose.onNodeWithTag(RollTestTags.PICKER).assertDoesNotExist()
  }

  @Test
  fun `opening the dice brings the formula back, and it stays when they are shut`() {
    show()
    typeFormula("3d6")
    shake()

    compose.onNodeWithTag(RollTestTags.DICE_MENU).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.DICE_MENU).performClick()

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()
  }

  @Test
  fun `a shake that throws nothing leaves the top as it was`() {
    // A formula that does not read throws nothing, so there are no dice to
    // clear the view of — and the tab is where the mistake is marked.
    show()
    typeFormula("3d6 +")
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()

    shake()

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()
  }

  @Test
  fun `a double tap clears every control off the table and the next brings them back as they were`() {
    val presenter = show()
    typeFormula("3d6")
    shake()
    lookAtTheFelt()
    compose.onNodeWithTag(RollTestTags.TOP).assertExists()
    assertTrue(presenter.state is RollState.Settled)

    doubleTap()

    compose.onNodeWithTag(RollTestTags.TOP).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.DICE_MENU).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).assertDoesNotExist()

    doubleTap()

    compose.onNodeWithTag(RollTestTags.DICE_MENU).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).assertIsDisplayed()
    // Folded, as the throw left it, and the result pushed down, as the
    // player left it.
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.SHEET).assertIsNotDisplayed()
  }

  @Test
  fun `a double tap on a die picks nothing, and a single tap there does`() {
    val presenter = show()
    typeFormula("3d6")
    shake()
    lookAtTheFelt()
    val at = whereIs(presenter, 1)

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      click(at)
      advanceEventTime(viewConfiguration.doubleTapMinTimeMillis * 2)
      click(at)
    }
    compose.mainClock.advanceTimeBy(PAST_A_DOUBLE_TAP)
    compose.waitForIdle()

    assertTrue("a double tap picked a die: ${presenter.picked}", presenter.picked.isEmpty())
    compose.onNodeWithTag(RollTestTags.TOP).assertDoesNotExist()

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput { click(at) }
    compose.mainClock.advanceTimeBy(PAST_A_DOUBLE_TAP)
    compose.waitForIdle()

    assertEquals("a single tap on the cleared table did not pick", setOf(1), presenter.picked)
  }

  @Test
  fun `a screen reader clears and restores the table with the tray's action`() {
    show()
    typeFormula("3d6")

    trayAction("Hide the controls")
    compose.onNodeWithTag(RollTestTags.TOP).assertDoesNotExist()

    trayAction("Show the controls")
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()
  }

  @Test
  fun `a shake on a cleared table brings the controls back, with the top folded`() {
    // The result arrives with the throw, and a result behind a cleared table
    // would be a total nobody saw.
    show()
    typeFormula("3d6")
    trayAction("Hide the controls")

    shake()

    compose.onNodeWithTag(RollTestTags.DICE_MENU).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertDoesNotExist()
  }

  private fun show(): RollPresenter {
    val presenter = rollPresenter(DirectTray(), PassingRolls(listOf(threeDown)))
    compose.setContent { RollScreen(presenter = presenter) }
    compose.waitForIdle()
    return presenter
  }

  /** Types [text] and shuts the drawer again, as a player does before reaching for the felt. */
  private fun typeFormula(text: String) {
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(text)
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.waitForIdle()
  }

  private fun shake() {
    compose.runOnUiThread { shaking.hand.shake() }
    compose.waitForIdle()
  }

  /** Pushes the result sheet down, which is what a player does to see the dice. */
  private fun lookAtTheFelt() {
    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).performClick()
    compose.waitForIdle()
  }

  private fun doubleTap() {
    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      click(center)
      advanceEventTime(viewConfiguration.doubleTapMinTimeMillis * 2)
      click(center)
    }
    compose.mainClock.advanceTimeBy(PAST_A_DOUBLE_TAP)
    compose.waitForIdle()
  }

  private fun trayAction(label: String) {
    val action =
      compose
        .onNodeWithTag(RollTestTags.TRAY)
        .fetchSemanticsNode()
        .config[SemanticsActions.CustomActions]
        .single()
    assertEquals(label, action.label)
    compose.runOnIdle { action.action() }
    compose.waitForIdle()
  }

  /** Where on the tray node the die at [position] is drawn. */
  private fun whereIs(
    presenter: RollPresenter,
    position: Int,
  ): Offset {
    val size = compose.onNodeWithTag(RollTestTags.TRAY).fetchSemanticsNode().size
    val ratio = size.width.toDouble() / size.height
    val mark =
      requireNotNull(TrayPick.through(geometry, ratio, presenter.looking).markOf(presenter.onTheTable[position]))
    return Offset((mark.acrossFraction * size.width).toFloat(), (mark.downFraction * size.height).toFloat())
  }

  /** Three dice down along the middle of the tray, a hand's width apart. */
  private val threeDown =
    SimulationOutcome(
      faces = mapOf(0 to 0, 1 to 1, 2 to 2),
      restingAt =
        (0..2).associateWith { at ->
          RestingPlace(Vector3((at - 1) * APART_MM, 0.0, REST_HEIGHT_MM), Quaternion.Identity)
        },
    )

  private companion object {
    const val APART_MM = 40.0
    const val REST_HEIGHT_MM = 8.0

    /** Twice the platform's double-tap timeout, which is what a lone tap waits out. */
    val PAST_A_DOUBLE_TAP: Long = android.view.ViewConfiguration.getDoubleTapTimeout() * 2L
  }
}
