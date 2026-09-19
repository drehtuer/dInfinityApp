package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.drehtuer.dinfinity.render.filament.ScreenSpot
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import kotlinx.coroutines.awaitCancellation

/**
 * The tray, drawn by the roll thread onto a surface of its own
 * (`design/dInfinity.dc.html`, option 1b).
 *
 * `AndroidExternalSurface` rather than an `AndroidView` around a
 * `SurfaceView`: it hands over a `Surface` and its size and takes them back
 * again, which is the whole of what [Tray] wants, and Compose keeps the
 * surface in the right place in the z-order so the formula field and the
 * result sheet draw *over* the dice rather than behind them. A bare
 * `SurfaceView` punches a hole through the window and everything above it has
 * to be argued with.
 *
 * Nothing is drawn from here. This composable's whole job is to say "there is
 * somewhere to draw, this big" and, later, "there is not" — the roll thread
 * does the rest, and carries on rolling through both
 * (`docs/physics-and-rendering.md`).
 *
 * @param describing what is on the tray, for a screen reader. There is nothing
 *   in the view hierarchy under a `Surface`, so without this the app's home
 *   screen is a rectangle with nothing in it at all. What the words are is
 *   [TrayReading]'s (`docs/architecture.md`, "Accessibility").
 * @param view where the player is looking. Held by the caller rather than
 *   here, because the camera is put back to the whole tray when a throw
 *   starts and this composable is not what knows a throw has started: a view
 *   remembered inside the gesture goes stale the moment the dice are thrown,
 *   and the first touch afterwards snaps the camera back to a zoom the player
 *   left behind ([RollPresenter.looking]).
 * @param onLook the player moved the camera. The caller tells the tray, for
 *   the same reason: one thing owns where the camera is pointed.
 */
@Composable
fun DiceTray(
  driver: Tray,
  geometry: TableGeometry,
  modifier: Modifier = Modifier,
  describing: String = "",
  view: TrayView = TrayView.Whole,
  onLook: (TrayView) -> Unit = {},
) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle

  // Read inside the gesture rather than captured by it. `pointerInput` keeps
  // its block alive across recompositions, so a captured `view` would be the
  // one the gesture was started with for ever; keying the input on the view
  // instead would tear the gesture down and rebuild it on every frame of a
  // drag, which is worse.
  val looking = rememberUpdatedState(view)
  val told = rememberUpdatedState(onLook)

  // Keyed on the driver so that a new one gets a surface of its own. `onSurface`
  // fires when the surface is *created*, not when this composable's arguments
  // change, so a driver swapped in afterwards would otherwise never be told
  // there is anywhere to draw — a silently black tray with the dice rolling on
  // it. Swapping one mid-visit is a bug in the caller, and this is what stops
  // that bug being invisible.
  key(driver) {
    AndroidExternalSurface(
      modifier =
        modifier
          .testTag(RollTestTags.TRAY)
          .semantics { contentDescription = describing }
          .lookAround(geometry, looking, told),
    ) {
      onSurface { surface, width, height ->
        // The size as it stands, because the stage is handed over again every
        // time the screen comes back and a resize may have happened in between.
        var size = width to height

        // A resize is a new stage, because Filament fixes its swap chain and
        // viewport when one is made. The roll being drawn does not notice: the
        // scene is rebuilt from what the simulation has already said
        // (`docs/architecture.md`, decision 49).
        surface.onChanged { changedWidth, changedHeight ->
          size = changedWidth to changedHeight
          driver.surfaceAvailable(surface, changedWidth, changedHeight)
        }

        // Blocks until the engine has let go. A `Surface` may not be touched
        // after the callback that withdrew it has returned, and the roll thread
        // is drawing to this one.
        surface.onDestroyed { driver.surfaceLost() }

        // And the stage is held only while the screen is one somebody could be
        // looking at.
        //
        // `onDestroyed` is not enough on its own, which is what the lock screen
        // showed: locking the Pixel 10a stops the screen without always taking
        // the surface away, so nothing fired, the old stage stayed, and the
        // roll thread went on drawing frames at a display that was off
        // (`docs/TODO.md`, Step 4.1). Nothing above this composable was
        // watching — the whole app had one lifecycle observer and it was the
        // accelerometer's.
        //
        // STARTED rather than RESUMED: a dialog over the tray pauses without
        // hiding it, and giving the surface up for that would black the tray
        // behind the sheet the player just opened. STARTED is "not on screen at
        // all", which is the question being asked.
        //
        // The roll is not stopped with it. A roll asks for frames whether or
        // not there is anywhere to draw, because the frame callback is what
        // steps it and a roll that stops being asked is a roll that never lands
        // (`docs/physics-and-rendering.md`).
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
          val (wide, high) = size
          // Not after the surface has been withdrawn: `onDestroyed` may have
          // fired on the way down, and a `Surface` is not to be touched again
          // once it has.
          if (surface.isValid) driver.surfaceAvailable(surface, wide, high)
          try {
            awaitCancellation()
          } finally {
            driver.surfaceLost()
          }
        }
      }
    }
  }

  // Giving the tray up on the way out is deliberately *not* here: this
  // composable is only on the screen when there is something to draw, and a
  // power-saving tray draws nothing, so a tray closed from here is a tray
  // closed only some of the time. [RollScreen] does it for every tray it has.
}

/**
 * Pinch to look closer, **two fingers** to look elsewhere
 * (`docs/physics-and-rendering.md`, "Rendering").
 *
 * The camera frames the whole tray and never moves off it on its own, so this
 * is the only thing that moves it. Where the view may go is [TrayView]'s to
 * say — it keeps the camera on the table — and all that happens here is
 * turning fingers into a ratio, two fractions of the screen and the spot they
 * are gathered at.
 *
 * Fractions rather than pixels because the tray is measured in millimetres and
 * the screen in neither: a drag of half the width should move the view half a
 * screen's worth of table, whatever the phone's pixel density. Dragging *up*
 * the screen looks further up the tray, which is `+x` (`docs/tables.md`), and
 * the sign is flipped for each because dragging content moves it with the
 * finger while the camera goes the other way. The centroid is flipped the same
 * way and for the same reason — it is a place on the same two axes.
 *
 * **One finger moves nothing and consumes nothing.** That is why this is a
 * pointer loop rather than `detectTransformGestures`, which reports a pan for
 * a single pointer and so panned the camera with one finger — the thing the
 * phone complained about. The first down is awaited without requiring it
 * unconsumed and is then simply watched: until a second finger arrives
 * nothing is read off the event and no change is consumed, so the gesture is
 * still there for whatever picks a die up.
 *
 * The event where the second finger lands is skipped too. `calculatePan` and
 * `calculateZoom` compare this event's pointers with where those same pointers
 * were, and a pointer that has only just appeared did not come from anywhere
 * — reading it is a jump at the start of every pinch.
 *
 * One finger is reserved for picking a die up, and a tap on the tray
 * deliberately does not roll. The arithmetic under that gesture is built and
 * tested — `TrayPick` says which die a finger is on and `PickUp` says which
 * dice a hand may go near — and it is still unspent here, because what a
 * re-throw does to the *record* of a roll is a decision nobody has taken
 * (`docs/TODO.md`, "Open questions"). Wiring it to something else in the
 * meantime would spend the only gesture the tray has left on whatever came
 * along first.
 */
private fun Modifier.lookAround(
  geometry: TableGeometry,
  view: State<TrayView>,
  onLook: State<(TrayView) -> Unit>,
): Modifier =
  this.pointerInput(geometry) {
    awaitEachGesture {
      // Not `requireUnconsumed`: a first finger somebody else is already
      // handling is still the first finger of a two-finger gesture.
      awaitFirstDown(requireUnconsumed = false)
      var wereDown = 1
      var down: Int
      do {
        val event = awaitPointerEvent()
        down = event.changes.count { it.pressed }
        if (down >= 2 && wereDown >= 2) {
          moveTheCamera(event, geometry, view.value, onLook.value)
          event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
        wereDown = down
      } while (down > 0)
    }
  }

/**
 * One event of a two-finger gesture, turned into a view and reported.
 *
 * Its own function because it is the arithmetic and the loop above is the
 * bookkeeping, and because everything it decides — how far a drag moves the
 * table, what a pinch does about the point it is pinched at — belongs to
 * [TrayView] and is tested there on a JVM rather than here on a phone.
 */
private fun PointerInputScope.moveTheCamera(
  event: PointerEvent,
  geometry: TableGeometry,
  from: TrayView,
  onLook: (TrayView) -> Unit,
) {
  val width = size.width.toFloat()
  val height = size.height.toFloat()
  if (width <= 0f || height <= 0f) return
  val pan = event.calculatePan()
  val centroid = event.calculateCentroid(useCurrent = true)
  if (centroid == Offset.Unspecified) return
  onLook(
    from.movedBy(
      by = event.calculateZoom().toDouble(),
      // **The table follows the finger.** Tray `+x` is screen-up and `+y` is
      // screen-left (`TrayCamera`), so moving the camera's target *with* the
      // drag is what makes the felt come with it; negating it moved the table
      // the other way, which is what the second device session reported as
      // the drag being inverted. Pinching is anchored the other way round on
      // purpose — see `about` below — because that is a place on the screen
      // rather than a movement of one.
      alongFraction = (pan.y / height).toDouble(),
      acrossFraction = (pan.x / width).toDouble(),
      geometry = geometry,
      about =
        ScreenSpot(
          alongFraction = -((centroid.y - height / 2f) / height).toDouble(),
          acrossFraction = -((centroid.x - width / 2f) / width).toDouble(),
        ),
    ),
  )
}
