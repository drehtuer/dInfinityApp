package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.RuleWeight
import de.drehtuer.dinfinity.ui.common.Sheet

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
  Sheet(
    title = stringResource(R.string.collections_title),
    onDismiss = onDismiss,
    modifier = modifier.testTag(ExportTestTags.SHEET),
    actions = {
      // The only action is the way out, and a way out is a `.btn-ghost`.
      ModernistButton(
        text = stringResource(R.string.group_cancel),
        onClick = onDismiss,
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(ExportTestTags.CANCEL),
      )
    },
  ) {
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
    Rule(weight = RuleWeight.Hairline)
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
}

/**
 * One way in or out: a name, and under it how much is in it.
 *
 * A row rather than a button. `ModernistButton` takes a `String` because every
 * button in this app says a word, and this says two things in two sizes — so
 * the tap goes on the row and `Role.Button` tells a screen reader what the row
 * is. The two lines merge into one node for the same reason a saved roll's do:
 * "Everything, 24 rolls" is one thing to hear.
 */
@Composable
private fun Choice(
  label: String,
  note: String,
  tag: String,
  onClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {}
        .testTag(tag)
        // What `TextButton` was padding it by, so the row stays a target.
        .padding(vertical = Modernist.x2),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      // Explicitly the text colour: `TextButton` was printing this in the
      // accent, where the prototype's sheet rows are ink and only the count
      // under them is muted.
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = note,
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
    )
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
