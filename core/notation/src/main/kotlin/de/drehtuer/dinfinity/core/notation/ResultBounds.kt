package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieRole
import de.drehtuer.dinfinity.core.model.PlannedGroup
import java.math.BigInteger

/**
 * How large a formula's result can get, worked out before a single die is
 * thrown.
 *
 * `docs/dice-notation.md` says a result has to fit in 64 bits and that
 * overflow is a *parse-time* error. This is where that promise is kept: every
 * sub-expression is bounded here, so the evaluator can add and multiply in
 * plain `Long` arithmetic without a single overflow check, and a formula that
 * could not be scored honestly is refused while it is still text.
 *
 * The work is done in [BigInteger] because the whole point is to reason about
 * numbers that do not fit in a `Long`. It runs once per plan, over a tree with
 * at most a few dozen nodes.
 *
 * A division whose divisor can be zero is caught here too, for the same
 * reason: better a message under the formula field than a throw halfway
 * through a roll the player is watching.
 */
internal class ResultBounds(
  groups: List<PlannedGroup>,
) {
  private val diceById = groups.associateBy(PlannedGroup::id)

  /** The smallest and largest value a node can take. */
  data class Span(
    val min: BigInteger,
    val max: BigInteger,
  ) {
    /** The larger of the two magnitudes — what the 64-bit promise is about. */
    val magnitude: BigInteger get() = min.abs().max(max.abs())
  }

  /** Bounds [node], throwing when it or any part of it could not be scored in 64 bits. */
  fun of(node: FormulaNode): Span {
    val span =
      when (node) {
        is NumberNode -> Span(node.value.toBigInteger(), node.value.toBigInteger())
        is NegateNode -> of(node.operand).let { Span(it.max.negate(), it.min.negate()) }
        is DiceNode -> diceSpan(node)
        is BinaryNode -> binarySpan(node)
      }
    if (span.magnitude > CAP) {
      throw failure(
        NotationErrorCode.ResultTooLarge,
        "this can produce a number too large for the app to add up",
        node.range,
      )
    }
    return span
  }

  private fun binarySpan(node: BinaryNode): Span {
    val left = of(node.left)
    val right = of(node.right)
    return when (node.operator) {
      BinaryOperator.Plus -> Span(left.min + right.min, left.max + right.max)
      BinaryOperator.Minus -> Span(left.min - right.max, left.max - right.min)
      BinaryOperator.Times -> productSpan(left, right)
      BinaryOperator.Divide -> quotientSpan(left, right, node)
    }
  }

  private fun productSpan(
    left: Span,
    right: Span,
  ): Span {
    val corners = listOf(left.min * right.min, left.min * right.max, left.max * right.min, left.max * right.max)
    return Span(corners.min(), corners.max())
  }

  /**
   * Dividing by something that could be zero has no answer to round, so it is
   * refused before the dice are thrown rather than thrown halfway through one.
   */
  private fun quotientSpan(
    left: Span,
    right: Span,
    node: BinaryNode,
  ): Span {
    if (right.min <= BigInteger.ZERO && right.max >= BigInteger.ZERO) {
      throw failure(
        NotationErrorCode.DivisionByZero,
        "this divides by something that can be zero",
        node.right.range,
      )
    }
    // |a / b| never exceeds |a| once |b| is at least one, whichever way it rounds.
    return Span(left.magnitude.negate(), left.magnitude)
  }

  /**
   * A group's bounds, generous on purpose: an exploding group is allowed its
   * full depth of re-throws, and `min n` can only raise a die.
   */
  private fun diceSpan(node: DiceNode): Span {
    val dice = diceById.getValue(node.id).dice
    val unit = unitSpan(dice)
    val raised = node.modifiers.filterIsInstance<DiceModifier.Minimum>().maxOfOrNull { it.value }
    val low = raised?.let { maxOf(unit.first, it) } ?: unit.first
    val high = raised?.let { maxOf(unit.second, it) } ?: unit.second
    val chains = if (node.explodes) NotationLimits.MAX_EXPLOSION_DEPTH + 1 else 1
    val perUnit = high.toBigInteger() * chains.toBigInteger()
    val counted = countedUnits(node).toBigInteger()
    return Span(counted * low.toBigInteger(), counted * perUnit)
  }

  /** The smallest and largest a single unit of a group can score. */
  private fun unitSpan(dice: List<DieInstance>): Pair<Int, Int> {
    val pair = dice.firstOrNull()?.role != DieRole.Normal
    if (pair) return PERCENTILE_LOW to PERCENTILE_HIGH
    val die = dice.first().die
    return die.minValue to die.maxValue
  }

  /** How many units of the group actually count, after keep and drop. */
  private fun countedUnits(node: DiceNode): Int =
    node.modifiers.fold(node.count) { counted, modifier ->
      when (modifier) {
        is DiceModifier.KeepHighest -> modifier.n
        is DiceModifier.KeepLowest -> modifier.n
        is DiceModifier.DropHighest -> counted - modifier.n
        is DiceModifier.DropLowest -> counted - modifier.n
        else -> counted
      }
    }

  private companion object {
    val CAP: BigInteger = NotationLimits.MAX_RESULT_MAGNITUDE.toBigInteger()

    /** A percentile pair reads 1 at the lowest and 100 at the highest. */
    const val PERCENTILE_LOW = 1
    const val PERCENTILE_HIGH = 100
  }
}
