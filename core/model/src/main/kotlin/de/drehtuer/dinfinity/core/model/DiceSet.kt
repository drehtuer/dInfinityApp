package de.drehtuer.dinfinity.core.model

/**
 * An installed package: dice, table looks, or both (`docs/dice-sets.md`).
 *
 * The built-in set is one of these and nothing more. It is parsed and
 * validated by the same code as a set downloaded from a stranger's repository,
 * because a privileged path that skips the validator is a path the validator
 * is never tested on.
 *
 * @param id the slug notation uses as a `setref` and the folder name on disk.
 * @param version semver, compared when checking for updates.
 * @param homepage shown as text and opened only when the user taps it; `https`
 *   only, enforced at validation.
 * @param dice the dice this package defines, in file order.
 * @param tables the table looks it defines. Tables are global: installing a
 *   dice set adds its tables to every picker, and a set never overrides the
 *   selected table (`docs/tables.md`).
 */
data class DiceSet(
  val id: String,
  val name: String,
  val version: String,
  val author: String? = null,
  val license: String? = null,
  val description: String? = null,
  val homepage: String? = null,
  val dice: List<Die> = emptyList(),
  val tables: List<TableLook> = emptyList(),
) {
  init {
    require(dice.distinctBy(Die::id).size == dice.size) { "Set '$id' has two dice with the same id" }
    require(tables.distinctBy(TableLook::id).size == tables.size) { "Set '$id' has two tables with the same id" }
  }

  /** The die [dieId] names, or `null` when this set does not define it. */
  fun die(dieId: String): Die? = dice.firstOrNull { it.id == dieId }

  /** The table look [tableId] names, or `null` when this set does not define it. */
  fun table(tableId: String): TableLook? = tables.firstOrNull { it.id == tableId }

  companion object {
    /** The slug rule for a set id (`docs/dice-sets.md`). */
    val IdPattern: Regex = Regex("[a-z0-9][a-z0-9-]{1,38}[a-z0-9]")

    /** The id of the bundled package, which is always installed. */
    const val BUILTIN_ID: String = "builtin"

    /** The id of the package the face designer and "use a photo" write into. */
    const val PERSONAL_ID: String = "mine"

    /**
     * The die ids plain notation resolves without a `setref`
     * (`docs/dice-notation.md`). A set is free to leave any of them out;
     * notation then falls back to the built-in set for that one die.
     *
     * They are lower case because a die id is a slug. Notation is
     * case-insensitive everywhere except set ids, so `dF` typed by a player
     * and `df` in a set file are the same die.
     */
    val StandardDieIds: List<String> =
      listOf("d2", "d4", "d6", "d8", "d10", "d10-tens", "d12", "d18", "d20", "df")
  }
}
