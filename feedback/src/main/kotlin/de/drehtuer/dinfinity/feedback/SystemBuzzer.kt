package de.drehtuer.dinfinity.feedback

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The phone's own actuator (`docs/physics-and-rendering.md`, "Haptics and
 * sound").
 *
 * It decides nothing — [HapticTick] already said how long and how hard — and it
 * is the only file in this module that names a vibration API. What it does
 * carry is the three ways a phone can disagree with what it is asked for, and
 * all three are answered by doing less rather than by failing:
 *
 * - **No vibrator at all.** A tablet, an emulator, a phone with the motor
 *   dead: `hasVibrator` is false and every tick after that is a no-op. Nothing
 *   is thrown and nothing is logged on every impact.
 * - **No amplitude control.** Older or cheaper actuators are on or off. The
 *   strength of the impact then cannot be felt, so the system's own
 *   `EFFECT_TICK` is used instead of a one-shot at an amplitude the phone
 *   would round to "on" anyway — it is tuned by the manufacturer for exactly
 *   this.
 * - **The player has haptics turned off in Android's settings.** That is not
 *   ours to override and is not checked here either: the effects go out under
 *   `VibrationAttributes.USAGE_TOUCH`, which is the usage the system's own
 *   touch-feedback setting governs, so a player who turned haptic feedback off
 *   in their phone gets none from this app without the app having to ask.
 */
class SystemBuzzer(
  context: Context,
) : Buzzer {
  private val vibrator: Vibrator? =
    context
      .getSystemService(VibratorManager::class.java)
      ?.defaultVibrator
      ?.takeIf { it.hasVibrator() }

  private val byAmplitude: Boolean = vibrator?.hasAmplitudeControl() == true

  override fun tick(tick: Tick) {
    val motor = vibrator ?: return
    val effect =
      if (byAmplitude) {
        VibrationEffect.createOneShot(tick.milliseconds, tick.amplitude)
      } else {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
      }
    motor.vibrate(effect, TOUCH)
  }

  private companion object {
    /**
     * What these ticks are *for*, which is what makes the system's own haptic
     * setting apply to them.
     *
     * `USAGE_TOUCH` rather than `USAGE_MEDIA`: a die landing is feedback about
     * something the player did, not part of a soundtrack, and the two are
     * governed by different switches in Android's settings.
     */
    val TOUCH: VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
  }
}
