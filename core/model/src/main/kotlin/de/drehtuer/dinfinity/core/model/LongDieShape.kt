package de.drehtuer.dinfinity.core.model

/**
 * The proportions of [DieShape.ElongatedSquareBipyramid], the long d4: a
 * square prism lying on one of its four long faces, with a short pyramid on
 * each end (`docs/dice-sets.md`, "The d4"; `docs/architecture.md`, decision
 * 109).
 *
 * Like the coin's, these are decisions rather than a name, and three places
 * have to make the same ones: `simulation/api` builds the hull from them, the
 * renderer draws that hull, and [DieVolume] works out how much material is
 * in it.
 *
 * In units of the die's bounding radius — half its `size_mm` — measured along
 * its length, so the two points of the ends lie on the bounding sphere and a
 * long d4 is `size_mm` long. The prism's corners lie inside that sphere: the
 * one solid of the catalogue whose corners are not all on it.
 */
object LongDieShape {
  /** Half the width of a long face, and of the square the prism stands on. */
  const val HALF_WIDTH: Double = 0.4

  /**
   * Half the length of the prism, from the middle to where an end begins.
   * The end's point is at one, so each end is `1 − 0.7 = 0.3` long.
   *
   * **Long enough that the die cannot rest on an end.** A die rests on a face
   * when its centre lies over that face. An end's triangle of height `h` over
   * a half-width `w` has the foot of the centre `h / (h² + w²)` out along the
   * die (point at one), and it only falls outside the triangle — past its
   * base, at `w` — when `h` is between 0.2 and 0.8. At 0.3 the foot is at
   * 0.48, a fifth past the edge, so a die on an end always tips back onto a
   * long face. Ends at the 0.18 that would put every corner on one sphere
   * were inside that range's lower bound, and a die could balance there.
   */
  const val BODY_HALF_LENGTH: Double = 0.7

  /** How long the whole die is against how wide: 2 against 0.8. */
  const val LENGTH_TO_WIDTH: Double = 1.0 / HALF_WIDTH
}
