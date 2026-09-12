package de.drehtuer.dinfinity.core.probability

import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.Formula
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.ParseResult
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutcomeGraphTest {
  private val catalog = DiceCatalog.of(listOf(StandardDice.set()))

  @Test
  fun `a d20 is flat, which is the whole reason not to draw a bell curve`() {
    val d20 = graph("1d20")
    assertEquals(1..20, d20.support)
    (1..20).forEach { assertEquals(0.05, d20.probabilityOf(it), 1e-12) }
  }

  @Test
  fun `3d6 is bell-shaped and centred on ten and a half`() {
    val sum = graph("3d6")
    assertEquals(3..18, sum.support)
    assertEquals(10.5, sum.mean, 1e-12)
    assertEquals(27.0 / 216, sum.probabilityOf(10), 1e-12)
    assertEquals(1.0 / 216, sum.probabilityOf(3), 1e-12)
    assertEquals(1.0 / 216, sum.probabilityOf(18), 1e-12)
  }

  @Test
  fun `2d6 is the triangle everybody knows`() {
    val sum = graph("2d6")
    assertEquals(6.0 / 36, sum.probabilityOf(7), 1e-12)
    assertEquals(1.0 / 36, sum.probabilityOf(2), 1e-12)
    assertEquals(5.0 / 36, sum.probabilityOf(6), 1e-12)
  }

  @Test
  fun `NdX averages N times X plus one over two`() {
    listOf(1 to 4, 3 to 6, 5 to 8, 2 to 10, 4 to 12, 7 to 20).forEach { (count, sides) ->
      val sum = graph("${count}d$sides")
      assertEquals(count * (sides + 1) / 2.0, sum.mean, 1e-9, "${count}d$sides")
    }
  }

  @Test
  fun `2d20kh1 matches the closed form for the maximum of two`() {
    val advantage = graph("2d20kh1")
    (1..20).forEach { k ->
      assertEquals((2.0 * k - 1) / 400, advantage.probabilityOf(k), 1e-12, "P(max = $k)")
    }
  }

  @Test
  fun `2d20kl1 matches the closed form for the minimum of two`() {
    val disadvantage = graph("2d20kl1")
    (1..20).forEach { k ->
      assertEquals((2.0 * (21 - k) - 1) / 400, disadvantage.probabilityOf(k), 1e-12, "P(min = $k)")
    }
  }

  @Test
  fun `advantage lifts the average by exactly the known amount`() {
    assertEquals(13.825, graph("2d20kh1").mean, 1e-9)
    assertEquals(7.175, graph("2d20kl1").mean, 1e-9)
  }

  @Test
  fun `4d6dl1 has the mean every stat array is built on`() {
    assertEquals(12.244598765432098, graph("4d6dl1").mean, 1e-9)
  }

  @Test
  fun `a percentile is flat from one to a hundred`() {
    val percentile = graph("1d%")
    assertEquals(1..100, percentile.support)
    (1..100).forEach { assertEquals(0.01, percentile.probabilityOf(it), 1e-12) }
  }

  @Test
  fun `d100 graphs exactly as d percent does`() {
    assertTrue(graph("1d100").approximates(graph("1d%")))
  }

  @Test
  fun `a fudge roll is symmetric around zero`() {
    val fate = graph("4dF")
    assertEquals(0.0, fate.mean, 1e-12)
    assertEquals(-4..4, fate.support)
    (1..4).forEach { assertEquals(fate.probabilityOf(it), fate.probabilityOf(-it), 1e-12) }
  }

  @Test
  fun `an exploding d6 can exceed six, and its mean is the geometric one`() {
    val exploding = graph("1d6!")
    assertTrue(exploding.max > 6, "${exploding.support}")
    // E[chain] = (1+2+3+4+5)/6 + (1/6)(6 + E[chain]) = 4.2 in the untruncated limit.
    assertEquals(4.2, exploding.mean, 1e-6)
    assertEquals(0.0, exploding.probabilityOf(6), 1e-15)
  }

  @Test
  fun `the truncated tail of an exploding d6 is vanishingly small, and reported`() {
    val computed = OutcomeGraph.of(parsed("8d6!"), plan("8d6!")) as DistributionResult.Computed
    assertTrue(computed.truncatedMass < 1e-15, "${computed.truncatedMass}")
    assertTrue(computed.truncatedMass > 0.0)
  }

  @Test
  fun `a formula that cannot explode reports no truncated tail at all`() {
    val computed = OutcomeGraph.of(parsed("3d6"), plan("3d6")) as DistributionResult.Computed
    assertEquals(0.0, computed.truncatedMass)
  }

  @Test
  fun `the label becomes the chart's title`() {
    val computed = OutcomeGraph.of(parsed("8d6 [Fire]"), plan("8d6 [Fire]")) as DistributionResult.Computed
    assertEquals("Fire", computed.label)
  }

  @Test
  fun `arithmetic moves the whole distribution`() {
    val shifted = graph("3d6 + 1d20 - 4")
    assertEquals(10.5 + 10.5 - 4, shifted.mean, 1e-9)
    assertEquals(0, shifted.min)
    assertEquals(34, shifted.max)
  }

  @Test
  fun `multiplying by a constant stretches it`() {
    val doubled = graph("2 * (1d8 + 3)")
    assertEquals(8..22, doubled.support)
    assertEquals(2 * (4.5 + 3), doubled.mean, 1e-12)
    assertEquals(0.0, doubled.probabilityOf(9))
  }

  @Test
  fun `dividing rounds the way the setting says, and the setting only`() {
    val down = graph("1d6 / 2")
    assertEquals(0..3, down.support)
    assertEquals(2.0 / 6, down.probabilityOf(1), 1e-12)
    val up = graph("1d6 / 2", Rounding.Up)
    assertEquals(1..3, up.support)
  }

  @Test
  fun `500d6 graphs, because the outcome graph has no table to fit on`() {
    val big = graph("500d6")
    assertEquals(1750.0, big.mean, 1e-6)
    assertTrue(abs(big.total() - 1.0) < 1e-12)
    // The very ends are trimmed because they are smaller than a Double holds:
    // all five hundred dice showing a six has probability 6^-500, which is
    // 1e-389. There is no bar to draw for an outcome that cannot be written
    // down, and the ones next to it are just as unreachable.
    assertTrue(big.min >= 500 && big.max <= 3000, "${big.support}")
    assertEquals(0.0, big.atMost(600), 1e-15)
  }

  @Test
  fun `every mass adds to one, whatever the formula`() {
    listOf(
      "1d20",
      "3d6 + 1d20 - 4",
      "2d20kh1 + 6",
      "4d6dl1",
      "8d6!",
      "1d100",
      "2d10kl1",
      "(3d6 + 5) / 2",
      "20d6",
      "1d4 + 1d2",
      "4d6r1",
      "4d6min3",
      "2 * (1d8 + 3)",
      "1d6 * 1d4",
      "500d6",
    ).forEach { text ->
      val mass = graph(text).total()
      assertTrue(abs(mass - 1.0) <= 1e-12, "$text summed to $mass")
    }
  }

  private fun graph(
    text: String,
    rounding: Rounding = Rounding.Default,
  ): Pmf =
    when (val result = OutcomeGraph.of(parsed(text), plan(text), rounding)) {
      is DistributionResult.Computed -> result.pmf
      is DistributionResult.TooLarge -> error("'$text' should graph, but: ${result.reason}")
    }

  private fun parsed(text: String): Formula = (FormulaParser.parse(text) as ParseResult.Parsed).formula

  private fun plan(text: String): RollPlan = (RollPlanner.plan(parsed(text), catalog) as PlanResult.Planned).plan
}
