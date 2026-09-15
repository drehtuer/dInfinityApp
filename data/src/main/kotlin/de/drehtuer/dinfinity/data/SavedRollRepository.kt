package de.drehtuer.dinfinity.data

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
  /** Every saved roll there is, favourites first and then by recent use. */
  val all: Flow<List<SavedRoll>> =
    database.savedRolls().all().map { rows -> rows.map(SavedRollRow::asRoll) }

  /** The rolls of one group, in the order the list shows them. */
  fun inGroup(groupId: String): Flow<List<SavedRoll>> =
    database.savedRolls().inGroup(groupId).map { rows -> rows.map(SavedRollRow::asRoll) }

  /** Writes a saved roll, new or changed. */
  suspend fun save(roll: SavedRoll) {
    database.savedRolls().upsert(roll.asRow(createdAt = roll.createdAtEpochMs.orNow()))
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
