package de.drehtuer.dinfinity.simulation.api

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Where the catalogue's solids come from: closed forms, not a model somebody
 * exported.
 *
 * The physics hull, the rendered mesh and the face reading all have to agree
 * about which way a face points, and the cheapest way to guarantee that is for
 * all three to be built from the same arithmetic. A measurement of an OBJ file
 * would be one more thing that could be a hundredth of a degree out.
 */
internal object Solids {
  private const val TURN = 2 * PI

  /** The golden ratio, which is most of the geometry of a d12 and a d20. */
  val PHI: Double = (1 + sqrt(FIVE)) / 2

  /** Every sign combination of three magnitudes. */
  fun signs(
    x: Double,
    y: Double,
    z: Double,
  ): List<Vector3> =
    listOf(-1.0, 1.0).flatMap { sx ->
      listOf(-1.0, 1.0).flatMap { sy ->
        listOf(-1.0, 1.0).map { sz -> Vector3(sx * x, sy * y, sz * z) }
      }
    }

  /** The three cyclic permutations of a coordinate triple, in every sign. */
  fun cycled(
    a: Double,
    b: Double,
    c: Double,
  ): List<Vector3> = signs(a, b, c) + signs(b, c, a) + signs(c, a, b)

  /** A dodecahedron's twelve faces point at an icosahedron's twelve vertices. */
  fun icosahedronVertices(): List<Vector3> = cycled(0.0, 1.0, PHI).distinctDirections()

  /** An icosahedron's twenty faces point at a dodecahedron's twenty vertices. */
  fun dodecahedronVertices(): List<Vector3> = (signs(1.0, 1.0, 1.0) + cycled(0.0, PHI, 1 / PHI)).distinctDirections()

  /**
   * The corners of an n-gonal trapezohedron: two apexes and two staggered
   * rings, all of them on one sphere.
   *
   * These are the points the physics collides, and they and the face normals
   * below come out of the same two numbers — see [ringHeight] for what those
   * two numbers have to satisfy.
   */
  fun trapezohedronVertices(n: Int): List<Vector3> {
    val ring = ringHeight(n)
    val apex = apexHeight(n)
    return listOf(Vector3(0.0, 0.0, apex), Vector3(0.0, 0.0, -apex)) +
      (0 until n).flatMap { k ->
        listOf(ringVertex(k.toDouble() / n, ring), ringVertex((k + HALF) / n, -ring))
      }
  }

  /**
   * The outward normals of the 2n kite faces of an n-gonal trapezohedron.
   *
   * A kite is four points, and a normal taken from three of them is the
   * *face's* normal only if the fourth lies on the same plane. That planarity
   * is not free: it is the condition [ringHeight] solves, and it is what makes
   * this a trapezohedron rather than a bag of triangles with a die's outline.
   */
  fun trapezohedron(n: Int): List<Vector3> {
    val ring = ringHeight(n)
    val apex = Vector3(0.0, 0.0, apexHeight(n))
    val upper = (0 until n).map { ringVertex(it.toDouble() / n, ring) }
    val lower = (0 until n).map { ringVertex((it + HALF) / n, -ring) }
    return (0 until n).flatMap { k ->
      listOf(
        outward(apex, upper[k], lower[k], upper[(k + 1) % n]),
        outward(-apex, lower[k], upper[(k + 1) % n], lower[(k + 1) % n]),
      )
    }
  }

  /** The bounding radius of an n-gonal trapezohedron whose apex edge is 1. */
  fun trapezohedronRadiusPerEdge(n: Int): Double {
    val ring = ringHeight(n)
    val apex = apexHeight(n)
    return apex / sqrt(1 + (apex - ring) * (apex - ring))
  }

  /** The outward normal of the plane through a kite's four corners. */
  private fun outward(
    tip: Vector3,
    left: Vector3,
    far: Vector3,
    right: Vector3,
  ): Vector3 {
    val normal = cross(left - tip, far - tip).normalised()
    val centre = (tip + left + far + right) * QUARTER
    return if (normal dot centre < 0) -normal else normal
  }

  private fun ringVertex(
    turns: Double,
    height: Double,
  ): Vector3 = Vector3(Exact.cos(TURN * turns), Exact.sin(TURN * turns), height)

  private const val HALF = 0.5
  private const val QUARTER = 0.25
  private const val FIVE = 5.0
}

private const val TURN = 2 * PI
private const val ROUNDING = 1e9
private const val TWO = 2.0

/**
 * How far the rings sit above and below the middle, for a ring of radius 1.
 *
 * Two conditions fix it and leave no freedom at all.
 *
 * The kite faces have to be **planar** — that is what makes this a
 * trapezohedron rather than a bag of triangles with a die's outline, and a
 * die with no flat face has nothing to land on. Planarity forces the apex to
 * sit a fixed multiple of the ring height up: `2 / (1 − cos(π/n)) − 1`.
 *
 * And every corner has to be on one sphere, which is what a fair die is: an
 * insphere touching every face, a circumsphere through every corner.
 */
private fun ringHeight(n: Int): Double = 1 / sqrt(apexRatio(n) * apexRatio(n) - 1)

private fun apexHeight(n: Int): Double = apexRatio(n) * ringHeight(n)

/** How many times higher than the ring the apex sits, for the faces to be flat. */
private fun apexRatio(n: Int): Double = TWO / (1 - Exact.cos(PI / n)) - 1

/** How many sides a coin's rim is drawn and collided with. */
private const val COIN_SEGMENTS = 24

/**
 * Unit vectors in the catalogue's face order: from the top of the reference
 * orientation downwards, and anticlockwise around each ring from the `+x`
 * side.
 *
 * The whole solid is first turned so that its **first position points straight
 * up**. That is worth doing rather than taking whatever orientation the
 * construction happened to produce: a tetrahedron built out of cube corners
 * has no corner up, an octahedron built the same way has no face up, and a
 * trapezohedron has two rings and neither of them at the top — so in every one
 * of those the "reference orientation" would be a die balanced on an edge,
 * which is not a thing a die is ever in. With this, face 0 is the face that is
 * up when nothing has been turned, which is what both the atlas and every test
 * of a face reading expect.
 */
internal fun order(directions: List<Vector3>): List<Vector3> =
  uprightRotation(directions).let { rotation -> directions.turnedAndOrdered(rotation) }

/**
 * The rotation that puts the highest of [directions] straight up.
 *
 * Worked out once and applied to a solid's faces *and* its corners, so the two
 * describe the same solid in the same orientation.
 */
internal fun uprightRotation(directions: List<Vector3>): Quaternion {
  val unit = directions.map(Vector3::normalised)
  val highest = unit.maxWith(compareBy<Vector3> { rounded(it.z) }.thenByDescending(::azimuth))
  return Quaternion.taking(highest, Vector3.Up)
}

/** These directions, turned by [rotation] and put in the catalogue's order. */
internal fun List<Vector3>.turnedAndOrdered(rotation: Quaternion): List<Vector3> =
  map(Vector3::normalised)
    .map { rotation.rotate(it).normalised() }
    .sortedWith(compareByDescending<Vector3> { rounded(it.z) }.thenBy(::azimuth))

internal fun List<Vector3>.distinctDirections(): List<Vector3> =
  map(Vector3::normalised).distinctBy { Triple(rounded(it.x), rounded(it.y), rounded(it.z)) }

private fun azimuth(direction: Vector3): Double {
  val angle = Exact.atan2(direction.y, direction.x)
  return if (angle < 0) angle + TURN else angle
}

/** Rounds away the last few bits, so two directions that are the same sort as the same. */
private fun rounded(value: Double): Long = Math.round(value * ROUNDING)

/**
 * The corners of a coin: a rim of [segments] points at each end of a
 * cylinder as thick as a quarter of its width.
 *
 * A cylinder is not a polyhedron, so the hull is an approximation — but a
 * 24-sided one is smoother than any d2 anybody has ever minted, and the
 * corners of the rim are exactly the furthest points from the middle, which
 * is what the bounding radius is computed from.
 */
fun coinVertices(
  thicknessRatio: Double,
  segments: Int = COIN_SEGMENTS,
): List<Vector3> =
  (0 until segments).flatMap { k ->
    val turns = k.toDouble() / segments
    listOf(
      Vector3(Exact.cos(TURN * turns) / 2, Exact.sin(TURN * turns) / 2, thicknessRatio / 2),
      Vector3(Exact.cos(TURN * turns) / 2, Exact.sin(TURN * turns) / 2, -thicknessRatio / 2),
    )
  }
