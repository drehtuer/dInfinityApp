package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead

/**
 * One flat surface of a catalogue solid: which corners are on it, which way it
 * faces, and the frame the atlas draws its cell in.
 *
 * @param index the catalogue face this is, which is also the atlas cell it is
 *   painted from and the entry of a set file's `faces` list it scores
 *   (`docs/dice-sets.md`).
 * @param normal which way the surface faces, outward, one unit long.
 * @param corners the corners on that surface, anticlockwise as seen from
 *   outside, one unit from the middle of the solid.
 * @param along which way `u` increases across the cell — the face's "right".
 * @param up which way the cell is drawn up: `+z` flattened onto the face
 *   (`docs/dice-sets.md`, "Up is `+z`").
 */
data class SolidFace(
  val index: Int,
  val normal: Vector3,
  val corners: List<Vector3>,
  val along: Vector3,
  val up: Vector3,
) {
  /** The middle of the face: the mean of its corners. */
  val centre: Vector3 = corners.reduce(Vector3::plus) * (1.0 / corners.size)

  /**
   * The face's own circle, which is what fills its cell.
   *
   * The furthest corner from [centre], so a triangle and a pentagon both touch
   * the edges of the cell they are drawn in and a strip of cells comes out at
   * one size (`docs/dice-sets.md`, "Up is `+z`").
   */
  val radius: Double = corners.maxOf { (it - centre).length }

  /** [point] as an offset from [centre], along the face rather than in the tray. */
  fun flatOf(point: Vector3): Pair<Double, Double> {
    val offset = point - centre
    return (offset dot along) to (offset dot up)
  }

  /**
   * [point] in the coordinates of its own atlas cell, which run `0..1`.
   *
   * `(0, 0)` is the top-left of the cell, because an image counts its rows
   * downwards and a die is drawn up — so one of the two has to be turned over,
   * and it is this one.
   */
  fun cellOf(point: Vector3): Pair<Double, Double> {
    val (across, upwards) = flatOf(point)
    return HALF + across / (2 * radius) to HALF - upwards / (2 * radius)
  }

  private companion object {
    const val HALF = 0.5
  }
}

/**
 * The faces of the catalogue's solids, found rather than written down.
 *
 * `ShapeGeometry` owns a solid's corners and the direction of each readable
 * position; what nothing owned until now was *which corners make up which
 * face*, and two answers to that would be two descriptions of one die — the
 * thing `docs/dice-sets.md` warns comes apart the first time either is
 * touched. So the grouping is here, once: the corners furthest along a face's
 * normal are the corners on that face, and the order they come back in is the
 * order a polygon is wound.
 *
 * Both the mesh the tray draws (`render/filament`'s `DieMesh`) and the solid
 * the face designer turns over (`designer`'s `SolidStage`) are built from
 * this, so face *i* is the same polygon in both by construction rather than by
 * inspection.
 *
 * It is plain arithmetic over closed forms, so it is worked out once per shape
 * and kept: a die being spun on the designer's stage asks for it every frame.
 */
object SolidFaces {
  /**
   * Two corners of a unit solid count as being on the same plane when their
   * heights above it agree to this much.
   *
   * The catalogue's closed forms are exact to the last few bits of a double,
   * so this is generous by a wide margin and still nowhere near the gap to the
   * next ring of corners.
   */
  const val PLANE_TOLERANCE: Double = 1e-9

  /** The faces of [shape], in the catalogue's face order. */
  fun of(shape: DieShape): List<SolidFace> = FACES.getValue(shape)

  /**
   * Which way each of the shape's cells faces.
   *
   * For a face-read solid that is the direction the face is read from, and the
   * two are the same thing. For a tetrahedron they are not: a d4 is read from
   * the corner pointing up, so the catalogue's directions are corners, and the
   * flat surface that carries cell *i* is the face **opposite** corner *i* —
   * the triangle whose corners are the three that are not *i*
   * (`docs/dice-sets.md`, "The d4").
   */
  private fun outwardNormals(shape: DieShape): List<Vector3> =
    ShapeGeometry.directionsOf(shape).map { direction ->
      when (shape.naturalRead) {
        FaceRead.FaceUp -> direction.normalised()
        FaceRead.VertexUp -> -direction.normalised()
      }
    }

  private fun facesOf(shape: DieShape): List<SolidFace> {
    val corners = ShapeGeometry.verticesOf(shape)
    return outwardNormals(shape).mapIndexed { index, normal -> faceOn(shape, index, normal, corners) }
  }

  private fun faceOn(
    shape: DieShape,
    index: Int,
    normal: Vector3,
    corners: List<Vector3>,
  ): SolidFace {
    val height = corners.maxOf { it dot normal }
    val onPlane = corners.filter { (it dot normal) >= height - PLANE_TOLERANCE }
    check(onPlane.size >= TRIANGLE) {
      "${shape.id} face $index has ${onPlane.size} corners on its plane, which is not a polygon"
    }
    val up = flattened(Vector3.Up, normal) ?: flattened(SIDEWAYS, normal) ?: error("no frame for $normal")
    val along = cross(up, normal)
    val middle = onPlane.reduce(Vector3::plus) * (1.0 / onPlane.size)
    return SolidFace(
      index = index,
      normal = normal,
      corners = onPlane.sortedBy { corner -> angleOf(corner - middle, along, up) },
      along = along,
      up = up,
    )
  }

  /** Where [offset] sits around the face, for winding its polygon. */
  private fun angleOf(
    offset: Vector3,
    along: Vector3,
    up: Vector3,
  ): Double = Exact.atan2(offset dot up, offset dot along)

  /** [direction] with the part along [normal] taken out, or null if nothing is left. */
  private fun flattened(
    direction: Vector3,
    normal: Vector3,
  ): Vector3? {
    val flat = direction - normal * (direction dot normal)
    return if (flat.length > FLAT_TOLERANCE) flat.normalised() else null
  }

  /** What a face pointing straight up or straight down is turned by instead. */
  private val SIDEWAYS = Vector3(0.0, 1.0, 0.0)

  private const val FLAT_TOLERANCE = 1e-6
  private const val TRIANGLE = 3

  private val FACES: Map<DieShape, List<SolidFace>> = DieShape.entries.associateWith(::facesOf)
}
