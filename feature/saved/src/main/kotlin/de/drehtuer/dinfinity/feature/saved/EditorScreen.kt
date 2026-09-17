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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.ui.common.FormulaField
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.OptionBox
import de.drehtuer.dinfinity.ui.common.OptionFill
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.UpButton

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
  groups: GroupPresenter,
  modifier: Modifier = Modifier,
  onDone: () -> Unit = {},
  onRollNow: (String) -> Unit = {},
  /**
   * Where the chevron in the header goes.
   *
   * A lambda rather than a destination: this module does not know what the
   * navigation graph is, and the rule that the chevron *climbs* rather than
   * retraces belongs to the graph (`docs/architecture.md`, "Navigation").
   *
   * It matters more here than anywhere else, because this is the one screen
   * with no menu button: the editor is about a roll rather than a subject, so
   * without a way out of its own it could only be left by saving, deleting or
   * the system's own back.
   */
  onUp: () -> Unit = {},
) {
  val state = presenter.state
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag(EditorTestTags.SCREEN),
  ) {
    Title(existing = state.existing, onUp = onUp)
    Form(
      state = state,
      presenter = presenter,
      groups = groups,
      onRollNow = onRollNow,
    )
  }

  NewGroup(groups = groups, onMade = { groupId -> presenter.choose { copy(groupId = groupId) } })

  // Written down, or taken away: either way there is nothing left to edit.
  // An effect rather than a call from the composition, because leaving a
  // screen is not something to do while drawing it.
  LaunchedEffect(state.saved, state.gone) {
    if (state.saved || state.gone) onDone()
  }
}

/**
 * Everything about the roll that can be typed or chosen, under the title bar.
 *
 * Its own composable rather than the screen's own body: the screen is the
 * chrome and the leaving, and the form is long enough to read on its own.
 */
@Composable
private fun Form(
  state: EditorState,
  presenter: EditorPresenter,
  groups: GroupPresenter,
  onRollNow: (String) -> Unit,
) {
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(Modernist.x4),
    verticalArrangement = Arrangement.spacedBy(Modernist.x4),
  ) {
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
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
        modifier = Modifier.testTag(EditorTestTags.ODDS),
      )
    }

    Icons(chosen = state.icon) { icon -> presenter.choose { copy(icon = icon) } }
    Colours(chosen = state.colourArgb) { argb -> presenter.choose { copy(colourArgb = argb) } }
    Groups(
      state = state,
      onPick = { groupId -> presenter.choose { copy(groupId = groupId) } },
      // A roll can be filed somewhere that does not exist yet. Without this
      // the only way to make a group is to leave the roll half-written.
      onNew = { groups.create() },
    )
    Tables(state = state) { pin -> presenter.choose { copy(tablePin = pin) } }

    Favourite(on = state.favourite) { chosen -> presenter.choose { copy(favourite = chosen) } }
    Buttons(state = state, presenter = presenter, onRollNow = onRollNow)
  }
}

/**
 * What the screen is, over the 2 dp rule every screen in the prototype hangs
 * from. `titleLarge` is the heading face at 800 — the system's own heading
 * weight — where `headlineSmall` is a style the theme does not fill in and so
 * was Material's 24 sp at 400.
 */
@Composable
private fun Title(
  existing: Boolean,
  onUp: () -> Unit,
) {
  // The chevron, then the name of what is being written down — the header the
  // prototype draws on every screen it can be left from
  // (`design/dInfinityPhone.dc.html`, the editor).
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = Modernist.x2, vertical = Modernist.x1),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    UpButton(onUp = onUp)
    Text(
      text = stringResource(if (existing) R.string.editor_title_edit else R.string.editor_title_new),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
  Rule()
}

@Composable
private fun Favourite(
  on: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
    modifier =
      Modifier
        .fillMaxWidth()
        .toggleable(value = on, role = Role.Checkbox, onValueChange = onChange)
        .testTag(EditorTestTags.FAVOURITE),
  ) {
    Checkbox(checked = on, onCheckedChange = null)
    Text(
      text = stringResource(R.string.editor_favourite),
      style = MaterialTheme.typography.bodyLarge,
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
  // Over a 2 dp rule, and in the prototype's three weights: the one thing to
  // do is filled, the other is outlined, and the one that takes something away
  // is a ghost.
  Rule()
  Row(
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ModernistButton(
      text = stringResource(R.string.editor_save),
      onClick = { presenter.save() },
      kind = ModernistButtonKind.Primary,
      enabled = state.savable,
      modifier = Modifier.testTag(EditorTestTags.SAVE),
    )
    ModernistButton(
      text = stringResource(R.string.editor_roll_now),
      onClick = { onRollNow(state.formula) },
      enabled = state.savable,
      modifier = Modifier.testTag(EditorTestTags.ROLL_NOW),
    )
    Text(text = "", modifier = Modifier.weight(1f))
    if (state.existing) {
      ModernistButton(
        text = stringResource(R.string.editor_delete),
        onClick = presenter::delete,
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(EditorTestTags.DELETE),
      )
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Modernist.x1)) {
      ICONS.forEach { icon ->
        OptionBox(
          text = icon,
          selected = icon == chosen,
          onClick = { onPick(if (icon == chosen) "" else icon) },
          // The ink, not the accent: a mark already prints in the accent when
          // the roll wears one, and a red square behind a red mark is the two
          // saying the same thing over each other.
          fill = OptionFill.Ink,
          square = true,
          // Tapping the chosen mark clears it, so this is a set that can end
          // up empty — checkboxes, not a radio group.
          role = Role.Checkbox,
          // The emoji is the label, and an emoji is not a name: a screen
          // reader is told what the picture is of (`MarkNames.kt`).
          contentDescription = markName(icon)?.let { name -> stringResource(name) },
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
    Row(
      horizontalArrangement = Arrangement.spacedBy(Modernist.x1),
      verticalAlignment = Alignment.CenterVertically,
    ) {
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
          .background(colour ?: MaterialTheme.colorScheme.surface)
          // The prototype draws every swatch with the same 2 px border and
          // says which one is chosen by its colour: the ink, against the
          // divider the others wear.
          .border(
            width = Modernist.rule,
            color = if (chosen) MaterialTheme.colorScheme.onBackground else Ink.divider,
          ).clickable(onClick = onPick)
          .testTag(tag),
    ) { }
  }
}

/**
 * The same sheet the saved-rolls screen uses.
 *
 * Shared rather than a second dialog, so a group made from here is made the
 * same way and refused for the same reasons. A group saved from it becomes
 * this roll's, which is what asking for it meant.
 */
@Composable
private fun NewGroup(
  groups: GroupPresenter,
  onMade: (String) -> Unit,
) {
  groups.draft?.let { draft -> GroupSheet(draft = draft, presenter = groups, onSaved = onMade) }
}

@Composable
private fun Groups(
  state: EditorState,
  onPick: (String) -> Unit,
  onNew: () -> Unit,
) {
  Field(stringResource(R.string.editor_group)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Modernist.x1)) {
      state.groups.forEach { group ->
        OptionBox(
          text = group.name,
          selected = group.id == state.groupId,
          onClick = { onPick(group.id) },
          fill = OptionFill.Accent,
          modifier = Modifier.testTag(EditorTestTags.groupOf(group.id)),
        )
      }
      // Not one of the options: making a group is an action, and a control
      // that does something rather than standing for a choice is a button.
      ModernistButton(
        text = stringResource(R.string.group_new),
        onClick = onNew,
        kind = ModernistButtonKind.Secondary,
        modifier = Modifier.testTag(EditorTestTags.NEW_GROUP),
      )
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Modernist.x1)) {
      state.tables.forEach { choice ->
        OptionBox(
          text = choice.name ?: stringResource(R.string.editor_table_default),
          selected = choice.pin == state.tablePin,
          onClick = { onPick(choice.pin) },
          fill = OptionFill.Accent,
          modifier = Modifier.testTag(EditorTestTags.tableOf(choice.pin)),
        )
      }
    }
    Text(
      text = stringResource(R.string.editor_table_note),
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
    )
  }
}

@Composable
private fun Field(
  label: String,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Modernist.x1)) {
    Text(
      // `.field > label`: small, quiet, and above the thing it names.
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
    )
    content()
  }
}

/** `width:34px;height:34px` — the prototype's own colour swatch. */
private val SWATCH: Dp = 34.dp

/** The marks on offer. Emoji, because every phone already draws them. */
internal val ICONS = listOf("⚔️", "🏹", "🔥", "🛡️", "✨", "💀", "🗡️", "💥", "🎲", "🧪")

/** What the tests reach the editor by. */
object EditorTestTags {
  const val SCREEN: String = "editor:screen"
  const val NAME: String = "editor:name"
  const val ODDS: String = "editor:odds"
  const val FAVOURITE: String = "editor:favourite"
  const val SAVE: String = "editor:save"
  const val ROLL_NOW: String = "editor:roll-now"
  const val DELETE: String = "editor:delete"
  const val NEW_GROUP: String = "editor:new-group"

  fun iconOf(icon: String): String = "editor:icon:$icon"

  fun colourOf(argb: Int?): String = "editor:colour:${argb ?: "none"}"

  fun groupOf(id: String): String = "editor:group:$id"

  fun tableOf(pin: TablePin?): String = "editor:table:${pin?.let { "${it.setId}/${it.tableId}" } ?: "default"}"
}
