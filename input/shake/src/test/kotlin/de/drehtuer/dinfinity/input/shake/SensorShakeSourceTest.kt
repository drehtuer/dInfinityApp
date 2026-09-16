package de.drehtuer.dinfinity.input.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor

/**
 * The Android half of shake input: registration, unit conversion and hand-off.
 *
 * It decides nothing — [ShakeSessionTest] covers everything that does — so
 * what is worth asserting here is the wiring: that it listens to the two
 * sensors `docs/physics-and-rendering.md` names, that it copes with a phone
 * that has neither, and that metres become millimetres on the way through.
 */
@RunWith(RobolectricTestRunner::class)
// ShadowSensor is deprecated in Robolectric 4.17 with no replacement that can
// make a Sensor: the platform class has no public constructor, so the only
// alternative is reflecting into its own fields, which is strictly worse than
// using the shadow that exists to do it.
@Suppress("DEPRECATION")
class SensorShakeSourceTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val shadow = shadowOf(manager)

  @Test
  fun `a phone with no linear acceleration sensor cannot be shaken`() {
    assertFalse(SensorShakeSource(manager).start())
    assertTrue(shadow.listeners.isEmpty())
  }

  @Test
  fun `it listens to both sensors when the phone has both`() {
    addSensors()
    val source = SensorShakeSource(manager)
    assertTrue(source.start())
    assertTrue(shadow.hasListener(source, manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)))
    assertTrue(shadow.hasListener(source, manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)))
  }

  @Test
  fun `a phone with no gyroscope still rolls, it just cannot tilt gravity`() {
    shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_LINEAR_ACCELERATION))
    val source = SensorShakeSource(manager)
    assertTrue(source.start())
    assertTrue(shadow.hasListener(source))
  }

  @Test
  fun `stopping unregisters everything, and stopping twice is not an error`() {
    addSensors()
    val source = SensorShakeSource(manager)
    source.start()
    source.stop()
    source.stop()
    assertFalse(shadow.hasListener(source))
  }

  @Test
  fun `shaking the phone starts a shake, and going still ends it`() {
    addSensors()
    val session = ShakeSession()
    var started = 0
    var ended = 0
    val source =
      SensorShakeSource(
        manager,
        session,
        onStarted = {
          started++
          true
        },
        onEnded = { ended++ },
      )
    source.start()

    shakeHard(atMillis = 0)
    assertEquals(0, started)
    shakeHard(atMillis = ShakeThresholds.START_MILLIS)
    assertEquals(1, started)
    assertTrue(session.shaking)

    beStill(atMillis = 1_000)
    beStill(atMillis = 1_000 + ShakeThresholds.STOP_MILLIS)
    assertEquals(1, ended)
    assertFalse(session.shaking)
  }

  @Test
  fun `every moment of a shake is handed over as it arrives, not at the end`() {
    // The dice are spawned when the shake begins, so the rest of the shake has
    // to reach a roll that is already running. A source that only spoke at the
    // end would be describing a throw that had already happened.
    addSensors()
    val moments = mutableListOf<de.drehtuer.dinfinity.simulation.api.ShakeSample>()
    val source = SensorShakeSource(manager, ShakeSession(), onSample = moments::add)
    source.start()

    shakeHard(atMillis = 0)
    assertTrue("a moment before the shake was confirmed was handed over", moments.isEmpty())
    shakeHard(atMillis = ShakeThresholds.START_MILLIS)
    shakeHard(atMillis = ShakeThresholds.START_MILLIS + 20)

    assertEquals("the shake was not handed over while it was happening", 2, moments.size)
    assertTrue("the moments are not in step order", moments[0].stepIndex <= moments[1].stepIndex)
  }

  @Test
  fun `a shake at dice already in the air still reaches the roll driving them`() {
    // The whole of the bug: a second shake used to number its moments from
    // zero, naming steps the running roll took a second ago, so shaking a
    // phone at moving dice did nothing (`docs/TODO.md`, Step 4.1).
    addSensors()
    val session = ShakeSession()
    var threw = true
    val source = SensorShakeSource(manager, session, onStarted = { threw })
    source.start()

    shakeHard(atMillis = 0)
    shakeHard(atMillis = ShakeThresholds.START_MILLIS)
    // From here a roll is in the air, so a shake throws nothing.
    threw = false
    beStill(atMillis = 1_000)
    beStill(atMillis = 1_000 + ShakeThresholds.STOP_MILLIS)
    val driven = session.recorded().last().stepIndex

    shakeHard(atMillis = 2_000)
    shakeHard(atMillis = 2_000 + ShakeThresholds.START_MILLIS)

    assertTrue(
      "the second shake named steps the roll had already taken",
      session.recorded().last().stepIndex > driven,
    )
  }

  @Test
  fun `a second shake still claims the hand, because a hand is still on the phone`() {
    // It starts no throw — there is nothing prepared to throw — but the edges
    // are held and the haptics that go with them fire either way.
    addSensors()
    var started = 0
    val source =
      SensorShakeSource(
        manager,
        ShakeSession(),
        onStarted = {
          started++
          // A roll is already in the air, so this shake threw nothing.
          false
        },
      )
    source.start()

    shakeHard(atMillis = 0)
    shakeHard(atMillis = ShakeThresholds.START_MILLIS)

    assertEquals(1, started)
  }

  @Test
  fun `with no roll in the air a shake is a throw of its own, numbered from zero`() {
    addSensors()
    val session = ShakeSession()
    val source = SensorShakeSource(manager, session, onStarted = { true })
    source.start()

    shakeHard(atMillis = 0)
    shakeHard(atMillis = ShakeThresholds.START_MILLIS)
    beStill(atMillis = 1_000)
    beStill(atMillis = 1_000 + ShakeThresholds.STOP_MILLIS)

    shakeHard(atMillis = 2_000)
    shakeHard(atMillis = 2_000 + ShakeThresholds.START_MILLIS)

    assertEquals(0, session.recorded().first().stepIndex)
  }

  @Test
  fun `ordinary handling is not a shake`() {
    // Picking the phone up, setting it down, handing it over: all of them
    // cross the threshold for a moment and none of them stays there.
    addSensors()
    var started = 0
    val source =
      SensorShakeSource(
        manager,
        ShakeSession(),
        onStarted = {
          started++
          true
        },
      )
    source.start()

    send(Sensor.TYPE_LINEAR_ACCELERATION, atMillis = 0, x = 8f)
    send(Sensor.TYPE_LINEAR_ACCELERATION, atMillis = 40, x = 1f)
    send(Sensor.TYPE_LINEAR_ACCELERATION, atMillis = 80, x = 8f)
    send(Sensor.TYPE_LINEAR_ACCELERATION, atMillis = 120, x = 1f)

    assertEquals("a phone being handled was taken for a shake", 0, started)
  }

  @Test
  fun `metres become millimetres on the way through`() {
    addSensors()
    val session = ShakeSession()
    SensorShakeSource(manager, session).start()
    shakeHard(atMillis = 0)
    shakeHard(atMillis = ShakeThresholds.START_MILLIS)
    // Android reports metres per second squared, so 12 m/s² is 12,000 mm/s².
    // Asserted as a length, because which axis it lands on is the tray's frame
    // rather than the phone's and has its own tests (`PhoneAxesTest`).
    assertEquals(
      12_000.0,
      session
        .recorded()
        .first()
        .accelerationMmPerSecond2.length,
      1.0,
    )
  }

  @Test
  fun `the gyroscope tilts gravity, in radians a second and not millimetres`() {
    addSensors()
    val session = ShakeSession()
    SensorShakeSource(manager, session).start()
    send(Sensor.TYPE_GYROSCOPE, atMillis = 0, x = 0f)
    send(Sensor.TYPE_GYROSCOPE, atMillis = 100, x = (Math.PI / 2 / 0.1).toFloat())
    assertEquals(0.0, session.gravity().z, 1e-6)
  }

  @Test
  fun `a sensor the source does not listen for changes nothing`() {
    addSensors()
    val session = ShakeSession()
    SensorShakeSource(manager, session).start()
    send(Sensor.TYPE_PRESSURE, atMillis = 0, x = 9f)
    assertFalse(session.shaking)
  }

  @Test
  fun `how accurate the sensor thinks it is changes nothing, because a shake is not a measurement`() {
    addSensors()
    val source = SensorShakeSource(manager)
    source.start()
    source.onAccuracyChanged(manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), 0)
    assertTrue(shadow.hasListener(source))
  }

  private fun addSensors() {
    shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_LINEAR_ACCELERATION))
    shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE))
  }

  /** Well over the start threshold: about 1.2 g, which is a shake and not a nudge. */
  private fun shakeHard(atMillis: Long) = send(Sensor.TYPE_LINEAR_ACCELERATION, atMillis, x = 12f)

  private fun beStill(atMillis: Long) = send(Sensor.TYPE_LINEAR_ACCELERATION, atMillis, x = 0.1f)

  private fun send(
    type: Int,
    atMillis: Long,
    x: Float,
  ) {
    shadow.sendSensorEventToListeners(event(type, atMillis, x))
  }

  /**
   * A `SensorEvent`, which has no public constructor: the platform only ever
   * makes them itself, so a test that wants one has to reach for it.
   */
  private fun event(
    type: Int,
    atMillis: Long,
    x: Float,
  ): SensorEvent {
    val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
    constructor.isAccessible = true
    val event = constructor.newInstance(VALUES)
    SensorEvent::class.java.getField("sensor").set(event, ShadowSensor.newInstance(type))
    SensorEvent::class.java.getField("timestamp").setLong(event, atMillis * NANOS_PER_MILLI)
    event.values[0] = x
    return event
  }

  private companion object {
    const val VALUES = 3
    const val NANOS_PER_MILLI = 1_000_000L
  }
}
