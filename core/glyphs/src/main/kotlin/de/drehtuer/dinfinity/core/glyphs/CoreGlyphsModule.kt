package de.drehtuer.dinfinity.core.glyphs

/**
 * Marks `:core:glyphs` as present and wired into the build.
 *
 * Every module carries one of these: a graph that only compiles proves the
 * declarations exist, not that a module can see what it says it depends on.
 */
object CoreGlyphsModule {
  /** This module's Gradle path. */
  const val PATH: String = ":core:glyphs"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":core:model")
}
