package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.simulation.api.TableGeometry

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
 */
@Composable
fun DiceTray(
  driver: Tray,
  geometry: TableGeometry,
  modifier: Modifier = Modifier,
  describing: String = "",
) {
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
          .lookAround(driver, geometry),
    ) {
      onSurface { surface, width, height ->
        driver.surfaceAvailable(surface, width, height)

        // A resize is a new stage, because Filament fixes its swap chain and
        // viewport when one is made. The roll being drawn does not notice: the
        // scene is rebuilt from what the simulation has already said
        // (`docs/architecture.md`, decision 49).
        surface.onChanged { changedWidth, changedHeight ->
          driver.surfaceAvailable(surface, changedWidth, changedHeight)
        }

        // Blocks until the engine has let go. A `Surface` may not be touched
        // after the callback that withdrew it has returned, and the roll thread
        // is drawing to this one.
        surface.onDestroyed { driver.surfaceLost() }
      }
    }
  }

  // Leaving the screen gives up the physics world and the scene. The roll does
  // not survive it and is not meant to: a throw the player walked away from
  // never landed, so there is nothing to score. The thread and the engine
  // underneath are not given up with them — rebuilding those is a black tray
  // on the way back (`docs/architecture.md`, decision 50).
  DisposableEffect(driver) {
    onDispose { driver.close() }
  }
}

/**
 * Pinch to look closer, drag to look elsewhere (`docs/TODO.md`, Step 4.1).
 *
 * The camera frames the whole tray and never moves off it on its own, so this
 * is the only thing that moves it. Where the view may go is [TrayView]'s to
 * say — it keeps the camera on the table — and all that happens here is
 * turning fingers into a ratio and two fractions of the screen.
 *
 * Fractions rather than pixels because the tray is measured in millimetres and
 * the screen in neither: a drag of half the width should move the view half a
 * screen's worth of table, whatever the phone's pixel density. Dragging *up*
 * the screen looks further up the tray, which is `+x` (`docs/tables.md`), and
 * the sign is flipped for each because dragging content moves it with the
 * finger while the camera goes the other way.
 *
 * One finger is left alone. It is reserved for picking a die up, and a tap on
 * the tray deliberately does not roll (`docs/physics-and-rendering.md`).
 */
private fun Modifier.lookAround(
  driver: Tray,
  geometry: TableGeometry,
): Modifier =
  this.pointerInput(driver, geometry) {
    var view = TrayView.Whole
    detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
      val width = size.width.toFloat()
      val height = size.height.toFloat()
      if (width <= 0f || height <= 0f) return@detectTransformGestures
      view =
        view.movedBy(
          by = zoom.toDouble(),
          alongFraction = -(pan.y / height).toDouble(),
          acrossFraction = -(pan.x / width).toDouble(),
          geometry = geometry,
        )
      driver.look(view)
    }
  }
