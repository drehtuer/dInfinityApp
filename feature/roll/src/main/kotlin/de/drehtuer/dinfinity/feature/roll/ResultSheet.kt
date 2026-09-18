package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.FudgeTotal
import de.drehtuer.dinfinity.core.notation.NotationLimits
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.SegmentedControl
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
  /**
   * What the formula was expected to come to: the lowest, the highest and the
   * average ([Expectation]).
   *
   * Under the breakdown rather than beside the total, because it is context
   * and the total is the answer. A number with nothing to read it against is
   * the commonest complaint a dice roller gets — "is 14 good?" — and this is
   * the line that answers it. Null for a throw whose formula has since been
   * typed over.
   */
  expected: Expectation? = null,
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
  /**
   * The two things to do with the roll that has just landed, at the foot of
   * the breakdown (`design/dInfinity.dc.html`, options 7a and 3b).
   *
   * They are **here rather than in the grip**, and that is the difference
   * between the two halves of this sheet: the grip is what stays on the
   * bottom edge when the result is pushed away, so anything in it is a plate
   * over the felt for as long as a total lasts. Two buttons that cannot be
   * put down are two of the four the device session asked to be rid of.
   *
   * Both default to doing nothing, so a preview or a test of the breakdown
   * alone draws them without wiring anything.
   */
  onSeeTheOdds: () -> Unit = {},
  onSaveAsRoll: () -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxWidth().testTag(RollTestTags.SHEET),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = result.label?.let { "$it · ${result.formula}" } ?: result.formula,
      style = MaterialTheme.typography.labelLarge,
      color = Ink.muted,
      modifier = Modifier.testTag(RollTestTags.SHEET_FORMULA),
    )

    // One row per group rather than one per die: `3d6 + 1d20` is two things a
    // player asked for, and the dice under each are how it came out.
    //
    // Their subtotals are drawn unless the only one of them is the total
    // itself, which would be the roll's number printed twice ([Subtotals]).
    val subtotals = Subtotals.shownOn(result)
    result.groups.forEach { group -> GroupRow(group, subtotals, onDoodle) }

    // And a row per number the formula adds, so the rows on screen add up to
    // the total. Absent for a formula whose total cannot be read off them —
    // `(2d6 + 3) * 2` multiplies its three along with the dice, and listing it
    // as "+ 3" would be adding up to the wrong number (`RollResult.itemised`).
    result.adjustments.forEach { amount -> AdjustmentRow(amount) }

    // Offered only for a throw it could change. `Down`, `Nearest` and `Up` all
    // give the same answer to `3d6 + 4`.
    if (divides) RoundingControl(chosen = result.rounding, onRound = onRound)

    // And what it was expected to come to, so the total is a number in a
    // range rather than a number on its own.
    if (expected != null) Expected(expected)

    Doing(onSeeTheOdds = onSeeTheOdds, onSaveAsRoll = onSaveAsRoll)
  }
}

/**
 * What to do with the roll that has just landed.
 *
 * "See the odds" is the distribution behind it, with this throw's total
 * marked on it (`design/dInfinity.dc.html`, option 7a). "Save as roll" opens
 * the editor with the formula already typed, which is the same pair the
 * outcome graph offers at the foot of its own bars — the two screens are
 * about the same formula, so they end the same way.
 *
 * Neither does the thing itself: where those go is the navigation graph's,
 * and that belongs to `:app` (`docs/architecture.md`, "Modules").
 *
 * Both are on the sheet, which is an opaque surface of its own and keeps the
 * rule a plate keeps — accent never touches felt
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 */
@Composable
private fun Doing(
  onSeeTheOdds: () -> Unit,
  onSaveAsRoll: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    ModernistButton(
      text = stringResource(R.string.roll_see_the_odds),
      onClick = onSeeTheOdds,
      kind = ModernistButtonKind.Ghost,
      modifier = Modifier.testTag(RollTestTags.ODDS),
    )
    ModernistButton(
      text = stringResource(R.string.roll_save_as_roll),
      onClick = onSaveAsRoll,
      kind = ModernistButtonKind.Secondary,
      modifier = Modifier.testTag(RollTestTags.SAVE_AS_ROLL),
    )
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
      style = MaterialTheme.typography.labelLarge,
      color = Ink.muted,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = abs(amount).toString(),
      // The same 20 sp heading a group's subtotal is set in: they are the
      // numbers that add up to the total and they line up in one column.
      style = MaterialTheme.typography.titleLarge.tabular(),
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
 *
 * Drawn as the design system's **segmented control** (`.seg` / `.seg-opt`,
 * `ui/common`'s `SegmentedControl`): one bordered box, the options divided by
 * a hairline, the chosen one filled with the accent and printed in the ground
 * colour. It used to be three Material `FilterChip`s — separate pills with a
 * tick in front of the chosen one — which is a component this system does not
 * have, and which Material draws with a rounded corner whatever the theme's
 * `Shapes` say.
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
      color = Ink.muted,
    )
    SegmentedControl(
      options = Rounding.entries,
      selected = chosen,
      label = { rounding -> stringResource(rounding.label()) },
      onSelect = onRound,
      tagOf = { rounding -> RollTestTags.roundingOf(rounding) },
    )
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
  subtotal: Boolean,
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
        style = MaterialTheme.typography.labelLarge.tabular(),
        color = MaterialTheme.colorScheme.onBackground,
      )
      if (subtotal) {
        Text(
          text = FudgeTotal.writeRoll(group.subtotal, group.dice),
          style = MaterialTheme.typography.titleLarge.tabular(),
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.testTag(RollTestTags.subtotalOf(group.id)),
        )
      }
    }

    // A roll that could not have the set it asked for says so here rather than
    // quietly giving the player different dice (`docs/dice-sets.md`).
    if (group.fellBack) {
      Text(
        text = stringResource(R.string.roll_group_fell_back, group.setId),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
        modifier = Modifier.testTag(RollTestTags.fallbackOf(group.id)),
      )
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
    color = Ink.muted,
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
      style = MaterialTheme.typography.labelLarge.tabular(),
      color = reading.colour(),
      textAlign = TextAlign.Center,
      textDecoration = if (reading == DieReading.Dropped) TextDecoration.LineThrough else null,
      modifier =
        Modifier
          .testTag(RollTestTags.dieAt(die.instanceIndex))
          // A cell rather than a loose number: the design draws every die that
          // landed in a bordered square of its own, so a breakdown reads as
          // the dice on the table and not as a sentence of digits.
          .border(CHIP_RULE, MaterialTheme.colorScheme.outline)
          .defaultMinSize(minWidth = CHIP, minHeight = CHIP)
          .wrapContentSize(Alignment.Center)
          .padding(horizontal = CHIP_PADDING)
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
    DieReading.Dropped -> MaterialTheme.colorScheme.onBackground.copy(alpha = DROPPED)
    DieReading.NaturalMax -> MaterialTheme.colorScheme.primary
    DieReading.Kept -> MaterialTheme.colorScheme.onBackground
  }

/**
 * `font-variant-numeric: tabular-nums`.
 *
 * Every number on this sheet is in a column with other numbers, and figures
 * of different widths make those columns crawl as the dice change.
 */
internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

private const val TABULAR = "tnum"

/**
 * A die the formula threw away, at the opacity the design gives it. It is
 * struck through as well, and said in words to a screen reader — the colour is
 * the least of the three.
 */
private const val DROPPED = 0.45f

/** One die's cell: `min-width:26px; height:26px; padding:0 6px; border:1px`. */
private val CHIP = 26.dp
private val CHIP_PADDING = 6.dp
private val CHIP_RULE = 1.dp
