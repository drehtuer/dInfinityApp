package de.drehtuer.dinfinity.data

import androidx.room.withTransaction
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.SavedRollGroupRow
import de.drehtuer.dinfinity.data.db.SavedRollRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Saved rolls and the groups they live in
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * It hands out `core/model` types rather than rows, so the screens above never
 * see a column name and the storage below can change without them.
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
class SavedRollRepository(
  private val database: DInfinityDatabase,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  /** Every group, parents and children alike, in the order the switcher shows them. */
  val groups: Flow<List<SavedRollGroup>> =
    database.savedRollGroups().all().map { rows -> rows.map(SavedRollGroupRow::asGroup) }

  /** Every saved roll there is, favourites first and then by recent use. */
  val all: Flow<List<SavedRoll>> =
    database.savedRolls().all().map { rows -> rows.map(SavedRollRow::asRoll) }

  /** The rolls of one group, in the order the list shows them. */
  fun inGroup(groupId: String): Flow<List<SavedRoll>> =
    database.savedRolls().inGroup(groupId).map { rows -> rows.map(SavedRollRow::asRoll) }

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

  /**
   * Writes a group, new or changed.
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
    }
    database.savedRollGroups().upsert(group.asRow())
  }

  /** Writes a saved roll, new or changed. */
  suspend fun save(roll: SavedRoll) {
    database.savedRolls().upsert(roll.asRow(createdAt = roll.createdAtEpochMs.orNow()))
  }

  /**
   * Takes a group away and moves what was in it to Unfiled.
   *
   * Never a cascade in practice, though the schema has one under it: a group
   * is a folder, and deleting a folder full of things somebody wrote should
   * not delete the things. Its child groups are lifted to the top level for
   * the same reason. All of it is one transaction, so there is no moment
   * where a roll belongs to a group that is gone.
   */
  suspend fun deleteGroup(
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

  /** One saved roll by its id, or null when nothing answers to it. */
  suspend fun byId(rollId: String): SavedRoll? = database.savedRolls().byId(rollId)?.asRoll()

  /** Takes one saved roll away. */
  suspend fun delete(rollId: String) {
    database.savedRolls().delete(rollId)
  }

  /** One more use of [rollId], now. Drives the ordering and the statistics. */
  suspend fun used(rollId: String) {
    database.savedRolls().used(rollId, clock())
  }

  /** True when another group already answers to [name]. */
  suspend fun nameTaken(
    name: String,
    exceptId: String = "",
  ): Boolean = database.savedRollGroups().countNamed(name, exceptId) > 0

  private fun Long.orNow(): Long = if (this == 0L) clock() else this
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

private fun SavedRollRow.asRoll(): SavedRoll =
  SavedRoll(
    id = id,
    groupId = groupId,
    name = name,
    formula = formula,
    icon = icon,
    colorArgb = colourArgb,
    favourite = favourite,
    tablePin = pin(tableSetId, tableId),
    createdAtEpochMs = createdAtEpochMs,
    lastUsedAtEpochMs = lastUsedAtEpochMs,
    useCount = useCount,
  )

private fun SavedRoll.asRow(createdAt: Long): SavedRollRow =
  SavedRollRow(
    id = id,
    groupId = groupId,
    name = name,
    formula = formula,
    icon = icon,
    colourArgb = colorArgb,
    favourite = favourite,
    tableSetId = tablePin?.setId,
    tableId = tablePin?.tableId,
    createdAtEpochMs = createdAt,
    lastUsedAtEpochMs = lastUsedAtEpochMs,
    useCount = useCount,
  )

/** A table pin is both halves or neither: an id with no package names nothing. */
private fun pin(
  setId: String?,
  tableId: String?,
): TablePin? = if (setId != null && tableId != null) TablePin(setId, tableId) else null
