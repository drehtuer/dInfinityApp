package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.OptionBox
import de.drehtuer.dinfinity.ui.common.OptionFill

/**
 * Naming a group (`docs/dice-notation.md`, "Saved rolls").
 *
 * A dialog rather than a screen: a group is a name, a mark and which group it
 * sits in, and three fields do not deserve a destination of their own — nor a
 * place in the navigation graph that the back button would have to mean
 * something on.
 *
 * The two rules a player can break are answered while they type rather than
 * when they press Save. A name already taken says whose it is, because "that
 * name is taken" is only useful if it says by what.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupSheet(
  draft: GroupDraft,
  presenter: GroupPresenter,
  modifier: Modifier = Modifier,
  onSaved: (String) -> Unit = {},
) {
  AlertDialog(
    modifier = modifier.testTag(GroupTestTags.SHEET),
    onDismissRequest = presenter::dismiss,
    // `.dialog`: the surface, no corner, and a title at the scale's `h4` in
    // the heading weight. Material's own dialog title is `headlineSmall`,
    // which is not a style the theme fills in.
    shape = Modernist.square,
    title = {
      Text(
        text =
          stringResource(
            if (draft.fresh) R.string.group_title_new else R.string.group_title_edit,
          ),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = { Body(draft = draft, presenter = presenter) },
    confirmButton = {
      Button(
        onClick = { presenter.save(onSaved) },
        enabled = draft.savable,
        shape = Modernist.square,
        modifier = Modifier.testTag(GroupTestTags.SAVE),
      ) {
        Text(stringResource(R.string.group_save))
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(Modernist.x2)) {
        if (draft.deletable) {
          TextButton(
            onClick = { presenter.delete() },
            shape = Modernist.square,
            modifier = Modifier.testTag(GroupTestTags.DELETE),
          ) {
            // The system's one red, which is the accent (`Modernist`).
            Text(stringResource(R.string.group_delete), color = Ink.accent)
          }
        }
        TextButton(
          onClick = presenter::dismiss,
          shape = Modernist.square,
          modifier = Modifier.testTag(GroupTestTags.CANCEL),
        ) {
          Text(stringResource(R.string.group_cancel))
        }
      }
    },
  )
}

/** Everything about the group that can be typed or chosen. */
@Composable
private fun Body(
  draft: GroupDraft,
  presenter: GroupPresenter,
) {
  Column(
    modifier = Modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(Modernist.x3),
  ) {
    OutlinedTextField(
      value = draft.name,
      onValueChange = presenter::name,
      singleLine = true,
      isError = draft.clash != null,
      label = { Text(stringResource(R.string.group_name)) },
      placeholder = { Text(stringResource(R.string.group_name_hint)) },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      modifier = Modifier.fillMaxWidth().testTag(GroupTestTags.NAME),
    )
    // Naming the clash rather than only reporting one: the group it collides
    // with may be one the player forgot they made, or one an import brought in.
    draft.clash?.let { taken ->
      Text(
        text = stringResource(R.string.group_name_taken, taken),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.accent,
        modifier = Modifier.testTag(GroupTestTags.CLASH),
      )
    }

    Marks(chosen = draft.icon, onPick = { emoji -> presenter.choose { copy(icon = emoji) } })
    Parents(draft = draft, onPick = { id -> presenter.choose { copy(parentId = id) } })
    Tables(draft = draft, onPick = { pin -> presenter.choose { copy(tablePin = pin) } })

    if (draft.deletable && draft.rolls > 0) {
      Text(
        text = pluralStringResource(R.plurals.group_delete_moves, draft.rolls, draft.rolls),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
        modifier = Modifier.testTag(GroupTestTags.MOVES),
      )
    }
  }
}

/** The marks a group can wear: a game, a character, a bestiary. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Marks(
  chosen: String,
  onPick: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Modernist.x1)) {
    Label(stringResource(R.string.group_icon))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Modernist.x1)) {
      GROUP_ICONS.forEach { icon ->
        OptionBox(
          text = icon,
          selected = icon == chosen,
          onClick = { onPick(if (icon == chosen) "" else icon) },
          // The ink rather than the accent, for the reason the editor's marks
          // use it: the mark itself is what carries the accent.
          fill = OptionFill.Ink,
          square = true,
          // Tapping the chosen mark clears it, so the set can end up empty.
          role = Role.Checkbox,
          // The emoji is the label, and an emoji is not a name: a screen
          // reader is told what the picture is of (`MarkNames.kt`).
          contentDescription = markName(icon)?.let { name -> stringResource(name) },
          modifier = Modifier.testTag(GroupTestTags.iconOf(icon)),
        )
      }
    }
  }
}

/**
 * Which group this one lives in.
 *
 * Only top-level groups are offered, and a group that already has groups
 * inside it is offered none at all — with the reason on screen, because a
 * chooser that is simply empty reads as a bug (`docs/dice-notation.md`: one
 * level, `D&D / Thorin`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Parents(
  draft: GroupDraft,
  onPick: (String?) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Modernist.x1)) {
    Label(stringResource(R.string.group_parent))
    if (!draft.nestable) {
      Text(
        text = stringResource(R.string.group_parent_has_children),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
        modifier = Modifier.testTag(GroupTestTags.NO_NESTING),
      )
      return@Column
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Modernist.x1)) {
      OptionBox(
        text = stringResource(R.string.group_parent_none),
        selected = draft.parentId == null,
        onClick = { onPick(null) },
        fill = OptionFill.Accent,
        modifier = Modifier.testTag(GroupTestTags.parentOf(null)),
      )
      draft.parents.forEach { group ->
        OptionBox(
          text = group.name,
          selected = draft.parentId == group.id,
          onClick = { onPick(group.id) },
          fill = OptionFill.Accent,
          modifier = Modifier.testTag(GroupTestTags.parentOf(group.id)),
        )
      }
    }
  }
}

/**
 * The table every roll in this group lands on (`docs/tables.md`).
 *
 * "Default" is first and means *follow the app's table*. A roll that pins its
 * own wins over this one — most specific first — which is what the note under
 * the row says, because a precedence nobody is told about is a precedence
 * somebody will read as a bug.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tables(
  draft: GroupDraft,
  onPick: (TablePin?) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Modernist.x1)) {
    Label(stringResource(R.string.group_table))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Modernist.x1)) {
      draft.tables.forEach { choice ->
        OptionBox(
          text = choice.name ?: stringResource(R.string.editor_table_default),
          selected = choice.pin == draft.tablePin,
          onClick = { onPick(choice.pin) },
          fill = OptionFill.Accent,
          modifier = Modifier.testTag(GroupTestTags.tableOf(choice.pin)),
        )
      }
    }
    Text(
      text = stringResource(R.string.group_table_note),
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
    )
  }
}

@Composable
private fun Label(text: String) {
  Text(
    // `.field > label`: small, quiet, and above the thing it names.
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = Ink.muted,
  )
}

/** The marks on offer. Emoji, for the same reason a saved roll's are. */
internal val GROUP_ICONS = listOf("🎲", "🐉", "🏰", "🗺️", "⚔️", "🧙", "🌲", "🚀", "📕", "⭐")

/** What the tests reach the group sheet by. */
object GroupTestTags {
  const val SHEET: String = "group:sheet"
  const val NAME: String = "group:name"
  const val CLASH: String = "group:clash"
  const val SAVE: String = "group:save"
  const val DELETE: String = "group:delete"
  const val CANCEL: String = "group:cancel"
  const val NO_NESTING: String = "group:no-nesting"
  const val MOVES: String = "group:moves"
  const val NEW: String = "group:new"

  fun iconOf(icon: String): String = "group:icon:$icon"

  fun parentOf(id: String?): String = "group:parent:${id ?: "none"}"

  fun tableOf(pin: TablePin?): String = "group:table:${pin?.let { "${it.setId}/${it.tableId}" } ?: "default"}"

  fun editOf(id: String): String = "group:edit:$id"
}
