package de.drehtuer.dinfinity.feature.saved

import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner

/**
 * Whether a saved formula still means something
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * A saved roll keeps its formula as text, and the set it names may have been
 * uninstalled since it was written. So it is re-checked every time it is
 * shown — parsed *and* resolved, because `brass:1d20` parses perfectly well
 * whether or not `brass` is installed, and the second half is the one that
 * goes wrong on somebody else's phone.
 *
 * A roll that fails this is not deleted and not rewritten — the set may be
 * re-installed tomorrow, and rewriting somebody's formula to keep it working
 * would lose the thing they wrote.
 *
 * **It does not fall back when it is thrown, and should not.** The per-die
 * fallback in `docs/dice-notation.md` is the *default* set's: a default with
 * no d12 still rolls `1d20 + 1d12`, taking the d12 from the bundled set. A
 * `setref:` gets none — somebody who wrote `brass:1d20` asked for brass, and
 * handing them the bundled d20 under that name would be changing their dice
 * without saying so. Thrown, it puts the formula in the field with the error
 * under it, which is what typing it would have given them.
 */
object SavedFormula {
  fun resolves(
    formula: String,
    catalog: DiceCatalog,
  ): Boolean {
    val parsed = FormulaParser.parseOrNull(formula) ?: return false
    return RollPlanner.plan(parsed, catalog) is PlanResult.Planned
  }
}
