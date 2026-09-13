package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import de.drehtuer.dinfinity.render.filament.Tray

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
 */
@Composable
fun DiceTray(
  driver: Tray,
  modifier: Modifier = Modifier,
) {
  AndroidExternalSurface(modifier = modifier.testTag(RollTestTags.TRAY)) {
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

  // Leaving the screen gives up the thread, the engine and the physics world.
  // The roll does not survive it and is not meant to: a throw the player
  // walked away from never landed, so there is nothing to score.
  DisposableEffect(driver) {
    onDispose { driver.close() }
  }
}
