package de.drehtuer.dinfinity.data

import androidx.room.withTransaction
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.SavedRollGroupRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The folders saved rolls live in
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * Split out of [SavedRollRepository] rather than kept beside it, because the
 * two answer different questions and were growing at different rates: this one
 * is about the shape of the list, that one is about the rolls in it. The
 * repository had reached the class size detekt allows, and a raised threshold
 * would have been a way of not noticing that (`docs/TODO.md`, 4.3).
 *
 * Two rules are kept here rather than in a screen, because a screen is not the
 * only thing that writes: an import writes too, and a rule only one of them
 * follows is not a rule.
 *
 * - **Groups nest exactly one level.** A group whose parent has a parent is
 *   refused. SQLite cannot say that, and a cycle built by an import would be a
 *   list that never finishes drawing.
 * - **Unfiled always exists.** It is where a roll with no group of its own
 *   lives and where the rolls of a deleted group go, so the app is never in a
 *   state where a roll has nowhere to be.
 */
class SavedRollGroupRepository(
  private val database: DInfinityDatabase,
) {
  /** Every group, parents and children alike, in the order the switcher shows them. */
  val all: Flow<List<SavedRollGroup>> =
    database.savedRollGroups().all().map { rows -> rows.map(SavedRollGroupRow::asGroup) }

  /**
   * Makes sure the group a roll can always be put in exists.
   *
   * Called when the saved-rolls screen opens. Creating it lazily rather than
   * at install time means a player who never opens that screen never gets a
   * row they did not ask for, and one who does gets it before anything can
   * need it.
   */
  suspend fun ensureUnfiled(name: String): SavedRollGroup {
    val existing = database.savedRollGroups().byId(SavedRollGroup.UNFILED_ID)
    if (existing != null) return existing.asGroup()
    val unfiled = SavedRollGroup(id = SavedRollGroup.UNFILED_ID, name = name)
    database.savedRollGroups().upsert(unfiled.asRow())
    return unfiled
  }

  /** One group by its id, or null when nothing answers to it. */
  suspend fun byId(groupId: String): SavedRollGroup? = database.savedRollGroups().byId(groupId)?.asGroup()

  /**
   * Writes a group, new or changed.
   *
   * One level is checked from both ends, because there are two ways to break
   * it and they are not the same mistake: putting a group inside one that is
   * already inside another, and moving a group that has children of its own
   * into anything at all.
   *
   * @throws IllegalArgumentException if it would nest more than one level
   *   deep, or be its own parent.
   */
  suspend fun save(group: SavedRollGroup) {
    val parentId = group.parentId
    require(parentId != group.id) { "A group cannot be inside itself" }
    if (parentId != null) {
      val parent = database.savedRollGroups().byId(parentId)
      requireNotNull(parent) { "There is no group '$parentId' to put '${group.name}' in" }
      require(parent.parentId == null) {
        "Groups nest ${SavedRollGroup.MAX_DEPTH} deep: '${parent.name}' is already inside another group"
      }
      require(database.savedRollGroups().countChildren(group.id) == 0) {
        "Groups nest ${SavedRollGroup.MAX_DEPTH} deep: '${group.name}' has groups inside it already"
      }
    }
    database.savedRollGroups().upsert(group.asRow())
  }

  /**
   * Takes a group away and moves what was in it to Unfiled.
   *
   * Never a cascade in practice, though the schema has one under it: a group
   * is a folder, and deleting a folder full of things somebody wrote should
   * not delete the things. Its child groups are lifted to the top level for
   * the same reason. All of it is one transaction, so there is no moment
   * where a roll belongs to a group that is gone.
   *
   * It reaches the rolls' table as well as its own, which is why deleting a
   * group lives here and not on either side alone: the move and the delete
   * have to be the same transaction.
   */
  suspend fun delete(
    groupId: String,
    unfiledName: String,
  ) {
    require(groupId != SavedRollGroup.UNFILED_ID) { "Unfiled is where rolls go; it cannot be deleted" }
    database.withTransaction {
      ensureUnfiled(unfiledName)
      // The children are lifted to the top level rather than deleted with
      // their parent: a group inside one that no longer exists is a group
      // nothing can reach, and deleting them would take their rolls too.
      database.savedRollGroups().detachChildren(groupId)
      database.savedRolls().move(fromGroupId = groupId, toGroupId = SavedRollGroup.UNFILED_ID)
      database.savedRollGroups().delete(groupId)
    }
  }

  /** True when another group already answers to [name]. */
  suspend fun nameTaken(
    name: String,
    exceptId: String = "",
  ): Boolean = database.savedRollGroups().countNamed(name, exceptId) > 0
}

private fun SavedRollGroupRow.asGroup(): SavedRollGroup =
  SavedRollGroup(
    id = id,
    name = name,
    icon = icon,
    parentId = parentId,
    sortOrder = sortOrder,
    tablePin = pin(tableSetId, tableId),
  )

private fun SavedRollGroup.asRow(): SavedRollGroupRow =
  SavedRollGroupRow(
    id = id,
    name = name,
    icon = icon,
    parentId = parentId,
    sortOrder = sortOrder,
    tableSetId = tablePin?.setId,
    tableId = tablePin?.tableId,
  )

/**
 * A table pin is both halves or neither: an id with no package names nothing.
 *
 * Shared by the two repositories because a group and a roll pin a table the
 * same way, and the fallback between them only works if they agree
 * (`docs/tables.md`).
 */
internal fun pin(
  setId: String?,
  tableId: String?,
): TablePin? = if (setId != null && tableId != null) TablePin(setId, tableId) else null
