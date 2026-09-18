package de.drehtuer.dinfinity.data

import androidx.room.withTransaction
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.SavedRollRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The saved rolls themselves (`docs/dice-notation.md`, "Saved rolls").
 *
 * It hands out `core/model` types rather than rows, so the screens above never
 * see a column name and the storage below can change without them.
 *
 * The groups they live in are [SavedRollGroupRepository]'s, and the two are
 * apart rather than together because they were growing at different rates and
 * the one class had reached the size detekt allows. A screen that needs both
 * takes both; most need only one (`docs/TODO.md`, 4.3).
 */
class SavedRollRepository(
  private val database: DInfinityDatabase,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  /** Every saved roll there is, in the order the player dragged them into. */
  val all: Flow<List<SavedRoll>> =
    database.savedRolls().all().map { rows -> rows.map(SavedRollRow::asRoll) }

  /** The rolls of one group, in the order the list shows them. */
  fun inGroup(groupId: String): Flow<List<SavedRoll>> =
    database.savedRolls().inGroup(groupId).map { rows -> rows.map(SavedRollRow::asRoll) }

  /**
   * Writes a saved roll, new or changed.
   *
   * A roll nothing answers to yet lands at the **bottom** of its group's list
   * rather than at the top. A new roll is a thing somebody has just made and
   * not yet placed, and putting it at the top would move everything they had
   * already placed down by one — the list is theirs, and the app does not get
   * to reorder it on their behalf (`docs/dice-notation.md`, "Saved rolls").
   */
  suspend fun save(roll: SavedRoll) {
    val known = database.savedRolls().byId(roll.id) != null
    val order = if (known) roll.sortOrder else (database.savedRolls().maxSortOrder(roll.groupId) ?: -1) + 1
    database.savedRolls().upsert(roll.asRow(createdAt = roll.createdAtEpochMs.orNow(), order = order))
  }

  /**
   * Writes down the order a drag left the list in.
   *
   * [rollIds] is the whole of one group's list, top first; each roll is given
   * its place in it. In one transaction, because a list half in its old order
   * and half in its new one is an order nobody asked for — and because the
   * flow this repository hands out would otherwise emit every step of the
   * renumbering as if it were a list somebody was looking at.
   */
  suspend fun reorder(rollIds: List<String>) {
    database.withTransaction {
      rollIds.forEachIndexed { index, id -> database.savedRolls().setSortOrder(id, index) }
    }
  }

  /** One saved roll by its id, or null when nothing answers to it. */
  suspend fun byId(rollId: String): SavedRoll? = database.savedRolls().byId(rollId)?.asRoll()

  /** Takes one saved roll away. */
  suspend fun delete(rollId: String) {
    database.savedRolls().delete(rollId)
  }

  /** One more use of [rollId], now. The per-roll statistics, and nothing about the order. */
  suspend fun used(rollId: String) {
    database.savedRolls().used(rollId, clock())
  }

  private fun Long.orNow(): Long = if (this == 0L) clock() else this
}

private fun SavedRollRow.asRoll(): SavedRoll =
  SavedRoll(
    id = id,
    groupId = groupId,
    name = name,
    formula = formula,
    icon = icon,
    colorArgb = colourArgb,
    sortOrder = sortOrder,
    tablePin = pin(tableSetId, tableId),
    createdAtEpochMs = createdAtEpochMs,
    lastUsedAtEpochMs = lastUsedAtEpochMs,
    useCount = useCount,
  )

private fun SavedRoll.asRow(
  createdAt: Long,
  order: Int,
): SavedRollRow =
  SavedRollRow(
    id = id,
    groupId = groupId,
    name = name,
    formula = formula,
    icon = icon,
    colourArgb = colorArgb,
    sortOrder = order,
    tableSetId = tablePin?.setId,
    tableId = tablePin?.tableId,
    createdAtEpochMs = createdAt,
    lastUsedAtEpochMs = lastUsedAtEpochMs,
    useCount = useCount,
  )
