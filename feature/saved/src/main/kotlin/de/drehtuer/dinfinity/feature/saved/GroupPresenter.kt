package de.drehtuer.dinfinity.feature.saved

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.SavedRollLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Making a group, renaming one, and taking one away
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * Its own presenter rather than more methods on [SavedPresenter], because it
 * answers a different question: that one is about which rolls are on screen,
 * this one is about the folders they sit in. The saved-rolls screen holds both
 * and the group sheet is drawn over the list.
 *
 * It watches the group list rather than asking, which is what lets every rule
 * it enforces be answered without a round trip: whether a name is taken and
 * whether a group has children of its own are both facts about the list it
 * already has. The repository checks the nesting rule again when it writes,
 * because an import writes too and a rule only the screen follows is not a
 * rule.
 *
 * @param unfiledName what the group a roll can always be put in is called;
 *   deleting a group moves its rolls there.
 */
class GroupPresenter(
  private val library: SavedRollLibrary,
  private val catalog: DiceCatalog,
  private val scope: CoroutineScope,
  private val unfiledName: String,
  private val ids: () -> String = {
    java.util.UUID
      .randomUUID()
      .toString()
  },
) {
  /** What the sheet draws, or a draft of `null` when it is closed. */
  var draft: GroupDraft? by mutableStateOf(null)
    private set

  /** False until the database has answered once, so no rule is judged on an empty list. */
  var loaded: Boolean by mutableStateOf(false)
    private set

  private var known: List<SavedRollGroup> = emptyList()
  private var counts: Map<String, Int> = emptyMap()

  init {
    // One collector over both flows rather than two: the rules are about the
    // groups and the rolls together — whether a name is taken, and how much
    // would move if the group went — and two collectors would let the sheet
    // be drawn from half of each.
    scope.launch {
      combine(library.groups.all, library.rolls.all) { groups, rolls -> groups to rolls }
        .collect { (groups, rolls) ->
          known = groups
          counts = rolls.groupingBy { it.groupId }.eachCount()
          loaded = true
          // A group deleted under us — by an import, or on another screen — is
          // not a group to go on editing.
          val open = draft
          val vanished = open != null && !open.fresh && groups.none { it.id == open.id }
          if (vanished) draft = null else refresh()
        }
    }
  }

  /**
   * Starts a group that does not exist yet.
   *
   * @param inside the group to put it in, when the player asked for a
   *   subgroup of the one they are looking at.
   */
  fun create(inside: String? = null) {
    draft = drafted(SavedRollGroup(id = ids(), name = "", parentId = inside), fresh = true)
  }

  /** Opens an existing group for renaming, re-icon-ing or moving. */
  fun edit(groupId: String) {
    val group = known.firstOrNull { it.id == groupId } ?: return
    draft = drafted(group, fresh = false)
  }

  /** The name was typed into. */
  fun name(typed: String) {
    draft = draft?.let { open -> drafted(open.group.copy(name = typed), open.fresh) }
  }

  /**
   * Everything about the group that is a choice rather than a keystroke: its
   * mark, the group it lives in, and the table its rolls land on.
   *
   * One function rather than three, for the reason `EditorPresenter.choose`
   * gives — none of them needs the name re-checked, a name does, and it has
   * its own. Three identical one-line setters would be three places for the
   * fourth to be written slightly differently.
   */
  fun choose(change: SavedRollGroup.() -> SavedRollGroup) {
    draft = draft?.let { open -> drafted(open.group.change(), open.fresh) }
  }

  /** Writes the group. Does nothing while the draft is not savable. */
  fun save(onSaved: (String) -> Unit = {}) {
    val open = draft ?: return
    if (!open.savable) return
    val group = open.group.copy(name = open.group.name.trim())
    draft = null
    scope.launch {
      library.groups.save(group)
      onSaved(group.id)
    }
  }

  /**
   * Takes the group away, moving its rolls to Unfiled and lifting its
   * children to the top level.
   *
   * Never deletes a roll, which is the rule the repository keeps: a folder
   * being removed should not remove what somebody put in it.
   */
  fun delete(onDeleted: () -> Unit = {}) {
    val open = draft ?: return
    if (!open.deletable) return
    draft = null
    scope.launch {
      library.groups.delete(open.id, unfiledName)
      onDeleted()
    }
  }

  /** The sheet was dismissed without saving. */
  fun dismiss() {
    draft = null
  }

  private fun refresh() {
    draft = draft?.let { open -> drafted(open.group, open.fresh) }
  }

  private fun drafted(
    group: SavedRollGroup,
    fresh: Boolean,
  ): GroupDraft {
    val typed = group.name.trim()
    return GroupDraft(
      group = group,
      fresh = fresh,
      // The list is what says a name is taken, so the answer is there the
      // moment the letter is typed rather than a query later.
      clash = known.firstOrNull { it.id != group.id && it.name.equals(typed, ignoreCase = true) }?.name,
      // One level, from this end: a group with groups inside it cannot go
      // inside anything, because that would make its children two deep.
      nestable = known.none { it.parentId == group.id },
      parents = known.filter { it.parentId == null && it.id != group.id },
      tables = tableChoicesOf(catalog),
      rolls = counts[group.id] ?: 0,
    )
  }
}

/**
 * A group being written, and everything the sheet needs to know about it.
 *
 * @param fresh true while it has never been saved, which is what tells the
 *   sheet whether to offer Delete and what to call itself.
 * @param clash the name of the group that already answers to this one's name,
 *   or `null` when the name is free. Names are unique because an import
 *   refuses a collection whose group name is taken, and a rule that only holds
 *   at import is a rule the app can walk into (`docs/dice-notation.md`).
 * @param nestable false when groups are already inside this one, so it cannot
 *   be put inside another without making them two levels deep.
 * @param parents the groups it could be put inside: top-level ones, never
 *   itself.
 * @param tables every table a group can be pinned to, with "Default" at the
 *   front meaning *follow the app's* (`docs/tables.md`).
 * @param rolls how many saved rolls are in it, so deleting can say what will
 *   move rather than asking for a leap of faith.
 */
data class GroupDraft(
  val group: SavedRollGroup,
  val fresh: Boolean,
  val clash: String?,
  val nestable: Boolean,
  val parents: List<SavedRollGroup>,
  val tables: List<TableChoice>,
  val rolls: Int,
) {
  val id: String get() = group.id
  val name: String get() = group.name
  val icon: String get() = group.icon
  val parentId: String? get() = group.parentId
  val tablePin: TablePin? get() = group.tablePin

  /** A group needs a name of its own, and no other group may have it. */
  val savable: Boolean get() = group.name.isBlank().not() && clash == null

  /** Unfiled is where rolls go when their group is deleted; it cannot go itself. */
  val deletable: Boolean get() = !fresh && group.id != SavedRollGroup.UNFILED_ID
}
