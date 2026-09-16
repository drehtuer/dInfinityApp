package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RolledGroup

/**
 * Why a group stopped throwing dice before the notation said it could
 * (`docs/dice-notation.md`, "Limits").
 *
 * Every other thing a die has to say about itself is visible on the sheet
 * already: a dropped die is struck through, a rerolled one stands beside its
 * replacement, and an exploded one is simply another chip in the row. These
 * two are the exceptions, because what they describe is a die that **is not
 * there** — a chip that never appeared. `8d6!` that stops at its depth limit
 * and `8d6!` that ran out of table look identical on the sheet and read, both
 * times, as an explosion that did not happen.
 *
 * It is a group-level answer rather than a per-die one on purpose. A player
 * asking "why is that not still going?" is asking about the group they wrote,
 * and which die of the eight happened to be the one that hit the limit is not
 * something anybody needs to know.
 *
 * Lifted out of the sheet's draw lambda because it is a decision rather than a
 * drawing, which is the rule the coverage section of `docs/TODO.md` records:
 * what a group has to say is arithmetic over its notes and is tested as such.
 */
internal enum class ChainLimit {
  /**
   * A die went on exploding until the chain reached
   * [de.drehtuer.dinfinity.core.notation.NotationLimits.MAX_EXPLOSION_DEPTH].
   */
  ExplosionDepth,

  /**
   * The tray had no clear floor left, so the die this one called for was never
   * thrown.
   *
   * Both an explosion and a reroll end this way, and the sheet says the same
   * thing about either: an added die is dropped into the floor the settled
   * dice leave clear, and a tray with none left cannot take one
   * (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll
   * adds").
   */
  TrayFull,
  ;

  companion object {
    /**
     * What [group] has to say, in enum order, each reason at most once.
     *
     * Order is the enum's rather than the dice's so that two throws which hit
     * the same two limits in a different order read the same way. At most one
     * line of each: twelve dice that each ran out of table is one fact about
     * the tray, not twelve.
     */
    fun of(group: RolledGroup): List<ChainLimit> {
      val notes = group.dice.flatMapTo(mutableSetOf()) { die -> die.notes }
      return entries.filter { it.note in notes }
    }
  }

  /** The note in the model this reason reads. */
  internal val note: DieNote
    get() =
      when (this) {
        ExplosionDepth -> DieNote.ExplosionLimitReached
        TrayFull -> DieNote.TrayFull
      }
}
