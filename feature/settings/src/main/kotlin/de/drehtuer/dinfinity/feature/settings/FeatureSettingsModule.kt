package de.drehtuer.dinfinity.feature.settings

/**
 * Marks `:feature:settings` as present and wired into the build.
 *
 * It holds Settings, the menu, the notation reference and the developer screen.
 * The last of those is why `:simulation:api` is here: a replay is a
 * `ThrowSpec` and the anomaly log is about a `SimulationOutcome`, both of which
 * live beside the simulation they describe and neither of which brings a
 * physics engine with it (`docs/architecture.md`, decision 40).
 *
 * This object and its test are what prove the module can see the modules it
 * declares — a graph that only compiles proves nothing.
 */
object FeatureSettingsModule {
  /** This module's Gradle path. */
  const val PATH: String = ":feature:settings"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":data", ":core:notation", ":simulation:api")
}
