package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.DiceSet

/**
 * The dice sets a formula may resolve against: everything installed, and which
 * of them Settings has made the default.
 *
 * It is an interface rather than a list because the sets live behind storage
 * and this module must not: the formula field re-validates on every keystroke,
 * and `:core:notation` has no Android dependency to reach a database with.
 */
interface DiceCatalog {
  /** The set plain notation resolves against first (`docs/dice-notation.md`). */
  val defaultSetId: String

  /**
   * Every installed set, in the order they were installed.
   *
   * A list rather than a lookup because two screens want the whole of it: the
   * first-launch screen counts it, and the dice-set browser lists it
   * (`docs/TODO.md`, Steps 4.1 and 4.4). Resolving a formula still goes
   * through [set], which is a lookup and stays one.
   */
  val installed: List<DiceSet>

  /** The installed set with this id, or `null` when nothing by that name is installed. */
  fun set(id: String): DiceSet?

  companion object {
    /**
     * A catalogue of [sets], defaulting to [defaultSetId].
     *
     * The bundled set has to be among them: it is what a die missing from the
     * default set falls back to, so a catalogue without it has no floor.
     */
    fun of(
      sets: List<DiceSet>,
      defaultSetId: String = DiceSet.BUILTIN_ID,
    ): DiceCatalog {
      require(sets.any { it.id == DiceSet.BUILTIN_ID }) {
        "A catalogue must contain the '${DiceSet.BUILTIN_ID}' set; it is what notation falls back to"
      }
      require(sets.any { it.id == defaultSetId }) { "The default set '$defaultSetId' is not installed" }
      val byId = sets.associateBy(DiceSet::id)
      return object : DiceCatalog {
        override val defaultSetId: String = defaultSetId

        override val installed: List<DiceSet> = sets

        override fun set(id: String): DiceSet? = byId[id]
      }
    }
  }
}
