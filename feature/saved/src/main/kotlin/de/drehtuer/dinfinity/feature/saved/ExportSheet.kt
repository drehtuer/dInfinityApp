package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist

/**
 * Collections, in and out (`docs/dice-notation.md`, "Export and import").
 *
 * Two ways out, because the specification names two: a group with its
 * subgroups, or everything. Both say how much is in them before they are
 * pressed — an export is a thing somebody sends to another person, and
 * "everything" is a word that deserves a number beside it.
 *
 * There is no third choice for a hand-picked selection. A collection is a
 * group or it is the lot; anything finer is a file somebody has to describe
 * when they send it.
 *
 * One way in, which leads to a screen rather than doing anything here: an
 * import has five states and two of them are lists.
 */
@Composable
internal fun ExportSheet(
  state: SavedState,
  onExport: (String?) -> Unit,
  onImport: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val group = state.activeGroup
  AlertDialog(
    modifier = modifier.testTag(ExportTestTags.SHEET),
    onDismissRequest = onDismiss,
    // `.dialog`: the surface, no corner, `.dialog-title` at the scale's `h4`.
    shape = Modernist.square,
    title = {
      Text(
        text = stringResource(R.string.collections_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Modernist.x3)) {
        Text(
          text = stringResource(R.string.export_note),
          style = MaterialTheme.typography.bodyLarge,
          color = Ink.muted,
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
        HorizontalDivider(thickness = Modernist.hairline, color = Ink.divider)
        // In as well as out. The same sheet, because a file arriving and a
        // file leaving are one idea to a player and the alternative is a
        // second control on a bar that already has a group name in it.
        Choice(
          label = stringResource(R.string.import_open),
          note = stringResource(R.string.import_note),
          tag = ExportTestTags.IMPORT,
          onClick = onImport,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        shape = Modernist.square,
        modifier = Modifier.testTag(ExportTestTags.CANCEL),
      ) {
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
  TextButton(
    onClick = onClick,
    shape = Modernist.square,
    modifier = Modifier.fillMaxWidth().testTag(tag),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(text = label, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = note,
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
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
  const val IMPORT: String = "export:import"
  const val CANCEL: String = "export:cancel"
}
