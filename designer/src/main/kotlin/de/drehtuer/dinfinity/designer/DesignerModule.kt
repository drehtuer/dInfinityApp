package de.drehtuer.dinfinity.designer

/**
 * Marks `:designer` as present and wired into the build.
 *
 * The module has real types now — the drawing model behind the face designer
 * (`docs/face-designer.md`) — so what this object still does is name the
 * dependencies, which is the one thing a compiler cannot say back.
 */
object DesignerModule {
  /** This module's Gradle path. */
  const val PATH: String = ":designer"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":dicesets:format")
}
