package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.collection.CollectionProblem

/**
 * Taking a collection in (`design/dInfinity.dc.html`, options 9f and 9g).
 *
 * A screen of its own rather than a dialog, because it has something to say in
 * three of its five states and two of those are lists. A refusal here is not a
 * toast: somebody has a file they expected to work, and the reason it did not
 * is the only useful thing on screen.
 */
@Composable
fun ImportScreen(
  presenter: ImportPresenter,
  modifier: Modifier = Modifier,
  onChooseFile: () -> Unit = {},
  onDone: () -> Unit = {},
  menu: @Composable () -> Unit = {},
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .testTag(ImportTestTags.SCREEN),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = stringResource(R.string.import_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
      )
      menu()
    }

    when (val state = presenter.state) {
      is ImportState.Waiting -> Waiting(onChooseFile)
      is ImportState.Reading -> Reading()
      is ImportState.Unopenable -> Unopenable(state, presenter, onChooseFile)
      is ImportState.Unreadable -> Unreadable(state, presenter, onChooseFile)
      is ImportState.Clash -> Clash(state, presenter, onChooseFile)
      is ImportState.Imported -> Imported(state, onDone)
    }
  }
}

/** Nothing chosen yet (design option 9f). */
@Composable
private fun Waiting(onChooseFile: () -> Unit) {
  Note(stringResource(R.string.import_explain))
  Note(stringResource(R.string.import_never_merges))
  Button(onClick = onChooseFile, modifier = Modifier.testTag(ImportTestTags.CHOOSE)) {
    Text(stringResource(R.string.import_choose))
  }
}

@Composable
private fun Reading() {
  CircularProgressIndicator(modifier = Modifier.testTag(ImportTestTags.READING))
}

@Composable
private fun Unopenable(
  state: ImportState.Unopenable,
  presenter: ImportPresenter,
  onChooseFile: () -> Unit,
) {
  Refusal(stringResource(R.string.import_unopenable), modifier = Modifier.testTag(ImportTestTags.UNOPENABLE))
  Note(state.why)
  Again(presenter, onChooseFile)
}

/**
 * The file is not a collection, and this is everything wrong with it.
 *
 * Every line, not the first: the collection is refused either way, so there is
 * nothing to gain by stopping, and somebody fixing a file by hand wants the
 * whole list (`docs/dice-notation.md`).
 */
@Composable
private fun Unreadable(
  state: ImportState.Unreadable,
  presenter: ImportPresenter,
  onChooseFile: () -> Unit,
) {
  Refusal(
    pluralStringResource(R.plurals.import_unreadable, state.problems.size, state.problems.size),
    modifier = Modifier.testTag(ImportTestTags.UNREADABLE),
  )
  Problems(state.problems, ImportTestTags.PROBLEMS)
  Again(presenter, onChooseFile)
}

/**
 * A group in the file is already here (design option 6e).
 *
 * The one refusal that is not about the file being wrong. Nothing is merged,
 * nothing is deleted, and the name is said out loud because it is the thing
 * somebody has to go and change — in the file, or in the app.
 */
@Composable
private fun Clash(
  state: ImportState.Clash,
  presenter: ImportPresenter,
  onChooseFile: () -> Unit,
) {
  Refusal(
    stringResource(R.string.import_clash, state.name),
    modifier = Modifier.testTag(ImportTestTags.CLASH),
  )
  Note(stringResource(R.string.import_clash_what_to_do))
  Again(presenter, onChooseFile)
}

/** It is in (design option 9g). */
@Composable
private fun Imported(
  state: ImportState.Imported,
  onDone: () -> Unit,
) {
  Text(
    text = stringResource(R.string.import_done, state.name),
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.testTag(ImportTestTags.DONE),
  )
  Text(
    text =
      stringResource(
        R.string.import_counts,
        pluralStringResource(R.plurals.import_groups, state.groups, state.groups),
        pluralStringResource(R.plurals.saved_group_rolls, state.rolls, state.rolls),
      ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.testTag(ImportTestTags.COUNTS),
  )
  // A roll whose dice are not installed came in anyway, and says so here as
  // well as on the list: the set may be installed tomorrow.
  if (state.warnings.isNotEmpty()) {
    Note(pluralStringResource(R.plurals.import_warnings, state.warnings.size, state.warnings.size))
    Problems(state.warnings, ImportTestTags.WARNINGS)
  }
  Button(onClick = onDone, modifier = Modifier.testTag(ImportTestTags.SEE)) {
    Text(stringResource(R.string.import_see))
  }
}

@Composable
private fun Problems(
  problems: List<CollectionProblem>,
  tag: String,
) {
  Column(
    modifier = Modifier.fillMaxWidth().testTag(tag),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    problems.forEach { problem ->
      Text(
        text = problem.toString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun Refusal(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.error,
    modifier = modifier,
  )
}

@Composable
private fun Note(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun Again(
  presenter: ImportPresenter,
  onChooseFile: () -> Unit,
) {
  TextButton(
    onClick = {
      presenter.again()
      onChooseFile()
    },
    modifier = Modifier.testTag(ImportTestTags.AGAIN),
  ) {
    Text(stringResource(R.string.import_again))
  }
}

/** What the tests reach the import screen by. */
object ImportTestTags {
  const val SCREEN: String = "import:screen"
  const val CHOOSE: String = "import:choose"
  const val READING: String = "import:reading"
  const val UNOPENABLE: String = "import:unopenable"
  const val UNREADABLE: String = "import:unreadable"
  const val PROBLEMS: String = "import:problems"
  const val CLASH: String = "import:clash"
  const val DONE: String = "import:done"
  const val COUNTS: String = "import:counts"
  const val WARNINGS: String = "import:warnings"
  const val SEE: String = "import:see"
  const val AGAIN: String = "import:again"
}
