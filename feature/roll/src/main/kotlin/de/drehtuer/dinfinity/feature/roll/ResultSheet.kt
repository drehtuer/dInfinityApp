package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup

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
    result.groups.forEach { group -> GroupRow(group) }
  }
}

@Composable
private fun GroupRow(group: RolledGroup) {
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
      group.dice.forEach { die -> DieChip(die) }
    }
  }
}

/**
 * One die as it landed.
 *
 * Three states and no more: kept, kept and showing its highest face, or
 * dropped. Anything else a die has to say about itself is a note in the
 * breakdown, not a colour nobody can decode.
 */
@Composable
private fun DieChip(die: RolledDie) {
  Text(
    text = die.label,
    style = MaterialTheme.typography.bodyMedium,
    color = die.colour(),
    textDecoration = if (die.kept) null else TextDecoration.LineThrough,
    modifier = Modifier.testTag(RollTestTags.dieAt(die.instanceIndex)),
  )
}

@Composable
private fun RolledDie.colour(): Color =
  when {
    !kept -> MaterialTheme.colorScheme.onSurfaceVariant
    naturalMax -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onBackground
  }
