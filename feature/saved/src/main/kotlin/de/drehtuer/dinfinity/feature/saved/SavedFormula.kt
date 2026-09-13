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
 * A roll that fails this is not deleted and not rewritten. It carries a
 * warning and falls back to the built-in set when it is thrown, which is the
 * only answer that loses nothing: the set may be re-installed tomorrow.
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
