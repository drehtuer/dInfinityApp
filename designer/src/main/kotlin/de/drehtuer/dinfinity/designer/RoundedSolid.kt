package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.simulation.api.HullMargin
import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.abs

/**
 * A catalogue solid with its edges rounded, flattened into the pieces a
 * drawing of it needs (`docs/face-designer.md`, "Material, colour and edges").
 *
 * The solver collides a die whose face planes are pulled in by a radius and
 * grown back out by a ball of it (`docs/physics-and-rendering.md`, "Dice
 * bodies"), and the tray draws that die (`render/filament`'s `RoundedEdges`).
 * The turning die in the designer is a drawing in Compose, not a mesh, so it
 * draws the same construction with flat pieces: every face shrunk on its own
 * plane to where its flat part ends, a band across every edge from one face's
 * flat part to its neighbour's, and a patch over every corner where the flat
 * parts meet. A band stands for a strip of a cylinder and a patch for a piece of
 * a sphere — a chamfer rather than a bend — which is as much as a picture a few
 * hundred points across can show, and enough to see the rounding change as the
 * slider moves.
 *
 * **The radius is the solver's**, by Jolt's own rule as `HullMargin` states it:
 * what the die asks for, cut down until no corner stands more than the error
 * the die is allowed inside the sharp one. That is what keeps a d4 at half of
 * what it asks for, as on the tray. In the solid's own units, one to the
 * bounding radius, which is half the die's size.
 */
class RoundedSolid(
  faces: List<SolidFace>,
  material: DieMaterial,
) {
  /** Every corner of the solid, once, with the faces that meet there. */
  private val corners: List<Corner> = cornersOf(faces)

  /** How far the edges are rounded, in the solid's own units. */
  val radius: Double =
    run {
      val reach = material.clampedToLimits().sizeMm / 2
      val sharpest = corners.maxOfOrNull { it.sharpness } ?: 0.0
      val asked = HullMargin.requestedMm(material) / reach
      val allowed = if (sharpest > 0) HullMargin.maxErrorMm(material) / reach / sharpest else asked
      minOf(asked, allowed)
    }

  /** Each face's flat part: its corners pulled in to where the bends begin, by face index. */
  val flats: Map<Int, List<Vector3>> =
    faces.associate { face -> face.index to face.corners.map { flatCorner(face, it) } }

  /** A band across every edge, between the two faces' flat parts. */
  val bands: List<Bend> =
    faces.flatMap { face ->
      face.corners.indices.mapNotNull { at ->
        val from = face.corners[at]
        val to = face.corners[(at + 1) % face.corners.size]
        val other = faces.firstOrNull { it !== face && it.has(from) && it.has(to) }
        // Each edge once: from the face with the smaller index.
        other?.takeIf { face.index < it.index }?.let {
          Bend(
            outline = listOf(flatCorner(face, from), flatCorner(face, to), flatCorner(it, to), flatCorner(it, from)),
            normal = (face.normal + it.normal).normalised(),
            faces = setOf(face.index, it.index),
          )
        }
      }
    }

  /** A patch over every corner, through the flat corners of the faces meeting there. */
  val patches: List<Bend> =
    corners.filter { it.faces.size > 2 }.map { corner ->
      Bend(
        outline = corner.faces.map { flatCorner(it, corner.at) },
        normal = corner.m.normalised(),
        faces = corner.faces.map(SolidFace::index).toSet(),
      )
    }

  /**
   * Points on the surface of the rounded die that its outline is drawn round,
   * from any side.
   *
   * Every rounded corner is a ball of [radius] round `at − r·m`, and the whole
   * ball is inside the die, so the outline is the hull of those balls: each is
   * sampled in twenty-six directions and in the normals of its own faces —
   * the last so that the flat corners, which are on the balls, are never
   * outside the outline drawn round them. A corner only one face meets — the
   * rim of a coin, which no face owns — is not rounded and is its own point.
   */
  val outlinePoints: List<Vector3> =
    corners.flatMap { corner ->
      if (corner.faces.size < 3) {
        listOf(corner.at)
      } else {
        val core = corner.at - corner.m * radius
        (BALL + corner.faces.map(SolidFace::normal)).map { core + it * radius }
      }
    }

  private fun flatCorner(
    face: SolidFace,
    point: Vector3,
  ): Vector3 {
    val corner = corners.first { it.at.near(point) }
    return point - corner.m * radius + face.normal * radius
  }

  /**
   * One piece of the rounding: a band or a corner patch, drawn flat.
   *
   * @param normal which way it faces, for the light.
   * @param faces the faces it joins, so a drawing can leave out the pieces
   *   every one of whose faces is turned away.
   */
  class Bend(
    val outline: List<Vector3>,
    val normal: Vector3,
    val faces: Set<Int>,
  )

  /**
   * A corner of the solid, the faces meeting at it, and the vector `m` with a
   * unit component along every one of their normals (`n · m = 1`): the
   * rounded corner is a ball round `at − r·m`.
   */
  private class Corner(
    val at: Vector3,
    val faces: List<SolidFace>,
  ) {
    val m: Vector3 = unitAlongEvery(faces.map(SolidFace::normal))

    /** How far the sharp corner stands beyond the rounded one, per unit of radius: `|m| − 1`. */
    val sharpness: Double = (m.length - 1).coerceAtLeast(0.0)
  }

  private companion object {
    /** Corners closer than this are one corner: the faces list each separately. */
    const val SAME = 1e-6

    /** The twenty-six neighbours of the middle of a cube, made unit length. */
    val BALL: List<Vector3> =
      (-1..1).flatMap { x ->
        (-1..1).flatMap { y ->
          (-1..1).mapNotNull { z ->
            if (x == 0 && y == 0 && z == 0) null else Vector3(x.toDouble(), y.toDouble(), z.toDouble()).normalised()
          }
        }
      }

    fun SolidFace.has(point: Vector3): Boolean = corners.any { it.near(point) }

    fun Vector3.near(other: Vector3): Boolean = (this - other).length < SAME

    fun cornersOf(faces: List<SolidFace>): List<Corner> {
      val points = mutableListOf<Vector3>()
      faces.flatMap(SolidFace::corners).forEach { point -> if (points.none { it.near(point) }) points += point }
      return points.map { point -> Corner(point, faces.filter { it.has(point) }) }
    }

    /**
     * The `m` with `n · m = 1` for every one of [normals], in the
     * least-squares sense — exact where three faces meet, and the best fit
     * where a kite die's tip has five or nine. Solved from the normal
     * equations, `(Σ n nᵀ) m = Σ n`, by Cramer's rule: three unknowns.
     */
    fun unitAlongEvery(normals: List<Vector3>): Vector3 {
      // The columns of Σ n nᵀ, which is symmetric, and Σ n.
      val across = normals.fold(Vector3.Zero) { sum, n -> sum + n * n.x }
      val along = normals.fold(Vector3.Zero) { sum, n -> sum + n * n.y }
      val up = normals.fold(Vector3.Zero) { sum, n -> sum + n * n.z }
      val wanted = normals.fold(Vector3.Zero, Vector3::plus)
      val det = triple(across, along, up)
      // Faces meeting along an edge only (a coin's rim) leave the system short
      // of a rank: the mean of the normals, scaled to reach their planes.
      if (abs(det) < SAME) {
        val mean = normals.reduce(Vector3::plus).normalised()
        return mean * (1 / (mean dot normals.first()))
      }
      return Vector3(
        triple(wanted, along, up) / det,
        triple(across, wanted, up) / det,
        triple(across, along, wanted) / det,
      )
    }

    /** The determinant of the matrix whose columns are [a], [b] and [c]: `a · (b × c)`. */
    fun triple(
      a: Vector3,
      b: Vector3,
      c: Vector3,
    ): Double = a.x * (b.y * c.z - b.z * c.y) - a.y * (b.x * c.z - b.z * c.x) + a.z * (b.x * c.y - b.y * c.x)
  }
}
