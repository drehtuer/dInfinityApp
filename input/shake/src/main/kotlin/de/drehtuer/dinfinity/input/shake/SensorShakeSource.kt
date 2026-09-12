package de.drehtuer.dinfinity.input.shake

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * @param onStarted called when a shake is confirmed: the dice are spawned now.
 * @param onEnded called when it is over: the dice are released.
 */
class SensorShakeSource(
  private val sensors: SensorManager,
  private val session: ShakeSession = ShakeSession(),
  private val onStarted: () -> Unit = {},
  private val onEnded: (ShakeSession) -> Unit = {},
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
      Sensor.TYPE_GYROSCOPE -> session.rotation(event.timestamp, vectorOf(event, scale = 1.0))
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
    when (session.acceleration(atMillis, vectorOf(event, MM_PER_METRE))) {
      ShakeDetector.Event.Started -> onStarted()
      ShakeDetector.Event.Ended -> onEnded(session)
      ShakeDetector.Event.None -> Unit
    }
  }

  private fun vectorOf(
    event: SensorEvent,
    scale: Double,
  ): Vector3 =
    Vector3(
      event.values[0].toDouble() * scale,
      event.values[1].toDouble() * scale,
      event.values[2].toDouble() * scale,
    )

  private companion object {
    /** Android reports metres; the simulation is in millimetres throughout. */
    const val MM_PER_METRE = 1_000.0
    const val NANOS_PER_MILLI = 1_000_000L
  }
}
