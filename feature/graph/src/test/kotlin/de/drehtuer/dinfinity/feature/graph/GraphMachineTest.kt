package de.drehtuer.dinfinity.feature.graph

import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.core.probability.DistributionResult
import de.drehtuer.dinfinity.core.probability.OutcomeGraph
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The outcome graph's state (`docs/TODO.md`, Step 4.2).
 *
 * The claim it exists for: **every number on the chart is the exact one**.
 * `core/probability` computes the distribution by convolution over the same
 * `RollPlan` a roll uses, and this is the layer between that and a picture —
 * so the test that matters most is that nothing is rounded, sampled or
 * approximated on the way (`docs/probability.md`).
 */
class GraphMachineTest {
  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  @Test
  fun `an empty field graphs nothing`() {
    assertEquals(GraphState.Empty, machine().state)
  }

  @Test
  fun `a formula that does not read says where it stops reading`() {
    val machine = machine()

    machine.type("3d6 +")

    val invalid = machine.state as GraphState.Invalid
    assertTrue("the error has no range to put a squiggle under", !invalid.error.range.isEmpty())
  }

  @Test
  fun `a die no installed set has is an error, not an empty chart`() {
    val machine = machine()

    machine.type("1d7")

    assertTrue(machine.state is GraphState.Invalid)
  }

  @Test
  fun `the bars are the exact distribution, value for value`() {
    // Not "close to". The chart is drawn from the same convolution that
    // `core/probability` is tested against, and this is the wire between them.
    val machine = machine()

    machine.type("2d6")

    val exact = pmfOf("2d6")
    val bars = (machine.state as GraphState.Graphed).bars
    assertEquals(exact.support.count(), bars.size)
    bars.forEach { bar ->
      assertEquals("bar ${bar.from}", exact.probabilityOf(bar.from), bar.exact, 0.0)
      assertEquals("bar ${bar.from}", exact.atLeast(bar.from), bar.atLeast, 0.0)
    }
  }

  @Test
  fun `the tallest bar is full height and the rest are measured against it`() {
    // Scaled to the tallest rather than to 1: `2d6` peaks under a fifth, and a
    // chart scaled to certainty is a chart of nothing.
    val machine = machine()

    machine.type("2d6")

    val bars = (machine.state as GraphState.Graphed).bars
    assertEquals(1.0, bars.maxOf { it.share }, 1e-12)
    assertTrue("a bar was taller than the tallest", bars.all { it.share <= 1.0 })
  }

  @Test
  fun `asking the at-least question redraws the same distribution`() {
    val machine = machine()
    machine.type("2d6")

    machine.show(GraphMode.AtLeast)

    val graphed = machine.state as GraphState.Graphed
    assertEquals(GraphMode.AtLeast, graphed.mode)
    // P(≥ min) is 1, and it is now the tallest bar.
    assertEquals(1.0, graphed.bars.first().share, 1e-12)
  }

  @Test
  fun `a great many totals are gathered into buckets rather than dropped`() {
    // A bucket is a sum: the area under the chart is still one. Drawing every
    // hundredth total instead would be a chart of a different distribution.
    val machine = machine()

    machine.type("100d6")

    val graphed = machine.state as GraphState.Graphed
    assertTrue("too many bars to draw", graphed.bars.size <= GraphBars.MOST_BARS)
    assertEquals(1.0, graphed.bars.sumOf { it.exact }, 1e-9)
    assertTrue("a bucket of one total was drawn for 501 totals", graphed.bars.any { !it.single })
  }

  @Test
  fun `the statistics come from the distribution, not from the bars`() {
    // A bucketed chart would give a mean that depends on how wide the phone is.
    val machine = machine()

    machine.type("100d6")

    val exact = pmfOf("100d6")
    val stats = (machine.state as GraphState.Graphed).stats
    assertEquals(exact.mean, stats.mean, 1e-9)
    assertEquals(exact.standardDeviation, stats.standardDeviation, 1e-9)
    assertEquals(exact.min, stats.lowest)
    assertEquals(exact.max, stats.highest)
    assertEquals(100, stats.dice)
  }

  @Test
  fun `tapping a bar gives its exact odds and its at-least odds`() {
    val machine = machine()
    machine.type("2d6")

    machine.pick(7)

    val picked = (machine.state as GraphState.Graphed).picked
    assertEquals(7, picked?.value)
    assertEquals(SIX_THIRTY_SIXTHS, picked?.exact ?: 0.0, 1e-12)
  }

  @Test
  fun `tapping outside the distribution is a tap on nothing`() {
    val machine = machine()
    machine.type("2d6")

    machine.pick(99)

    assertNull((machine.state as GraphState.Graphed).picked)
  }

  @Test
  fun `retyping drops the pick, because a bar at 14 is not the same bar`() {
    val machine = machine()
    machine.type("2d6")
    machine.pick(7)

    machine.type("2d20")

    assertNull((machine.state as GraphState.Graphed).picked)
  }

  @Test
  fun `the roll that opened the graph is marked on it`() {
    val machine = machine()
    machine.type("2d6")

    machine.rolled(total = 7, formula = "2d6")

    val rolled = (machine.state as GraphState.Graphed).rolled
    assertEquals(7, rolled?.value)
    assertEquals(SIX_THIRTY_SIXTHS, rolled?.exact ?: 0.0, 1e-12)
  }

  @Test
  fun `tidying the formula does not lose the mark`() {
    // Spacing, case and the label change nothing about a distribution, and a
    // mark that vanished because somebody deleted a space would look like a bug.
    val machine = machine()
    machine.rolled(total = 7, formula = "2d6 [Attack]")

    machine.type("2D6")

    assertNotNull((machine.state as GraphState.Graphed).rolled)
  }

  @Test
  fun `changing the dice takes the mark away`() {
    // A line saying "your roll" on a chart of a different formula is pointing
    // at somebody else's roll (`design/dInfinity.dc.html`, option 7a).
    val machine = machine()
    machine.rolled(total = 7, formula = "2d6")

    machine.type("2d8")

    assertNull((machine.state as GraphState.Graphed).rolled)
  }

  @Test
  fun `a throw the table would refuse still graphs`() {
    // The graph is about the formula. `500d6` is refused at the tray and
    // nowhere else (`docs/probability.md`, `docs/tables.md`).
    val machine = machine()

    machine.type("500d6")

    assertTrue("a formula too big to roll would not graph", machine.state is GraphState.Graphed)
  }

  @Test
  fun `a formula past what can be computed exactly says so rather than guessing`() {
    val machine = machine()

    machine.type(TOO_BIG)

    assertTrue("a formula past the limit was graphed anyway", machine.state is GraphState.TooLarge)
  }

  @Test
  fun `a label is carried through to the chart's title`() {
    val machine = machine()

    machine.type("2d6 [Fireball]")

    assertEquals("Fireball", (machine.state as GraphState.Graphed).label)
  }

  private fun machine() = GraphMachine(catalog)

  private fun pmfOf(formula: String) =
    with(FormulaParser.parseOrNull(formula)!!) {
      val planned = RollPlanner.plan(this, catalog) as PlanResult.Planned
      (OutcomeGraph.of(this, planned.plan) as DistributionResult.Computed).pmf
    }

  private companion object {
    /** P(2d6 = 7). */
    const val SIX_THIRTY_SIXTHS = 6.0 / 36.0

    /** Two rolls multiplied together, which the exact graph will not do at size. */
    const val TOO_BIG = "300d20 * 300d20"
  }
}
