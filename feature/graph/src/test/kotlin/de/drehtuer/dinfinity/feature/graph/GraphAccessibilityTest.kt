package de.drehtuer.dinfinity.feature.graph

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the outcome graph says to somebody who cannot see it
 * (`docs/architecture.md`, "Accessibility").
 *
 * This is the screen where an unlabelled drawing is worst: the numbers beside
 * the chart say the mean and the deviation, and the chart says the *shape* —
 * which is the thing a distribution is looked at for. A `Canvas` with no
 * description is that whole answer missing.
 */
@RunWith(RobolectricTestRunner::class)
class GraphAccessibilityTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the chart says what shape it is`() {
    show("2d6")

    compose.onNodeWithTag(GraphTestTags.CHART).assertContentDescriptionContains(
      "Bar chart of 11 totals. They run from 2 to 12. The likeliest is 7.",
    )
  }

  @Test
  fun `the line the roll is marked with is said as well as drawn`() {
    // On the chart it is a stroke in the accent and nothing else.
    show("2d6", rolled = 9)

    compose.onNodeWithTag(GraphTestTags.CHART).assertContentDescriptionContains(
      "Bar chart of 11 totals. They run from 2 to 12. The likeliest is 7. Your roll, 9, is marked.",
    )
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
