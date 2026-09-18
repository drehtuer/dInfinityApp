package de.drehtuer.dinfinity.feature.roll

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.drehtuer.dinfinity.core.model.SavedRollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The bottom edge of the roll screen, which two pull-ups share
 * (`design/dInfinity.dc.html`, options 1c and 1e–1g;
 * `docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * Its own class rather than more of [RollScreenTest], because it is its own
 * question: the result arrives on its own and the saved rolls are pulled up
 * by hand, they may not both be up, and neither may ever be out of reach.
 * What a rest *is* and what a drag settles to is [SheetSlideTest]'s, on the
 * JVM; the rule about the pair of them is [BottomEdgeTest]'s. What is left
 * here is the two of them on one screen with a roll going on between them.
 */
@RunWith(RobolectricTestRunner::class)
class RollScreenEdgesTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a tap on the strip fills the field and throws nothing`() {
    // The slot is how the roll screen is handed saved rolls without knowing
    // what one is. What it hands back is the formula *and* which roll put it
    // there, so the throw that follows can be recorded as that roll's
    // (`docs/statistics.md`, per saved roll and per group).
    //
    // **It used to throw.** That made the strip the one control in the app
    // that rolled without a hand, and a saved roll brushed by a thumb was
    // dice already on the table (`docs/physics-and-rendering.md`, "Starting
    // a roll").
    lateinit var presenter: RollPresenter
    val tray = DirectTray()
    compose.setContent {
      presenter = remember { rollPresenter(tray, LandingRolls(mapOf(0 to 0))) }
      RollScreen(
        presenter = presenter,
        strip = { fill ->
          Button(
            onClick = { fill("1d20", SavedRollSource(rollId = "fireball", groupId = "thorin")) },
            modifier = Modifier.testTag(STRIP_TAG),
          ) { Text("Fireball") }
        },
      )
    }

    openSavedRolls()
    compose.onNodeWithTag(STRIP_TAG).performClick()

    assertEquals("1d20", presenter.text)
    assertTrue("the strip threw the roll rather than filling the field", presenter.state is RollState.Ready)
    assertEquals("the strip threw something", 0, tray.throws)
  }

  @Test
  fun `and the shake after it is the throw`() {
    // The formula reached the field with the saved roll behind it, so the
    // throw the hand makes is still that roll's.
    lateinit var presenter: RollPresenter
    compose.setContent {
      presenter = remember { rollPresenter(DirectTray(), LandingRolls(mapOf(0 to 0))) }
      RollScreen(
        presenter = presenter,
        strip = { fill ->
          Button(
            onClick = { fill("1d20", SavedRollSource(rollId = "fireball", groupId = "thorin")) },
            modifier = Modifier.testTag(STRIP_TAG),
          ) { Text("Fireball") }
        },
      )
    }
    openSavedRolls()
    compose.onNodeWithTag(STRIP_TAG).performClick()

    shake()

    compose.waitUntil(PATIENCE) { presenter.state is RollState.Settled }
    assertEquals("1d20", presenter.text)
  }

  @Test
  fun `a typed formula from the slot belongs to no saved roll`() {
    // The other side of the slot: a caller with nothing to attribute passes
    // none, and the screen takes the ordinary path.
    lateinit var presenter: RollPresenter
    compose.setContent {
      presenter = remember { rollPresenter(DirectTray(), LandingRolls(mapOf(0 to 0))) }
      RollScreen(
        presenter = presenter,
        strip = { fill ->
          Button(onClick = { fill("1d6", null) }, modifier = Modifier.testTag(STRIP_TAG)) { Text("Two") }
        },
      )
    }

    openSavedRolls()
    compose.onNodeWithTag(STRIP_TAG).performClick()
    shake()

    compose.waitUntil(PATIENCE) { presenter.state is RollState.Settled }
    assertEquals("1d6", presenter.text)
  }

  @Test
  fun `the saved rolls are parked, so the whole table is shown`() {
    // The last plate standing across the bottom of the felt, and the second
    // device session asked for the same of it as of the picker and the
    // result (`design/dInfinity.dc.html`, option 1c).
    showWithStrip()

    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).assertIsDisplayed()
    compose.onNodeWithTag(STRIP_TAG).assertIsNotDisplayed()
  }

  @Test
  fun `a result that lands takes the bottom edge from the saved rolls`() {
    // Two pull-ups on one edge, and the rule that they are never both up is
    // [BottomEdge]'s. A total arriving under a strip somebody is reading is
    // a number nobody sees.
    val presenter = showWithStrip()
    openSavedRolls()
    compose.onNodeWithTag(STRIP_TAG).assertIsDisplayed()
    typeFormula("1d20")

    shake()

    compose.waitUntil(PATIENCE) { presenter.state is RollState.Settled }
    compose.waitForIdle()

    compose.onNodeWithTag(RollTestTags.SHEET).assertIsDisplayed()
    // Parked behind the sheet rather than beside it: the saved rolls went
    // down to the bottom edge, where the result — an opaque surface drawn
    // over them — now is.
    val sheet = compose.onNodeWithTag(RollTestTags.PULL_UP).getUnclippedBoundsInRoot()
    val rolls = compose.onNodeWithTag(STRIP_TAG).getUnclippedBoundsInRoot()

    assertTrue("the saved rolls stayed up over the result: $rolls against $sheet", rolls.top >= sheet.top)
  }

  @Test
  fun `and pulling the saved rolls back up pushes the result down`() {
    val presenter = showWithStrip()
    typeFormula("1d20")
    shake()
    compose.waitUntil(PATIENCE) { presenter.state is RollState.Settled }
    compose.waitForIdle()

    openSavedRolls()

    val sheet = compose.onNodeWithTag(RollTestTags.PULL_UP).getUnclippedBoundsInRoot()
    val rolls = compose.onNodeWithTag(STRIP_TAG).getUnclippedBoundsInRoot()

    assertTrue("the saved rolls did not clear the result: $rolls against $sheet", rolls.top < sheet.top)
    compose.onNodeWithTag(STRIP_TAG).assertIsDisplayed()
    // Pushed down is not gone: the total is the one thing a result may never
    // take away from the screen.
    compose.onNodeWithTag(RollTestTags.TOTAL).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.SHEET).assertIsNotDisplayed()
  }

  @Test
  fun `what the next shake is worth stays readable once the sheet is pushed down`() {
    // The fourth thing the device session found: during a chain of re-rolls
    // the expected range flashed past and was then covered by the result. It
    // is in the sheet's grip now, which is the half that survives a push
    // down ([PullUpResult]).
    val presenter = show(faces = mapOf(0 to 0, 1 to 0, 2 to 0))
    typeFormula("3d6")
    shake()
    compose.waitUntil(PATIENCE) { presenter.state is RollState.Settled }
    compose.waitForIdle()

    compose.onNodeWithTag(RollTestTags.RESULT_HANDLE).performClick()
    compose.waitForIdle()

    compose.onNodeWithTag(RollTestTags.SHEET).assertIsNotDisplayed()
    compose.onNodeWithTag(RollTestTags.EXPECTED).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertIsDisplayed()
  }

  /** Pulls the saved rolls up, the way a player does — they are put away. */
  private fun openSavedRolls() {
    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).performClick()
    compose.waitForIdle()
  }

  /**
   * The screen with something in the saved-roll slot.
   *
   * The slot is how `:app` hands the strip in without this module knowing
   * what a saved roll is, and it is empty by default — so a test about where
   * the strip *sits* has to put something in it.
   */
  private fun showWithStrip(): RollPresenter {
    lateinit var presenter: RollPresenter
    compose.setContent {
      presenter = remember { rollPresenter(DirectTray(), LandingRolls(mapOf(0 to 0))) }
      RollScreen(
        presenter = presenter,
        strip = { fill ->
          Button(onClick = { fill("1d20", null) }, modifier = Modifier.testTag(STRIP_TAG)) { Text("Fireball") }
        },
      )
    }
    compose.waitForIdle()
    return presenter
  }

  /**
   * Types a formula the way a player does: bring the drawer in, then type.
   *
   * The formula is not on the tray at all until somebody asks for it
   * (`design/dInfinity.dc.html`, option 2a).
   */
  private fun typeFormula(text: String) {
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(text)
  }

  /**
   * Throws the dice the only way the screen offers one that is not a hand:
   * the table's custom accessibility action
   * (`docs/architecture.md`, "Accessibility").
   */
  private fun shake() {
    val throwThem =
      compose
        .onNodeWithTag(RollTestTags.TRAY)
        .fetchSemanticsNode()
        .config[SemanticsActions.CustomActions]
        .single()
    compose.runOnUiThread { throwThem.action() }
    compose.waitForIdle()
  }

  private fun show(faces: Map<Int, Int>): RollPresenter {
    val presenter = rollPresenter(DirectTray(), LandingRolls(faces))
    compose.setContent { RollScreen(presenter = presenter) }
    compose.waitForIdle()
    return presenter
  }

  private companion object {
    const val STRIP_TAG = "test:strip"
    const val PATIENCE = 2_000L
  }
}
