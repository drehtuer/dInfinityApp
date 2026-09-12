package de.drehtuer.dinfinity.feature.roll

/**
 * Marks `:feature:roll` as present and wired into the build.
 *
 * The module is a skeleton: plan Step 1 builds the graph, and the step that
 * owns this module fills it with real types. Until then this object and its
 * test are what prove the module compiles, runs its tests, and can see the
 * modules it depends on — a graph that only compiles proves nothing.
 */
object FeatureRollModule {
  /** This module's Gradle path. */
  const val PATH: String = ":feature:roll"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":core:notation", ":simulation:api", ":data")
}
