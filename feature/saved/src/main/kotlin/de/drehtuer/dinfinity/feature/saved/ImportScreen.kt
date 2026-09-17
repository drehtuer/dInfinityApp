package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.drehtuer.dinfinity.core.collection.CollectionProblem
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Rule

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
        .padding(Modernist.x4)
        .testTag(ImportTestTags.SCREEN),
    verticalArrangement = Arrangement.spacedBy(Modernist.x3),
  ) {
    // The screen's own title bar, over the 2 dp rule every screen in the
    // prototype hangs from. `titleLarge` is the heading face at 800.
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.import_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
      )
      menu()
    }
    Rule()

    when (val state = presenter.state) {
      is ImportState.Waiting -> {
        Waiting(onChooseFile)
        // Beside `Waiting` rather than inside it, so that composable keeps the
        // one parameter it had: a `@Composable` costs skip branches per
        // parameter whether or not anything ever calls it twice.
        FromLink(presenter)
      }

      is ImportState.Reading -> Reading()
      is ImportState.Fetching -> {
        CircularProgressIndicator(modifier = Modifier.testTag(ImportTestTags.FETCHING))
        Note(stringResource(R.string.import_fetching, state.url))
      }
      is ImportState.Unreachable -> {
        // Said apart from an unreadable file on purpose: this is not a bad
        // collection, it is no collection, and the thing to do about it is
        // different. Inline for the reason `FromLink` is called beside
        // `Waiting` — a composable costs skip branches per parameter.
        Refusal(stringResource(R.string.import_unreachable), Modifier.testTag(ImportTestTags.UNREACHABLE))
        Note(state.url)
        Note(state.why)
        Again(presenter, onChooseFile)
      }
      is ImportState.Unopenable -> Unopenable(state, presenter, onChooseFile)
      is ImportState.Unreadable -> Unreadable(state, presenter, onChooseFile)
      is ImportState.Clash -> Clash(state, presenter, onChooseFile)
      is ImportState.Imported -> Imported(state, onDone)
    }
  }
}

/**
 * Nothing chosen yet (design option 9f).
 *
 * The file comes first of the two ways in: it is the one that always works,
 * where a link depends on somebody else's server being up.
 */
@Composable
private fun Waiting(onChooseFile: () -> Unit) {
  Note(stringResource(R.string.import_explain))
  Note(stringResource(R.string.import_never_merges))
  ModernistButton(
    text = stringResource(R.string.import_choose),
    onClick = onChooseFile,
    kind = ModernistButtonKind.Primary,
    modifier = Modifier.testTag(ImportTestTags.CHOOSE),
  )
}

/**
 * The link field.
 *
 * The button is dead until there is something to fetch, because a download of
 * nothing is a spinner that stops for no reason.
 */
@Composable
private fun FromLink(presenter: ImportPresenter) {
  var url by rememberSaveable { mutableStateOf("") }
  Note(stringResource(R.string.import_from_link))
  OutlinedTextField(
    value = url,
    onValueChange = { typed -> url = typed },
    singleLine = true,
    label = { Text(stringResource(R.string.import_link_label)) },
    modifier = Modifier.fillMaxWidth().testTag(ImportTestTags.LINK),
  )
  ModernistButton(
    text = stringResource(R.string.import_fetch),
    onClick = { presenter.fetch(url) },
    kind = ModernistButtonKind.Primary,
    enabled = url.isNotBlank(),
    modifier = Modifier.testTag(ImportTestTags.FETCH),
  )
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
    // The prototype sets the line that says what happened a step under the
    // screen's own title (`.card-title`'s 17 px, the scale's step below 20).
    style = MaterialTheme.typography.titleMedium,
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
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.testTag(ImportTestTags.COUNTS),
  )
  // A roll whose dice are not installed came in anyway, and says so here as
  // well as on the list: the set may be installed tomorrow.
  if (state.warnings.isNotEmpty()) {
    Note(pluralStringResource(R.plurals.import_warnings, state.warnings.size, state.warnings.size))
    Problems(state.warnings, ImportTestTags.WARNINGS)
  }
  ModernistButton(
    text = stringResource(R.string.import_see),
    onClick = onDone,
    kind = ModernistButtonKind.Primary,
    modifier = Modifier.testTag(ImportTestTags.SEE),
  )
}

@Composable
private fun Problems(
  problems: List<CollectionProblem>,
  tag: String,
) {
  Column(
    modifier = Modifier.fillMaxWidth().testTag(tag),
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    problems.forEach { problem ->
      Text(
        text = problem.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
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
    // The system's one red is the accent; Material's `error` is a second one
    // the Modernist palette does not contain (`Modernist`).
    color = Ink.accent,
    modifier = modifier,
  )
}

@Composable
private fun Note(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = Ink.muted,
  )
}

@Composable
private fun Again(
  presenter: ImportPresenter,
  onChooseFile: () -> Unit,
) {
  ModernistButton(
    text = stringResource(R.string.import_again),
    onClick = {
      presenter.again()
      onChooseFile()
    },
    kind = ModernistButtonKind.Ghost,
    modifier = Modifier.testTag(ImportTestTags.AGAIN),
  )
}

/** What the tests reach the import screen by. */
object ImportTestTags {
  const val SCREEN: String = "import:screen"
  const val CHOOSE: String = "import:choose"
  const val READING: String = "import:reading"
  const val LINK: String = "import:link"
  const val FETCH: String = "import:fetch"
  const val FETCHING: String = "import:fetching"
  const val UNREACHABLE: String = "import:unreachable"
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
