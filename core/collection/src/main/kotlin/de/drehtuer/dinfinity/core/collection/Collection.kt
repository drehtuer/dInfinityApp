package de.drehtuer.dinfinity.core.collection

/**
 * Saved rolls travelling as a file
 * (`docs/dice-notation.md`, "Export and import").
 *
 * A collection is the whole of what is exported and the whole of what is
 * imported: a name, the groups, and the rolls in them. It is deliberately not
 * the database's shape. Ids inside it are **slugs a person could have typed**,
 * stable across an export and an import, so a community repository of "stat
 * blocks for monster manual X" can be edited by hand and a re-import can
 * recognise what it already has.
 *
 * What it does not carry is as much of the point as what it does. No colours
 * of the app's choosing, no use counts, no timestamps, no seeds, no image
 * files, and no sort order — the rolls travel in the order they are in — a
 * collection is what somebody wrote, not what the app made of it.
 *
 * @param format the version of this file format. Written as
 *   [CollectionLimits.FORMAT]; a file claiming any other number is refused
 *   rather than guessed at, because a format that reads a newer file as best
 *   it can is a format that silently drops what it does not understand.
 */
data class DiceCollection(
  val name: String,
  val groups: List<CollectionGroup> = emptyList(),
  val rolls: List<CollectionRoll> = emptyList(),
  val format: Int = CollectionLimits.FORMAT,
)

/**
 * One group in a collection.
 *
 * @param id the slug rolls refer to, and the id the group is given when it is
 *   imported.
 * @param parent the id of the group it sits inside, or null for the top level.
 *   One level, like everywhere else: a group whose parent has a parent is
 *   refused at import (`docs/dice-notation.md`).
 */
data class CollectionGroup(
  val id: String,
  val name: String,
  val icon: String = "",
  val parent: String? = null,
)

/**
 * One saved roll in a collection.
 *
 * @param group the slug of the group it belongs to, which must be one of the
 *   collection's own.
 * @param formula text, and validated as text by the same parser the formula
 *   field uses. A set reference naming a set that is not installed is kept and
 *   flagged rather than rewritten — the set may be installed tomorrow.
 *
 * There is no field for where a roll sits in its group's list. **The order is
 * the file's own order** — the rolls arrive in it and are written back in it —
 * which is what a person editing the file by hand would expect and the one
 * spelling that cannot disagree with itself (`docs/dice-notation.md`,
 * "Export and import").
 */
data class CollectionRoll(
  val group: String,
  val name: String,
  val formula: String,
  val icon: String = "",
)
