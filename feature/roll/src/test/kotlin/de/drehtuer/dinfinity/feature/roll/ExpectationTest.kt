package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.Formula
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.ParseResult
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.fixtures.StandardDice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a formula is expected to come to, before a die has been thrown
 * ([Expectation]).
 *
 * Plain JVM: nothing here touches Compose or Android. What is worth asserting
 * is that the two halves come from the two places they are supposed to come
 * from and that the expensive half is allowed to decline — the arithmetic
 * itself belongs to `RollBounds` and `OutcomeGraph` and is tested there.
 */
class ExpectationTest {
  @Test
  fun `the ends and the middle of an ordinary formula`() {
    val expected = expectationOf("3d6 + 4")

    assertEquals(7L, expected.range.lowest)
    assertEquals(22L, expected.range.highest)
    assertEquals(14.5, requireNotNull(expected.mean), 1e-9)
  }

  @Test
  fun `one die is its own range, and the average is the middle of it`() {
    val expected = expectationOf("1d20")

    assertEquals(1L, expected.range.lowest)
    assertEquals(20L, expected.range.highest)
    assertEquals(10.5, requireNotNull(expected.mean), 1e-9)
  }

  @Test
  fun `subtraction puts the highest die at the bottom of the range`() {
    // The interval is walked corner by corner rather than taken as it comes
    // out of the evaluator, which is the whole reason `RollBounds` exists.
    val expected = expectationOf("20 - 1d6")

    assertEquals(14L, expected.range.lowest)
    assertEquals(19L, expected.range.highest)
  }

  @Test
  fun `an exploding chain says its ceiling is not the end of it`() {
    // Every die at its highest and every throw they earn at its lowest, with
    // `more` to say it can go higher (`RollRange`).
    val expected = expectationOf("3d6!")

    assertEquals(3L, expected.range.lowest)
    assertTrue("an exploding chain claimed a ceiling", expected.range.more)
  }

  @Test
  fun `a formula past the graph keeps its range and loses only its average`() {
    // A set file is free to define a d20 with faces in the thousands, and a
    // thousand of those reach more totals than the graph computes exactly
    // (`docs/probability.md`, limits). The ends do not care.
    val expected = expectationOf("1000d20", vast)

    assertEquals(500_000L, expected.range.lowest)
    assertEquals(10_000_000L, expected.range.highest)
    assertNull("a formula the graph refuses was given an average", expected.mean)
  }

  @Test
  fun `the rounding it is asked for is the rounding it answers with`() {
    // `Down` and `Up` disagree about `1d6 / 4`, and the screen's setting is
    // what decides (`docs/dice-notation.md`, "Division rounding").
    assertEquals(1L, expectationOf("1d6 / 4", rounding = Rounding.Down).range.highest)
    assertEquals(2L, expectationOf("1d6 / 4", rounding = Rounding.Up).range.highest)
  }

  private fun expectationOf(
    text: String,
    catalog: DiceCatalog = standard,
    rounding: Rounding = Rounding.Default,
  ): Expectation = Expectation.of(parsed(text), plan(text, catalog), rounding)

  private fun parsed(text: String): Formula = (FormulaParser.parse(text) as ParseResult.Parsed).formula

  private fun plan(
    text: String,
    catalog: DiceCatalog,
  ): RollPlan = (RollPlanner.plan(parsed(text), catalog) as PlanResult.Planned).plan

  private val standard = DiceCatalog.of(listOf(StandardDice.set()))

  /** A d20 whose faces run into the thousands, which a set file is free to define. */
  private val vast =
    DiceCatalog.of(
      listOf(
        StandardDice.set().copy(
          dice =
            listOf(
              Die(
                id = "d20",
                shape = DieShape.Icosahedron,
                faces = List(20) { Face(index = it, value = (it + 1) * 500, label = "${(it + 1) * 500}") },
              ),
            ),
        ),
      ),
    )
}
