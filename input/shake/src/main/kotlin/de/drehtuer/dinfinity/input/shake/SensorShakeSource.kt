package de.drehtuer.dinfinity.input.shake

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * The one Android-shaped piece of shake input: `SensorManager` callbacks
 * turned into calls on a [ShakeSession].
 *
 * It decides nothing. Everything that decides anything is in the session, so
 * the thresholds, the fusion and the quantisation are all testable without an
 * emulator, and what is left here is registration, unit conversion and
 * hand-off — which is exactly what a device test is for and a unit test is
 * not.
 *
 * The sensors are the two `docs/physics-and-rendering.md` names: linear
 * acceleration, which is the accelerometer with gravity already removed by the
 * platform, and the gyroscope, both at `SENSOR_DELAY_GAME`.
 *
 * Every vector goes through [PhoneAxes] on the way in. Android reports sensors
 * in the device's frame and the tray has its own, a quarter turn away before
 * the phone is turned at all, so a vector used as it arrives moves the dice in
 * a direction unrelated to the hand (`docs/physics-and-rendering.md`,
 * "Coordinates").
 *
 * @param onStarted called when a shake is confirmed: the dice are spawned now.
 *   **Answers whether dice were actually thrown.** A shake that begins while a
 *   roll is still in the air throws none — those dice are already thrown — and
 *   says so, which keeps its moments numbered on the running roll's clock so
 *   that they reach it (`docs/physics-and-rendering.md`, "Shake input"). The
 *   default is true: a source nobody wired to an app is a source whose every
 *   shake is its own throw.
 * @param onEnded called when it is over.
 * @param onSample every moment recorded while the shake lasts, in order. The
 *   dice are already in the air by then, so these reach the roll as they come
 *   rather than waiting for the hand to stop.
 * @param rotationDegrees how far the display is turned from the phone's
 *   natural orientation, asked for every sample because the player is holding
 *   the thing and may turn it mid-shake.
 */
class SensorShakeSource(
  private val sensors: SensorManager,
  private val session: ShakeSession = ShakeSession(),
  private val onStarted: () -> Boolean = { true },
  private val onEnded: (ShakeSession) -> Unit = {},
  private val onSample: (ShakeSample) -> Unit = {},
  private val rotationDegrees: () -> Int = { 0 },
) : SensorEventListener {
  /** Starts listening. Returns false when the phone has no sensors to listen to. */
  fun start(): Boolean {
    val linear = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: return false
    val gyroscope = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    session.reset()
    sensors.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME)
    gyroscope?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    return true
  }

  /** Stops listening. Safe to call twice. */
  fun stop() {
    sensors.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent) {
    when (event.sensor?.type) {
      Sensor.TYPE_LINEAR_ACCELERATION -> acceleration(event)
      Sensor.TYPE_GYROSCOPE -> session.rotation(event.timestamp, trayVectorOf(event, scale = 1.0))
      else -> Unit
    }
  }

  /** Nothing here cares how accurate the sensor thinks it is; a shake is not a measurement. */
  override fun onAccuracyChanged(
    sensor: Sensor?,
    accuracy: Int,
  ) = Unit

  private fun acceleration(event: SensorEvent) {
    val atMillis = event.timestamp / NANOS_PER_MILLI
    // [onStarted] is called from inside, before this sample is recorded, and
    // its answer decides whether the clock starts over. The spawn happening
    // first is what lets the sample that confirmed the shake drive the roll it
    // started, rather than being the one moment that is thrown away; and a
    // shake that threw nothing — because a roll is already in the air — goes on
    // numbering on that roll's clock so the hand reaches it.
    val change = session.acceleration(atMillis, trayVectorOf(event, MM_PER_METRE), onStarted)
    if (change == ShakeDetector.Event.Ended) onEnded(session)
    session.latest?.let(onSample)
  }

  /** The event's vector, in the units the tray uses and the axes it uses. */
  private fun trayVectorOf(
    event: SensorEvent,
    scale: Double,
  ): Vector3 =
    PhoneAxes.toTray(
      Vector3(
        event.values[0].toDouble() * scale,
        event.values[1].toDouble() * scale,
        event.values[2].toDouble() * scale,
      ),
      rotationDegrees(),
    )

  private companion object {
    /** Android reports metres; the simulation is in millimetres throughout. */
    const val MM_PER_METRE = 1_000.0
    const val NANOS_PER_MILLI = 1_000_000L
  }
}
