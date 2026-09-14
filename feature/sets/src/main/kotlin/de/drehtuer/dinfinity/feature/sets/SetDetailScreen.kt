package de.drehtuer.dinfinity.feature.sets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.notation.Sides
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.ui.common.DieSilhouette

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
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.weight(1f).testTag(SetDetailTestTags.NAME),
    )
    version?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = stringResource(R.string.sets_detail_gone_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    item { HorizontalDivider() }
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
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .let { if (linkable) it.clickable { onSource(source) } else it }
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    if (state.isDefault) {
      Text(
        text = stringResource(R.string.sets_detail_is_default),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(SetDetailTestTags.IS_DEFAULT),
      )
    } else if (state.canBeDefault) {
      TextButton(
        onClick = { presenter.makeDefault() },
        modifier = Modifier.testTag(SetDetailTestTags.MAKE_DEFAULT),
      ) {
        Text(stringResource(R.string.sets_detail_default))
      }
    }
    if (state.isDefault || state.canBeDefault) {
      Text(
        text = stringResource(R.string.sets_detail_default_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
      modifier = Modifier.testTag(SetDetailTestTags.TOGGLE),
    ) {
      Text(stringResource(if (row.enabled) R.string.sets_sheet_disable else R.string.sets_sheet_enable))
    }
    TextButton(onClick = { presenter.remove() }, modifier = Modifier.testTag(SetDetailTestTags.REMOVE)) {
      Text(text = stringResource(R.string.sets_sheet_remove), color = MaterialTheme.colorScheme.error)
    }
  }
}

/** Every die the set defines, drawn as the outline a player recognises. */
private fun LazyListScope.dice(dice: List<Die>) {
  item {
    Label(
      text = stringResource(R.string.sets_detail_dice),
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
      fill = MaterialTheme.colorScheme.surfaceVariant,
      ink = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(32.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(text = die.id, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(
        text = pluralStringResource(R.plurals.sets_detail_faces, die.faces.size, die.faces.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
      Label(stringResource(R.string.sets_detail_report))
      Text(
        text = stringResource(R.string.sets_detail_report_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  items(report) { message ->
    Text(
      text = message.toString(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
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

@Composable
private fun Label(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

  fun dieOf(id: String): String = "setdetail:die:$id"
}
