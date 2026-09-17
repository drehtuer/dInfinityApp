package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.ModernistIconButton
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.RuleWeight
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET

/**
 * Named formulas, rolled with one tap
 * (`design/dInfinity.dc.html`, options 1o and 9b).
 *
 * Rows rather than tiles: a saved roll is a name and a formula, and a dense
 * ruled list puts more of them on a phone than cards do while still leaving
 * room to show the formula — which is the thing a player checks before
 * throwing something they wrote weeks ago.
 *
 * Tap rolls it. A long press edits it. Both are on the row rather than on
 * separate controls, because the row *is* the roll.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedScreen(
  presenter: SavedPresenter,
  groups: GroupPresenter,
  modifier: Modifier = Modifier,
  onRoll: (SavedEntry) -> Unit = {},
  onEdit: (SavedEntry) -> Unit = {},
  onNew: () -> Unit = {},
  onExport: (CollectionFile) -> Unit = {},
  onImport: () -> Unit = {},
  menu: @Composable () -> Unit = {},
) {
  val state = presenter.state
  // Local to the screen rather than in the presenter: whether a sheet is open
  // is not something the database or another screen has an opinion about.
  var exporting by remember { mutableStateOf(false) }
  val everything = stringResource(R.string.export_everything)
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .testTag(SavedTestTags.SCREEN),
  ) {
    TopBar(
      groupName = state.activeGroup?.group?.name ?: stringResource(R.string.saved_loading),
      switching = state.switching,
      onSwitch = { presenter.showGroups(!state.switching) },
      onExport = { exporting = true },
      onNew = onNew,
      menu = menu,
    )

    if (exporting) {
      Exporting(
        state = state,
        everything = everything,
        onDismiss = { exporting = false },
        onExport = onExport,
        onImport = {
          exporting = false
          onImport()
        },
      )
    }

    if (state.switching) {
      GroupSwitcher(
        groups = state.groups,
        activeId = state.activeGroupId,
        onOpen = presenter::open,
        onEdit = groups::edit,
        onNew = { groups.create() },
      )
    }

    // The sheet is drawn over whatever is below it, so making a group while
    // looking at a list does not lose the list.
    groups.draft?.let { draft -> GroupSheet(draft = draft, presenter = groups) }

    if (state.empty) {
      Empty(onNew = onNew)
      return@Column
    }

    Rolls(
      rolls = state.rolls,
      onRoll = { entry ->
        presenter.used(entry.roll.id)
        onRoll(entry)
      },
      onEdit = onEdit,
    )
  }
}

/** The rolls of the group that is open, in the order SQL put them in. */
@Composable
private fun ColumnScope.Rolls(
  rolls: List<SavedEntry>,
  onRoll: (SavedEntry) -> Unit,
  onEdit: (SavedEntry) -> Unit,
) {
  Text(
    text = stringResource(R.string.saved_order),
    style = MaterialTheme.typography.labelSmall,
    color = Ink.muted,
    modifier = Modifier.padding(horizontal = Modernist.x4, vertical = Modernist.x2),
  )
  // The list hangs from a rule and is ruled inside by hairlines: 2 dp says
  // "a new thing starts here", 1 dp says "another row of the same thing"
  // (`design/dInfinityPhone.dc.html`, the saved-rolls list).
  Rule()
  LazyColumn(modifier = Modifier.fillMaxSize().testTag(SavedTestTags.LIST)) {
    itemsIndexed(rolls, key = { _, entry -> entry.roll.id }) { index, entry ->
      if (index > 0) Rule(weight = RuleWeight.Hairline)
      SavedRow(entry = entry, onRoll = { onRoll(entry) }, onEdit = { onEdit(entry) })
    }
  }
}

/**
 * Gathering what was chosen and handing it up.
 *
 * The screen decides what goes in the file; what to *do* with a file is the
 * app's, because the provider that hands one to another app is declared in the
 * application's manifest (`CollectionSharing`).
 */
@Composable
private fun Exporting(
  state: SavedState,
  everything: String,
  onDismiss: () -> Unit,
  onExport: (CollectionFile) -> Unit,
  onImport: () -> Unit,
) {
  ExportSheet(
    state = state,
    onDismiss = onDismiss,
    onImport = onImport,
    onExport = { groupId ->
      onDismiss()
      val called =
        state.groups
          .firstOrNull { it.group.id == groupId }
          ?.group
          ?.name ?: everything
      onExport(
        CollectionExport.of(
          groups = state.groups.map { it.group },
          rolls = state.allRolls.map { it.roll },
          only = groupId,
          called = called,
        ),
      )
    },
  )
}

@Composable
private fun TopBar(
  groupName: String,
  switching: Boolean,
  onSwitch: () -> Unit,
  onExport: () -> Unit,
  onNew: () -> Unit,
  menu: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = Modernist.x2, vertical = Modernist.x1),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    // A heading with a tap on it rather than a button with a heading in it.
    // `titleLarge` is already the heading face at 800, which is the size and
    // weight the prototype puts the group name at, and no button in this
    // system prints at that size — the prototype says so itself, drawing this
    // one as a `.btn-ghost` with `color:inherit;font-size:20px` to undo both.
    // So the type stays and the tap is put on it; `Role.Button` is what tells
    // a screen reader it is pressable.
    Text(
      text = if (switching) "$groupName ▴" else "$groupName ▾",
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier =
        Modifier
          .clickable(role = Role.Button, onClick = onSwitch)
          // `TextButton` was quietly supplying Android's 48 dp target; a bare
          // `Text` is only as tall as its line, so it is given back by hand.
          .heightIn(min = TOUCH_TARGET)
          .wrapContentHeight(Alignment.CenterVertically)
          .testTag(SavedTestTags.SWITCHER),
    )
    Text(text = "", modifier = Modifier.weight(1f))
    // A mark rather than a word, because the bar has a group name in it that
    // may be long. The name is what TalkBack reads, and `ModernistIconButton`
    // is where a button whose label is a picture lives.
    ModernistIconButton(
      contentDescription = stringResource(R.string.export_open),
      onClick = onExport,
      modifier = Modifier.testTag(ExportTestTags.OPEN),
    ) {
      Text("⤴")
    }
    ModernistButton(
      text = stringResource(R.string.saved_new),
      onClick = onNew,
      kind = ModernistButtonKind.Primary,
      modifier = Modifier.testTag(SavedTestTags.NEW),
    )
    menu()
  }
  // Every screen in the prototype hangs from a 2 dp rule under its title bar.
  Rule()
}

/**
 * A second way into the group sheet, because a long press is not discoverable
 * and the switcher is the only place a group is ever seen.
 *
 * An ellipsis is a picture, so the name is what TalkBack reads and the target
 * around it is the platform's 48 dp rather than the glyph's — both of which
 * `ModernistIconButton` is the place for
 * (`docs/architecture.md`, "Accessibility").
 */
@Composable
private fun EditGroup(
  group: SavedRollGroup,
  onEdit: () -> Unit,
) {
  ModernistIconButton(
    contentDescription = stringResource(R.string.group_edit_it, group.name),
    onClick = onEdit,
    modifier = Modifier.testTag(GroupTestTags.editOf(group.id)),
  ) {
    Text("…")
  }
}

/**
 * Which group the list is showing.
 *
 * One level, so a child is drawn as its parent's name above its own rather
 * than as an indent: an indent is a tree control waiting to happen, and
 * `docs/dice-notation.md` says there is no tree.
 */
@Composable
private fun GroupSwitcher(
  groups: List<GroupEntry>,
  activeId: String,
  onOpen: (String) -> Unit,
  onEdit: (String) -> Unit,
  onNew: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
    groups.forEachIndexed { index, entry ->
      if (index > 0) Rule(weight = RuleWeight.Hairline)
      val parent = groups.firstOrNull { it.group.id == entry.group.parentId }?.group?.name
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .combinedClickable(
              onLongClickLabel = stringResource(R.string.group_edit_it, entry.group.name),
              onClick = { onOpen(entry.group.id) },
              onLongClick = { onEdit(entry.group.id) },
            ).semantics(mergeDescendants = true) {}
            .testTag(SavedTestTags.groupOf(entry.group.id))
            .padding(horizontal = Modernist.x4, vertical = Modernist.x3),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          if (parent != null) {
            Text(
              text = parent,
              style = MaterialTheme.typography.labelSmall,
              color = Ink.muted,
            )
          }
          Text(
            text = entry.group.name,
            style = MaterialTheme.typography.bodyLarge,
            // The prototype marks the group that is open with a check in the
            // accent; here the weight does that job, because the row already
            // ends in a count and a way to edit it.
            fontWeight = if (entry.group.id == activeId) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }
        Text(
          text = pluralStringResource(R.plurals.saved_group_rolls, entry.rolls, entry.rolls),
          style = MaterialTheme.typography.labelSmall,
          color = Ink.muted,
        )
        EditGroup(group = entry.group, onEdit = { onEdit(entry.group.id) })
      }
    }
    Rule()
    ModernistButton(
      text = stringResource(R.string.group_new),
      onClick = onNew,
      kind = ModernistButtonKind.Ghost,
      modifier = Modifier.fillMaxWidth().testTag(GroupTestTags.NEW),
    )
    Rule()
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedRow(
  entry: SavedEntry,
  onRoll: () -> Unit,
  onEdit: () -> Unit,
) {
  val roll = entry.roll
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .combinedClickable(
          onClickLabel = stringResource(R.string.saved_roll_it, roll.name),
          onLongClickLabel = stringResource(R.string.saved_edit_it, roll.name),
          onClick = onRoll,
          onLongClick = onEdit,
        ).semantics(mergeDescendants = true) {}
        .testTag(SavedTestTags.rollOf(roll.id))
        .padding(horizontal = Modernist.x4, vertical = Modernist.x3),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Modernist.x3),
  ) {
    // The icon prints in the roll's own colour tag, which is the one place a
    // saved roll gets to look like itself (design option 9d).
    Text(
      text = roll.icon.ifBlank { DEFAULT_ICON },
      style = MaterialTheme.typography.titleLarge,
      color = roll.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        // The star prints in the accent, as it does on the prototype's rows.
        // Part of the same text rather than a second one, so a long name
        // ellipsises around it instead of pushing it off the row.
        text = starred(roll.name, roll.favourite),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      // A roll whose dice are not installed says so rather than failing when
      // it is thrown (`docs/dice-notation.md`).
      if (entry.broken) {
        Text(
          text = stringResource(R.string.saved_broken),
          style = MaterialTheme.typography.labelSmall,
          color = Ink.accent,
          modifier = Modifier.testTag(SavedTestTags.brokenOf(roll.id)),
        )
      }
    }
    Text(
      text = roll.formula,
      // The prototype's `font-size:13px` with no weight of its own: the name
      // is what is read first, and the formula is what is checked after it.
      style = MaterialTheme.typography.bodyMedium,
      color = Ink.muted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** [name], with the accent star a favourite wears after it. */
@Composable
private fun starred(
  name: String,
  favourite: Boolean,
) = buildAnnotatedString {
  append(name)
  if (favourite) {
    withStyle(SpanStyle(color = Ink.accent)) { append(" ★") }
  }
}

/** Nothing saved yet (`design/dInfinity.dc.html`, option 9b). */
@Composable
private fun Empty(onNew: () -> Unit) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = Modernist.x4, vertical = Modernist.x6)
        .testTag(SavedTestTags.EMPTY),
    verticalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    Text(
      // The display line of the screen, at the scale's `h3`. `headlineSmall`
      // is not one of the styles the theme fills in, so it was Material's own
      // 24 sp at 700 — neither the size nor the weight the system has.
      text = stringResource(R.string.saved_empty_title),
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.saved_empty_body),
      style = MaterialTheme.typography.bodyLarge,
      color = Ink.muted,
    )
    ModernistButton(
      text = stringResource(R.string.saved_empty_new),
      onClick = onNew,
      kind = ModernistButtonKind.Primary,
      modifier = Modifier.padding(top = Modernist.x2),
    )
  }
}

/** What a roll with no icon of its own wears. */
private const val DEFAULT_ICON = "●"

/** What the tests reach this screen by. */
object SavedTestTags {
  const val SCREEN: String = "saved:screen"
  const val LIST: String = "saved:list"
  const val SWITCHER: String = "saved:switcher"
  const val NEW: String = "saved:new"
  const val EMPTY: String = "saved:empty"

  fun rollOf(id: String): String = "saved:roll:$id"

  fun brokenOf(id: String): String = "saved:roll:$id:broken"

  fun groupOf(id: String): String = "saved:group:$id"
}
