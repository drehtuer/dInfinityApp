package de.drehtuer.dinfinity.feature.graph

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The outcome graph, drawn (`design/dInfinity.dc.html`, options 1k and 7a).
 *
 * What it can check is that the distribution reaches the screen: that the
 * chart is there for a formula that graphs, that a formula past the exact
 * limit says so rather than showing an approximation, and that the roll which
 * opened it is named. What it cannot check is whether the bars look like a
 * distribution — that is a person with a phone.
 */
@RunWith(RobolectricTestRunner::class)
class GraphScreenTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a formula that graphs gets a chart`() {
    show("3d6")

    compose.onNodeWithTag(GraphTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(GraphTestTags.CHART).assertIsDisplayed()
    compose.onNodeWithTag(GraphTestTags.FORMULA).assertTextContains("3d6")
  }

  @Test
  fun `the numbers beside the chart are the ones a picture cannot say`() {
    show("2d6")

    compose.onNodeWithTag(GraphTestTags.statOf("mean")).assertTextContains("7.0", substring = true)
    compose.onNodeWithTag(GraphTestTags.statOf("range")).assertTextContains("2–12", substring = true)
    compose.onNodeWithTag(GraphTestTags.statOf("dice")).assertTextContains("2", substring = true)
  }

  @Test
  fun `an impossible-looking chance is never printed as zero`() {
    // `8d20` gives 160 about one time in twenty-five billion. Printing that as
    // "0.0 %" would be the app saying something cannot happen when it can,
    // which on a screen about probability is the one unforgivable answer.
    show("8d20")

    compose.onNodeWithTag(GraphTestTags.statOf("highest")).assertTextContains("< 0.1 %", substring = true)
  }

  @Test
  fun `the question can be changed and the chart follows`() {
    show("2d6")

    compose.onNodeWithTag(GraphTestTags.modeOf(GraphMode.AtLeast)).performClick()

    compose.onNodeWithTag(GraphTestTags.modeOf(GraphMode.AtLeast)).assertIsSelected()
    compose.onNodeWithTag(GraphTestTags.CHART).assertIsDisplayed()
  }

  @Test
  fun `nothing tapped yet says so rather than showing a number for nothing`() {
    show("2d6")

    compose.onNodeWithTag(GraphTestTags.PICKED).assertTextContains("Tap a bar", substring = true)
  }

  @Test
  fun `the roll that opened the graph is named on it`() {
    show("2d6", rolled = 7)

    compose.onNodeWithTag(GraphTestTags.ROLLED).assertTextContains("7", substring = true)
    compose.onNodeWithTag(GraphTestTags.ROLLED).assertTextContains("16.7 %", substring = true)
  }

  @Test
  fun `a graph opened without a roll marks nothing`() {
    show("2d6")

    compose.onNodeWithTag(GraphTestTags.ROLLED).assertDoesNotExist()
  }

  @Test
  fun `a graph opened on a formula that does not read says so instead of drawing`() {
    show("3d6 +")

    compose.onNodeWithTag(GraphTestTags.INVALID).assertIsDisplayed()
    compose.onNodeWithTag(GraphTestTags.CHART).assertDoesNotExist()
  }

  @Test
  fun `a formula past what can be worked out exactly says so rather than guessing`() {
    // An approximated curve presented as the odds is a number somebody bets on
    // (`docs/probability.md`).
    show("300d20 * 300d20")

    compose.onNodeWithTag(GraphTestTags.TOO_LARGE).assertIsDisplayed()
    compose.onNodeWithTag(GraphTestTags.CHART).assertDoesNotExist()
  }

  @Test
  fun `the formula can be changed here, and the chart follows`() {
    // The same live-validated field the tray has, so the two screens cannot
    // come to disagree about whether a formula is valid.
    show("2d6")

    compose.onNodeWithTag(GraphTestTags.FORMULA).performTextReplacement("1d20")

    compose.onNodeWithTag(GraphTestTags.statOf("range")).assertTextContains("1–20", substring = true)
  }

  @Test
  fun `a formula typed here that does not read is squiggled in the field`() {
    show("2d6")

    compose.onNodeWithTag(GraphTestTags.FORMULA).performTextReplacement("3d6 +")

    compose.onNodeWithTag(GraphTestTags.INVALID).assertIsDisplayed()
    compose.onNodeWithTag(GraphTestTags.CHART).assertDoesNotExist()
  }

  @Test
  fun `a graph opened with no formula at all says where one comes from`() {
    show("")

    compose.onNodeWithTag(GraphTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun `a throw too big for any table still graphs`() {
    show("500d6")

    compose.onNodeWithTag(GraphTestTags.CHART).assertIsDisplayed()
  }

  private fun show(
    formula: String,
    rolled: Int? = null,
  ) {
    val presenter =
      GraphPresenter(
        machine = GraphMachine(DiceCatalog.of(listOf(BuiltinDiceSet.set))),
        formula = formula,
        rolled = rolled,
      )
    compose.setContent { GraphScreen(presenter = presenter) }
  }
}
