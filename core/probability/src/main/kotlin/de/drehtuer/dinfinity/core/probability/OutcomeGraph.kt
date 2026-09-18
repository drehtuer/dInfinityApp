package de.drehtuer.dinfinity.core.probability

import de.drehtuer.dinfinity.core.model.DieRole
import de.drehtuer.dinfinity.core.model.PlannedGroup
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.BinaryNode
import de.drehtuer.dinfinity.core.notation.BinaryOperator
import de.drehtuer.dinfinity.core.notation.DiceModifier
import de.drehtuer.dinfinity.core.notation.DiceNode
import de.drehtuer.dinfinity.core.notation.Formula
import de.drehtuer.dinfinity.core.notation.FormulaNode
import de.drehtuer.dinfinity.core.notation.NegateNode
import de.drehtuer.dinfinity.core.notation.NumberNode

/**
 * The exact distribution of a formula's total, before a single die is thrown
 * (`docs/probability.md`).
 *
 * It works from the same [RollPlan] a roll does, so a graph is never about a
 * slightly different formula than the dice are: the dice, their face values,
 * the fallbacks and the percentile pairing have all been resolved already, and
 * this reads their faces exactly as the simulator will.
 *
 * The table has no say here. `500d6` graphs perfectly well and is refused only
 * when it reaches the tray; it is the *throw* that is refused, not the maths
 * (`docs/probability.md`, `docs/tables.md`).
 */
object OutcomeGraph {
  /**
   * The distribution of [formula] rolled as [plan] says.
   *
   * @param rounding division rounds the way the *setting* says, always: the
   *   graph is computed before the throw exists, so the result sheet's
   *   per-throw override has nothing yet to apply to
   *   (`docs/dice-notation.md`, "Division rounding").
   */
  fun of(
    formula: Formula,
    plan: RollPlan,
    rounding: Rounding = Rounding.Default,
  ): DistributionResult =
    try {
      val groups = plan.groups.associateBy(PlannedGroup::id)
      val nodes = formula.diceNodes
      val distributions = nodes.associate { it.id to groupPmf(it, groups.getValue(it.id)) }
      DistributionResult.Computed(
        pmf = Walk(distributions, rounding).of(formula.root),
        truncatedMass = nodes.maxOfOrNull { truncated(it, groups.getValue(it.id)) } ?: 0.0,
        label = formula.label,
      )
    } catch (tooLarge: TooLargeToGraph) {
      DistributionResult.TooLarge(tooLarge.reason)
    }

  /** What one group of the formula contributes. */
  private fun groupPmf(
    node: DiceNode,
    group: PlannedGroup,
  ): Pmf {
    val unit = unitPmf(group)
    val chain =
      ChainPmf.of(
        die = unit,
        rerollAtOrBelow = node.modifiers.filterIsInstance<DiceModifier.Reroll>().maxOfOrNull { it.threshold },
        explodesAt = if (node.explodes) ChainPmf.explodingValue(unit) else null,
        minimum = node.modifiers.filterIsInstance<DiceModifier.Minimum>().maxOfOrNull { it.value },
      )
    return selected(chain, node.count, node.modifiers)
  }

  /**
   * One scoring unit of a group: a die, or the two halves of a percentile pair
   * added together with `00` and `0` reading as 100.
   */
  private fun unitPmf(group: PlannedGroup): Pmf {
    val dice = group.dice
    if (dice.isEmpty()) return Pmf.certain(0)
    if (dice.first().role == DieRole.Normal) return Pmf.uniformOver(dice.first().die.values())
    val tens = Pmf.uniformOver(dice.first { it.role == DieRole.PercentileTens }.die.values())
    val units = Pmf.uniformOver(dice.first { it.role == DieRole.PercentileUnits }.die.values())
    return PmfArithmetic.remap(Convolution.of(tens, units)) { if (it == 0) PERCENTILE_MAX else it.toLong() }
  }

  /** `kh`/`kl`/`dh`/`dl`, or simply every chain added up. */
  private fun selected(
    chain: Pmf,
    count: Int,
    modifiers: List<DiceModifier>,
  ): Pmf =
    when (val selection = modifiers.firstOrNull(::selects)) {
      is DiceModifier.KeepHighest -> OrderStatistics.keepHighest(chain, count, selection.n)
      is DiceModifier.KeepLowest -> OrderStatistics.keepLowest(chain, count, selection.n)
      is DiceModifier.DropHighest -> OrderStatistics.keepLowest(chain, count, count - selection.n)
      is DiceModifier.DropLowest -> OrderStatistics.keepHighest(chain, count, count - selection.n)
      else -> Convolution.repeated(chain, count)
    }

  private fun selects(modifier: DiceModifier): Boolean =
    modifier is DiceModifier.KeepHighest ||
      modifier is DiceModifier.KeepLowest ||
      modifier is DiceModifier.DropHighest ||
      modifier is DiceModifier.DropLowest

  private fun truncated(
    node: DiceNode,
    group: PlannedGroup,
  ): Double {
    if (!node.explodes) return 0.0
    val unit = unitPmf(group)
    return ChainPmf.truncatedMass(unit, ChainPmf.explodingValue(unit))
  }

  /** A percentile pair reads 1 to 100; `00` and `0` together are the 100. */
  private const val PERCENTILE_MAX = 100L
}

/** What [OutcomeGraph.of] came to. */
sealed interface DistributionResult {
  /**
   * The exact distribution.
   *
   * @param truncatedMass how much probability sits in exploding chains that
   *   ran into the depth limit — under 1e-15 for a d6, and shown in the
   *   tooltip when it is not (`docs/probability.md`).
   */
  data class Computed(
    val pmf: Pmf,
    val truncatedMass: Double = 0.0,
    val label: String? = null,
  ) : DistributionResult

  /**
   * The formula is legal but past what the graph computes exactly
   * (`docs/probability.md`, limits). It may still be rollable.
   */
  data class TooLarge(
    val reason: String,
  ) : DistributionResult
}

/** Walks the formula with each group replaced by its distribution. */
private class Walk(
  private val groups: Map<Int, Pmf>,
  private val rounding: Rounding,
) {
  fun of(node: FormulaNode): Pmf =
    when (node) {
      is NumberNode -> Pmf.certain(node.value.toInt())
      is NegateNode -> PmfArithmetic.negate(of(node.operand))
      is DiceNode -> groups.getValue(node.id)
      is BinaryNode -> apply(node)
    }

  private fun apply(node: BinaryNode): Pmf {
    val left = of(node.left)
    val right = of(node.right)
    return when (node.operator) {
      BinaryOperator.Plus -> Convolution.of(left, right)
      BinaryOperator.Minus -> PmfArithmetic.subtract(left, right)
      BinaryOperator.Times -> times(left, right)
      BinaryOperator.Divide -> dividedBy(left, right)
    }
  }

  /** Multiplying by a certain value is a remap; multiplying two rolls is an enumeration. */
  private fun times(
    left: Pmf,
    right: Pmf,
  ): Pmf =
    when {
      right.certain() -> PmfArithmetic.scale(left, right.min.toLong())
      left.certain() -> PmfArithmetic.scale(right, left.min.toLong())
      else -> PmfArithmetic.multiply(left, right)
    }

  private fun dividedBy(
    left: Pmf,
    right: Pmf,
  ): Pmf =
    when {
      right.certain() -> PmfArithmetic.divideBy(left, right.min.toLong(), rounding)
      left.certain() -> PmfArithmetic.divideInto(left.min.toLong(), right, rounding)
      else -> PmfArithmetic.divide(left, right, rounding)
    }
}

/** True when this distribution has only one possible value. */
private fun Pmf.certain(): Boolean = min == max
