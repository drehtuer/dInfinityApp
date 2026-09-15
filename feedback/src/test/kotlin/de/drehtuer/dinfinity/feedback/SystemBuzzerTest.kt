package de.drehtuer.dinfinity.feedback

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowVibrator

/**
 * The phone's actuator, on a phone Robolectric is pretending to be.
 *
 * What is worth asserting here is not that it buzzes — only a hand can say
 * that (`docs/TODO.md`, Step 5.6) — but that it **degrades**: a device with no
 * vibrator, or one that cannot vary its amplitude, still takes every tick, and
 * the effects go out under the usage that Android's own haptic setting
 * governs.
 */
@RunWith(RobolectricTestRunner::class)
class SystemBuzzerTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `a tick reaches the vibrator`() {
    SystemBuzzer(context).tick(HapticTick.of(strength = 1.0))

    assertNotNull("nothing reached the vibrator", motor().vibrationAttributesFromLastVibration)
  }

  @Test
  fun `a phone with no vibrator takes every tick and does nothing`() {
    motor().setHasVibrator(false)

    SystemBuzzer(context).tick(HapticTick.of(strength = 0.5))

    assertNull("a phone with no motor tried to vibrate", motor().vibrationAttributesFromLastVibration)
  }

  @Test
  fun `a phone with no amplitude control still ticks`() {
    motor().setHasAmplitudeControl(false)

    SystemBuzzer(context).tick(HapticTick.of(strength = 0.25))

    assertNotNull("an on-or-off actuator was left silent", motor().vibrationAttributesFromLastVibration)
  }

  @Test
  fun `a tick is feedback about a touch, which is what the system's setting governs`() {
    SystemBuzzer(context).tick(HapticTick.of(strength = 1.0))

    val attributes = motor().vibrationAttributesFromLastVibration as VibrationAttributes
    assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
  }

  @Test
  fun `every strength is a tick the system accepts`() {
    val buzzer = SystemBuzzer(context)

    listOf(0.0, 0.5, 1.0).forEach { strength -> buzzer.tick(HapticTick.of(strength)) }

    assertNotNull(motor().vibrationAttributesFromLastVibration)
  }

  private fun motor(): ShadowVibrator = shadowOf(context.getSystemService(VibratorManager::class.java).defaultVibrator)

  private companion object {
    init {
      // Amplitude control is what the one-shot path needs, and Robolectric's
      // default is off — so the default case here has to say so.
      check(HapticTick.STRONGEST > HapticTick.FAINTEST)
    }
  }
}
