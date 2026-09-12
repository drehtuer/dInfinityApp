package de.drehtuer.dinfinity.render.headless

/**
 * Marks `:render:headless` as present and wired into the build.
 *
 * It holds the [Renderer] contract as well as the renderer that does nothing,
 * so that `render/filament` depends on this module rather than the other way
 * round — and so that the interface a roll is observed through has no Android
 * in it at all.
 */
object RenderHeadlessModule {
  /** This module's Gradle path. */
  const val PATH: String = ":render:headless"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":simulation:api")
}
