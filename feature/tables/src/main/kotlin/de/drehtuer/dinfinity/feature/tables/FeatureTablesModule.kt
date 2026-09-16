package de.drehtuer.dinfinity.feature.tables

/**
 * Marks `:feature:tables` as present and wired into the build.
 *
 * The module has real types now — the table picker (`docs/tables.md`) — so
 * what this object still does is name the dependencies, which is the one thing
 * a compiler cannot say back: a module that depends on more than it should
 * compiles perfectly well.
 */
object FeatureTablesModule {
  /** This module's Gradle path. */
  const val PATH: String = ":feature:tables"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":dicesets:format", ":data", ":designer")
}
