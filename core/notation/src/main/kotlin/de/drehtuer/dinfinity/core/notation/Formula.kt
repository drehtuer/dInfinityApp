package de.drehtuer.dinfinity.core.notation

/**
 * A parsed formula: the tree, and the label that followed it
 * (`docs/dice-notation.md`).
 *
 * [text] is kept beside the tree because every error range, every breakdown
 * heading and the result sheet's own title quote the formula as the player
 * wrote it, spaces and all.
 *
 * @param label the contents of a trailing `[…]`, used as the outcome graph's
 *   title and shown in the breakdown. It takes no part in the arithmetic.
 * @param diceNodes every `dice` node in source order, which is the order their
 *   dice are thrown and the order the breakdown lists them.
 */
data class Formula(
  val text: String,
  val root: FormulaNode,
  val label: String? = null,
) {
  val diceNodes: List<DiceNode> get() = collectDice(root)

  private fun collectDice(node: FormulaNode): List<DiceNode> =
    when (node) {
      is DiceNode -> listOf(node)
      is NumberNode -> emptyList()
      is NegateNode -> collectDice(node.operand)
      is BinaryNode -> collectDice(node.left) + collectDice(node.right)
    }
}

/**
 * One node of a formula.
 *
 * Every node carries the character range it was parsed from, so an error, a
 * squiggle or a highlight can point at the part of the formula that caused it
 * rather than at the whole line (`docs/dice-notation.md`, "Error messages").
 */
sealed interface FormulaNode {
  /** The half-open character range of [Formula.text] this node was parsed from. */
  val range: IntRange
}

/** A plain integer, e.g. the `4` of `3d6 - 4`. */
data class NumberNode(
  val value: Long,
  override val range: IntRange,
) : FormulaNode

/** A leading minus, e.g. the `-1d4` of `2d6 + -1d4`. */
data class NegateNode(
  val operand: FormulaNode,
  override val range: IntRange,
) : FormulaNode

/** `+`, `-`, `*` or `/` applied to two sub-expressions. */
data class BinaryNode(
  val operator: BinaryOperator,
  val left: FormulaNode,
  val right: FormulaNode,
  override val range: IntRange,
) : FormulaNode

/**
 * A group of dice: `3d6`, `2d20kh1`, `brass:1d20`, `8d6!`.
 *
 * @param id the node's position in source order, `0` upwards. It is the same
 *   number as the [de.drehtuer.dinfinity.core.model.PlannedGroup.id] it plans
 *   to and the breakdown group it scores into, which is what lets a die in the
 *   tray be traced back to the part of the formula that asked for it.
 * @param setRef the `setref:` prefix, or `null` to use the default set.
 * @param count how many dice — or, for [Sides.Percentile], how many *pairs*.
 * @param modifiers in source order; the order they are *applied* in is fixed
 *   and does not depend on how they were written (see `RollEvaluator`).
 */
data class DiceNode(
  val id: Int,
  val setRef: String?,
  val count: Int,
  val sides: Sides,
  val modifiers: List<DiceModifier>,
  override val range: IntRange,
) : FormulaNode {
  /** True when this group carries `!`, which the capacity rule has to know. */
  val explodes: Boolean get() = modifiers.any { it is DiceModifier.Explode }
}

/** The four operators a formula can use. */
enum class BinaryOperator(
  val symbol: Char,
) {
  Plus('+'),
  Minus('-'),
  Times('*'),
  Divide('/'),
}

/** What follows the `d`. */
sealed interface Sides {
  /** `d6`, `d20` — resolves to the die whose id is `d` followed by [value]. */
  data class Numeric(
    val value: Int,
  ) : Sides

  /**
   * `d%` and `d100` alike: a tens d10 and a units d10 from the same set,
   * thrown as a pair (`docs/dice-notation.md`, "d100 and d%").
   */
  data object Percentile : Sides

  /** `dF` — the fudge die, the set's `df` (−1, 0 or +1). */
  data object Fudge : Sides
}

/**
 * Something written after the sides that changes what the group scores.
 *
 * Each carries its own range so `2d20kh3` can underline the `kh3` rather than
 * the whole group.
 */
sealed interface DiceModifier {
  val range: IntRange

  /** `kh n` — keep the [n] highest dice, drop the rest. */
  data class KeepHighest(
    val n: Int,
    override val range: IntRange,
  ) : DiceModifier

  /** `kl n` — keep the [n] lowest. */
  data class KeepLowest(
    val n: Int,
    override val range: IntRange,
  ) : DiceModifier

  /** `dh n` — drop the [n] highest. */
  data class DropHighest(
    val n: Int,
    override val range: IntRange,
  ) : DiceModifier

  /** `dl n` — drop the [n] lowest. */
  data class DropLowest(
    val n: Int,
    override val range: IntRange,
  ) : DiceModifier

  /** `!` — every die showing its highest face throws another of the same die. */
  data class Explode(
    override val range: IntRange,
  ) : DiceModifier

  /** `r n` — a die showing [threshold] or less is thrown once more. */
  data class Reroll(
    val threshold: Int,
    override val range: IntRange,
  ) : DiceModifier

  /** `min n` — a die below [value] counts as [value]. The face it showed is still shown. */
  data class Minimum(
    val value: Int,
    override val range: IntRange,
  ) : DiceModifier
}
