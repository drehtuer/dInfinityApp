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
import kotlin.test.assertTrue

/**
 * The graph against the dice.
 *
 * Every case here is rolled *every possible way* by [BruteForce], scored with
 * the same evaluator a real roll uses, and the tally compared against what the
 * chart would draw. This is the strongest thing that can be said about the
 * outcome graph: not that its arithmetic is self-consistent, but that it
 * predicts what the app will actually do.
 *
 * The cases are small because enumeration is exponential. That costs nothing:
 * a convolution that is wrong is wrong for `3d6` too.
 */
class GoldenDistributionTest {
  private val catalog = DiceCatalog.of(listOf(StandardDice.set()))

  @Test
  fun `plain sums match a full enumeration`() {
    listOf("1d6", "2d6", "3d6", "1d20", "2d4", "1d4 + 1d2", "1d8 + 2", "3d6 + 1d20 - 4").forEach(::assertMatches)
  }

  @Test
  fun `keep and drop match a full enumeration`() {
    listOf("2d20kh1", "2d20kl1", "4d6dl1", "4d6dh1", "3d6kh2", "3d6kl2", "5d4kh3", "2d10kl1").forEach(::assertMatches)
  }

  @Test
  fun `exploding matches a full enumeration`() {
    listOf("1d4!", "2d4!", "1d6!", "2d4!kh1").forEach(::assertMatches)
  }

  @Test
  fun `rerolling matches a full enumeration`() {
    listOf("1d6r1", "2d6r2", "4d6r1dl1", "1d20r5").forEach(::assertMatches)
  }

  @Test
  fun `a minimum matches a full enumeration`() {
    listOf("1d6min3", "4d6min2", "2d6min2dl1", "1d4!min2").forEach(::assertMatches)
  }

  @Test
  fun `percentiles match a full enumeration`() {
    listOf("1d%", "1d100 + 5", "2d%kh1").forEach(::assertMatches)
  }

  @Test
  fun `arithmetic matches a full enumeration`() {
    listOf(
      "2 * (1d8 + 3)",
      "1d6 * 1d4",
      "(3d6 + 5) / 2",
      "1d20 / 1d4",
      "-1d6 + 10",
      "20 / 1d4",
      "1d4 * 3",
      // Doubling first leaves gaps at the odd totals, which the product with
      // a die must step over rather than spread nothing from.
      "1d4 * 2 * 1d6",
    ).forEach(::assertMatches)
  }

  @Test
  fun `the rounding the graph is given is the rounding the roll uses`() {
    Rounding.entries.forEach { rounding ->
      assertMatches("(2d6 + 1) / 3", rounding)
      assertMatches("1d20 / 2", rounding)
    }
  }

  @Test
  fun `a fudge roll matches a full enumeration`() {
    listOf("1dF", "2dF", "4dF + 2", "2dFkh1", "3dFdl1").forEach(::assertMatches)
  }

  @Test
  fun `percentiles under every modifier match a full enumeration`() {
    // The pair is scored as one number and a chain of them explodes on the
    // hundred; a graph that treated the halves as dice would be close and wrong.
    listOf("1d%!", "1d%r50", "1d%min50", "1d%!min50", "1d%r1!", "2d%dl1", "1d100r99").forEach(::assertMatches)
  }

  @Test
  fun `a fudge die under every modifier matches a full enumeration`() {
    // Floors and thresholds in the middle of a die that goes below nought.
    // Its explosion has too many branches to walk, so it is in `OutcomeGraphTest`.
    listOf("1dFr0", "2dFmin0", "1dFr1", "3dFmin0dl1").forEach(::assertMatches)
  }

  @Test
  fun `modifiers together match a full enumeration in their fixed order`() {
    listOf(
      "1d4r1!",
      "2d4r1!kh1",
      "1d4r4!",
      "1d6r1min3",
      "2d4r2min3dh1",
      "2d4!r1min2kl1",
      // A floor above the die's top face, which lifts every throw past it.
      "1d6min9",
      "1d4!min6",
    ).forEach(::assertMatches)
  }

  private fun assertMatches(
    text: String,
    rounding: Rounding = Rounding.Default,
  ) {
    val formula = parsed(text)
    val plan = plan(text)
    val expected = BruteForce.distributionOf(formula, plan, rounding)
    val actual =
      when (val result = OutcomeGraph.of(formula, plan, rounding)) {
        is DistributionResult.Computed -> result.pmf
        is DistributionResult.TooLarge -> error("'$text' should graph, but: ${result.reason}")
      }
    val values = minOf(expected.min, actual.min)..maxOf(expected.max, actual.max)
    val worst = values.maxOf { abs(expected.probabilityOf(it) - actual.probabilityOf(it)) }
    assertTrue(
      worst <= TOLERANCE,
      "$text: the graph and a full enumeration differ by $worst" +
        "\n  enumerated ${expected.support} mean ${expected.mean}" +
        "\n  graphed    ${actual.support} mean ${actual.mean}",
    )
  }

  private fun parsed(text: String): Formula = (FormulaParser.parse(text) as ParseResult.Parsed).formula

  private fun plan(text: String): RollPlan = (RollPlanner.plan(parsed(text), catalog) as PlanResult.Planned).plan

  private companion object {
    /**
     * The enumeration follows an exploding chain to the same depth the graph
     * does, so the two agree exactly up to floating-point addition order.
     */
    const val TOLERANCE = 1e-12
  }
}
