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
 * @param sortOrder where the player dragged it in its group's list, low
 *   first. It is the only thing that orders the list: there is no pinning, no
 *   favourites and no recency, because a list that reorders itself between two
 *   fights is a list nobody can point at (`docs/dice-notation.md`).
 * @param tablePin the table this roll is always thrown on, or `null` to follow
 *   the group's pin and then the app default (`docs/tables.md`).
 * @param useCount how often it has been rolled. It feeds the per-roll
 *   statistics and nothing else — ordering is [sortOrder]'s alone.
 */
data class SavedRoll(
  val id: String,
  val groupId: String,
  val name: String,
  val formula: String,
  val icon: String = "",
  val colorArgb: Int? = null,
  val sortOrder: Int = 0,
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

/**
 * Which saved roll a throw came from, and the table it lands on
 * (`docs/statistics.md`, per saved roll and per group; `docs/tables.md`).
 *
 * The group is carried beside the roll rather than looked up later, because
 * which group a roll belongs to is a fact about the roll and not about which
 * group a list happened to be showing when it was tapped.
 *
 * So is [tablePin]. It is the *answer* to the precedence rule rather than the
 * roll's own pin: whoever taps a saved roll has the roll and its group in
 * hand, which is the one moment both halves of the rule are known without
 * asking a database, so the question is settled there and the throw carries
 * its answer. `null` means the app default, and nothing downstream has to know
 * whether that is because nothing was pinned or because the default *is* the
 * pin.
 */
data class SavedRollSource(
  val rollId: String,
  val groupId: String,
  val tablePin: TablePin? = null,
)

/**
 * The table a throw of [roll] lands on, most specific pin first
 * (`docs/tables.md`, "Selecting a table").
 *
 * The roll's own pin, then the pin of the group it lives in, then `null` for
 * the app default. Written once, here, rather than at each of the two places a
 * saved roll can be tapped — a precedence that two screens each implement is a
 * precedence that will eventually disagree with itself.
 *
 * [group] is the group [roll] names. A group that is not there — deleted while
 * another screen was in front — is `null` and simply does not get a say, which
 * is the same fallback the default set and the default table already follow.
 */
fun tablePinFor(
  roll: SavedRoll,
  group: SavedRollGroup?,
): TablePin? = roll.tablePin ?: group?.tablePin
