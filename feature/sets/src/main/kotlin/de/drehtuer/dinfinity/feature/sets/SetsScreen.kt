package de.drehtuer.dinfinity.feature.sets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller

/**
 * What is installed (`design/dInfinity.dc.html`, option `5a`;
 * `docs/dice-sets.md`).
 *
 * A list, and a sheet on a long press. Everything that can be done to a set
 * from here is reversible or replaceable: switching one off keeps the folder
 * and the statistics, removing one keeps the statistics, and the bundled set
 * is offered neither because there is nothing it could mean.
 *
 * A row says which of the two ways a set can be unusable it is in, because the
 * remedies are opposite: **switched off** is a tap, and **will not load** is an
 * update. A list that showed only "unavailable" would send the player to the
 * wrong one.
 *
 * @param onOpen a row was tapped. Where that goes is the navigation graph's,
 *   which is why this takes a function instead of a controller.
 */
@Composable
fun SetsScreen(
  presenter: SetsPresenter,
  modifier: Modifier = Modifier,
  onOpen: (SetRow) -> Unit = {},
  onInstall: () -> Unit = {},
  menu: @Composable () -> Unit = {},
) {
  val state = presenter.state
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .testTag(SetsTestTags.SCREEN),
  ) {
    Header(menu)
    Installing(state, onInstall)
    FromLink(state, presenter)
    Updates(state, presenter)
    // The note goes *above* the list rather than instead of it. The bundled
    // set is a row like any other and is always there, so replacing the list
    // would hide the one set every fallback resolves against (`5a`).
    if (state.empty) EmptyNote()
    Sets(state, presenter, onOpen)
  }

  state.acting?.let { row -> ActionSheet(row, presenter) }
  state.outcome?.let { outcome -> OutcomeSheet(outcome, presenter) }
}

/**
 * The way in (design `1t`).
 *
 * Choosing the file is the application's business — a content URI is reached
 * through a context — so this only asks. While an install is running the
 * button says so and does nothing: an archive being extracted twice at once is
 * two installs racing for one folder.
 */
@Composable
private fun Installing(
  state: SetsState,
  onInstall: () -> Unit,
) {
  TextButton(
    onClick = onInstall,
    enabled = !state.installing,
    modifier = Modifier.padding(horizontal = 8.dp).testTag(SetsTestTags.INSTALL),
  ) {
    Text(stringResource(if (state.installing) R.string.sets_installing else R.string.sets_install))
  }
}

/**
 * The other way in: a link to an archive (`docs/dice-sets.md`, "Installing
 * from a URL or file").
 *
 * The file comes first of the two, because it is the one that always works
 * where a link depends on somebody else's server being up. The button is dead
 * while an install is running and while there is nothing to fetch — an archive
 * extracted twice at once is two installs racing for one folder, and a
 * download of nothing is a spinner that stops for no reason.
 */
@Composable
private fun FromLink(
  state: SetsState,
  presenter: SetsPresenter,
) {
  var url by rememberSaveable { mutableStateOf("") }
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedTextField(
      value = url,
      onValueChange = { typed -> url = typed },
      singleLine = true,
      label = { Text(stringResource(R.string.sets_link_label)) },
      modifier = Modifier.weight(1f).testTag(SetsTestTags.LINK),
    )
    TextButton(
      onClick = { presenter.installFrom(url) },
      enabled = !state.installing && url.isNotBlank(),
      modifier = Modifier.testTag(SetsTestTags.FETCH),
    ) {
      Text(stringResource(R.string.sets_fetch))
    }
  }
}

/**
 * What the install came to (design `1t`).
 *
 * A refusal lists **every** error rather than the first. An author fixing a set
 * wants the whole list, and a rejection that stopped at the first problem would
 * be one round trip per mistake.
 */
@Composable
private fun OutcomeSheet(
  outcome: PackageInstaller.Result,
  presenter: SetsPresenter,
) {
  AlertDialog(
    onDismissRequest = { presenter.dismiss() },
    modifier = Modifier.testTag(SetsTestTags.OUTCOME),
    title = { Text(outcomeTitle(outcome)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (outcome is PackageInstaller.Result.Failed) {
          Text(
            text = outcome.reason,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(SetsTestTags.OUTCOME_REASON),
          )
        }
        Messages(outcome)
      }
    },
    confirmButton = {
      TextButton(onClick = { presenter.dismiss() }, modifier = Modifier.testTag(SetsTestTags.OUTCOME_CLOSE)) {
        Text(stringResource(R.string.sets_install_close))
      }
    },
  )
}

@Composable
private fun outcomeTitle(outcome: PackageInstaller.Result): String =
  when (outcome) {
    is PackageInstaller.Result.Installed ->
      if (outcome.replaced) {
        stringResource(R.string.sets_replaced, outcome.set.name)
      } else {
        stringResource(R.string.sets_installed, outcome.set.name)
      }

    is PackageInstaller.Result.Failed -> stringResource(R.string.sets_install_refused)
  }

/** Every line the validator wrote, as it wrote it. */
@Composable
private fun Messages(outcome: PackageInstaller.Result) {
  val messages =
    when (outcome) {
      is PackageInstaller.Result.Installed -> outcome.warnings
      is PackageInstaller.Result.Failed -> outcome.report
    }
  if (messages.isEmpty()) return
  if (outcome is PackageInstaller.Result.Installed) {
    Text(
      text = stringResource(R.string.sets_install_warnings),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  messages.forEach { message ->
    Text(
      text = message.toString(),
      style = MaterialTheme.typography.bodySmall,
      color =
        if (outcome is PackageInstaller.Result.Failed) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
      modifier = Modifier.testTag(SetsTestTags.OUTCOME_LINE),
    )
  }
}

@Composable
private fun Header(menu: @Composable () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = stringResource(R.string.sets_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.weight(1f),
    )
    menu()
  }
  Text(
    text = stringResource(R.string.sets_order),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
  )
}

@Composable
private fun EmptyNote() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(SetsTestTags.EMPTY),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = stringResource(R.string.sets_empty_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = stringResource(R.string.sets_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun Sets(
  state: SetsState,
  presenter: SetsPresenter,
  onOpen: (SetRow) -> Unit,
) {
  LazyColumn(modifier = Modifier.fillMaxSize().testTag(SetsTestTags.LIST)) {
    items(state.sets, key = SetRow::id) { row ->
      SetLine(
        row = row,
        outdated = row.id in state.outdated,
        onOpen = { onOpen(row) },
        onHold = { presenter.act(row) },
      )
      HorizontalDivider()
    }
  }
}

/**
 * Asking the forges whether they have moved on (design `9h`).
 *
 * Drawn only when something could be asked: a set installed from a file has no
 * forge, and a plain archive has no commits to tell apart, so on an install
 * with neither there is nothing this button could do.
 *
 * What it found is said in a line rather than only on the rows, because a check
 * that found everything current and a check that could not reach anything look
 * identical on the list — nothing is badged either way.
 */
@Composable
private fun Updates(
  state: SetsState,
  presenter: SetsPresenter,
) {
  if (!state.checkable) return
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TextButton(
      onClick = { presenter.checkForUpdates() },
      enabled = !state.checking && !state.installing,
      modifier = Modifier.testTag(SetsTestTags.CHECK),
    ) {
      Text(stringResource(if (state.checking) R.string.sets_checking else R.string.sets_check))
    }
    state.checked?.let { checked ->
      Text(
        text =
          when {
            checked.outdated > 0 ->
              pluralStringResource(R.plurals.sets_check_outdated, checked.outdated, checked.outdated)
            checked.allCurrent ->
              pluralStringResource(R.plurals.sets_check_current, checked.asked, checked.asked)
            else ->
              pluralStringResource(R.plurals.sets_check_unreachable, checked.unreachable, checked.unreachable)
          },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(SetsTestTags.CHECKED),
      )
    }
  }
}

/**
 * One set.
 *
 * Tap opens it, long press asks what to do with it. Both through
 * `detectTapGestures` rather than a `combinedClickable`, because the row is
 * also a merge root for accessibility and the two gestures want to be one
 * node with one label.
 */
@Composable
private fun SetLine(
  row: SetRow,
  outdated: Boolean,
  onOpen: () -> Unit,
  onHold: () -> Unit,
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .pointerInput(row.id) {
          detectTapGestures(onTap = { onOpen() }, onLongPress = { onHold() })
        }.semantics(mergeDescendants = true) { }
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .testTag(SetsTestTags.setOf(row.id)),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = row.name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
      )
      row.version?.let { version ->
        Text(
          text = version,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Text(
      text = status(row),
      style = MaterialTheme.typography.bodySmall,
      color = if (row.usable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
    // Under the status rather than replacing it: whether a set is broken or
    // switched off is what the player can do something about first, and
    // "there is something newer" is true whatever else the row says.
    if (outdated) {
      Text(
        text = stringResource(R.string.sets_outdated),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(SetsTestTags.outdatedOf(row.id)),
      )
    }
  }
}

/**
 * The one line under the name, in words.
 *
 * Which of the four it is is [SetRow.status]'s to decide and is tested without
 * a screen; all that happens here is looking the words up.
 */
@Composable
private fun status(row: SetRow): String =
  when (row.status) {
    SetStatus.Broken -> pluralStringResource(R.plurals.sets_problems, row.problems, row.problems)
    SetStatus.Off -> stringResource(R.string.sets_disabled)
    SetStatus.Bundled -> stringResource(R.string.sets_bundled)
    SetStatus.Ready -> pluralStringResource(R.plurals.sets_dice, row.dice, row.dice)
  }

/** Disable or remove, on a long press (`5a`). */
@Composable
private fun ActionSheet(
  row: SetRow,
  presenter: SetsPresenter,
) {
  AlertDialog(
    onDismissRequest = { presenter.act(null) },
    modifier = Modifier.testTag(SetsTestTags.SHEET),
    title = { Text(row.name) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(if (row.enabled) R.string.sets_sheet_disable_note else R.string.sets_sheet_remove_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { presenter.setEnabled(row, enabled = !row.enabled) },
        modifier = Modifier.testTag(SetsTestTags.TOGGLE),
      ) {
        Text(stringResource(if (row.enabled) R.string.sets_sheet_disable else R.string.sets_sheet_enable))
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (row.checkable) {
          TextButton(
            // An update is a re-install from where the set came from, and
            // saying so at the one call site beats a wrapper that has to be
            // kept in step with it (`SetsPresenter.installFrom`).
            onClick = { presenter.installFrom(row.meta.source.orEmpty()) },
            modifier = Modifier.testTag(SetsTestTags.UPDATE),
          ) {
            Text(stringResource(R.string.sets_sheet_update))
          }
        }
        TextButton(
          onClick = { presenter.remove(row) },
          modifier = Modifier.testTag(SetsTestTags.REMOVE),
        ) {
          Text(
            text = stringResource(R.string.sets_sheet_remove),
            color = MaterialTheme.colorScheme.error,
          )
        }
        TextButton(
          onClick = { presenter.act(null) },
          modifier = Modifier.testTag(SetsTestTags.CANCEL),
        ) {
          Text(stringResource(R.string.sets_sheet_cancel))
        }
      }
    },
  )
}

/** What the tests reach for. */
object SetsTestTags {
  const val SCREEN: String = "sets:screen"
  const val LIST: String = "sets:list"
  const val CHECK: String = "sets:check"
  const val CHECKED: String = "sets:checked"
  const val UPDATE: String = "sets:update"

  fun outdatedOf(setId: String): String = "sets:outdated:$setId"

  const val EMPTY: String = "sets:empty"
  const val SHEET: String = "sets:sheet"
  const val TOGGLE: String = "sets:toggle"
  const val REMOVE: String = "sets:remove"
  const val CANCEL: String = "sets:cancel"
  const val INSTALL: String = "sets:install"
  const val LINK: String = "sets:link"
  const val FETCH: String = "sets:fetch"
  const val OUTCOME: String = "sets:outcome"
  const val OUTCOME_REASON: String = "sets:outcome:reason"
  const val OUTCOME_LINE: String = "sets:outcome:line"
  const val OUTCOME_CLOSE: String = "sets:outcome:close"

  fun setOf(id: String): String = "sets:set:$id"
}
