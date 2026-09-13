package de.drehtuer.dinfinity.data

import androidx.room.withTransaction
import de.drehtuer.dinfinity.core.collection.DiceCollection
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.SavedRollGroupRow
import de.drehtuer.dinfinity.data.db.SavedRollRow

/**
 * Writing an imported collection down
 * (`docs/dice-notation.md`, "Export and import"; `docs/architecture.md`,
 * decision 15).
 *
 * The rule this exists to keep is a refusal: **an import never merges and
 * never deletes.** A collection whose group name already exists is refused
 * outright, naming the clash; rename the group in the file or the one in the
 * app and import again. Everything else is added as new groups and new rolls.
 *
 * There is no conflict-resolution UI to get wrong, and an import can never
 * damage what is already there. That second half is not a promise this class
 * makes so much as one its shape cannot break: the check happens first, and
 * the writing happens in one transaction, so there is no state in which half a
 * collection has arrived.
 *
 * A class of its own rather than another method on [SavedRollRepository],
 * because importing is a transaction rather than a repository operation — and
 * because the repository is already as big as detekt will let a class be,
 * which was a fair warning rather than an obstacle.
 */
class CollectionImporter(
  private val database: DInfinityDatabase,
  private val clock: () -> Long = System::currentTimeMillis,
  private val ids: () -> String = {
    java.util.UUID
      .randomUUID()
      .toString()
  },
) {
  /**
   * Adds [collection] to what is already saved.
   *
   * @param unfiledName what the group a roll can always be put in is called.
   *   An import never needs it, but making sure it exists here means a fresh
   *   install whose first act is an import is in the same state as one whose
   *   first act was saving a roll.
   */
  suspend fun import(
    collection: DiceCollection,
    unfiledName: String,
  ): ImportResult {
    val clash = clashOf(collection)
    if (clash != null) return ImportResult.Refused(clash)
    return write(collection, unfiledName)
  }

  /**
   * The first group name in [collection] that something already answers to.
   *
   * Ignoring case, because two groups a capital apart are one group to a
   * person — and because that is the rule the group sheet keeps when somebody
   * types a name by hand.
   *
   * The *first* rather than all of them: the import is refused either way, and
   * one name is a thing a person can act on where a list of nine is a thing
   * they have to work through. The file has to be edited to fix even one, and
   * the next import says the next.
   */
  private suspend fun clashOf(collection: DiceCollection): String? {
    val here =
      database
        .savedRollGroups()
        .allNames()
        .map { it.lowercase() }
        .toSet()
    return collection.groups.firstOrNull { it.name.lowercase() in here }?.name
  }

  private suspend fun write(
    collection: DiceCollection,
    unfiledName: String,
  ): ImportResult =
    database.withTransaction {
      // New ids rather than the file's own slugs. A slug is stable inside the
      // file, which is what lets a person edit one by hand; it says nothing
      // about what this database already uses, and an id taken from a stranger
      // is an id that can collide with one somebody made here.
      val given = collection.groups.associate { it.id to ids() }
      val order = (database.savedRollGroups().maxSortOrder() ?: 0) + 1

      collection.groups.forEachIndexed { i, group ->
        database.savedRollGroups().upsert(
          SavedRollGroupRow(
            id = given.getValue(group.id),
            name = group.name,
            icon = group.icon,
            // The reader has already refused anything deeper than one level
            // and anything naming a group the file does not contain, so this
            // lookup cannot miss.
            parentId = group.parent?.let(given::getValue),
            sortOrder = order + i,
          ),
        )
      }
      val now = clock()
      collection.rolls.forEach { roll ->
        database.savedRolls().upsert(
          SavedRollRow(
            id = ids(),
            groupId = given.getValue(roll.group),
            name = roll.name,
            formula = roll.formula,
            icon = roll.icon,
            favourite = roll.favourite,
            createdAtEpochMs = now,
          ),
        )
      }
      // A collection may be the first thing a fresh install ever does.
      if (database.savedRollGroups().byId(SavedRollGroup.UNFILED_ID) == null) {
        database.savedRollGroups().upsert(
          SavedRollGroupRow(id = SavedRollGroup.UNFILED_ID, name = unfiledName),
        )
      }
      ImportResult.Imported(groups = collection.groups.size, rolls = collection.rolls.size)
    }
}

/**
 * What an import came to.
 *
 * Two outcomes and no third: it all arrived, or none of it did. The type is
 * the promise (`docs/dice-notation.md`: "an import can never damage what is
 * already there").
 */
sealed interface ImportResult {
  /** Everything in the file was added. Nothing that was here was touched. */
  data class Imported(
    val groups: Int,
    val rolls: Int,
  ) : ImportResult

  /**
   * Nothing was added, because [clash] is a group name that is already taken.
   *
   * @param clash the name as the *file* spells it, which is the one somebody
   *   has to go and change.
   */
  data class Refused(
    val clash: String,
  ) : ImportResult
}
