package de.drehtuer.dinfinity.simulation.api

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
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
  fun dodecahedronVertices(): List<Vector3> = (signs(1.0, 1.0, 1.0) + cycled(0.0, 1 / PHI, PHI)).distinctDirections()

  /**
   * The outward normals of the 2n kite faces of an n-gonal trapezohedron whose
   * vertices all sit on one sphere and whose edges are all the same length —
   * which is what a real d10 or d18 is.
   */
  fun trapezohedron(n: Int): List<Vector3> {
    val ring = ringHeight(n)
    val apex = Vector3(0.0, 0.0, sqrt(1 + ring * ring))
    return (0 until n).flatMap { k ->
      val a = ringVertex(k.toDouble() / n, ring)
      val b = ringVertex((k + HALF) / n, -ring)
      val next = ringVertex((k + 1.0) / n, ring)
      listOf(outward(apex, a, b), outward(-apex, next, b))
    }
  }

  /** The outward normal of the kite through [tip], [left] and [right]. */
  private fun outward(
    tip: Vector3,
    left: Vector3,
    right: Vector3,
  ): Vector3 {
    val normal = cross(left - tip, right - tip).normalised()
    return if (normal dot (tip + left + right) < 0) -normal else normal
  }

  private fun ringVertex(
    turns: Double,
    height: Double,
  ): Vector3 = Vector3(cos(TURN * turns), sin(TURN * turns), height)

  /**
   * How far the ring of vertices sits above the middle, solved by bisection
   * because the condition — apex edge equal to ring edge, with every vertex on
   * one sphere — has no closed form worth writing down.
   *
   * The bounding radius that falls out of it does: `cos(π / 2n)`.
   */
  private fun ringHeight(n: Int): Double {
    var low = SMALLEST_RING
    var high = LARGEST_RING
    repeat(BISECTIONS) {
      val middle = (low + high) / 2
      if (edgeDifference(n, low) * edgeDifference(n, middle) <= 0) high = middle else low = middle
    }
    return (low + high) / 2
  }

  private fun edgeDifference(
    n: Int,
    ring: Double,
  ): Double {
    val apex = sqrt(1 + ring * ring)
    val apexEdge = sqrt(1 + (apex - ring) * (apex - ring))
    val ringEdge = sqrt(2 - 2 * cos(PI / n) + FOUR * ring * ring)
    return apexEdge - ringEdge
  }

  private const val HALF = 0.5
  private const val FOUR = 4.0
  private const val FIVE = 5.0
  private const val BISECTIONS = 200
  private const val SMALLEST_RING = 1e-9
  private const val LARGEST_RING = 5.0
}

private const val TURN = 2 * PI
private const val ROUNDING = 1e9

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
internal fun order(directions: List<Vector3>): List<Vector3> {
  val unit = directions.map(Vector3::normalised)
  val highest = unit.maxWith(compareBy<Vector3> { rounded(it.z) }.thenByDescending(::azimuth))
  val upright = Quaternion.taking(highest, Vector3.Up)
  return unit
    .map { upright.rotate(it).normalised() }
    .sortedWith(compareByDescending<Vector3> { rounded(it.z) }.thenBy(::azimuth))
}

internal fun List<Vector3>.distinctDirections(): List<Vector3> =
  map(Vector3::normalised).distinctBy { Triple(rounded(it.x), rounded(it.y), rounded(it.z)) }

private fun azimuth(direction: Vector3): Double {
  val angle = atan2(direction.y, direction.x)
  return if (angle < 0) angle + TURN else angle
}

/** Rounds away the last few bits, so two directions that are the same sort as the same. */
private fun rounded(value: Double): Long = Math.round(value * ROUNDING)
