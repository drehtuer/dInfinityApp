package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.ui.common.FormulaField

/**
 * Writing down a saved roll (`design/dInfinity.dc.html`, options 1r and 7b).
 *
 * The formula field is the tray's — `ui/common`'s — so a mistake is shown the
 * same way here as there. Under it, when the formula reads, is what it is
 * worth: the exact mean and range, which is the thing a player is choosing
 * between when they write `2d6 + 3` or `1d12 + 2`.
 *
 * Saving is disabled while the formula does not read. A saved roll that cannot
 * be thrown is a button that fails when it is pressed, weeks later, in the
 * middle of somebody's game.
 */
@Composable
fun EditorScreen(
  presenter: EditorPresenter,
  modifier: Modifier = Modifier,
  onDone: () -> Unit = {},
  onRollNow: (String) -> Unit = {},
) {
  val state = presenter.state
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .testTag(EditorTestTags.SCREEN),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
      text = stringResource(if (state.existing) R.string.editor_title_edit else R.string.editor_title_new),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )

    OutlinedTextField(
      value = state.name,
      onValueChange = presenter::name,
      singleLine = true,
      label = { Text(stringResource(R.string.editor_name)) },
      placeholder = { Text(stringResource(R.string.editor_name_hint)) },
      modifier = Modifier.fillMaxWidth().testTag(EditorTestTags.NAME),
    )

    FormulaField(
      text = state.formula,
      onChange = presenter::formula,
      label = stringResource(R.string.editor_formula),
      hint = stringResource(R.string.editor_formula_hint),
      error = state.error,
    )

    // What it is worth, when it reads. Nothing has to be thrown to know it.
    val odds = state.odds
    if (odds != null) {
      Text(
        text = stringResource(R.string.editor_odds, "%.1f".format(odds.mean), odds.lowest, odds.highest),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(EditorTestTags.ODDS),
      )
    }

    Icons(chosen = state.icon) { icon -> presenter.choose { copy(icon = icon) } }
    Colours(chosen = state.colourArgb) { argb -> presenter.choose { copy(colourArgb = argb) } }
    Groups(state = state) { groupId -> presenter.choose { copy(groupId = groupId) } }
    Tables(state = state) { pin -> presenter.choose { copy(tablePin = pin) } }

    Favourite(on = state.favourite) { chosen -> presenter.choose { copy(favourite = chosen) } }
    Buttons(state = state, presenter = presenter, onRollNow = onRollNow)
  }

  // Written down, or taken away: either way there is nothing left to edit.
  // An effect rather than a call from the composition, because leaving a
  // screen is not something to do while drawing it.
  LaunchedEffect(state.saved, state.gone) {
    if (state.saved || state.gone) onDone()
  }
}

@Composable
private fun Favourite(
  on: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier =
      Modifier
        .fillMaxWidth()
        .toggleable(value = on, role = Role.Checkbox, onValueChange = onChange)
        .testTag(EditorTestTags.FAVOURITE),
  ) {
    Checkbox(checked = on, onCheckedChange = null)
    Text(
      text = stringResource(R.string.editor_favourite),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

/**
 * Save, roll now, and — for a roll that already exists — delete.
 *
 * Saving and rolling are both disabled while the formula does not read. There
 * is nothing to write down and nothing to throw, and a button that explains
 * itself by failing is not a button.
 */
@Composable
private fun Buttons(
  state: EditorState,
  presenter: EditorPresenter,
  onRollNow: (String) -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Button(
      onClick = { presenter.save() },
      enabled = state.savable,
      modifier = Modifier.testTag(EditorTestTags.SAVE),
    ) {
      Text(stringResource(R.string.editor_save))
    }
    TextButton(
      onClick = { onRollNow(state.formula) },
      enabled = state.savable,
      modifier = Modifier.testTag(EditorTestTags.ROLL_NOW),
    ) {
      Text(stringResource(R.string.editor_roll_now))
    }
    Text(text = "", modifier = Modifier.weight(1f))
    if (state.existing) {
      TextButton(onClick = presenter::delete, modifier = Modifier.testTag(EditorTestTags.DELETE)) {
        Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
      }
    }
  }
}

/**
 * A handful of marks a roll can wear.
 *
 * Emoji rather than an icon pack: the model already says an icon is "an emoji
 * or a name from the built-in icon pack", there is no pack yet, and a set of
 * characters every phone already draws is a better answer than a set of
 * drawings nobody has made (`docs/dice-notation.md`).
 */
@Composable
private fun Icons(
  chosen: String,
  onPick: (String) -> Unit,
) {
  Field(stringResource(R.string.editor_icon)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      ICONS.forEach { icon ->
        FilterChip(
          selected = icon == chosen,
          onClick = { onPick(if (icon == chosen) "" else icon) },
          label = { Text(icon) },
          modifier = Modifier.testTag(EditorTestTags.iconOf(icon)),
        )
      }
    }
  }
}

/**
 * The colour a roll's mark prints in (design option 9d).
 *
 * The same six the interface spends anywhere, plus none. A free picker would
 * let somebody choose a colour that vanishes against the ground, which is the
 * reason the accent is a fixed palette in the first place
 * (`docs/architecture.md`, decision 22).
 */
@Composable
private fun Colours(
  chosen: Int?,
  onPick: (Int?) -> Unit,
) {
  Field(stringResource(R.string.editor_colour)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Swatch(colour = null, chosen = chosen == null, onPick = { onPick(null) }, tag = EditorTestTags.colourOf(null))
      AccentColor.entries.forEach { accent ->
        Swatch(
          colour = Color(accent.argb),
          chosen = chosen == accent.argb,
          onPick = { onPick(accent.argb) },
          tag = EditorTestTags.colourOf(accent.argb),
        )
      }
    }
  }
}

@Composable
private fun Swatch(
  colour: Color?,
  chosen: Boolean,
  onPick: () -> Unit,
  tag: String,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(
      modifier =
        Modifier
          .size(SWATCH)
          .background(colour ?: MaterialTheme.colorScheme.surfaceVariant)
          .border(
            width = if (chosen) 3.dp else 1.dp,
            color = if (chosen) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
          ).clickable(onClick = onPick)
          .testTag(tag),
    ) { }
  }
}

@Composable
private fun Groups(
  state: EditorState,
  onPick: (String) -> Unit,
) {
  Field(stringResource(R.string.editor_group)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      state.groups.forEach { group ->
        FilterChip(
          selected = group.id == state.groupId,
          onClick = { onPick(group.id) },
          label = { Text(group.name) },
          modifier = Modifier.testTag(EditorTestTags.groupOf(group.id)),
        )
      }
    }
  }
}

/**
 * The table this roll is always thrown on (design option 7b).
 *
 * "Default" is first and means *follow whatever is pinned above* — the group's
 * table, then the app's. A pin here wins over both while this roll is the one
 * being thrown (`docs/tables.md`).
 */
@Composable
private fun Tables(
  state: EditorState,
  onPick: (TablePin?) -> Unit,
) {
  Field(stringResource(R.string.editor_table)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      state.tables.forEach { choice ->
        FilterChip(
          selected = choice.pin == state.tablePin,
          onClick = { onPick(choice.pin) },
          label = { Text(choice.name ?: stringResource(R.string.editor_table_default)) },
          modifier = Modifier.testTag(EditorTestTags.tableOf(choice.pin)),
        )
      }
    }
    Text(
      text = stringResource(R.string.editor_table_note),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun Field(
  label: String,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    content()
  }
}

private val SWATCH = 34.dp

/** The marks on offer. Emoji, because every phone already draws them. */
private val ICONS = listOf("⚔️", "🏹", "🔥", "🛡️", "✨", "💀", "🗡️", "💥", "🎲", "🧪")

/** What the tests reach the editor by. */
object EditorTestTags {
  const val SCREEN: String = "editor:screen"
  const val NAME: String = "editor:name"
  const val ODDS: String = "editor:odds"
  const val FAVOURITE: String = "editor:favourite"
  const val SAVE: String = "editor:save"
  const val ROLL_NOW: String = "editor:roll-now"
  const val DELETE: String = "editor:delete"

  fun iconOf(icon: String): String = "editor:icon:$icon"

  fun colourOf(argb: Int?): String = "editor:colour:${argb ?: "none"}"

  fun groupOf(id: String): String = "editor:group:$id"

  fun tableOf(pin: TablePin?): String = "editor:table:${pin?.let { "${it.setId}/${it.tableId}" } ?: "default"}"
}
