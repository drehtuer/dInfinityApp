package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.RollResult

/**
 * Whether a result's rows carry their own subtotals, or whether that would
 * only be the total written down twice.
 *
 * **The design has one total** (`docs/design-handover.md`: "Where does the
 * total go, and how many times? — Once, in the result sheet"). The sheet's own
 * promise is that nobody should have to add anything up, and a group's
 * subtotal is how it keeps that promise: `3d6 + 1d20` is two numbers and a
 * player should be able to see both of them make the third.
 *
 * `1d20` is not. There is one group, nothing is added to it, and its subtotal
 * *is* the total — so drawing it printed the roll's number at 42 dp in the
 * middle of the screen and again at 20 dp hard against the right edge, where
 * the first device session read it as clipped. One number, drawn twice, one of
 * them looking broken.
 *
 * So the rule is the narrowest one that says exactly that: a subtotal is drawn
 * unless it is the whole of the result. It is arithmetic over a
 * [RollResult] and it is here rather than inside the sheet's draw lambda so a
 * JVM test can ask it (`docs/TODO.md`, Step 4.1).
 */
internal object Subtotals {
  /**
   * Whether [result]'s group rows show a subtotal.
   *
   * False only when there is one group, nothing is added to or taken from it,
   * and what it came to is what the roll came to. Everything else — two
   * groups, a `+ 4`, a division that makes the total something other than the
   * sum — needs the number to be there for the rows to add up.
   */
  fun shownOn(result: RollResult): Boolean {
    val only = result.groups.singleOrNull() ?: return true
    if (result.adjustments.isNotEmpty()) return true
    return only.subtotal != result.total
  }
}
