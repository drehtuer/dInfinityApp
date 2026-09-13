package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.PlannedGroup
import de.drehtuer.dinfinity.core.model.RollPlan

/**
 * Resolves a parsed formula against the installed dice sets and produces the
 * plan a throw is built from (`docs/architecture.md`, "Data flow of a roll").
 *
 * This is step 2 of `docs/dice-notation.md`'s evaluation: every `dice` node
 * becomes concrete dice from concrete sets. Step 3 — does it fit on the
 * table — belongs to `:simulation:api`, which is why a `500d6` plan is a
 * perfectly good plan that the tray will refuse. The outcome graph takes this
 * same plan and has no such limit.
 *
 * Everything that can be known before the dice are thrown is settled here: a
 * die the set does not have, a `kh` that keeps more dice than it rolls, a `!`
 * that would never stop, a division that could be by zero, and a result too
 * large to add up. What is left over at roll time is the dice landing.
 */
object RollPlanner {
  /** [formula] as a plan, or the first thing that stops it resolving. */
  fun plan(
    formula: Formula,
    catalog: DiceCatalog,
  ): PlanResult =
    try {
      PlanResult.Planned(resolve(formula, catalog))
    } catch (failure: ParseFailure) {
      PlanResult.Failed(failure.error)
    }

  /** Parses and plans in one step, for callers that have only the text. */
  fun plan(
    text: String,
    catalog: DiceCatalog,
  ): PlanResult =
    when (val parsed = FormulaParser.parse(text)) {
      is ParseResult.Failed -> PlanResult.Failed(parsed.error)
      is ParseResult.Parsed -> plan(parsed.formula, catalog)
    }

  private fun resolve(
    formula: Formula,
    catalog: DiceCatalog,
  ): RollPlan {
    val resolver = DieResolver(catalog, formula.text)
    var index = 0
    val groups =
      formula.diceNodes.map { node ->
        val dice = resolver.resolve(node, index)
        index += dice.size
        checkModifiers(node, dice)
        PlannedGroup(
          id = node.id,
          notation = formula.text.substring(node.range),
          dice = dice,
          explodes = node.explodes,
        )
      }
    ResultBounds(groups).of(formula.root)
    return RollPlan(formula = formula.text, label = formula.label, groups = groups)
  }

  private fun checkModifiers(
    node: DiceNode,
    dice: List<DieInstance>,
  ) {
    node.modifiers.forEach { modifier ->
      when (modifier) {
        is DiceModifier.KeepHighest -> checkSelection(node, modifier.n, modifier.range, "keeps")
        is DiceModifier.KeepLowest -> checkSelection(node, modifier.n, modifier.range, "keeps")
        is DiceModifier.DropHighest -> checkSelection(node, modifier.n, modifier.range, "drops")
        is DiceModifier.DropLowest -> checkSelection(node, modifier.n, modifier.range, "drops")
        is DiceModifier.Explode -> checkExplodes(modifier, dice)
        else -> Unit
      }
    }
  }

  private fun checkSelection(
    node: DiceNode,
    n: Int,
    range: IntRange,
    verb: String,
  ) {
    if (n in 1..node.count) return
    throw failure(
      NotationErrorCode.KeepDropOutOfRange,
      "this $verb $n of ${node.count} dice",
      range,
    )
  }

  /**
   * A die whose every face is its highest would explode for ever. No set in
   * the catalogue has one, but a set file is written by a stranger and this is
   * cheaper than finding out at 120 Hz.
   */
  private fun checkExplodes(
    modifier: DiceModifier.Explode,
    dice: List<DieInstance>,
  ) {
    val endless = dice.all { instance -> instance.die.values().all { it == instance.die.maxValue } }
    if (!endless) return
    throw failure(
      NotationErrorCode.ExplodesForever,
      "every face of this die is its highest, so '!' would never stop",
      modifier.range,
    )
  }
}

/** What [RollPlanner.plan] came to. */
sealed interface PlanResult {
  /** The formula resolved. The plan may still be too big for the table. */
  data class Planned(
    val plan: RollPlan,
  ) : PlanResult

  /** The formula did not resolve, and this is why. */
  data class Failed(
    val error: NotationError,
  ) : PlanResult
}
