package de.drehtuer.dinfinity.core.probability

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.Formula
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.ParseResult
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the graph stops, and why it says so rather than approximating.
 *
 * A formula the graph refuses can still be perfectly rollable. The two limits
 * have nothing to do with each other: this one is about how many numbers the
 * chart would have to be exact about, the table's is about how many dice fit
 * (`docs/probability.md`, `docs/tables.md`).
 */
class ProbabilityLimitsTest {
  /** A d20 whose faces run into the thousands, which a set file is free to define. */
  private val vast =
    Die(
      id = "d20",
      shape = DieShape.Icosahedron,
      faces = List(20) { Face(index = it, value = (it + 1) * 500, label = "${(it + 1) * 500}") },
    )

  private val standard = DiceCatalog.of(listOf(StandardDice.set()))
  private val vastSet = DiceCatalog.of(listOf(StandardDice.set().copy(dice = listOf(vast))))

  @Test
  fun `the published numbers are these`() {
    assertEquals(1_000_000, ProbabilityLimits.MAX_SUPPORT)
    assertEquals(20, ProbabilityLimits.EXPLOSION_DEPTH)
  }

  @Test
  fun `the graph follows an explosion exactly as far as the roll does`() {
    assertEquals(
      de.drehtuer.dinfinity.core.notation.NotationLimits.MAX_EXPLOSION_DEPTH,
      ProbabilityLimits.EXPLOSION_DEPTH,
    )
  }

  @Test
  fun `a thousand dice with huge faces is refused rather than graphed`() {
    val result = OutcomeGraph.of(parsed("1000d20", vastSet), plan("1000d20", vastSet))
    assertTrue(result is DistributionResult.TooLarge, "$result")
    assertTrue("totals" in result.reason, result.reason)
  }

  @Test
  fun `keeping half of two hundred dice with huge faces is refused rather than graphed`() {
    val result = OutcomeGraph.of(parsed("200d20kh100", vastSet), plan("200d20kh100", vastSet))
    assertTrue(result is DistributionResult.TooLarge, "$result")
  }

  @Test
  fun `a formula the graph refuses is refused by reason, not by exception`() {
    val result = OutcomeGraph.of(parsed("1000d20", vastSet), plan("1000d20", vastSet))
    assertTrue((result as DistributionResult.TooLarge).reason.isNotEmpty())
  }

  @Test
  fun `the largest ordinary formula still graphs`() {
    val result = OutcomeGraph.of(parsed("1000d20", standard), plan("1000d20", standard))
    assertTrue(result is DistributionResult.Computed, "$result")
    assertEquals(10_500.0, result.pmf.mean, 1e-6)
  }

  @Test
  fun `keeping one of a great many dice is cheap, and still exact`() {
    val result = OutcomeGraph.of(parsed("200d20kh1", standard), plan("200d20kh1", standard))
    val pmf = (result as DistributionResult.Computed).pmf
    // P(max of 200 d20 = 20) = 1 - (19/20)^200.
    assertEquals(1.0 - Math.pow(0.95, 200.0), pmf.probabilityOf(20), 1e-12)
  }

  private fun parsed(
    text: String,
    catalog: DiceCatalog,
  ): Formula {
    check(catalog.defaultSetId.isNotEmpty())
    return (FormulaParser.parse(text) as ParseResult.Parsed).formula
  }

  private fun plan(
    text: String,
    catalog: DiceCatalog,
  ): RollPlan = (RollPlanner.plan(parsed(text, catalog), catalog) as PlanResult.Planned).plan
}
