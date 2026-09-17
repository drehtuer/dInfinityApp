package de.drehtuer.dinfinity.feature.saved

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.SavedRollSource
import de.drehtuer.dinfinity.core.model.tablePinFor
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.SavedRollLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The saved-rolls screen's state (`docs/dice-notation.md`, "Saved rolls").
 *
 * It watches the database rather than reading it: a roll saved on the editor
 * screen, or arriving in an import, appears here without anybody asking. The
 * two flows are combined rather than collected apart, because a list of rolls
 * and the groups they belong to that arrive a frame apart is a list that
 * flickers through a wrong state.
 *
 * @param scope the screen's own scope; every write and every read ends with it.
 * @param unfiledName what the group a roll can always be put in is called. It
 *   is passed in rather than hard-coded because it is words on a screen, and
 *   words on a screen are the resources' (`docs/TODO.md`, Step 6).
 */
class SavedPresenter(
  private val library: SavedRollLibrary,
  private val catalog: DiceCatalog,
  private val scope: CoroutineScope,
  private val unfiledName: String,
  private val onActiveGroup: (String) -> Unit = {},
  activeGroupId: String = SavedRollGroup.UNFILED_ID,
) {
  /** What the screen draws. */
  var state: SavedState by mutableStateOf(SavedState())
    private set

  private var active: String = activeGroupId

  /**
   * The order a drag has left the list in, until the database says the same.
   *
   * The list reorders live under the finger and is written down when the
   * finger lifts, so between those two moments what the screen shows is not
   * yet what the database holds. Without this, an emission arriving mid-drag —
   * from a `used` count, an import, anything — would snap the row back under
   * the finger that is moving it.
   */
  private var order: List<String>? = null

  init {
    scope.launch {
      library.groups.ensureUnfiled(unfiledName)
      combine(library.groups.all, library.rolls.all) { groups, rolls -> groups to rolls }
        .collect { (groups, rolls) -> publish(groups, rolls) }
    }
  }

  /** The player chose a different group. */
  fun open(groupId: String) {
    active = groupId
    // Another group is another list; an order held for this one means nothing
    // there.
    order = null
    onActiveGroup(groupId)
    state = state.copy(activeGroupId = groupId, rolls = state.allRolls.rollsOf(groupId))
    showGroups(false)
  }

  /** The group switcher was opened or closed. */
  fun showGroups(showing: Boolean) {
    state = state.copy(switching = showing)
  }

  /**
   * The row under the finger is [onto]; put [rollId] there.
   *
   * Live rather than on release, which is what makes a drag readable: the list
   * makes room as the finger arrives rather than rearranging itself once it
   * has gone. Nothing is written down here — [settle] does that when the drag
   * ends, because a drag across a screenful of rolls would otherwise be thirty
   * transactions nobody asked for.
   */
  fun move(
    rollId: String,
    onto: Int,
  ) {
    val shown = state.rolls
    val moving = shown.firstOrNull { it.roll.id == rollId } ?: return
    val next = SavedOrder.moved(shown, moving, onto)
    if (next === shown) return
    order = next.map { it.roll.id }
    state = state.copy(rolls = next)
  }

  /** The finger lifted: the order the list is in is now the order it is in. */
  fun settle() {
    val settled = order ?: return
    scope.launch { library.rolls.reorder(settled) }
  }

  /** One more use of a roll, which the statistics count and the order ignores. */
  fun used(rollId: String) {
    scope.launch { library.rolls.used(rollId) }
  }

  /** Takes a saved roll away. */
  fun delete(rollId: String) {
    scope.launch { library.rolls.delete(rollId) }
  }

  private fun publish(
    groups: List<SavedRollGroup>,
    rolls: List<SavedRoll>,
  ) {
    // A group that has been deleted under us is not a group to keep showing.
    if (groups.none { it.id == active }) active = SavedRollGroup.UNFILED_ID
    // Once the database reports the order a drag left, the drag is over in
    // every sense and the override has nothing left to say.
    if (rolls.filter { it.groupId == active }.map(SavedRoll::id) == order) order = null
    val byId = groups.associateBy(SavedRollGroup::id)
    val entries =
      rolls.map { roll ->
        SavedEntry(
          roll = roll,
          broken = !resolves(roll.formula),
          source =
            SavedRollSource(
              rollId = roll.id,
              groupId = roll.groupId,
              tablePin = tablePinFor(roll, byId[roll.groupId]),
            ),
        )
      }
    state =
      SavedState(
        groups = groups.map { group -> GroupEntry(group = group, rolls = rolls.count { it.groupId == group.id }) },
        allRolls = entries,
        rolls = entries.rollsOf(active),
        activeGroupId = active,
        switching = state.switching,
        loaded = true,
      )
  }

  /**
   * Whether this formula still means something.
   *
   * Checked every time the list is drawn rather than stored, because the set
   * it names can be uninstalled between one drawing and the next. A roll that
   * no longer resolves is not deleted and not rewritten — it says so and falls
   * back to the built-in set when thrown (`docs/dice-notation.md`).
   */
  private fun resolves(formula: String): Boolean = SavedFormula.resolves(formula, catalog)

  /**
   * One group's rolls, in the order the database has them — or in the order a
   * drag has left them, while that drag is still being written down.
   *
   * The override is dropped the moment it stops fitting: a list that has
   * gained or lost a roll since the drag is a different list, and holding an
   * order for it would hide whatever arrived.
   */
  private fun List<SavedEntry>.rollsOf(groupId: String): List<SavedEntry> {
    val mine = filter { it.roll.groupId == groupId }
    val places = order?.withIndex()?.associate { (at, id) -> id to at }
    return if (places == null || mine.any { it.roll.id !in places }) {
      mine
    } else {
      mine.sortedBy { places.getValue(it.roll.id) }
    }
  }
}

/** One saved roll as the list shows it. */
data class SavedEntry(
  val roll: SavedRoll,
  /** True when its formula no longer names dice any installed set has. */
  val broken: Boolean,
  /**
   * What a throw of it carries: which roll, which group, and the table it
   * lands on (`docs/tables.md`, "Selecting a table").
   *
   * The table is settled here because here is where both halves of the rule
   * are already known — the roll and the group it lives in arrive in the same
   * emission, so nothing has to be asked for a second time when somebody taps.
   */
  val source: SavedRollSource,
)

/** One group in the switcher, with how many rolls are in it. */
data class GroupEntry(
  val group: SavedRollGroup,
  val rolls: Int,
)

/**
 * What the saved-rolls screen is showing.
 *
 * One object rather than several pieces of state, so the list and the group it
 * belongs to can never be drawn a frame apart.
 */
data class SavedState(
  val groups: List<GroupEntry> = emptyList(),
  val rolls: List<SavedEntry> = emptyList(),
  val activeGroupId: String = SavedRollGroup.UNFILED_ID,
  val switching: Boolean = false,
  /** False until the database has answered once, so an empty list is not "nothing saved". */
  val loaded: Boolean = false,
  internal val allRolls: List<SavedEntry> = emptyList(),
) {
  /** The group the list is showing, or null before anything has loaded. */
  val activeGroup: GroupEntry? get() = groups.firstOrNull { it.group.id == activeGroupId }

  /** True when this group really has nothing in it, rather than not having answered yet. */
  val empty: Boolean get() = loaded && rolls.isEmpty()
}
