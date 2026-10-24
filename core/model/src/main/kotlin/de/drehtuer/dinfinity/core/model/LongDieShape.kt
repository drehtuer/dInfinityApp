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
 * In units of the die's bounding radius, measured along its length, so the
 * two points of the ends lie on the bounding sphere. The prism's corners lie
 * inside that sphere: the one solid of the catalogue whose corners are not all
 * on it. That sphere is [SIZE] times its set's `size_mm` across, not
 * `size_mm` itself.
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

  /**
   * How much longer a long d4 is than its set's `size_mm`.
   *
   * Every other die fills the sphere `size_mm` across; a long d4 that did was
   * 6.4 mm wide beside a d6 edge of 9.2 mm, and read as too small at arm's
   * length (owner, 2026-10-24). At 1.25 a 16 mm set's d4 is 20 mm long and
   * 8 mm wide. It is a rule of the shape, not a value in the file, so every
   * set's d4 grows and `size_mm` keeps meaning what it means for the rest
   * ([Die.physicalMaterial]).
   */
  const val SIZE: Double = 1.25

  /** How long the whole die is against how wide: 2 against 0.8. */
  const val LENGTH_TO_WIDTH: Double = 1.0 / HALF_WIDTH
}
