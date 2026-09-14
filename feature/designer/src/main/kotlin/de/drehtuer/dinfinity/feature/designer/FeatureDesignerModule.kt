package de.drehtuer.dinfinity.feature.designer

/**
 * Marks `:feature:designer` as present and wired into the build.
 *
 * The module has real types now — the face designer (`docs/face-designer.md`)
 * — so what this object still does is name the dependencies, which is the one
 * thing a compiler cannot say back: a module that depends on more than it
 * should compiles perfectly well.
 */
object FeatureDesignerModule {
  /** This module's Gradle path. */
  const val PATH: String = ":feature:designer"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":designer")
}
