package de.drehtuer.dinfinity.designer

/**
 * The one part of an export that needs a device: turning a plan into pixels.
 *
 * Everything that decides *what goes where* is [Atlas], in plain Kotlin a unit
 * test can assert on. What is left behind this interface is putting an
 * anti-aliased line down and encoding a PNG — a thing that can fail but cannot
 * be *wrong* — which is the same seam `PhysicsWorld` and `Stage` draw
 * (`docs/architecture.md`, decisions 40 and 47).
 */
fun interface AtlasPainter {
  /**
   * [plan] as the bytes of a PNG, or null when it could not be drawn.
   *
   * Null rather than an exception: a phone that cannot allocate a 1280×1024
   * bitmap is a phone the export still has to survive, and a die whose atlas
   * could not be drawn is a die with no texture — which is a die that prints
   * its labels, and a package that still installs.
   */
  fun png(plan: AtlasPlan): ByteArray?

  companion object {
    /** Paints nothing. The fallback, and what a test uses when the pixels are not the point. */
    val NONE: AtlasPainter = AtlasPainter { null }
  }
}
