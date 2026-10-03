package de.drehtuer.dinfinity.feature.roll

import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.input.shake.ShakeThresholds
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor

/**
 * Where the roll screen hears a shake from: the sensors, or a test's hand
 * standing behind the same code ([ShakeInput]).
 *
 * The sensor half is driven here with real `SensorEvent`s through Robolectric's
 * sensor manager, all the way to dice in the air — the one test that shakes
 * the screen the way a phone does, so the [TestHand] every other screen test
 * uses is known to be standing in for something that works.
 */
@RunWith(RobolectricTestRunner::class)
// ShadowSensor is deprecated with no replacement that can make a Sensor; see
// `SensorShakeSourceTest`, which has the same problem and the same answer.
@Suppress("DEPRECATION")
class ShakeInputTest {
  @get:Rule
  val compose = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val shadow = shadowOf(manager)

  /**
   * A context with a display, which is what the roll screen's activity is on a
   * phone. The display's rotation is asked for on every sample, and
   * Robolectric's activity — unlike a real one — refuses to name its display.
   */
  private val screen: Context =
    context.createDisplayContext(
      context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
    )

  @After
  fun sensorsBack() {
    ShakeInput.current = ShakeInput.SENSORS
  }

  @Test
  fun `a shipped build listens to the sensors`() {
    assertSame(ShakeInput.SENSORS, ShakeInput.current)
    assertSame(SensorShakeInput, ShakeInput.SENSORS)
  }

  @Test
  fun `the sensors are listened to until they are stopped`() {
    addSensors()
    val stop = SensorShakeInput.listen(context, ShakeInput.Hand({ true }, {}, {}))
    assertFalse("nothing was listening", shadow.listeners.isEmpty())

    stop.stop()
    stop.stop()
    assertTrue("the sensors were left running", shadow.listeners.isEmpty())
  }

  @Test
  fun `a shake on the sensors starts, is sampled, and ends`() {
    addSensors()
    val heard = mutableListOf<String>()
    SensorShakeInput.listen(
      screen,
      ShakeInput.Hand(
        onStarted = {
          heard += "started"
          true
        },
        onEnded = { heard += "ended" },
        onSample = { heard += "sample" },
      ),
    )

    shakeHard(atMillis = 0)
    shakeHard(atMillis = ShakeThresholds.START_MILLIS)
    beStill(atMillis = 1_000)
    beStill(atMillis = 1_000 + ShakeThresholds.STOP_MILLIS)

    assertEquals("started", heard.first())
    assertTrue("no moment of the shake was handed over", "sample" in heard)
    assertTrue("the shake never ended", "ended" in heard)
  }

  @Test
  fun `a phone with no sensor service cannot be shaken, and does not crash for it`() {
    val noSensors =
      object : ContextWrapper(context) {
        override fun getSystemService(name: String): Any? =
          if (name == Context.SENSOR_SERVICE) null else super.getSystemService(name)
      }

    val stop = SensorShakeInput.listen(noSensors, ShakeInput.Hand({ error("shaken") }, {}, {}))
    stop.stop()

    assertTrue(shadow.listeners.isEmpty())
  }

  @Test
  fun `the roll screen throws when the sensors say the phone was shaken`() {
    // The whole of the real path, short of a GPU: sensor events in, a throw
    // out, through `ShakeToRoll` exactly as it ships.
    addSensors()
    val tray = DirectTray()
    val presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0)))
    compose.setContent {
      CompositionLocalProvider(LocalContext provides screen) { ShakeToRoll(presenter) }
    }
    compose.runOnIdle { presenter.type("1d20") }

    compose.runOnIdle {
      shakeHard(atMillis = 0)
      shakeHard(atMillis = ShakeThresholds.START_MILLIS)
    }
    compose.waitForIdle()

    assertEquals("the shake threw nothing", 1, tray.throws)
    assertTrue("the shake did not reach the dice it threw", tray.shaken.isNotEmpty())

    // And the hand lets go, which gives the edges back; a second shake is a
    // second throw of its own, the first roll having landed.
    compose.runOnIdle {
      beStill(atMillis = 1_000)
      beStill(atMillis = 1_000 + ShakeThresholds.STOP_MILLIS)
    }
    compose.waitForIdle()
    compose.runOnIdle {
      shakeHard(atMillis = 2_000)
      shakeHard(atMillis = 2_000 + ShakeThresholds.START_MILLIS)
    }
    compose.waitForIdle()
    assertEquals("a second shake after the hand let go threw nothing", 2, tray.throws)
  }

  @Test
  fun `the roll screen stops listening when it goes`() {
    addSensors()
    var shown by mutableStateOf(true)
    val presenter = rollPresenter(DirectTray(), LandingRolls(mapOf(0 to 0)))
    compose.setContent { if (shown) ShakeToRoll(presenter) }
    compose.waitForIdle()
    assertFalse("the screen is not listening", shadow.listeners.isEmpty())

    shown = false
    compose.waitForIdle()

    assertTrue("the sensors were left running behind a screen that went", shadow.listeners.isEmpty())
  }

  @Test
  fun `a test hand with no screen listening throws nothing`() {
    val hand = TestHand()

    assertFalse(hand.heard)
    assertFalse(hand.shake())
  }

  @Test
  fun `a test hand shakes the way the sensors do — started, every moment, ended`() {
    val hand = TestHand()
    val heard = mutableListOf<Any>()
    hand.listen(
      context,
      ShakeInput.Hand(
        onStarted = {
          heard += "started"
          false
        },
        onEnded = { heard += "ended" },
        onSample = { heard += it },
      ),
    )
    val moment =
      ShakeSample(
        stepIndex = 3,
        accelerationMmPerSecond2 = Vector3(1.0, 0.0, 0.0),
        gravity = Vector3(0.0, 0.0, 0.0),
      )

    assertTrue(hand.heard)
    assertFalse("the hand did not pass on what the screen answered", hand.shake(listOf(moment)))
    assertEquals(listOf("started", moment, "ended"), heard)
  }

  @Test
  fun `a screen that stops after a newer one started does not deafen the newer one`() {
    // What a recreated activity does: the new screen resumes before the old
    // one is disposed of.
    val hand = TestHand()
    val old = hand.listen(context, ShakeInput.Hand({ error("the old screen was shaken") }, {}, {}))
    var thrown = 0
    val new =
      hand.listen(
        context,
        ShakeInput.Hand(
          onStarted = {
            thrown++
            true
          },
          onEnded = {},
          onSample = {},
        ),
      )

    old.stop()
    assertTrue(hand.shake())
    assertEquals(1, thrown)

    new.stop()
    assertFalse(hand.heard)
  }

  @Test
  fun `the display's quarter turns become degrees`() {
    assertEquals(0, Surface.ROTATION_0.asDegrees())
    assertEquals(90, Surface.ROTATION_90.asDegrees())
    assertEquals(180, Surface.ROTATION_180.asDegrees())
    assertEquals(270, Surface.ROTATION_270.asDegrees())
  }

  private fun addSensors() {
    shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_LINEAR_ACCELERATION))
    shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE))
  }

  /** About 1.2 g: a shake and not a nudge. */
  private fun shakeHard(atMillis: Long) = send(atMillis, x = 12f)

  private fun beStill(atMillis: Long) = send(atMillis, x = 0.1f)

  private fun send(
    atMillis: Long,
    x: Float,
  ) {
    // `SensorEvent` has no public constructor; the platform makes them itself.
    val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
    constructor.isAccessible = true
    val event = constructor.newInstance(VALUES)
    SensorEvent::class.java
      .getField("sensor")
      .set(event, ShadowSensor.newInstance(Sensor.TYPE_LINEAR_ACCELERATION))
    SensorEvent::class.java.getField("timestamp").setLong(event, atMillis * NANOS_PER_MILLI)
    event.values[0] = x
    shadow.sendSensorEventToListeners(event)
  }

  private companion object {
    const val VALUES = 3
    const val NANOS_PER_MILLI = 1_000_000L
  }
}
