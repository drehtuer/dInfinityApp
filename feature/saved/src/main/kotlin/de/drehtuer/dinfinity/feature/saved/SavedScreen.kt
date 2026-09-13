package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  )
  LazyColumn(modifier = Modifier.fillMaxSize().testTag(SavedTestTags.LIST)) {
    items(rolls, key = { it.roll.id }) { entry ->
      HorizontalDivider()
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
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    val exportLabel = stringResource(R.string.export_open)
    TextButton(onClick = onSwitch, modifier = Modifier.testTag(SavedTestTags.SWITCHER)) {
      Text(
        text = if (switching) "$groupName ▴" else "$groupName ▾",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }
    Text(text = "", modifier = Modifier.weight(1f))
    // A mark rather than a word, because the bar has a group name in it that
    // may be long. The label is what TalkBack reads.
    TextButton(
      onClick = onExport,
      modifier =
        Modifier
          .semantics { contentDescription = exportLabel }
          .testTag(ExportTestTags.OPEN),
    ) {
      Text("⤴")
    }
    Button(onClick = onNew, modifier = Modifier.testTag(SavedTestTags.NEW)) {
      Text(stringResource(R.string.saved_new))
    }
    menu()
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
  Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
    groups.forEach { entry ->
      HorizontalDivider()
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          if (parent != null) {
            Text(
              text = parent,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = entry.group.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (entry.group.id == activeId) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }
        Text(
          text = pluralStringResource(R.plurals.saved_group_rolls, entry.rolls, entry.rolls),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A second way in, because a long press is not discoverable and the
        // switcher is the only place a group is ever seen.
        TextButton(
          onClick = { onEdit(entry.group.id) },
          modifier = Modifier.testTag(GroupTestTags.editOf(entry.group.id)),
        ) {
          Text("…")
        }
      }
    }
    HorizontalDivider()
    TextButton(
      onClick = onNew,
      modifier = Modifier.fillMaxWidth().testTag(GroupTestTags.NEW),
    ) {
      Text(stringResource(R.string.group_new))
    }
    HorizontalDivider()
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
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // The icon prints in the roll's own colour tag, which is the one place a
    // saved roll gets to look like itself (design option 9d).
    Text(
      text = roll.icon.ifBlank { DEFAULT_ICON },
      style = MaterialTheme.typography.titleMedium,
      color = roll.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = if (roll.favourite) "${roll.name} ★" else roll.name,
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
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(SavedTestTags.brokenOf(roll.id)),
        )
      }
    }
    Text(
      text = roll.formula,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** Nothing saved yet (`design/dInfinity.dc.html`, option 9b). */
@Composable
private fun Empty(onNew: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(24.dp).testTag(SavedTestTags.EMPTY),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = stringResource(R.string.saved_empty_title),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.saved_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onNew, modifier = Modifier.padding(top = 8.dp)) {
      Text(stringResource(R.string.saved_empty_new))
    }
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
