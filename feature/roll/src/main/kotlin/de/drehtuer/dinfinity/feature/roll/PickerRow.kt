package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.notation.PickableDie
import de.drehtuer.dinfinity.ui.common.DieSilhouette
import de.drehtuer.dinfinity.ui.common.SegmentedControl

/**
 * Dice added by tapping rather than by typing
 * (`design/dInfinity.dc.html`, option 1h).
 *
 * Tap adds one, a long press takes one off, and the badge says how many of
 * that die the formula is currently asking for. Every one of those is an edit
 * to the formula field and nothing else: `DicePicker` decides what the text
 * becomes and the same `type` that a keystroke goes through carries it, so the
 * row cannot reach a state the field could not have been typed into
 * (`docs/dice-notation.md`, "Picking dice without typing").
 *
 * It scrolls rather than wrapping. Ten dice at a touch target worth pressing
 * do not fit across a 360 dp phone, and a row that reflows to two lines when a
 * set happens to define one more die is a row whose dice move about.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PickerRow(
  dice: List<PickableDie>,
  counts: Map<PickableDie, Int>,
  onAdd: (PickableDie) -> Unit,
  onRemove: (PickableDie) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (dice.isEmpty()) return
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .testTag(RollTestTags.PICKER),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    dice.forEach { die ->
      PickerDie(
        die = die,
        count = counts[die] ?: 0,
        onAdd = { onAdd(die) },
        onRemove = { onRemove(die) },
      )
    }
  }
}

/**
 * Which set the row above is offering (`design/dInfinity.dc.html`, option 4a).
 *
 * Not drawn until there is a second set to choose between: a chooser with one
 * entry is furniture, and until somebody installs a set there is exactly one.
 * That is the same rule the session and set choosers on the statistics screen
 * follow.
 *
 * It scrolls beside the dice for the same reason they do — a name is as long
 * as its author made it — and it does **not** change which set a bare `d20`
 * means. That is a preference and it is chosen where the sets are
 * (`docs/dice-sets.md`, design `6a`).
 *
 * Drawn as the design system's segmented control (`.seg`), which is what this
 * system has for picking one of a few: one bordered box, the chosen name
 * filled with the accent. It was a row of Material text buttons, which is
 * three pills and no indication that they are alternatives.
 */
@Composable
internal fun SetChooser(
  sets: List<DiceSet>,
  chosen: String,
  onChoose: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (sets.size < 2) return
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .testTag(RollTestTags.SETS),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SegmentedControl(
      options = sets,
      selected = sets.firstOrNull { set -> set.id == chosen } ?: sets.first(),
      label = { set -> set.name },
      onSelect = { set -> onChoose(set.id) },
      tagOf = { set -> RollTestTags.setOf(set.id) },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerDie(
  die: PickableDie,
  count: Int,
  onAdd: () -> Unit,
  onRemove: () -> Unit,
) {
  val add = stringResource(R.string.roll_picker_add, die.notation)
  val remove = stringResource(R.string.roll_picker_remove, die.notation)
  Box(
    modifier =
      Modifier
        .sizeIn(minWidth = TARGET, minHeight = TARGET)
        .combinedClickable(
          // Spelled out for TalkBack, which otherwise announces a long press
          // as "double tap and hold" with no word about what it does.
          onClickLabel = add,
          onLongClickLabel = remove,
          onClick = onAdd,
          onLongClick = onRemove,
        ).testTag(RollTestTags.pickerDie(die.notation)),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 4.dp),
    ) {
      DieSilhouette(
        sides = die.sides,
        // `--color-surface`, named rather than reached for through
        // `surfaceVariant`: the palette has one surface and no variant of it.
        fill = MaterialTheme.colorScheme.surface,
        ink = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.size(SILHOUETTE),
      )
      Text(
        text = die.notation,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }

    // Only when there are any. A row of zeroes is a row of noise, and the
    // badge is what tells a player at a glance which dice are in the throw.
    if (count > 0) {
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelSmall,
        // 800, like every number the design prints on the accent.
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 4.dp)
            .testTag(RollTestTags.pickerCount(die.notation)),
      )
    }
  }
}

/** The smallest thing worth pressing, and the picture inside it. */
private val TARGET = 48.dp
private val SILHOUETTE = 26.dp
