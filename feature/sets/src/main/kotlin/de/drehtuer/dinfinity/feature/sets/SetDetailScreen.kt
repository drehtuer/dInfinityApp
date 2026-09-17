package de.drehtuer.dinfinity.feature.sets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.notation.Sides
import de.drehtuer.dinfinity.designer.MinePackage
import de.drehtuer.dinfinity.designer.SetLicense
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.ui.common.DieSilhouette
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.SectionKicker

/**
 * One dice set, in detail (`design/dInfinity.dc.html`, options `6a` and `6b`).
 *
 * Who wrote it, under what licence, where it came from, and what is in it —
 * **or**, for a package that no longer validates, what is wrong with it. The
 * report stands exactly where the dice would (`6b`), because it answers the
 * same question: one of the answers is "nothing yet, and here is why".
 *
 * @param onSource the source link was tapped. Opening a URL is the
 *   application's business, not a feature module's, so it leaves by a
 *   function.
 */
@Composable
fun SetDetailScreen(
  presenter: SetDetailPresenter,
  modifier: Modifier = Modifier,
  onSource: (String) -> Unit = {},
  menu: @Composable () -> Unit = {},
) {
  val state = presenter.state
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .testTag(SetDetailTestTags.SCREEN),
  ) {
    val row = state.row
    Header(row?.name.orEmpty(), row?.version, menu)
    when {
      row != null -> Body(row, presenter, onSource)
      state.missing -> Missing()
      else -> Unit
    }
  }
}

@Composable
private fun Header(
  name: String,
  version: String?,
  menu: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = name,
      // `titleLarge` is already the heading font at 800; a `Bold` here pulled
      // it back to Material's 700 (`--font-heading-weight: 800`).
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.weight(1f).testTag(SetDetailTestTags.NAME),
    )
    version?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.labelMedium,
        color = Ink.muted,
      )
    }
    menu()
  }
}

/** A set that is not installed any more — removed here, or from somewhere else. */
@Composable
private fun Missing() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(SetDetailTestTags.MISSING),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = stringResource(R.string.sets_detail_gone),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.ExtraBold,
    )
    Text(
      text = stringResource(R.string.sets_detail_gone_body),
      style = MaterialTheme.typography.bodyMedium,
      color = Ink.muted,
    )
  }
}

@Composable
private fun Body(
  row: SetRow,
  presenter: SetDetailPresenter,
  onSource: (String) -> Unit,
) {
  LazyColumn(modifier = Modifier.fillMaxSize().testTag(SetDetailTestTags.LIST)) {
    item { Provenance(row, onSource) }
    item { Default(presenter) }
    item { Manage(row, presenter) }
    item { Export(presenter) }
    // The 2 dp rule, not Material's hairline. This is a boundary *between
    // blocks* — the prototype draws it under the facts and above the dice —
    // and `HorizontalDivider` only ever draws the 1 dp line that separates
    // rows inside one (`ui/common/Rule.kt`).
    item { Rule() }
    if (row.broken) report(row.report) else dice(row.set?.dice.orEmpty())
  }
}

/** Who wrote it, under what licence, and where it came from. */
@Composable
private fun Provenance(
  row: SetRow,
  onSource: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    row.set?.description?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
    Field(stringResource(R.string.sets_detail_author), row.set?.author)
    Field(stringResource(R.string.sets_detail_license), row.set?.license)
    Source(row, onSource)
  }
}

/**
 * Where the package came from, and which commit of it.
 *
 * A link only when there is somewhere to go: a set installed from a file, or a
 * folder the app never installed, has a source that is a name rather than a
 * URL, and making that tappable would promise something it cannot do.
 */
@Composable
private fun Source(
  row: SetRow,
  onSource: (String) -> Unit,
) {
  val source = row.meta.source
  if (source == null) {
    if (!row.bundled) {
      Field(stringResource(R.string.sets_detail_source), stringResource(R.string.sets_detail_source_unknown))
    }
    return
  }
  val linkable = source.startsWith("https://")
  val openIt = stringResource(R.string.sets_detail_source_open)
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        // The one difference between a source you can open and one you cannot
        // is that it prints in the accent. The click label is the same fact
        // said out loud (`docs/architecture.md`, "Accessibility").
        .let { if (linkable) it.clickable(onClickLabel = openIt) { onSource(source) } else it }
        .semantics(mergeDescendants = true) { }
        .testTag(SetDetailTestTags.SOURCE),
  ) {
    Label(stringResource(R.string.sets_detail_source))
    Text(
      text = source,
      style = MaterialTheme.typography.bodyMedium,
      color = if (linkable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
    row.meta.commit?.let {
      Text(
        text = stringResource(R.string.sets_detail_commit, it),
        style = MaterialTheme.typography.bodySmall,
        color = Ink.muted,
      )
    }
  }
}

/**
 * Which set plain notation reaches for first (design `6a`).
 *
 * A set that is switched off or will not load is not offered the job: naming
 * it would point every plain `d20` at a set that is then fallen straight past.
 */
@Composable
private fun Default(presenter: SetDetailPresenter) {
  val state = presenter.state
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (state.isDefault) {
      Text(
        text = stringResource(R.string.sets_detail_is_default),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(SetDetailTestTags.IS_DEFAULT),
      )
    } else if (state.canBeDefault) {
      // `btn btn-secondary` in the prototype: an outline in the divider
      // colour with the text in the ink, not another accent-coloured ghost.
      OutlinedButton(
        onClick = { presenter.makeDefault() },
        shape = Modernist.square,
        border = BorderStroke(Modernist.hairline, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        modifier = Modifier.testTag(SetDetailTestTags.MAKE_DEFAULT),
      ) {
        Text(stringResource(R.string.sets_detail_default))
      }
    }
    if (state.isDefault || state.canBeDefault) {
      Text(
        text = stringResource(R.string.sets_detail_default_note),
        style = MaterialTheme.typography.bodySmall,
        color = Ink.muted,
      )
    }
  }
}

/** Switch off, switch on, remove. Not offered for the bundled set. */
@Composable
private fun Manage(
  row: SetRow,
  presenter: SetDetailPresenter,
) {
  if (row.bundled) return
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    TextButton(
      onClick = { presenter.setEnabled(!row.enabled) },
      shape = Modernist.square,
      modifier = Modifier.testTag(SetDetailTestTags.TOGGLE),
    ) {
      Text(stringResource(if (row.enabled) R.string.sets_sheet_disable else R.string.sets_sheet_enable))
    }
    TextButton(
      onClick = { presenter.remove() },
      shape = Modernist.square,
      modifier = Modifier.testTag(SetDetailTestTags.REMOVE),
    ) {
      Text(text = stringResource(R.string.sets_sheet_remove), color = Ink.accent)
    }
  }
}

/**
 * The personal package on its way out, gated on a licence (design `8c`).
 *
 * Only "My dice" has one, because it is the only package this phone wrote —
 * every other set on the list came from somewhere that already has a copy.
 *
 * **The licence comes before the button and the button will not work without
 * it.** A dice set is something somebody else installs and draws with, and the
 * `license` field is the only thing in the file that says what they may do with
 * it (`docs/dice-sets.md`, "Fields"). Offering "share" first and asking
 * afterwards would be asking about a file that had already gone.
 */
@Composable
private fun Export(presenter: SetDetailPresenter) {
  val state = presenter.state
  if (!state.personal) return
  Rule()
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(SetDetailTestTags.EXPORT),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = stringResource(R.string.sets_detail_export),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.ExtraBold,
    )
    Text(
      text = stringResource(R.string.sets_detail_export_note),
      style = MaterialTheme.typography.bodySmall,
      color = Ink.muted,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      LicenseChooser(state.license, presenter::choose, Modifier.weight(1f))
      Button(
        onClick = { presenter.export() },
        enabled = state.canExport,
        shape = Modernist.square,
        modifier = Modifier.testTag(SetDetailTestTags.EXPORT_DO),
      ) {
        Text(stringResource(R.string.sets_detail_export_do))
      }
    }
    if (state.exported) {
      Text(
        text = stringResource(R.string.sets_detail_exported, MinePackage.FILE_NAME),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(SetDetailTestTags.EXPORTED),
      )
    }
    state.exportProblem.forEach { message ->
      Text(
        text = message.toString(),
        style = MaterialTheme.typography.bodySmall,
        color = Ink.accent,
        modifier = Modifier.fillMaxWidth().testTag(SetDetailTestTags.EXPORT_PROBLEM),
      )
    }
  }
}

/**
 * The short list of licences a package may go out under (design `8c`).
 *
 * A menu rather than a text field: the point of the field is that whoever
 * installs the set *recognises* what it says, and a box somebody types into
 * would fill up with sentences no reader can act on ([SetLicense]).
 *
 * There is no entry for "not chosen". Taking the choice back is not something
 * anybody wants to do, and an entry offering it would be the one a finger hits
 * by accident.
 */
@Composable
private fun LicenseChooser(
  chosen: SetLicense?,
  onChoose: (SetLicense) -> Unit,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  Box(modifier = modifier) {
    OutlinedButton(
      onClick = { open = true },
      shape = Modernist.square,
      border = BorderStroke(Modernist.hairline, MaterialTheme.colorScheme.outline),
      colors =
        ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ),
      modifier = Modifier.fillMaxWidth().testTag(SetDetailTestTags.LICENSE_CHOOSER),
    ) {
      // An `.input` reads from its left edge, whatever Material would centre.
      Text(
        text = chosen?.label ?: stringResource(R.string.sets_detail_license_choose),
        modifier = Modifier.weight(1f),
      )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      SetLicense.entries.forEach { license ->
        DropdownMenuItem(
          text = { Text(license.label) },
          onClick = {
            open = false
            onChoose(license)
          },
          modifier = Modifier.testTag(SetDetailTestTags.licenseOf(license)),
        )
      }
    }
  }
}

/** Every die the set defines, drawn as the outline a player recognises. */
private fun LazyListScope.dice(dice: List<Die>) {
  item {
    SectionKicker(
      // The count is in the kicker, where the prototype puts it: the heading
      // of the block is the one place it says how big the block is, and a
      // second line saying "7 dice" would be the same fact twice.
      text = stringResource(R.string.sets_detail_dice, dice.size),
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
  }
  items(dice, key = Die::id) { die -> DieLine(die) }
}

@Composable
private fun DieLine(die: Die) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) { }
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .testTag(SetDetailTestTags.dieOf(die.id)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    DieSilhouette(
      // The shape is what a die *looks* like, and the shape knows how many
      // faces it has — so the outline follows the solid rather than the
      // values printed on it. A d6 of skulls is still a cube.
      sides = Sides.Numeric(die.shape.faceCount),
      // **The set's own colour**, which is what makes this a picture of *this*
      // die rather than of a die. The prototype fills each shape in the grid
      // with `{{ s.color }}` for the same reason, and the heading above says
      // these are rendered from the set — a claim a row of identical grey
      // outlines did not meet (`design/dInfinityPhone.dc.html`, the Dice set
      // screen).
      fill = Color(die.material.colorArgb),
      // The ink the row is written in, thinned. It stays the outline rather
      // than following the die, because a pale die on the pale ground would
      // otherwise have no edge at all.
      ink = Ink.muted,
      modifier = Modifier.size(32.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(text = die.id, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
      Text(
        text = pluralStringResource(R.plurals.sets_detail_faces, die.faces.size, die.faces.size),
        style = MaterialTheme.typography.bodySmall,
        color = Ink.muted,
      )
    }
  }
}

/**
 * Everything wrong with the package, where its dice would be (`6b`).
 *
 * Each line as the validator wrote it — `diceset.toml:14: error: …` — because
 * the person who can fix it is the author, and `file:line` is what they need.
 */
private fun LazyListScope.report(report: List<ValidationMessage>) {
  item {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      SectionKicker(stringResource(R.string.sets_detail_report))
      Text(
        text = stringResource(R.string.sets_detail_report_note),
        style = MaterialTheme.typography.bodySmall,
        color = Ink.muted,
      )
    }
  }
  items(report) { message ->
    Text(
      text = message.toString(),
      style = MaterialTheme.typography.bodySmall,
      color = Ink.accent,
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .testTag(SetDetailTestTags.PROBLEM),
    )
  }
}

/** One labelled fact, shown only when there is one. */
@Composable
private fun Field(
  label: String,
  value: String?,
) {
  if (value == null) return
  Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
    Label(label)
    Text(text = value, style = MaterialTheme.typography.bodyMedium)
  }
}

/**
 * The quiet name of one fact — "Author", "Licence", "Installed from".
 *
 * Not a [SectionKicker], although both are small: a kicker names a *part of
 * the screen* and is the accent's one job on a page of plain text, while this
 * names the line beside it and stays in the muted ink. The prototype's
 * metadata grid sets these at `opacity:.6`, not in the accent.
 */
@Composable
private fun Label(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = Ink.muted,
    modifier = modifier,
  )
}

/** What the tests reach for. */
object SetDetailTestTags {
  const val SCREEN: String = "setdetail:screen"
  const val LIST: String = "setdetail:list"
  const val NAME: String = "setdetail:name"
  const val MISSING: String = "setdetail:missing"
  const val SOURCE: String = "setdetail:source"
  const val TOGGLE: String = "setdetail:toggle"
  const val REMOVE: String = "setdetail:remove"
  const val PROBLEM: String = "setdetail:problem"
  const val MAKE_DEFAULT: String = "setdetail:makedefault"
  const val IS_DEFAULT: String = "setdetail:isdefault"
  const val EXPORT: String = "setdetail:export"
  const val EXPORT_DO: String = "setdetail:export:do"
  const val EXPORTED: String = "setdetail:export:done"
  const val EXPORT_PROBLEM: String = "setdetail:export:problem"
  const val LICENSE_CHOOSER: String = "setdetail:license"

  fun dieOf(id: String): String = "setdetail:die:$id"

  fun licenseOf(license: SetLicense): String = "setdetail:license:${license.id}"
}
