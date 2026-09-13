package de.drehtuer.dinfinity.feature.graph

/**
 * What `:feature:graph` is and what it may depend on.
 *
 * Kept now that the module has real types in it, because the dependency list
 * is the interesting part: the outcome graph sees notation and probability and
 * nothing else. It has no simulator and no table, which is the module boundary
 * saying what `docs/probability.md` says in words — the graph is about the
 * formula, and `500d6` graphs perfectly well however few dice fit on a tray.
 */
object FeatureGraphModule {
  /** This module's Gradle path. */
  const val PATH: String = ":feature:graph"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":core:notation", ":core:probability")
}
