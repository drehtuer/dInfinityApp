package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.Formula
import de.drehtuer.dinfinity.core.notation.RollBounds
import de.drehtuer.dinfinity.core.notation.RollRange
import de.drehtuer.dinfinity.core.notation.ThrowOutcome
import de.drehtuer.dinfinity.core.probability.DistributionResult
import de.drehtuer.dinfinity.core.probability.OutcomeGraph

/**
 * What a formula is expected to come to, before a die has been thrown
 * (`docs/probability.md`; `docs/dice-notation.md`, "The live range").
 *
 * The lowest, the highest and the average, which is the sentence a player
 * wants before they shake: *is the number I need even reachable, and how far
 * from the middle is it*. Shaking is the only way to throw now
 * (`docs/physics-and-rendering.md`, "Starting a roll"), so the screen has to
 * say what a shake is worth rather than leaving it on a button's label.
 *
 * Nothing here decides a number. It is arithmetic over the same plan the dice
 * will be thrown from, and the throw that follows ignores every word of it
 * (`docs/architecture.md`, goal 1).
 *
 * @param range the lowest and highest the throw can come to. The same
 *   [RollRange] the counting plate draws mid-roll, asked of a throw that has
 *   read no dice at all — so the plate and this are one calculation with two
 *   moments, not two calculations that could disagree.
 * @param mean the exact average, or null for a formula past what the outcome
 *   graph computes exactly. A range without an average is still worth having;
 *   nothing at all is not (`docs/probability.md`, limits).
 */
data class Expectation(
  val range: RollRange,
  val mean: Double?,
) {
  companion object {
    /**
     * What [formula], planned as [plan], is expected to come to.
     *
     * Both halves come from code that already exists and is already tested:
     * [RollBounds] for the ends and [OutcomeGraph] for the middle. The graph
     * is the expensive one and it is the one that can decline, which is why
     * the average is nullable and the range is not.
     */
    fun of(
      formula: Formula,
      plan: RollPlan,
      rounding: Rounding = Rounding.Default,
    ): Expectation =
      Expectation(
        range =
          RollBounds.of(
            formula = formula,
            plan = plan,
            // No die has been read, so every one of them is forced to each end
            // in turn — which is exactly the pre-throw range.
            outcome = ThrowOutcome(faces = emptyMap()),
            rounding = rounding,
          ),
        mean = (OutcomeGraph.of(formula, plan, rounding) as? DistributionResult.Computed)?.pmf?.mean,
      )
  }
}
