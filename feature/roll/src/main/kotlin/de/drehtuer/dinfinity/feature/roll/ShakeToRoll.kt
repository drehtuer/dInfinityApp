package de.drehtuer.dinfinity.feature.roll

import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.drehtuer.dinfinity.input.shake.SensorShakeSource

/**
 * Shake to roll (`docs/physics-and-rendering.md`, "Shake input").
 *
 * Registered while the screen is resumed and let go when it is not — an
 * accelerometer left running behind a backgrounded app is a battery bill for
 * nothing.
 *
 * **The dice are spawned when the shake begins**, not when it ends. Every
 * moment after that reaches the roll while the dice are already in the air, so
 * what the player sees is dice answering their hand rather than dice thrown
 * once the hand has stopped. The samples carry the step they belong to, so the
 * same record replayed afterwards drives exactly the same roll.
 *
 * A shake with no valid formula behind it throws nothing; the presenter
 * refuses it for the same reason the button is disabled.
 *
 * The display's rotation is read per sample rather than captured once. The
 * tray is the screen however the screen is held, so which device axis runs up
 * the tray changes when the phone is turned — and it can be turned in the
 * middle of a shake (`docs/physics-and-rendering.md`, "Coordinates").
 */
@Composable
internal fun ShakeToRoll(presenter: RollPresenter) {
  val context = LocalContext.current
  val sensors = context.getSystemService(SensorManager::class.java)
  var shaking by remember { mutableStateOf(false) }

  // A hand around a phone that is being shaken is a hand on both edges of it.
  HoldTheEdges(shaking)

  LifecycleResumeEffect(presenter, sensors) {
    val shakes =
      sensors?.let {
        SensorShakeSource(
          sensors = it,
          onStarted = {
            shaking = true
            presenter.roll()
          },
          onEnded = { shaking = false },
          onSample = presenter::shaking,
          rotationDegrees = { context.display.rotation.asDegrees() },
        ).apply { start() }
      }
    onPauseOrDispose {
      shakes?.stop()
      // Leaving the screen mid-shake must not leave the edges claimed.
      shaking = false
    }
  }
}

/** `Surface.ROTATION_*` is an ordinal of quarter turns; the map wants degrees. */
private fun Int.asDegrees(): Int =
  when (this) {
    Surface.ROTATION_90 -> QUARTER
    Surface.ROTATION_180 -> HALF_TURN
    Surface.ROTATION_270 -> THREE_QUARTERS
    else -> 0
  }

private const val QUARTER = 90
private const val HALF_TURN = 180
private const val THREE_QUARTERS = 270
