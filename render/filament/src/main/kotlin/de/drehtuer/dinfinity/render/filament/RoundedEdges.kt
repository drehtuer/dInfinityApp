package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.simulation.api.HullMargin
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.sin

/**
 * A die's edges and corners rounded off for drawing — rounded exactly as far
 * as the solver rounds the die it collides (`docs/physics-and-rendering.md`,
 * "Rounded edges"; `docs/architecture.md`, decision 91).
 *
 * **The solver's die was never sharp.** Jolt is handed the sharp corners and a
 * convex radius ([HullMargin]); it pulls every face plane in by that radius
 * and grows the smaller solid back out by a ball of the same size, so what
 * meets the felt is the solid with every edge a strip of a cylinder and every
 * corner a patch of a sphere. A sharp picture of that die stood *outside* it
 * at every corner — sunk into the felt by up to half a millimetre on a d4
 * balanced on its point, and into its neighbours wherever two touched.
 *
 * So this draws the same construction with the same radius:
 *
 * - every flat face is **on its own plane**, only smaller — a die resting on a
 *   face is drawn exactly where the physics holds it, and the face it reads is
 *   the face it shows;
 * - every edge is a strip of a cylinder of radius `r`, and every corner a
 *   patch of a sphere of radius `r`, cut from inside the sharp solid;
 * - the radius is the solver's, repeated rather than chosen ([radiusFor]), so
 *   the drawn surface is the surface that collides and there is no gap to
 *   argue about.
 *
 * Nothing about the physics moves: the hull it is handed, the radius it asks
 * for and what it makes of them are all as they were. This is only the
 * picture of it catching up. Nothing here knows what a GPU is either; it is
 * arithmetic over the faces the catalogue already has, done on the JVM where
 * a test can check every plane.
 */
object RoundedEdges {
  /**
   * The most a rounded strip's normal turns from one row of corners to the
   * next. Thirty degrees: a cube's edge is drawn in four steps, a d20's in
   * two, a coin's rim between two of its 24 sides in two — with a normal per
   * corner the GPU blends the light across each step, and at half a
   * millimetre nothing finer would show.
   */
  const val MAX_SEGMENT_TURN: Double = PI / 6

  /**
   * The radius a die of these [facets], made of [material] and drawn at
   * [scale], is rounded by — in the mesh's own units, one to the bounding
   * radius — so that it is the radius the solver collides it with.
   *
   * It is Jolt's rule, repeated over the same faces (`ConvexHullShape`'s
   * constructor): the radius [material] asks for ([HullMargin.requestedMm]),
   * cut down until it fits twice across the thinnest part of the die, and
   * until no corner stands more than [HullMargin.maxErrorMm] inside the
   * sharp one ([gapOf]). The last is what a d4's spike is limited by; every
   * other catalogue solid gets what it asked for. Both are the die's own, so
   * a set's `edge_rounding` reaches the picture exactly as it reaches the
   * solver (decision 94).
   */
  fun radiusFor(
    facets: List<Facet>,
    material: DieMaterial,
    scale: Double,
  ): Double {
    val solid = Solid(facets)
    val reachMm = material.boundingRadiusMm * scale
    val radiusMm =
      minOf(
        HullMargin.requestedMm(material),
        solid.thinnest * reachMm / 2,
        HullMargin.maxErrorMm(material) / solid.sharpness,
      )
    return radiusMm / reachMm
  }

  /**
   * How far a die of these [facets], rounded by [radius], stands inside its
   * sharp self at its worst — how much further the sharp corners reach than
   * the rounded ones, for the solver's die and the drawn one alike.
   *
   * A corner `v` is rounded round the point `v - r·m`, where `m` is the one
   * vector that is a unit along every face normal meeting there
   * (`n · m = 1`). The sharp corner reaches `r·|m|` beyond that point in
   * `m`'s direction and the rounded one `r`, so the gap is `r·(|m| - 1)` —
   * `0.73 r` for a cube, `2 r` for a d4's spike, `0.26 r` for a d20.
   */
  fun gapOf(
    facets: List<Facet>,
    radius: Double,
  ): Double = radius * Solid(facets).sharpness

  /**
   * The surfaces a die is drawn from: [facets] each shrunk on its own plane,
   * a strip round every edge and a patch over every corner, rounded by
   * [radius] in the die's own units.
   */
  fun of(
    facets: List<Facet>,
    radius: Double,
  ): List<Surface> {
    require(radius >= 0) { "a die cannot be rounded by $radius" }
    val solid = Solid(facets)
    return facets.indices.flatMap { facet ->
      buildList {
        add(solid.flat(facet, radius))
        addAll(solid.strips(facet, radius))
        addAll(solid.corners(facet, radius))
      }
    }
  }

  /**
   * The point of the unit sphere a corner patch is fanned out from: `m`'s own
   * direction, where the sharp and the rounded corner are furthest apart, so
   * the gap [gapOf] promises is the gap the mesh has. Or, at a corner whose
   * faces lean so unevenly that `m` falls outside them, the mean of their
   * normals, which is always inside.
   *
   * @param normals the normals of the faces meeting at the corner, in order
   *   round it.
   */
  internal fun centreOf(
    normals: List<Vector3>,
    m: Vector3,
  ): Vector3 {
    val direction = m.normalised()
    val sides = normals.indices.map { side -> triple(normals[side], normals[(side + 1) % normals.size], direction) }
    val inside = sides.all { it > 0 } || sides.all { it < 0 }
    return if (inside) direction else normals.reduce(Vector3::plus).normalised()
  }

  /**
   * The vector with a unit component along every one of [normals]: `n · m = 1`
   * for each, solved in the least-squares sense so that four or five faces
   * meeting at one corner are one equation each rather than too many.
   */
  internal fun reachOf(normals: List<Vector3>): Vector3 {
    // (Σ n nᵀ) m = Σ n, a symmetric three-by-three solved by Cramer's rule.
    val xx = normals.sumOf { it.x * it.x }
    val xy = normals.sumOf { it.x * it.y }
    val xz = normals.sumOf { it.x * it.z }
    val yy = normals.sumOf { it.y * it.y }
    val yz = normals.sumOf { it.y * it.z }
    val zz = normals.sumOf { it.z * it.z }
    val sum = normals.reduce(Vector3::plus)
    val determinant = xx * (yy * zz - yz * yz) - xy * (xy * zz - yz * xz) + xz * (xy * yz - yy * xz)
    require(abs(determinant) > SINGULAR) { "faces meeting at one corner must not all share an axis" }
    val x = sum.x * (yy * zz - yz * yz) - xy * (sum.y * zz - yz * sum.z) + xz * (sum.y * yz - yy * sum.z)
    val y = xx * (sum.y * zz - yz * sum.z) - sum.x * (xy * zz - yz * xz) + xz * (xy * sum.z - sum.y * xz)
    val z = xx * (yy * sum.z - sum.y * yz) - xy * (xy * sum.z - sum.y * xz) + sum.x * (xy * yz - yy * xz)
    return Vector3(x / determinant, y / determinant, z / determinant)
  }

  /**
   * The point a fraction [step] of [steps] of the way round the great circle
   * from [from] to [to], both unit vectors less than half a turn apart.
   *
   * The two ends are returned as they are, not recomputed: an edge strip, the
   * flat face beside it and the corner patches at its ends all meet along
   * these points, and two answers a bit apart would be a crack in the die.
   */
  internal fun arc(
    from: Vector3,
    to: Vector3,
    step: Int,
    steps: Int,
  ): Vector3 =
    when (step) {
      0 -> from
      steps -> to
      else -> {
        val angle = acos((from dot to).coerceIn(-1.0, 1.0))
        val share = step.toDouble() / steps
        (from * sin((1 - share) * angle) + to * sin(share * angle)) * (1 / sin(angle))
      }
    }

  /** How many steps half an edge between faces facing [one] and [other] is drawn in. */
  internal fun stepsBetween(
    one: Vector3,
    other: Vector3,
  ): Int = maxOf(1, ceil(acos((one dot other).coerceIn(-1.0, 1.0)) / 2 / MAX_SEGMENT_TURN).toInt())

  private fun triple(
    a: Vector3,
    b: Vector3,
    c: Vector3,
  ): Double = a.x * (b.y * c.z - b.z * c.y) - a.y * (b.x * c.z - b.z * c.x) + a.z * (b.x * c.y - b.y * c.x)

  /** Three face normals at one corner closer to coplanar than this have no corner. */
  private const val SINGULAR = 1e-12

  /** How far a corner's faces may disagree about where it is pulled in to. */
  internal const val REACH_TOLERANCE: Double = 1e-9

  /**
   * One face of the sharp solid, and where in the atlas any point on its plane
   * is painted from — or null for a surface that carries no cell, a coin's rim.
   *
   * The painting is a function rather than a list because the rounded die has
   * points the sharp one does not: a shrunk face's corners, and the strips and
   * patches that lean away from it. Each takes the cell coordinates of where
   * it sits on its face's plane, so the flat part of a face samples exactly
   * what it sampled before and the edge continues the face's own artwork round
   * the bend.
   */
  class Facet(
    val face: MeshFace,
    val paint: ((Vector3) -> TextureCoordinate)?,
  )

  /** The sharp solid, with every corner's neighbourhood worked out once. */
  private class Solid(
    private val facets: List<Facet>,
  ) {
    /** Which facet each directed edge of the solid belongs to, by its two corners. */
    private val owner: Map<Pair<Vector3, Vector3>, Int> =
      buildMap {
        facets.forEachIndexed { index, facet ->
          val corners = facet.face.positions
          corners.indices.forEach { put(corners[it] to corners[(it + 1) % corners.size], index) }
        }
      }

    /** Each corner's facets, in order round it. */
    private val around: Map<Vector3, List<Int>> =
      facets
        .flatMap { it.face.positions }
        .distinct()
        .associateWith(::ringAt)

    /** Each corner's `m`: the vector with a unit component along every one of its faces' normals. */
    private val reach: Map<Vector3, Vector3> =
      around.mapValues { (corner, ring) ->
        val normals = ring.map { facets[it].face.normal }
        reachOf(normals).also { m ->
          check(normals.all { abs((it dot m) - 1) < REACH_TOLERANCE }) {
            "the faces at $corner do not meet in one corner when pulled in evenly"
          }
        }
      }

    /**
     * The worst `|m| - 1` of any corner: how much further the sharp die
     * reaches than the rounded one, per unit of radius.
     */
    val sharpness: Double = reach.values.map { it.length }.max() - 1

    /** How thick the solid is across its thinnest way: from a face to the corner furthest behind it. */
    val thinnest: Double =
      facets
        .map { facet ->
          val height = facet.face.positions.first() dot facet.face.normal
          reach.keys.map { height - (it dot facet.face.normal) }.max()
        }.min()

    /** The facets round [corner], each the one across the edge leaving [corner] in the one before. */
    private fun ringAt(corner: Vector3): List<Int> {
      val first = facets.indexOfFirst { corner in it.face.positions }
      return generateSequence(first) { facet ->
        owner.getValue(next(facet, corner) to corner).takeIf { it != first }
      }.toList()
    }

    private fun next(
      facet: Int,
      corner: Vector3,
    ): Vector3 {
      val corners = facets[facet].face.positions
      return corners[(corners.indexOf(corner) + 1) % corners.size]
    }

    private fun previous(
      facet: Int,
      corner: Vector3,
    ): Vector3 {
      val corners = facets[facet].face.positions
      return corners[(corners.indexOf(corner) + corners.size - 1) % corners.size]
    }

    /** Where the rounded die's surface is whose normal is [direction], at the corner [corner] is rounded round. */
    private fun at(
      corner: Vector3,
      direction: Vector3,
      radius: Double,
    ): Vector3 = corner - reach.getValue(corner) * radius + direction * radius

    private fun paintOf(
      facet: Facet,
      points: List<Vector3>,
    ): List<TextureCoordinate> = facet.paint?.let { points.map(it) } ?: emptyList()

    /** The facet itself, shrunk on its own plane. */
    fun flat(
      index: Int,
      radius: Double,
    ): MeshFace {
      val facet = facets[index]
      val corners = facet.face.positions.map { at(it, facet.face.normal, radius) }
      return facet.face.copy(positions = corners, uvs = paintOf(facet, corners))
    }

    /** The half of each edge strip that belongs to [index], from its face to the middle of the bend. */
    fun strips(
      index: Int,
      radius: Double,
    ): List<Surface> {
      val facet = facets[index]
      val normal = facet.face.normal
      val corners = facet.face.positions
      return corners.indices.map { side ->
        val from = corners[side]
        val to = corners[(side + 1) % corners.size]
        val across = facets[owner.getValue(to to from)].face.normal
        val middle = (normal + across).normalised()
        val steps = stepsBetween(normal, across)
        val directions = (0..steps).map { arc(normal, middle, it, steps) }
        val positions = directions.map { at(from, it, radius) } + directions.map { at(to, it, radius) }
        Curved(
          positions = positions,
          normals = directions + directions,
          normal = normal,
          tangent = facet.face.tangent,
          uvs = paintOf(facet, positions),
          // Row `from` is 0..steps and row `to` follows it. The face lies to
          // the left of from→to seen from outside, so the strip, which lies to
          // the right, is wound from → further from → further to → to.
          triangles =
            (0 until steps).flatMap { step ->
              val a = step
              val b = steps + 1 + step
              listOf(a, a + 1, b + 1, a, b + 1, b)
            },
        )
      }
    }

    /** The share of each corner patch that belongs to [index]: from its face to halfway to each neighbour. */
    fun corners(
      index: Int,
      radius: Double,
    ): List<Surface> {
      val facet = facets[index]
      val normal = facet.face.normal
      return facet.face.positions.map { corner ->
        val ring = around.getValue(corner)
        val centre = centreOf(ring.map { facets[it].face.normal }, reach.getValue(corner))
        // The edge arriving at this corner and the one leaving it, each by the
        // face across it — the same arcs the strips along them were drawn on.
        val arriving = facets[owner.getValue(corner to previous(index, corner))].face.normal
        val leaving = facets[owner.getValue(next(index, corner) to corner)].face.normal
        val inbound = stepsBetween(normal, arriving)
        val outbound = stepsBetween(normal, leaving)
        val arrivingMiddle = (normal + arriving).normalised()
        val leavingMiddle = (normal + leaving).normalised()
        val rim =
          (inbound downTo 1).map { arc(normal, arrivingMiddle, it, inbound) } +
            (0..outbound).map { arc(normal, leavingMiddle, it, outbound) }
        val directions = listOf(centre) + rim
        val positions = directions.map { at(corner, it, radius) }
        Curved(
          positions = positions,
          normals = directions,
          normal = normal,
          tangent = facet.face.tangent,
          uvs = paintOf(facet, positions),
          triangles = (1 until rim.size).flatMap { listOf(0, it + 1, it) },
        )
      }
    }
  }
}

/**
 * A curved piece of a drawn die — part of a rounded edge or corner — with a
 * normal of its own at every corner.
 *
 * @param normal the face it belongs to, whose cell it is painted from.
 * @param tangent that face's: which way its texture runs.
 */
data class Curved(
  override val positions: List<Vector3>,
  override val normals: List<Vector3>,
  override val normal: Vector3,
  override val tangent: Vector3,
  override val uvs: List<TextureCoordinate>,
  override val triangles: List<Int>,
) : Surface
