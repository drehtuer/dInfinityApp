package de.drehtuer.dinfinity.core.model

/**
 * The proportions of [DieShape.Coin], which is the one solid in the catalogue
 * that is not a polyhedron.
 *
 * Every other shape is fixed by its name: a cube is a cube, and there is
 * nothing about it left to choose. A disc is not — how thick it is and how
 * many sides its rim is collided and drawn with are decisions, and three
 * places have to make the same ones. `simulation/api` builds the hull from
 * them, the renderer draws that hull, and [DieVolume] works out how much
 * material is in it. Written down once here, beside the catalogue itself, so
 * that a d2 cannot be one shape to the solver and another to the screen.
 */
object CoinShape {
  /** How thick a coin is, as a fraction of its width. */
  const val THICKNESS_RATIO: Double = 0.25

  /**
   * How many sides the rim is drawn and collided with.
   *
   * A cylinder is not a polyhedron, so the hull is an approximation — but a
   * 24-sided one is smoother than any d2 anybody has ever minted, and the
   * corners of the rim are exactly the furthest points from the middle, which
   * is what the bounding radius is measured to.
   */
  const val RIM_SEGMENTS: Int = 24
}
