package de.drehtuer.dinfinity.input.shake

/**
 * Marks `:input:shake` as present and wired into the build.
 *
 * Almost nothing in this module is Android-shaped. The thresholds, the sensor
 * fusion and the quantisation are plain Kotlin in [ShakeSession]; only
 * [SensorShakeSource] touches `SensorManager`, and it decides nothing.
 */
object InputShakeModule {
  /** This module's Gradle path. */
  const val PATH: String = ":input:shake"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":simulation:api")
}
