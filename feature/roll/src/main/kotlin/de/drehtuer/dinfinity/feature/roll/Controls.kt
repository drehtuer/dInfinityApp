package de.drehtuer.dinfinity.feature.roll

import androidx.compose.runtime.saveable.Saver

/**
 * What of the roll screen's controls is over the table, and what is put away
 * (`docs/physics-and-rendering.md`, "Clearing the table";
 * `docs/architecture.md`, decision 83).
 *
 * Four facts, held together because every one of them answers the same
 * question — how much of the felt the player can see — and because the rules
 * between them are the part that can be wrong:
 *
 * - **The two menus along the top are never both open.** Opening the dice
 *   pull-down shuts the formula drawer and the other way about: both hang off
 *   the top edge, and two open at once is the top half of the table covered.
 * - **A throw stows the top.** Once a shake has thrown dice, the dice
 *   pull-down is shut and the formula tab is folded into it, so nothing along
 *   the top but the `Dice` head stands over the dice while they roll and
 *   after they land. The top comes back out only when the player opens the
 *   pull-down — never on its own, because a tab that reappeared when a total
 *   landed would be one more thing sliding over the dice the player is
 *   looking at.
 * - **A double tap clears the table.** Every pull-down, pull-up and edge tab
 *   goes, and the next double tap brings each back in exactly the state it
 *   was left in — which is why clearing is a flag over the other three rather
 *   than a reset of them.
 * - **A throw ends a clear table.** The total is the one thing a player cannot
 *   get back without throwing again, and a result sheet that arrived hidden
 *   would be a total nobody saw; so a shake that throws brings the controls
 *   back, with the top stowed.
 *
 * Plain Kotlin, so all of that is asked on a JVM ([ControlsTest]) rather than
 * of a composable.
 */
internal data class Controls(
  /** The formula drawer is in. */
  val editing: Boolean = false,
  /** The dice pull-down is open. */
  val picking: Boolean = false,
  /** The formula tab is folded into the shut dice pull-down. */
  val stowed: Boolean = false,
  /** Everything over the table is put away for a full view of it. */
  val hidden: Boolean = false,
) {
  /** Whether the formula's tab, or its drawer, is on the table at all. */
  val formulaOut: Boolean get() = !stowed

  /** The formula drawer opened or shut. Opening it shuts the dice. */
  fun editing(open: Boolean): Controls =
    if (open) copy(editing = true, picking = false, stowed = false) else copy(editing = false)

  /**
   * The dice pull-down opened or shut. Opening it shuts the formula and takes
   * the top out of its stowed state, so the formula's tab is back beside it.
   */
  fun picking(open: Boolean): Controls =
    if (open) copy(picking = true, editing = false, stowed = false) else copy(picking = false)

  /**
   * A shake threw dice: both menus shut, the formula folded away, and a
   * cleared table given its controls back.
   */
  fun thrown(): Controls = Controls(stowed = true)

  /** A double tap on the table, or the tray's accessibility action: clear it, or put it all back. */
  fun toggled(): Controls = copy(hidden = !hidden)

  companion object {
    /**
     * Kept across a rotation, as the two menus always were: a phone turned
     * mid-formula comes back to the formula being typed.
     */
    val SAVER: Saver<Controls, List<Boolean>> =
      Saver(
        save = { listOf(it.editing, it.picking, it.stowed, it.hidden) },
        restore = { Controls(editing = it[0], picking = it[1], stowed = it[2], hidden = it[3]) },
      )
  }
}
