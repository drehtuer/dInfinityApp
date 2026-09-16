package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.NotationLimits
import kotlin.math.abs

/**
 * What the dice came to, in full (`design/dInfinity.dc.html`, option 1f).
 *
 * The rule the whole sheet follows: **nobody should have to add anything up,
 * and nothing should have to be taken on trust.** So every die that landed is
 * here, each group's subtotal is here, and the arithmetic between them is the
 * formula, shown as it was typed.
 *
 * A dropped die is struck through rather than removed. A player who wrote
 * `4d6dl1` wants to see the 1 it threw away — hiding it would leave four dice
 * on the table and three in the breakdown, which reads as the app having lost
 * one (`docs/dice-notation.md`).
 *
 * The accent is spent on exactly one thing: a die that showed its highest
 * face. It is the one number in a breakdown anybody scans for.
 */
@Composable
internal fun ResultSheet(
  result: RollResult,
  modifier: Modifier = Modifier,
  divides: Boolean = false,
  onRound: (Rounding) -> Unit = {},
  /**
   * Quick mode: the die under a long press, to be drawn on
   * (`docs/face-designer.md`, "Quick mode").
   *
   * The id of the die rather than the die, because this module has no idea
   * what a dice set holds — which of them is installed and what it looks like
   * is the designer's question and `:app`'s to answer.
   */
  onDoodle: (String) -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxWidth().testTag(RollTestTags.SHEET),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = result.label?.let { "$it · ${result.formula}" } ?: result.formula,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag(RollTestTags.SHEET_FORMULA),
    )

    // One row per group rather than one per die: `3d6 + 1d20` is two things a
    // player asked for, and the dice under each are how it came out.
    result.groups.forEach { group -> GroupRow(group, onDoodle) }

    // And a row per number the formula adds, so the rows on screen add up to
    // the total. Absent for a formula whose total cannot be read off them —
    // `(2d6 + 3) * 2` multiplies its three along with the dice, and listing it
    // as "+ 3" would be adding up to the wrong number (`RollResult.itemised`).
    result.adjustments.forEach { amount -> AdjustmentRow(amount) }

    // Offered only for a throw it could change. `Down`, `Nearest` and `Up` all
    // give the same answer to `3d6 + 4`.
    if (divides) RoundingControl(chosen = result.rounding, onRound = onRound)
  }
}

/**
 * One number the formula adds or takes away
 * (`docs/dice-notation.md`, "Evaluation", step 7).
 *
 * Its own row beside the groups because it is a thing the player wrote and a
 * thing that changed the total, which is the same claim every other row on
 * this sheet makes. Before it existed the modifier was visible only in the
 * formula line at the top, so a breakdown of `3d6 + 4` showed rows adding to
 * eleven under a total of fifteen.
 */
@Composable
private fun AdjustmentRow(amount: Long) {
  Row(
    modifier = Modifier.fillMaxWidth().testTag(RollTestTags.adjustmentOf(amount)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(if (amount < 0) R.string.roll_sheet_minus else R.string.roll_sheet_plus),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = abs(amount).toString(),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

/**
 * Down, Nearest or Up, **for this throw only**
 * (`design/dInfinity.dc.html`, option 6d).
 *
 * The dice do not move and are never thrown again. Changing this redoes the
 * arithmetic around subtotals that are already recorded, which is the only
 * honest way to offer it at all: a control that re-rolled to get a different
 * answer would be the app choosing the number (`docs/dice-notation.md`,
 * "Division rounding").
 *
 * Nor is the choice remembered. It belongs to the throw in front of the
 * player; the next roll uses the setting again.
 */
@Composable
private fun RoundingControl(
  chosen: Rounding,
  onRound: (Rounding) -> Unit,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.testTag(RollTestTags.ROUNDING),
  ) {
    Text(
      text = stringResource(R.string.roll_rounding_label),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Rounding.entries.forEach { rounding ->
      FilterChip(
        selected = rounding == chosen,
        onClick = { onRound(rounding) },
        label = { Text(stringResource(rounding.label())) },
        modifier = Modifier.testTag(RollTestTags.roundingOf(rounding)),
      )
    }
  }
}

private fun Rounding.label(): Int =
  when (this) {
    Rounding.Down -> R.string.roll_rounding_down
    Rounding.Nearest -> R.string.roll_rounding_nearest
    Rounding.Up -> R.string.roll_rounding_up
  }

@Composable
private fun GroupRow(
  group: RolledGroup,
  onDoodle: (String) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = group.notation,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text = group.subtotal.toString(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag(RollTestTags.subtotalOf(group.id)),
      )
    }

    // A roll that could not have the set it asked for says so here rather than
    // quietly giving the player different dice (`docs/dice-sets.md`).
    if (group.fellBack) {
      Text(
        text = stringResource(R.string.roll_group_fell_back, group.setId),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(RollTestTags.fallbackOf(group.id)),
      )
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      group.dice.forEach { die -> DieChip(die, onDoodle) }
    }

    // And why there are not more of them. Everything else a die has to say is
    // already on the row above; these two are about a die that never arrived.
    ChainLimit.of(group).forEach { limit -> ChainLimitLine(group.id, limit) }
  }
}

/**
 * Why this group stopped throwing dice (`docs/dice-notation.md`, "Limits").
 *
 * The sheet's promise is that nothing has to be taken on trust, and an
 * explosion that stopped is the one thing the dice themselves cannot show: a
 * chain that ran out of depth and a chain that ran out of table both look, on
 * the row above, like an explosion that never happened. So they get a line.
 *
 * It sits with the fell-back line rather than on a chip, because the question
 * a player is asking is about the group they wrote and not about which of the
 * eight dice happened to be the one that hit the limit.
 */
@Composable
private fun ChainLimitLine(
  groupId: Int,
  limit: ChainLimit,
) {
  Text(
    text =
      when (limit) {
        ChainLimit.ExplosionDepth ->
          pluralStringResource(
            R.plurals.roll_group_explosion_limit,
            NotationLimits.MAX_EXPLOSION_DEPTH,
            NotationLimits.MAX_EXPLOSION_DEPTH,
          )
        ChainLimit.TrayFull -> stringResource(R.string.roll_group_tray_full)
      },
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.testTag(RollTestTags.chainLimitOf(groupId, limit.name)),
  )
}

/**
 * One die as it landed, and the way to draw on it
 * (`docs/face-designer.md`, "Quick mode").
 *
 * Three states and no more: kept, kept and showing its highest face, or
 * dropped. Anything else a die has to say about itself is a note in the
 * breakdown, not a colour nobody can decode.
 *
 * **A long press here rather than on the picker row**, although the picker is
 * where a player picks dice: a long press there already takes one off the
 * formula, and it is a fast, repeated edit that a menu would slow down for the
 * sake of something a player does once a month. What a long press offers is
 * therefore per *die that landed* — the one in front of them, which is when
 * "this d6 is boring" is thought — and the picker keeps its gesture whole.
 *
 * The press **offers** rather than navigates. Leaving the screen on a gesture
 * nothing announced would be a tray that vanishes when a finger rests on it,
 * and the menu is also what tells anybody the shortcut is there.
 *
 * Which of the three states it is in is [DieReading]'s, and both the colour and
 * the words come from that one answer. The strike and the accent are marks only
 * an eye can read, so the same fact is said in the label a screen reader gets —
 * "18, highest face", "1, dropped" (`docs/architecture.md`, "Accessibility").
 */
@Composable
private fun DieChip(
  die: RolledDie,
  onDoodle: (String) -> Unit,
) {
  var offered by remember(die.instanceIndex) { mutableStateOf(false) }
  val doodle = stringResource(R.string.roll_die_doodle)
  val reading = DieReading.of(die)
  val spoken = reading.said?.let { stringResource(it, die.label) }
  Box {
    Text(
      text = die.label,
      style = MaterialTheme.typography.bodyMedium,
      color = reading.colour(),
      textDecoration = if (reading == DieReading.Dropped) TextDecoration.LineThrough else null,
      modifier =
        Modifier
          .testTag(RollTestTags.dieAt(die.instanceIndex))
          // A gesture rather than `combinedClickable`, which would make every
          // number in the breakdown a button whose tap does nothing — and
          // announce it as one. The long press is spelled out for TalkBack
          // beside it, which is the half of it a gesture detector cannot say.
          .pointerInput(die.instanceIndex) { detectTapGestures(onLongPress = { offered = true }) }
          .semantics {
            // Only said where there is something the drawing does not say. A
            // die that reads "7, ordinary" on every row is noise, and noise is
            // what makes somebody turn the reader off.
            spoken?.let { said -> contentDescription = said }
            onLongClick(label = doodle) {
              offered = true
              true
            }
          },
    )
    DropdownMenu(expanded = offered, onDismissRequest = { offered = false }) {
      DropdownMenuItem(
        text = { Text(doodle) },
        onClick = {
          offered = false
          onDoodle(die.dieId)
        },
        modifier = Modifier.testTag(RollTestTags.doodleOf(die.instanceIndex)),
      )
    }
  }
}

@Composable
private fun DieReading.colour(): Color =
  when (this) {
    DieReading.Dropped -> MaterialTheme.colorScheme.onSurfaceVariant
    DieReading.NaturalMax -> MaterialTheme.colorScheme.primary
    DieReading.Kept -> MaterialTheme.colorScheme.onBackground
  }
