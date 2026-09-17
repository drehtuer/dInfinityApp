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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.SectionKicker
import de.drehtuer.dinfinity.ui.common.Sheet
import de.drehtuer.dinfinity.ui.common.Tag
import de.drehtuer.dinfinity.ui.common.TagKind
import kotlin.math.roundToInt

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
    // Two kickers and one rule are the whole of the grouping on this screen:
    // the system has no cards, no shadows and no rounded containers, so an
    // accent label over a 2 dp line is the only thing that says where one
    // block ends and the next begins (`design/dInfinityPhone.dc.html`, the
    // Dice sets screen).
    Kicker(stringResource(R.string.sets_kicker_install))
    Installing(state, onInstall)
    FromLink(state, presenter)
    Downloading(state, onCancel = presenter::cancel)
    Rule(modifier = Modifier.padding(top = Modernist.x2))
    Updates(state, presenter)
    Kicker(stringResource(R.string.sets_kicker_installed))
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
    shape = Modernist.square,
    modifier = Modifier.padding(horizontal = 8.dp).testTag(SetsTestTags.INSTALL),
  ) {
    Text(stringResource(if (state.installing) R.string.sets_installing else R.string.sets_install))
  }
}

/**
 * How far the download has got, and a way to stop it
 * (`design/dInfinity.dc.html`, option `9i`).
 *
 * Only while something is actually coming down the wire. An install from a
 * file on the phone has nothing to show, and neither has the validation that
 * follows a download — a bar that reached full and sat there would say the app
 * had hung at the exact moment it was working hardest.
 *
 * Indeterminate when the server did not say how big the archive is. A
 * `Content-Length` is a claim rather than a fact, and a bar drawn from a
 * missing one would be a bar that jumps.
 */
@Composable
private fun Downloading(
  state: SetsState,
  onCancel: () -> Unit,
) {
  val far = state.progress ?: return
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag(SetsTestTags.PROGRESS),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val fraction = far.fraction
    // A bar is a picture of a number. Which number it is is the one thing a
    // screen reader cannot see, and "downloading" with no idea how far is the
    // state people give up in (`docs/architecture.md`, "Accessibility").
    val farAlong =
      if (fraction == null) {
        stringResource(R.string.sets_downloading)
      } else {
        stringResource(R.string.sets_downloading_far, (fraction * PER_CENT).roundToInt())
      }
    if (fraction == null) {
      LinearProgressIndicator(modifier = Modifier.weight(1f).semantics { contentDescription = farAlong })
    } else {
      LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.weight(1f).semantics { contentDescription = farAlong },
      )
    }
    TextButton(onClick = onCancel, shape = Modernist.square, modifier = Modifier.testTag(SetsTestTags.STOP)) {
      Text(stringResource(R.string.sets_cancel))
    }
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
    // The one filled button of the section: the prototype sets the action
    // beside the URL field as `btn btn-primary` and leaves "pick a file" and
    // "try this one" as ghosts beneath it.
    Button(
      onClick = { presenter.installFrom(url) },
      enabled = !state.installing && url.isNotBlank(),
      shape = Modernist.square,
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
  Sheet(
    title = outcomeTitle(outcome),
    onDismiss = { presenter.dismiss() },
    modifier = Modifier.testTag(SetsTestTags.OUTCOME),
    actions = {
      // The only way out of the sheet is a dismissal, not a confirmation, so
      // it is the ghost: there is nothing here to fill a button for.
      ModernistButton(
        text = stringResource(R.string.sets_install_close),
        onClick = { presenter.dismiss() },
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(SetsTestTags.OUTCOME_CLOSE),
      )
    },
  ) {
    // Tighter than the sheet's own spacing: the reason and the lines the
    // validator wrote are one block of text, not three parts of the sheet.
    Column(verticalArrangement = Arrangement.spacedBy(Modernist.x1)) {
      if (outcome is PackageInstaller.Result.Failed) {
        Text(
          text = outcome.reason,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.testTag(SetsTestTags.OUTCOME_REASON),
        )
      }
      Messages(outcome)
    }
  }
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
    // Muted, not the accent: the prototype writes the warning count over an
    // install that *worked* at `opacity:.6` and keeps the accent for the
    // refusal below, which is the one that needs doing something about.
    Text(
      text = stringResource(R.string.sets_install_warnings),
      style = MaterialTheme.typography.bodySmall,
      color = Ink.muted,
    )
  } else {
    SectionKicker(text = pluralStringResource(R.plurals.sets_install_errors, messages.size, messages.size))
  }
  messages.forEach { message ->
    Text(
      text = message.toString(),
      style = MaterialTheme.typography.bodySmall,
      color = if (outcome is PackageInstaller.Result.Failed) Ink.accent else Ink.muted,
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
      // `titleLarge` is already the heading font at 800; a `Bold` here pulled
      // it back to Material's 700 (`--font-heading-weight: 800`).
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.weight(1f),
    )
    menu()
  }
  Text(
    text = stringResource(R.string.sets_order),
    style = MaterialTheme.typography.bodySmall,
    color = Ink.muted,
    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
  )
}

/**
 * A [SectionKicker] at the screen's own gutter.
 *
 * Only the padding is local: what a kicker *is* — ten sp, tracked out,
 * semibold, in the accent — belongs to the design system, and a second
 * spelling of it here is how the app drifted from the prototype in the first
 * place (`docs/design-handover.md`).
 */
@Composable
private fun Kicker(text: String) {
  SectionKicker(
    text = text,
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = Modernist.x4)
        .padding(top = Modernist.x3, bottom = Modernist.x1),
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
      fontWeight = FontWeight.ExtraBold,
    )
    Text(
      text = stringResource(R.string.sets_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = Ink.muted,
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
      shape = Modernist.square,
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
        color = Ink.muted,
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
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = row.name,
            // `.card-title`: the heading font at 800, which is what every name
            // in this system is set in.
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
          )
          row.version?.let { version ->
            Text(
              text = version,
              style = MaterialTheme.typography.labelMedium,
              color = Ink.muted,
            )
          }
        }
        Text(
          text = status(row),
          style = MaterialTheme.typography.bodySmall,
          // The accent is for the one state that needs doing something about.
          // A set that is switched off is badged at the end of the row and is
          // not a problem, and colouring its line like one would make every
          // deliberate choice look like a fault.
          color = if (row.broken) Ink.accent else Ink.muted,
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
      Badges(row)
    }
  }
}

/**
 * What state a row is in, at the end of it.
 *
 * Where the prototype puts them (`design/dInfinityPhone.dc.html`, lines
 * 444–446): after the name and the meta line, neutral for a state that is
 * simply true and outlined for one somebody chose. Neither is pressable — the
 * row is the only thing on this line that does anything, and a badge that took
 * a tap would need a touch target twice its height.
 *
 * **The switched-off badge does not say the prototype's word.** The prototype
 * writes `disabled`, and it is a drawing: it does not know what a screen reader
 * says. On Android "disabled" is the word TalkBack uses for a control that
 * cannot be operated, and these rows are very much operable — a tap opens the
 * set and a long press acts on it — so "Brass, 1.0.0, 5 dice, disabled" would
 * tell a listener the row is dead. The app already says "switched off" in its
 * own voice everywhere else, and it is unambiguous where the prototype's word
 * is not. The divergence is deliberate: do not "correct" it back.
 *
 * There is no "update available" badge yet. `.tag-accent` is a pale accent
 * fill, and the two ramp steps it is made of exist only for the two accents the
 * design system ships where the app offers six; until that is answered the
 * newer version says so in words under the name (`docs/design-handover.md`).
 */
@Composable
private fun Badges(row: SetRow) {
  if (row.isDefault) {
    Tag(
      text = stringResource(R.string.sets_tag_default),
      kind = TagKind.Neutral,
      modifier = Modifier.testTag(SetsTestTags.defaultOf(row.id)),
    )
  }
  if (!row.enabled) {
    Tag(
      text = stringResource(R.string.sets_tag_disabled),
      kind = TagKind.Outline,
      modifier = Modifier.testTag(SetsTestTags.disabledOf(row.id)),
    )
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
    // The same words a set that is on gets. Once the row carries a tag saying
    // it is switched off, a line saying it again is the same fact twice — and
    // the prototype's meta line is always what a set *is* rather than what has
    // been done to it.
    SetStatus.Off -> pluralStringResource(R.plurals.sets_dice, row.dice, row.dice)
    SetStatus.Bundled -> stringResource(R.string.sets_bundled)
    SetStatus.Ready -> pluralStringResource(R.plurals.sets_dice, row.dice, row.dice)
  }

/** Disable or remove, on a long press (`5a`). */
@Composable
private fun ActionSheet(
  row: SetRow,
  presenter: SetsPresenter,
) {
  Sheet(
    title = row.name,
    onDismiss = { presenter.act(null) },
    modifier = Modifier.testTag(SetsTestTags.SHEET),
    // The prototype's order, left to right, and its kinds with it
    // (`design/dInfinityPhone.dc.html`, the set sheet): the toggle filled, the
    // other real choices bordered, and the ghost kept for the way out. The
    // sheet wraps them, which is what `flex-wrap: wrap` on the prototype's
    // `.dialog-actions` does.
    actions = {
      ModernistButton(
        text = stringResource(if (row.enabled) R.string.sets_sheet_disable else R.string.sets_sheet_enable),
        onClick = { presenter.setEnabled(row, enabled = !row.enabled) },
        kind = ModernistButtonKind.Primary,
        modifier = Modifier.testTag(SetsTestTags.TOGGLE),
      )
      if (row.checkable) {
        ModernistButton(
          text = stringResource(R.string.sets_sheet_update),
          // An update is a re-install from where the set came from, and
          // saying so at the one call site beats a wrapper that has to be
          // kept in step with it (`SetsPresenter.installFrom`).
          onClick = { presenter.installFrom(row.meta.source.orEmpty()) },
          kind = ModernistButtonKind.Secondary,
          modifier = Modifier.testTag(SetsTestTags.UPDATE),
        )
      }
      // A second real choice rather than the way out, so it is bordered and
      // not a ghost — `btn-secondary`, as the prototype draws it.
      ModernistButton(
        text = stringResource(R.string.sets_sheet_remove),
        onClick = { presenter.remove(row) },
        kind = ModernistButtonKind.Secondary,
        modifier = Modifier.testTag(SetsTestTags.REMOVE),
      )
      ModernistButton(
        text = stringResource(R.string.sets_sheet_cancel),
        onClick = { presenter.act(null) },
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(SetsTestTags.CANCEL),
      )
    },
  ) {
    Text(
      text = stringResource(if (row.enabled) R.string.sets_sheet_disable_note else R.string.sets_sheet_remove_note),
      style = MaterialTheme.typography.bodySmall,
      color = Ink.muted,
    )
  }
}

/** A fraction is spoken as a percentage; nobody says "nought point four one". */
private const val PER_CENT = 100.0

/** What the tests reach for. */
object SetsTestTags {
  const val SCREEN: String = "sets:screen"
  const val LIST: String = "sets:list"
  const val CHECK: String = "sets:check"
  const val CHECKED: String = "sets:checked"
  const val UPDATE: String = "sets:update"

  fun outdatedOf(setId: String): String = "sets:outdated:$setId"

  /** The badges at the end of a row (`design/dInfinityPhone.dc.html`, 444–446). */
  fun defaultOf(setId: String): String = "sets:default:$setId"

  fun disabledOf(setId: String): String = "sets:disabled:$setId"

  const val EMPTY: String = "sets:empty"
  const val SHEET: String = "sets:sheet"
  const val TOGGLE: String = "sets:toggle"
  const val REMOVE: String = "sets:remove"
  const val CANCEL: String = "sets:cancel"
  const val INSTALL: String = "sets:install"
  const val LINK: String = "sets:link"
  const val FETCH: String = "sets:fetch"

  /** The download bar, and the button that stops it (option `9i`). */
  const val PROGRESS: String = "sets:progress"
  const val STOP: String = "sets:stop"
  const val OUTCOME: String = "sets:outcome"
  const val OUTCOME_REASON: String = "sets:outcome:reason"
  const val OUTCOME_LINE: String = "sets:outcome:line"
  const val OUTCOME_CLOSE: String = "sets:outcome:close"

  fun setOf(id: String): String = "sets:set:$id"
}
