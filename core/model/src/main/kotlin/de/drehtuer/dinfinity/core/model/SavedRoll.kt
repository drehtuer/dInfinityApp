package de.drehtuer.dinfinity.core.model

/**
 * A named formula with an icon, rolled with one tap
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * The formula is stored as text and re-validated every time it is displayed,
 * because the dice set it names might have been uninstalled since. A saved
 * roll that no longer resolves is not deleted and not rewritten: it shows a
 * warning badge and falls back to the built-in set when rolled.
 *
 * @param groupId the group this roll lives in.
 * @param icon an emoji or a name from the built-in icon pack. Never an image
 *   file — collections travel as JSON and carry no binaries.
 * @param colorArgb the roll's colour tag, used for its icon in the list, or
 *   `null` to use the accent.
 * @param favourite favourites are pinned above the rest, which are ordered by
 *   recent use.
 * @param tablePin the table this roll is always thrown on, or `null` to follow
 *   the group's pin and then the app default (`docs/tables.md`).
 * @param useCount how often it has been rolled; drives the ordering and the
 *   per-roll statistics.
 */
data class SavedRoll(
  val id: String,
  val groupId: String,
  val name: String,
  val formula: String,
  val icon: String = "",
  val colorArgb: Int? = null,
  val favourite: Boolean = false,
  val tablePin: TablePin? = null,
  val createdAtEpochMs: Long = 0L,
  val lastUsedAtEpochMs: Long? = null,
  val useCount: Int = 0,
)

/**
 * How players organise rolls: per game, per character, per monster stat block.
 *
 * Groups nest exactly one level — `D&D / Thorin`, `Pathfinder / Ezren`. A
 * deeper tree is not worth the UI it would need, and one level is what a
 * campaign actually looks like.
 *
 * The active group also sets the default statistics session
 * (`docs/statistics.md`).
 *
 * @param parentId the group above this one, or `null` for a top-level group.
 *   A group whose parent has a parent is invalid and is refused at import.
 * @param tablePin the table every roll in this group is thrown on, unless the
 *   roll pins its own (`docs/tables.md`).
 */
data class SavedRollGroup(
  val id: String,
  val name: String,
  val icon: String = "",
  val parentId: String? = null,
  val sortOrder: Int = 0,
  val tablePin: TablePin? = null,
) {
  companion object {
    /** Groups nest one level deep: a game, and a character inside it. */
    const val MAX_DEPTH: Int = 2

    /** Where a roll with no group of its own lives, on a fresh install. */
    const val UNFILED_ID: String = "unfiled"
  }
}

/**
 * A table look pinned to a saved roll or a group, named by the package that
 * supplies it and its id within that package.
 *
 * It is a pair rather than a single string because table ids are unique only
 * within their package, and two packages may both ship a `green-felt`.
 */
data class TablePin(
  val setId: String,
  val tableId: String,
)
