package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.FudgeTotal
import de.drehtuer.dinfinity.ui.common.Rule

/**
 * What the dice came to, as a sheet that comes up from the bottom edge and can
 * be pushed back down (`design/dInfinity.dc.html`, options 1e–1g;
 * `docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * It used to be a plate in the column of controls: a band across the middle of
 * the tray that nothing could move, so a die that landed under it stayed under
 * it until the next throw. With the straight-down table view that is most of
 * the felt, which is why this exists.
 *
 * The slide itself — the gesture, the two rests, the measuring — is
 * [PullUpSheet]'s, which the saved rolls use too; the arithmetic under that is
 * [SheetSlide]'s. **What is here is what a result's pull-up carries**, which
 * is a grip and a breakdown.
 *
 * Three rules it keeps:
 *
 * 1. It **arrives by itself** once the dice have been read, sliding up from
 *    below the bottom edge. Nobody should have to reach for the number they
 *    just rolled.
 * 2. It can be **pushed down** until the whole table is visible again, and
 *    pulled back up.
 * 3. It **never goes away** while the roll is on the screen. Pushed all the
 *    way down it still shows its grip — and so the total, and what the throw
 *    was expected to come to. A result that could be dismissed would be a
 *    result nobody could get back without re-rolling, and a re-roll is the one
 *    act this app cannot undo.
 *
 * **What is dragged is the grip — the handle, the total and the expected range
 * together — and what is tapped is the handle alone.** The bar is 4 dp of ink
 * and a thumb is not, so a target the size of the drawing would be a target
 * nobody hits; the whole band above the breakdown therefore takes the drag.
 * The *tap* is narrower on purpose: the total is the one number the screen
 * exists to show, and a tap on it moving the sheet would be a control nobody
 * asked for sitting on top of the result.
 *
 * @param rest where the sheet is now. Hoisted, because the screen has a second
 *   pull-up on the same edge and the rule about the pair of them belongs to
 *   neither ([BottomEdge]).
 * @param expected what the formula is expected to come to, **in the grip** and
 *   not in the body. It is what the next shake is worth, and the second device
 *   session could not read it: it was under the breakdown, so it went away
 *   with the breakdown and a player deciding whether to throw again saw it
 *   only as a flash between rolls ([Expectation]).
 * @param onParked the height of the grip, in pixels, as it is measured. The
 *   screen lifts everything above the sheet by it, so nothing is left under a
 *   sheet that has been pushed down.
 */
@Composable
internal fun PullUpResult(
  result: RollResult,
  modifier: Modifier = Modifier,
  rest: SheetRest = SheetRest.Up,
  onRest: (SheetRest) -> Unit = {},
  divides: Boolean = false,
  expected: Expectation? = null,
  onRound: (Rounding) -> Unit = {},
  onDoodle: (String) -> Unit = {},
  /**
   * The two ways on from a result, drawn at the foot of the breakdown by
   * [ResultSheet] — so they go down with it rather than standing on the felt
   * for as long as a total does.
   */
  onSeeTheOdds: () -> Unit = {},
  onSaveAsRoll: () -> Unit = {},
  onParked: (Float) -> Unit = {},
) {
  PullUpSheet(
    rest = rest,
    onRest = onRest,
    arrives = true,
    onParked = onParked,
    tag = RollTestTags.PULL_UP,
    modifier = modifier,
    grip = { toggle ->
      Grip(
        written = FudgeTotal.writeRoll(result.total, result.groups.flatMap { it.dice }),
        rest = rest,
        expected = expected,
        onToggle = toggle,
      )
    },
    body = {
      ResultSheet(
        result = result,
        divides = divides,
        onRound = onRound,
        onDoodle = onDoodle,
        onSeeTheOdds = onSeeTheOdds,
        onSaveAsRoll = onSaveAsRoll,
        modifier = Modifier.padding(start = PLATE_EDGE, end = PLATE_EDGE, bottom = PLATE_EDGE),
      )
    },
  )
}

/**
 * The band that stays on the screen in both rests: the rule, the handle, the
 * total and what the throw was expected to come to.
 *
 * Its height is what [SheetSlide.travelOf] subtracts, so whatever is in here
 * is the promise the sheet makes about never disappearing. The total is in it
 * because a grip with no number on it would be a sheet that has to be opened
 * to find out what was rolled; the expected range is in it because a player
 * deciding whether to shake again wants the total and the shape of the throw
 * side by side, and under the breakdown it was neither.
 */
@Composable
private fun Grip(
  written: String,
  rest: SheetRest,
  expected: Expectation?,
  onToggle: () -> Unit,
) {
  val where = stringResource(if (rest == SheetRest.Up) R.string.roll_result_is_up else R.string.roll_result_is_down)
  val act =
    stringResource(
      if (rest == SheetRest.Up) R.string.roll_result_push_down else R.string.roll_result_pull_up,
    )
  Column(modifier = Modifier.fillMaxWidth()) {
    // The prototype's `border-top: 2px solid var(--color-divider)`: the sheet's
    // own top edge, which is also what says where to put a thumb.
    Rule()
    SheetHandle(
      name = stringResource(R.string.roll_result_handle),
      act = act,
      where = where,
      tag = RollTestTags.RESULT_HANDLE,
      onToggle = onToggle,
    )
    Row(
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(PLATE_EDGE),
      modifier = Modifier.fillMaxWidth().padding(start = PLATE_EDGE, end = PLATE_EDGE, bottom = BELOW_THE_TOTAL),
    ) {
      Text(
        text = written,
        style = MaterialTheme.typography.displayLarge.tabular(),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag(RollTestTags.TOTAL),
      )
      Box(modifier = Modifier.weight(1f))
      if (expected != null) Expected(expected)
    }
  }
}

/** `--space-1`, which is the gap under a line of display type. */
private val BELOW_THE_TOTAL = 4.dp

/** The inset the plates over the table use, so the breakdown lines up with them. */
private val PLATE_EDGE = 14.dp
