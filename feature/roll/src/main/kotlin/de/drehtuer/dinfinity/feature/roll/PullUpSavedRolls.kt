package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.SectionKicker

/**
 * The saved rolls, as a pull-up parked on the bottom edge
 * (`design/dInfinity.dc.html`, option 1c — "saved rolls behind a pull-up").
 *
 * It was the last plate standing across the bottom of the table, and the
 * second device session asked for the same thing of it that the first asked
 * of the picker and the result: **the whole table, by default**. With the
 * straight-down view every band over the felt is a place a die can land and
 * not be seen, and a strip of saved rolls is a band that is there in every
 * state of the screen, whether or not anybody wants a saved roll.
 *
 * So it is parked to start with, showing a grip and the word for what is
 * behind it, and a pull brings the rolls up. It is the same [PullUpSheet] the
 * result uses, with the same arithmetic under it — the only differences are
 * that this one does not arrive by itself, because it has nothing to
 * announce, and that it starts down.
 *
 * **It gives way to a result.** Two sheets on one edge may not both be up,
 * and which one yields is [BottomEdge]'s: a total is the thing that cannot be
 * got back without throwing the dice again, and the saved rolls are one pull
 * away for ever.
 *
 * @param rolls the strip itself, handed in already wired. This module does
 *   not know what a saved roll is (`docs/architecture.md`, "Modules").
 */
@Composable
internal fun PullUpSavedRolls(
  rest: SheetRest,
  onRest: (SheetRest) -> Unit,
  modifier: Modifier = Modifier,
  onParked: (Float) -> Unit = {},
  rolls: @Composable () -> Unit,
) {
  val up = rest == SheetRest.Up
  val name = stringResource(R.string.roll_saved_handle)
  val act = stringResource(if (up) R.string.roll_saved_push_down else R.string.roll_saved_pull_up)
  val where = stringResource(if (up) R.string.roll_saved_is_up else R.string.roll_saved_is_down)
  PullUpSheet(
    rest = rest,
    onRest = onRest,
    onParked = onParked,
    tag = RollTestTags.SAVED_PULL_UP,
    modifier = modifier,
    grip = { toggle ->
      Column(modifier = Modifier.fillMaxWidth()) {
        Rule()
        // The word is *in* the handle here, where the result's total is
        // deliberately outside it. A total is the answer to the roll and a tap
        // on it may not move anything; `SAVED ROLLS` is a label on a door, so
        // the whole of it is the door.
        SheetHandle(
          name = name,
          act = act,
          where = where,
          tag = RollTestTags.SAVED_HANDLE,
          onToggle = toggle,
        ) {
          SectionKicker(text = stringResource(R.string.roll_saved_kicker), color = Ink.muted)
        }
      }
    },
    body = { Column(modifier = Modifier.fillMaxWidth().padding(PLATE_EDGE)) { rolls() } },
  )
}

/** The inset the plates over the table use, so the strip lines up with them. */
private val PLATE_EDGE = 14.dp
