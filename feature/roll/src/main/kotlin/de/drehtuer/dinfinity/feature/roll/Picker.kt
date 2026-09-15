package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.DicePicker
import de.drehtuer.dinfinity.core.notation.PickableDie

/**
 * Which set the picker row is offering, and what its dice are called
 * (`design/dInfinity.dc.html`, options 1h and 4a;
 * `docs/dice-notation.md`, "Picking dice without typing").
 *
 * Apart from [RollMachine] because it is a question of its own with a rule of
 * its own, and because the machine is already as large as detekt will let a
 * class be — which was a fair warning rather than an obstacle. What is here is
 * everything about *which dice are on the row*; what a tap on one of them does
 * to the formula stays with the formula.
 *
 * **It is not the default set.** Which set a bare `d20` means is a preference
 * chosen where the sets are (`docs/dice-sets.md`, design `6a`). This is a
 * choice that lasts a screen visit, and the two are different questions half
 * the time: somebody whose default is their own set still reaches for a
 * borrowed d20.
 */
internal class Picker(
  private val catalog: DiceCatalog,
) {
  /** The set the row is offering. The default set until somebody says otherwise. */
  var from: String = catalog.defaultSetId
    private set

  /** The dice it offers, recomputed only when [from] changes. */
  var dice: List<PickableDie> = offeredBy(catalog.defaultSetId)
    private set

  /** Every set with dice to offer, for the chooser. Fixed: the catalogue is. */
  val sets: List<DiceSet> get() = catalog.installed

  /**
   * Offers [setId]'s dice instead, and says whether anything moved.
   *
   * A set that is not installed is not a set to pick from, and choosing the
   * one already chosen is not a change — a caller that recomputed the badges
   * either way would be doing work for a tap that did nothing.
   */
  fun choose(setId: String): Boolean {
    if (setId == from || catalog.set(setId) == null) return false
    from = setId
    dice = offeredBy(setId)
    return true
  }

  /**
   * The dice [setId] offers, written the way a tap on them should write them.
   *
   * Qualified with the set's id — `brass:1d20` — unless it *is* the default
   * set, where a bare `1d20` is both what somebody would type and the only
   * spelling the badges can be read back out of: `builtin:1d6` while `builtin`
   * is the default is a different spelling, and the picker deliberately edits
   * only what it can spell (`DicePicker`).
   */
  private fun offeredBy(setId: String): List<PickableDie> {
    val set = catalog.set(setId) ?: return emptyList()
    return DicePicker.offeredBy(set, setRef = setId.takeIf { it != catalog.defaultSetId })
  }
}
