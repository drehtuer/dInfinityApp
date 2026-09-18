package de.drehtuer.dinfinity.feature.roll

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * What the dice came to, as a sheet that comes up from the bottom edge and can
 * be pushed back down (`design/dInfinity.dc.html`, options 1e–1g;
 * `docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * It used to be a plate in the column of controls: a band across the middle of
 * the tray that nothing could move, so a die that landed under it stayed under
 * it until the next throw. With the straight-down table view that is most of
 * the felt, which is why this exists (`docs/TODO.md`, Step 4.1).
 *
 * It carries exactly what it carried before — the total, [ResultSheet]'s
 * breakdown and the rounding control. **This is about where the result sits,
 * not about what it says.**
 *
 * Three rules it keeps:
 *
 * 1. It **arrives by itself** once the dice have been read, sliding up from
 *    below the bottom edge. Nobody should have to reach for the number they
 *    just rolled.
 * 2. It can be **pushed down** until the whole table is visible again, and
 *    pulled back up. Where it comes to rest is [SheetSlide]'s, which is plain
 *    Kotlin with its own tests; all that happens here is the gesture.
 * 3. It **never goes away** while the roll is on the screen. Pushed all the
 *    way down it still shows its grip and the total, so the number is readable
 *    and the sheet is grabbable. A result that could be dismissed would be a
 *    result nobody could get back without re-rolling, and a re-roll is the one
 *    act this app cannot undo.
 *
 * **What is dragged is the grip — the handle and the total together — and what
 * is tapped is the handle alone.** The bar is 4 dp of ink and a thumb is not,
 * so a target the size of the drawing would be a target nobody hits; the whole
 * band above the breakdown therefore takes the drag. The *tap* is narrower on
 * purpose: the total is the one number the screen exists to show, and a tap on
 * it moving the sheet would be a control nobody asked for sitting on top of
 * the result. So the handle is the button — [TOUCH_TARGET] tall, the width of
 * the sheet, announced and labelled — and the total is text.
 *
 * The drag is `Modifier.draggable` over an [Animatable] rather than
 * `AnchoredDraggableState`. That class would hold the same two anchors, but it
 * wants them in pixels at composition time, it is still experimental, and the
 * decisions worth testing — where the rests are, what a drag does, what a
 * flick settles to — would end up inside it where a JVM test cannot ask them.
 * They are in [SheetSlide] instead, and what is left here is small enough not
 * to need a state holder.
 *
 * @param onParked the height of the grip, in pixels, as it is measured. The
 *   screen pads its column of controls by it, so the Roll button is never
 *   under a sheet that has been pushed down.
 */
@Composable
internal fun PullUpResult(
  result: RollResult,
  modifier: Modifier = Modifier,
  divides: Boolean = false,
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
  // No keys on any of these: this composable is on the screen only while a
  // roll has settled, so entering composition *is* a new result and leaving it
  // is the roll being put away. A key on the result itself would miss the
  // second of two identical throws, which are equal values.
  var height by remember { mutableFloatStateOf(0f) }
  var parked by remember { mutableFloatStateOf(0f) }
  var rest by remember { mutableStateOf(SheetRest.Up) }
  val offset = remember { Animatable(0f) }
  val travel = SheetSlide.travelOf(height, parked)
  val scope = rememberCoroutineScope()

  // The arrival. It waits for the first measurement, because how far below the
  // bottom edge the sheet starts is its own height and nothing knows that
  // until it has been laid out once.
  //
  // The wait is inside the coroutine rather than in the effect's key. An
  // effect keyed on the measurement is **started before the measurement and
  // restarted after it**, so the slide began on the first run and was then
  // cancelled by the second — which left the sheet parked below the bottom
  // edge with no way back. Keyed on `Unit` it runs once and is never
  // interrupted.
  LaunchedEffect(Unit) {
    snapshotFlow { height }.first { it > 0f }
    offset.snapTo(height)
    offset.animateTo(SheetSlide.offsetOf(rest, SheetSlide.travelOf(height, parked)))
  }

  fun settle(to: SheetRest) {
    rest = to
    scope.launch { offset.animateTo(SheetSlide.offsetOf(to, travel)) }
  }

  val drag =
    rememberDraggableState { by ->
      scope.launch { offset.snapTo(SheetSlide.draggedTo(offset.value, by, travel)) }
    }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .offset { IntOffset(x = 0, y = offset.value.roundToInt()) }
        .onSizeChanged { height = it.height.toFloat() }
        // `Shadow.md` — what floats over a screen, rather than the `sm` a plate
        // standing on the table is lifted by. This one is not on the table: it
        // is in front of it, and the prototype gives it the heaviest shadow the
        // system has (`design/dInfinityPhone.dc.html`, the result sheet).
        .shadow(elevation = Modernist.Shadow.md, shape = Modernist.square)
        .background(MaterialTheme.colorScheme.background)
        .navigationBarsPadding()
        .testTag(RollTestTags.PULL_UP),
  ) {
    Grip(
      total = result.total,
      rest = rest,
      onToggle = { settle(rest.other()) },
      drag = drag,
      onLetGo = { velocity -> settle(SheetSlide.settledAt(offset.value, velocity, travel)) },
      modifier =
        Modifier.onSizeChanged {
          parked = it.height.toFloat()
          onParked(parked)
        },
    )
    ResultSheet(
      result = result,
      divides = divides,
      onRound = onRound,
      onDoodle = onDoodle,
      onSeeTheOdds = onSeeTheOdds,
      onSaveAsRoll = onSaveAsRoll,
      modifier = Modifier.padding(start = PLATE_EDGE, end = PLATE_EDGE, bottom = PLATE_EDGE),
    )
  }
}

/**
 * The band that stays on the screen in both rests: the rule, the handle and
 * the total.
 *
 * Its height is what [SheetSlide.travelOf] subtracts, so whatever is in here
 * is the promise the sheet makes about never disappearing. The total is in it
 * for exactly that reason — a grip with no number on it would be a sheet that
 * has to be opened to find out what was rolled.
 */
@Composable
private fun Grip(
  total: Long,
  rest: SheetRest,
  onToggle: () -> Unit,
  drag: DraggableState,
  onLetGo: suspend CoroutineScope.(Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      modifier
        .fillMaxWidth()
        .draggable(state = drag, orientation = Orientation.Vertical, onDragStopped = onLetGo),
  ) {
    // The prototype's `border-top: 2px solid var(--color-divider)`: the sheet's
    // own top edge, which is also what says where to put a thumb.
    Rule()
    Handle(rest = rest, onToggle = onToggle)
    Text(
      text = total.toString(),
      style = MaterialTheme.typography.displayLarge.tabular(),
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.padding(bottom = Modernist.x1).testTag(RollTestTags.TOTAL),
    )
  }
}

/**
 * The bar to pull, and the way to move the sheet without dragging anything.
 *
 * A tap toggles the two rests, so every state of the sheet is reachable from a
 * switch, a keyboard or TalkBack — a gesture is not an interface
 * (`docs/architecture.md`, "Accessibility"). It says which state it is in
 * rather than leaving that to the drawing: the bar looks the same up and down,
 * and a screen reader has nothing else to go on.
 */
@Composable
private fun Handle(
  rest: SheetRest,
  onToggle: () -> Unit,
) {
  val up = rest == SheetRest.Up
  val name = stringResource(R.string.roll_result_handle)
  val act = stringResource(if (up) R.string.roll_result_push_down else R.string.roll_result_pull_up)
  val where = stringResource(if (up) R.string.roll_result_is_up else R.string.roll_result_is_down)
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .fillMaxWidth()
        .height(TOUCH_TARGET)
        .clickable(onClickLabel = act, role = Role.Button, onClick = onToggle)
        .testTag(RollTestTags.RESULT_HANDLE)
        .semantics {
          contentDescription = name
          stateDescription = where
        },
  ) {
    Box(modifier = Modifier.size(width = BAR_WIDE, height = BAR_THICK).background(Ink.divider))
  }
}

/** The handle's bar. Wide enough to read as a thing to pull, and no taller than a rule. */
private val BAR_WIDE = 44.dp
private val BAR_THICK = 4.dp

/** The inset the plates over the table use, so the breakdown lines up with them. */
private val PLATE_EDGE = 14.dp
