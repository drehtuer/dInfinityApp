package de.drehtuer.dinfinity.feature.roll

import android.hardware.SensorManager
import androidx.compose.runtime.Composable
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
 * The throw is made when the shake *ends*, driven by what the phone actually
 * did: the recorded session goes into the `ThrowSpec` and reaches the solver
 * as an inverse acceleration on gravity, which is what makes the dice slam
 * into the walls the way they do in a cupped hand. The refinement still to
 * come is spawning the dice when the shake *begins* so they tumble in the tray
 * while the player is still shaking (`docs/TODO.md`, Step 4.1).
 *
 * A shake with no valid formula behind it throws nothing; the presenter
 * refuses it for the same reason the button is disabled.
 */
@Composable
internal fun ShakeToRoll(presenter: RollPresenter) {
  val sensors = LocalContext.current.getSystemService(SensorManager::class.java)

  LifecycleResumeEffect(presenter, sensors) {
    val shakes =
      sensors?.let {
        SensorShakeSource(
          sensors = it,
          onEnded = { session -> presenter.roll(session.recorded()) },
        ).apply { start() }
      }
    onPauseOrDispose { shakes?.stop() }
  }
}
