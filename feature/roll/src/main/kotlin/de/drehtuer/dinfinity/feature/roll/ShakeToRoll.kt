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
 * **The dice are spawned when the shake begins**, not when it ends. Every
 * moment after that reaches the roll while the dice are already in the air, so
 * what the player sees is dice answering their hand rather than dice thrown
 * once the hand has stopped. The samples carry the step they belong to, so the
 * same record replayed afterwards drives exactly the same roll.
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
          onStarted = { presenter.roll() },
          onSample = presenter::shaking,
        ).apply { start() }
      }
    onPauseOrDispose { shakes?.stop() }
  }
}
