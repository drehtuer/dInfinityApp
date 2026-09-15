package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.core.model.Rounding

/**
 * Scores a throw: the faces the dice landed on become a [RollResult]
 * (`docs/dice-notation.md`, "Evaluation", steps 6 and 7).
 *
 * Nothing here decides a number. The face indices come from the simulation and
 * are the only source of randomness in the app; this turns them into a total
 * (`docs/architecture.md`, goal 1).
 *
 * [rescore] is the other half of that promise. The result sheet offers Down /
 * Nearest / Up for the throw in front of the player, and it recomputes the
 * total from the dice exactly as they landed — no die is re-read, re-rolled or
 * moved (`docs/dice-notation.md`, "Division rounding").
 */
object RollEvaluator {
  /**
   * The result of [outcome], scored against [formula] and [plan].
   *
   * @param extra throws the additional dice a reroll or an explosion calls
   *   for. It is only reached when the formula asks for one.
   */
  fun score(
    formula: Formula,
    plan: RollPlan,
    outcome: ThrowOutcome,
    rounding: Rounding = Rounding.Default,
    extra: ExtraThrow = ExtraThrow { error("'${plan.formula}' needs no extra throws") },
  ): RollResult {
    val faces = outcome.faces
    val indices = ThrowIndices(plan.dice.size)
    val nodes = formula.diceNodes.associateBy(DiceNode::id)
    val groups =
      plan.groups.map { group ->
        GroupRoller(nodes.getValue(group.id), group, extra, indices).roll(faces)
      }
    return RollResult(
      formula = formula.text,
      label = formula.label,
      total = Arithmetic(groups.subtotals(), rounding).of(formula.root),
      rounding = rounding,
      groups = groups,
      rethrows = outcome.rethrows,
      forcedSettles = outcome.forcedSettles,
      rolledAtEpochMs = outcome.rolledAtEpochMs,
      adjustments = adjustmentsIn(formula.root),
    )
  }

  /**
   * The same throw's total under a different rounding.
   *
   * The dice do not move: only the arithmetic around them is redone, from the
   * subtotals already in [result].
   */
  fun rescore(
    formula: Formula,
    result: RollResult,
    rounding: Rounding,
  ): RollResult =
    result.copy(
      total = Arithmetic(result.groups.subtotals(), rounding).of(formula.root),
      rounding = rounding,
      // The constants do not move when the rounding does — but a `/` in the
      // formula means the root is not a sum, so there were none anyway.
      adjustments = adjustmentsIn(formula.root),
    )
}

/**
 * What the simulation reported about one throw.
 *
 * @param faces the index of the face each die came to rest on, keyed by its
 *   position in the throw.
 * @param rethrows dice that were picked up and thrown again because they
 *   landed cocked or stacked (`docs/physics-and-rendering.md`).
 * @param forcedSettles dice still moving when the 12-second cap fired. Any
 *   value but zero is an anomaly.
 */
data class ThrowOutcome(
  val faces: Map<Int, Int>,
  val rethrows: Int = 0,
  val forcedSettles: Int = 0,
  val rolledAtEpochMs: Long = 0L,
)

/**
 * Walks the formula with each group replaced by what its dice came to.
 *
 * Plain `Long` arithmetic with no overflow checks, which is safe because
 * `ResultBounds` proved at plan time that every sub-expression fits
 * (`docs/dice-notation.md`, "Limits").
 */
private class Arithmetic(
  private val subtotals: Map<Int, Long>,
  private val rounding: Rounding,
) {
  fun of(node: FormulaNode): Long =
    when (node) {
      is NumberNode -> node.value
      is NegateNode -> -of(node.operand)
      is DiceNode -> subtotals[node.id] ?: 0L
      is BinaryNode -> apply(node)
    }

  private fun apply(node: BinaryNode): Long {
    val left = of(node.left)
    val right = of(node.right)
    return when (node.operator) {
      BinaryOperator.Plus -> left + right
      BinaryOperator.Minus -> left - right
      BinaryOperator.Times -> left * right
      BinaryOperator.Divide -> rounding.divide(left, right)
    }
  }
}

/** The subtotal of every group in this result, keyed by group id. */
internal fun List<RolledGroup>.subtotals(): Map<Int, Long> = associate { it.id to it.subtotal }

/**
 * The plain numbers [root] adds to or takes from the total, signed and in the
 * order they were written (`docs/dice-notation.md`, "Evaluation", step 7).
 *
 * **The top-level sum only.** A number anywhere else is not a number added to
 * the total: the `3` in `(2d6 + 3) * 2` is multiplied along with the dice, and
 * a sheet that listed it as `+ 3` would be adding up to the wrong answer in
 * front of the player. So a formula whose root is a product or a division has
 * no adjustments at all, and [RollResult.itemised] says so.
 *
 * It is the same rule the picker's badges follow, for the same reason: edit or
 * itemise only what can be read back (`DicePicker.counts`).
 */
internal fun adjustmentsIn(root: FormulaNode): List<Long> =
  when (root) {
    is NumberNode -> listOf(root.value)
    is NegateNode -> adjustmentsIn(root.operand).map { -it }
    is DiceNode -> emptyList()
    is BinaryNode ->
      when (root.operator) {
        BinaryOperator.Plus -> adjustmentsIn(root.left) + adjustmentsIn(root.right)
        BinaryOperator.Minus -> adjustmentsIn(root.left) + adjustmentsIn(root.right).map { -it }
        // A product or a quotient is not a sum, so nothing under it is
        // something added to the total.
        BinaryOperator.Times, BinaryOperator.Divide -> emptyList()
      }
  }
