package de.drehtuer.dinfinity.feature.stats

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.drehtuer.dinfinity.ui.common.Modernist

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
  AlertDialog(
    modifier = Modifier.testTag("$tagPrefix:dialog"),
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.export_title)) },
    text = { Text(body) },
    confirmButton = {
      TextButton(
        onClick = { onChosen(ExportFormat.Json) },
        shape = Modernist.square,
        modifier = Modifier.testTag("$tagPrefix:json"),
      ) {
        Text(stringResource(R.string.export_json))
      }
    },
    dismissButton = {
      TextButton(
        onClick = { onChosen(ExportFormat.Csv) },
        shape = Modernist.square,
        modifier = Modifier.testTag("$tagPrefix:csv"),
      ) {
        Text(stringResource(R.string.export_csv))
      }
    },
  )
}
