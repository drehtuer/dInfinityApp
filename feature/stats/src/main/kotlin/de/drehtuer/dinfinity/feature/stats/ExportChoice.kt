package de.drehtuer.dinfinity.feature.stats

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Sheet

/**
 * Which shape a file takes (`docs/statistics.md`, "Export and reset").
 *
 * Two formats because they answer different questions, and offering one would
 * be choosing for the player which question they are asking. What each format
 * *carries* is the exporting screen's business — the history puts the
 * breakdown in JSON, the statistics put the streaks there — so this file knows
 * only that there are two of them and what they are called.
 *
 * Shared by both screens that export rather than written twice: two dialogs
 * offering the same choice would eventually offer it in two different orders,
 * with two different words for the same format.
 *
 * @param tagPrefix what the test tags are built from, so each screen's dialog
 *   can be found on its own terms.
 * @param body a line above the choice saying what is about to be written.
 */
@Composable
internal fun ExportChoice(
  tagPrefix: String,
  body: String,
  onDismiss: () -> Unit,
  onChosen: (ExportFormat) -> Unit,
) {
  Sheet(
    title = stringResource(R.string.export_title),
    onDismiss = onDismiss,
    modifier = Modifier.testTag("$tagPrefix:dialog"),
    actions = {
      ModernistButton(
        text = stringResource(R.string.export_json),
        onClick = { onChosen(ExportFormat.Json) },
        kind = ModernistButtonKind.Primary,
        modifier = Modifier.testTag("$tagPrefix:json"),
      )
      // The second format is a choice too, not a way out, so it is the
      // bordered `.btn-secondary` the prototype gives a sheet's second real
      // action — not the ghost, which is what a Cancel is.
      ModernistButton(
        text = stringResource(R.string.export_csv),
        onClick = { onChosen(ExportFormat.Csv) },
        kind = ModernistButtonKind.Secondary,
        modifier = Modifier.testTag("$tagPrefix:csv"),
      )
    },
  ) {
    Text(body)
  }
}
