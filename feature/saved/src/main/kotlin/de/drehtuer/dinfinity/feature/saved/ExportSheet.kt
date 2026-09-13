package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * What to export (`docs/dice-notation.md`, "Export and import").
 *
 * Two choices, because the specification names two: a group with its
 * subgroups, or everything. Both say how much is in them before they are
 * pressed — an export is a thing somebody sends to another person, and
 * "everything" is a word that deserves a number beside it.
 *
 * There is no third choice for a hand-picked selection. A collection is a
 * group or it is the lot; anything finer is a file somebody has to describe
 * when they send it.
 */
@Composable
internal fun ExportSheet(
  state: SavedState,
  onExport: (String?) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val group = state.activeGroup
  AlertDialog(
    modifier = modifier.testTag(ExportTestTags.SHEET),
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.export_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = stringResource(R.string.export_note),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group != null) {
          Choice(
            label = stringResource(R.string.export_group, group.group.name),
            note = pluralStringResource(R.plurals.saved_group_rolls, group.rolls, group.rolls),
            tag = ExportTestTags.GROUP,
            onClick = { onExport(group.group.id) },
          )
        }
        Choice(
          label = stringResource(R.string.export_everything),
          note =
            pluralStringResource(
              R.plurals.saved_group_rolls,
              state.allRolls.size,
              state.allRolls.size,
            ),
          tag = ExportTestTags.EVERYTHING,
          onClick = { onExport(null) },
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag(ExportTestTags.CANCEL)) {
        Text(stringResource(R.string.group_cancel))
      }
    },
  )
}

@Composable
private fun Choice(
  label: String,
  note: String,
  tag: String,
  onClick: () -> Unit,
) {
  TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(text = label, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = note,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** What the tests reach the export sheet by. */
object ExportTestTags {
  const val OPEN: String = "export:open"
  const val SHEET: String = "export:sheet"
  const val GROUP: String = "export:group"
  const val EVERYTHING: String = "export:everything"
  const val CANCEL: String = "export:cancel"
}
