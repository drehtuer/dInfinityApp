package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.notation.PickableDie
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.Plate
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET

/**
 * The dice, at the top of the screen, behind a pull-down
 * (`design/dInfinity.dc.html`, options 1h and 4a;
 * `docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * The picker used to be the third of four plates stacked along the bottom
 * edge, and on a phone with the straight-down table view that stack was most
 * of the felt — a die could land where nothing could be seen. The design has
 * always asked for the dice in a strip at the top rather than a block at the
 * bottom; what the device session added is that it should be **put away**
 * until it is wanted, which is what this is.
 *
 * Two things are in it, because they are one control: the dice a tap adds, and
 * which set they come from. A set chooser somewhere else would be a control
 * whose effect is only visible here.
 *
 * **Collapsed it is one plate with a word and a chevron on it**, which is the
 * smallest thing that can say "there are dice behind this" and still be worth
 * pressing. Opened it pushes the rest of the top of the screen down rather
 * than floating over it: a pull-down that covered the formula would hide the
 * one thing a tap on a die changes.
 *
 * **The head carries the count** of everything [counts] holds, so a menu that
 * is shut still says how many dice are in the throw. Nought prints nothing: a
 * badge reading zero is noise, which is the same rule the badge on a die
 * follows.
 */
@Composable
internal fun DiceMenu(
  dice: List<PickableDie>,
  counts: Map<PickableDie, Int>,
  sets: List<DiceSet>,
  pickingFrom: String,
  expanded: Boolean,
  onExpand: (Boolean) -> Unit,
  onAdd: (PickableDie) -> Unit,
  onRemove: (PickableDie) -> Unit,
  onChoose: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  // A menu with nothing behind it is a control that lies. It happens: a
  // catalogue with no sets in it is what a screen wired to nothing has.
  if (dice.isEmpty()) return
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    // Hugging, so the head is a tab at the top of the table rather than a
    // band across it. The panel under it fills, because a scrolling row of
    // dice wants every millimetre it can have.
    Plate { DiceMenuHead(expanded = expanded, count = counts.values.sum(), onToggle = { onExpand(!expanded) }) }
    if (expanded) {
      Plate(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Modernist.x2)) {
          PickerRow(dice = dice, counts = counts, onAdd = onAdd, onRemove = onRemove)
          // Inside the pull-down rather than under the row on the screen: it
          // is how the row is switched, and the device session read a
          // segmented control sitting under the dice as a tab bar that did
          // not belong to them.
          SetChooser(sets = sets, chosen = pickingFrom, onChoose = onChoose)
        }
      }
    }
  }
}

/**
 * The word and the chevron that open it.
 *
 * One node to a screen reader, carrying what it is, what a press will do and
 * whether it is open — the chevron is a drawing and says none of that
 * (`docs/architecture.md`, "Accessibility").
 */
@Composable
private fun DiceMenuHead(
  expanded: Boolean,
  count: Int,
  onToggle: () -> Unit,
) {
  val name = stringResource(R.string.roll_dice_menu)
  val act = stringResource(if (expanded) R.string.roll_dice_menu_close else R.string.roll_dice_menu_open)
  val where = stringResource(if (expanded) R.string.roll_dice_menu_is_open else R.string.roll_dice_menu_is_shut)
  val ink = MaterialTheme.colorScheme.onBackground
  Row(
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier
        .sizeIn(minHeight = TOUCH_TARGET)
        .clickable(onClickLabel = act, role = Role.Button, onClick = onToggle)
        .testTag(RollTestTags.DICE_MENU)
        .semantics(mergeDescendants = true) {
          contentDescription = if (count > 0) "$name, $count" else name
          stateDescription = where
        },
  ) {
    Text(
      text = name,
      // The heading face, like the formula opposite it: these two are the
      // titles of the table rather than captions on it.
      style = MaterialTheme.typography.titleLarge,
      color = ink,
    )
    if (count > 0) {
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier =
          Modifier
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = Modernist.x1)
            .testTag(RollTestTags.DICE_MENU_COUNT),
      )
    }
    Canvas(modifier = Modifier.size(CHEVRON)) { chevron(ink, down = !expanded) }
  }
}

/**
 * `#ic-down` / `#ic-up`, drawn rather than fetched.
 *
 * The prototype's `m6 9 6 6 6-6`, as a share of its box rather than as the
 * 24-unit viewBox it is written in — the same drawing at any size, and no
 * number here that is a pixel. Turned over for the open state, which is what
 * every disclosure in the prototype does.
 */
private fun DrawScope.chevron(
  ink: Color,
  down: Boolean,
) {
  val x = { share: Float -> size.width * share }
  val y = { share: Float -> if (down) size.height * share else size.height * (1f - share) }
  val path =
    Path().apply {
      moveTo(x(LEFT), y(SHOULDER))
      lineTo(x(MIDDLE), y(POINT))
      lineTo(x(RIGHT), y(SHOULDER))
    }
  drawPath(
    path = path,
    color = ink,
    style = Stroke(width = Modernist.rule.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

/** The prototype draws its disclosures at 18 px. */
private val CHEVRON = 18.dp

// `m6 9 6 6 6-6`, as shares of a 24-unit box.
private const val LEFT = 6f / 24f
private const val MIDDLE = 12f / 24f
private const val RIGHT = 18f / 24f
private const val SHOULDER = 9f / 24f
private const val POINT = 15f / 24f
