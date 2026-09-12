package de.drehtuer.dinfinity.core.probability

/**
 * Marks `:core:probability` as present and wired into the build.
 *
 * It depends on `:core:notation` because the outcome graph is a second reading
 * of the same formula: it walks the tree the parser produced and the plan the
 * planner resolved, so the chart is never about a slightly different formula
 * than the dice are.
 */
object CoreProbabilityModule {
  /** This module's Gradle path. */
  const val PATH: String = ":core:probability"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":core:model", ":core:notation")
}
