package de.drehtuer.dinfinity.feature.roll

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A panel that comes up from the bottom edge and can be pushed back down
 * (`design/dInfinity.dc.html`, options 1c and 1e–1g;
 * `docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * **There are two of these on the roll screen** — the result ([PullUpResult])
 * and the saved rolls ([PullUpSavedRolls]) — so the gesture, the measuring and
 * the animation live here once rather than twice. What is *left* of a pull-up
 * once those are taken out is a grip and a body, which is what the two slots
 * are.
 *
 * Where it may rest, how far it can travel, what a drag does to that and what
 * a flick settles to are [SheetSlide]'s: plain Kotlin with its own JVM tests.
 * Nothing here decides any of it.
 *
 * **Which rest it is at is hoisted**, because the two sheets on the roll
 * screen share one edge and may not both be up — which is a rule about the
 * pair of them and so cannot live inside either ([BottomEdge]).
 *
 * @param tag what a test reaches the panel by. A parameter rather than
 *   something the caller puts on [modifier], because the tag has to sit
 *   *inside* `Modifier.offset` to move with the sheet: a semantics node
 *   outside it reports the place the sheet would be if it had never slid.
 * @param arrives true for a panel that slides up from below the bottom edge
 *   the first time it is drawn, which is what a result does: nobody should
 *   have to reach for the number they have just rolled. False for one that is
 *   simply *there*, at whatever rest it was given — the saved rolls are on the
 *   screen from the moment it opens, and a strip that slid in every time the
 *   screen was built would be a strip that announces itself once a visit.
 * @param onParked the measured height of the grip, in pixels. Whatever is
 *   stacked above this sheet is lifted by it, so nothing ends up under a
 *   panel that has been pushed down.
 * @param grip the band that never leaves the screen. It is handed the toggle
 *   its handle should call; the drag is put on it here, because the bar is
 *   4 dp of ink and a thumb is not.
 * @param body what is revealed by pulling it up.
 */
@Composable
internal fun PullUpSheet(
  rest: SheetRest,
  onRest: (SheetRest) -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
  arrives: Boolean = false,
  onParked: (Float) -> Unit = {},
  grip: @Composable (toggle: () -> Unit) -> Unit,
  body: @Composable () -> Unit,
) {
  var height by remember { mutableFloatStateOf(0f) }
  var parked by remember { mutableFloatStateOf(0f) }
  // Whether it has been laid out once. Until it has, how far below the bottom
  // edge the panel starts is unknown — it is the panel's own height — so it is
  // not drawn at all rather than drawn in the wrong place for a frame.
  var laidOut by remember { mutableStateOf(false) }
  // Bumped by every let-go and every tap, so that a drag which ends where it
  // started still springs back: [rest] has not changed, and without this there
  // would be nothing for the mover to notice.
  var settling by remember { mutableIntStateOf(0) }
  // Read by the mover and deliberately not a key of it: "has it been put
  // somewhere once" is not a reason to move it again.
  val placed = remember { mutableStateOf(false) }
  val offset = remember { Animatable(0f) }
  val travel = SheetSlide.travelOf(height, parked)
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    snapshotFlow { height }.first { it > 0f }
    laidOut = true
  }

  LaunchedEffect(laidOut, rest, travel, settling) {
    if (!laidOut) return@LaunchedEffect
    val to = SheetSlide.offsetOf(rest, travel)
    when {
      placed.value -> offset.animateTo(to)
      arrives -> {
        placed.value = true
        offset.snapTo(height)
        offset.animateTo(to)
      }
      else -> {
        placed.value = true
        offset.snapTo(to)
      }
    }
  }

  fun settle(to: SheetRest) {
    settling++
    onRest(to)
  }

  // A drag is not a coroutine and an `Animatable` can only be written from
  // one, so the finger's delta is put through [SheetSlide.draggedTo] — which
  // is where the clamp to the two rests lives — and snapped to.
  val drag =
    rememberDraggableState { by ->
      scope.launch { offset.snapTo(SheetSlide.draggedTo(offset.value, by, travel)) }
    }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .offset { IntOffset(x = 0, y = offset.value.roundToInt()) }
        .testTag(tag)
        .onSizeChanged { height = it.height.toFloat() }
        .alpha(if (laidOut) 1f else 0f)
        // `Shadow.md` — what floats over a screen, rather than the `sm` a plate
        // standing on the table is lifted by. This is not on the table: it is
        // in front of it (`design/dInfinityPhone.dc.html`).
        .shadow(elevation = Modernist.Shadow.md, shape = Modernist.square)
        .background(MaterialTheme.colorScheme.background)
        .navigationBarsPadding(),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .draggable(
            state = drag,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity -> settle(SheetSlide.settledAt(offset.value, velocity, travel)) },
          ).onSizeChanged {
            parked = it.height.toFloat()
            onParked(parked)
          },
    ) {
      grip { settle(rest.other()) }
    }
    body()
  }
}

/**
 * The bar to pull, and the way to move a sheet without dragging anything.
 *
 * A tap toggles the two rests, so every state of every pull-up is reachable
 * from a switch, a keyboard or TalkBack — a gesture is not an interface
 * (`docs/architecture.md`, "Accessibility"). It says what it is, what a tap
 * will do and which rest it is in, because the bar looks identical up and
 * down and a screen reader has nothing else to go on.
 */
@Composable
internal fun SheetHandle(
  name: String,
  act: String,
  where: String,
  tag: String,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit = {},
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = TOUCH_TARGET)
        .clickable(onClickLabel = act, role = Role.Button, onClick = onToggle)
        .testTag(tag)
        .semantics(mergeDescendants = true) {
          contentDescription = name
          stateDescription = where
        },
  ) {
    Box(modifier = Modifier.height(GRAB).fillMaxWidth(), contentAlignment = Alignment.Center) {
      Box(modifier = Modifier.size(width = BAR_WIDE, height = BAR_THICK).background(Ink.divider))
    }
    content()
  }
}

/** How much of the handle is the bar and the space around it, before any words. */
private val GRAB = 22.dp

/** The handle's bar. Wide enough to read as a thing to pull, and no taller than a rule. */
private val BAR_WIDE = 44.dp
private val BAR_THICK = 4.dp
