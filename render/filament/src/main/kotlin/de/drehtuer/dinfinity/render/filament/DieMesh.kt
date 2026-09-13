package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross

/**
 * The mesh a die is drawn from, built from the same closed forms the solver
 * collides and the face reader reads (`docs/architecture.md`, decision 35).
 *
 * Three descriptions of one solid would be three chances to be a hundredth of
 * a degree apart, and the one that would show is a die whose printed face and
 * scored face disagree — the worst bug this app could have, because it looks
 * like the physics cheating. So the mesh is not a model somebody exported: it
 * is `ShapeGeometry`'s corners, grouped onto `ShapeGeometry`'s face
 * directions, and face *i* of this mesh is face *i* of the catalogue by
 * construction.
 *
 * Everything here is one unit from the middle — the corners of a catalogue
 * solid all sit on one sphere, so a die of any size is this mesh times its
 * bounding radius (`docs/tables.md`). Nothing in this file knows what a GPU
 * is, which is the point: it is all decided in Kotlin and tested on the JVM,
 * and only the buffers it is poured into need a device.
 */
data class DieMesh(
  val shape: DieShape,
  val faces: List<MeshFace>,
) {
  /** Every corner of every face, in face order. */
  val positions: List<Vector3> get() = faces.flatMap(MeshFace::positions)

  companion object {
    /**
     * Two corners of a unit solid count as the same plane when their heights
     * above it agree to this much. The catalogue's closed forms are exact to
     * the last few bits of a double, so this is generous by a wide margin and
     * still nowhere near the gap to the next ring of corners.
     */
    const val PLANE_TOLERANCE: Double = 1e-9

    /** The mesh of [shape], one unit from the middle to every corner. */
    fun of(shape: DieShape): DieMesh {
      val corners = ShapeGeometry.verticesOf(shape)
      val grid = ShapeAtlas.gridFor(shape)
      val faces =
        outwardNormals(shape).mapIndexed { index, normal ->
          face(shape, index, normal, corners, grid)
        }
      return DieMesh(shape, faces + rimOf(shape, faces))
    }

    /**
     * Which way each of the shape's [ShapeAtlas] cells faces.
     *
     * For a face-read solid that is the direction the face is read from, and
     * the two are the same thing. For a tetrahedron they are not: a d4 is read
     * from the corner pointing up, so the catalogue's directions are corners,
     * and the flat surface that carries cell *i* is the face **opposite**
     * corner *i* — the triangle whose corners are the three that are not *i*
     * (`docs/dice-sets.md`, "The d4").
     *
     * That pairing is what lets a d4's numbers stay with its corners. Each
     * cell carries the values of its three corners, each drawn at its own
     * corner, so the number at the top of a settled d4 appears on all three
     * faces you can see and two faces sharing an edge agree along it.
     */
    private fun outwardNormals(shape: DieShape): List<Vector3> =
      ShapeGeometry.directionsOf(shape).map { direction ->
        when (shape.naturalRead) {
          FaceRead.FaceUp -> direction.normalised()
          FaceRead.VertexUp -> -direction.normalised()
        }
      }

    private fun face(
      shape: DieShape,
      index: Int,
      normal: Vector3,
      corners: List<Vector3>,
      grid: ShapeAtlas.Grid,
    ): MeshFace {
      val height = corners.maxOf { it dot normal }
      val onPlane = corners.filter { (it dot normal) >= height - PLANE_TOLERANCE }
      check(onPlane.size >= TRIANGLE) {
        "${shape.id} face $index has ${onPlane.size} corners on its plane, which is not a polygon"
      }
      val frame = TextureFrame.on(normal, onPlane)
      val ordered = onPlane.sortedBy(frame::angleOf)
      return MeshFace(
        index = index,
        positions = ordered,
        normal = normal,
        tangent = frame.along,
        uvs = ordered.map { frame.cell(it, grid, ShapeAtlas.cellOf(shape, index)) },
        triangles = fan(ordered.size),
      )
    }

    /**
     * The band around the outside of a coin, which belongs to no face.
     *
     * A coin is the one solid in the catalogue that is not the intersection of
     * its own face planes: it is a 24-sided prism standing in for a cylinder,
     * and the strip between its two flats carries no number and no cell. It is
     * drawn in the die's plain colour, so it has positions and normals and no
     * texture coordinates at all.
     */
    private fun rimOf(
      shape: DieShape,
      faces: List<MeshFace>,
    ): List<MeshFace> {
      if (shape != DieShape.Coin) return emptyList()
      val axis = faces[0].normal
      val top = faces[0].positions
      val bottom = faces[1].positions
      return top.indices.map { segment ->
        val a = top[segment]
        val b = top[(segment + 1) % top.size]
        quad(a, b, below(b, bottom, axis), below(a, bottom, axis))
      }
    }

    /**
     * The corner of the far flat directly under [corner], across the rim.
     *
     * Measured with the coin's thickness taken out, so which corner is "under"
     * this one does not depend on how thick a coin is. Nearest in three
     * dimensions would be the same answer for the coin the catalogue has and
     * the wrong one for a thicker coin, where the corner beside it on the far
     * ring is nearer than the one below it.
     */
    private fun below(
      corner: Vector3,
      bottom: List<Vector3>,
      axis: Vector3,
    ): Vector3 {
      val across = corner - axis * (corner dot axis)
      return bottom.minBy { (it - axis * (it dot axis) - across).length }
    }

    /** One rim segment, wound so that it faces away from the middle. */
    private fun quad(
      a: Vector3,
      b: Vector3,
      c: Vector3,
      d: Vector3,
    ): MeshFace {
      val corners = listOf(a, b, c, d)
      val middle = corners.reduce(Vector3::plus) * (1.0 / corners.size)
      val normal = cross(b - a, c - a).normalised()
      val outward = if ((normal dot middle) < 0) -normal else normal
      val wound = if ((normal dot middle) < 0) corners.reversed() else corners
      return MeshFace(
        index = null,
        positions = wound,
        normal = outward,
        tangent = (b - a).normalised(),
        uvs = emptyList(),
        triangles = fan(corners.size),
      )
    }

    /** A convex polygon of [corners] corners as a triangle fan from its first. */
    private fun fan(corners: Int): List<Int> = (1 until corners - 1).flatMap { listOf(0, it, it + 1) }

    private const val TRIANGLE = 3
  }
}

/**
 * One flat surface of a die: a convex polygon, wound anticlockwise as seen
 * from outside.
 *
 * @param index the catalogue face this is, which is also the atlas cell it is
 *   painted from and the entry of the set file's `faces` list it scores
 *   (`docs/dice-sets.md`). Null for a coin's rim, which is none of those.
 * @param positions the corners, in winding order, one unit from the middle.
 * @param normal which way the surface faces. For a face-read solid this is
 *   exactly the direction `simulation/api` reads that face from.
 * @param tangent which way the texture runs across the surface — `u`
 *   increasing. With [normal] it is the frame a renderer needs for lighting,
 *   and it comes from the same construction that laid the texture out rather
 *   than being guessed back from it afterwards.
 * @param uvs where each corner sits in the die's texture, in image
 *   coordinates — `(0, 0)` is the top-left of the atlas. Empty for a rim.
 * @param triangles indices into [positions], three per triangle.
 */
data class MeshFace(
  val index: Int?,
  override val positions: List<Vector3>,
  override val normal: Vector3,
  override val tangent: Vector3,
  override val uvs: List<TextureCoordinate>,
  override val triangles: List<Int>,
) : Surface {
  init {
    require(uvs.isEmpty() || uvs.size == positions.size) {
      "a face has a texture coordinate per corner or none at all, not ${uvs.size} for ${positions.size}"
    }
  }
}

/** A point in a die's texture. `(0, 0)` is the top-left of the atlas. */
data class TextureCoordinate(
  val u: Double,
  val v: Double,
)

/**
 * The two directions that make a face's texture the right way up.
 *
 * A cell is drawn with the face's "up" matching the shape's reference
 * orientation (`docs/dice-sets.md`), and up is `+z` — one right-handed
 * coordinate system shared by the tray, the solver and the renderer, so
 * nothing is turned over on the way between them. Up on a *face* is that up
 * flattened onto it: the part of `+z` that lies in the plane. A face pointing
 * straight up or straight down has no such part, and for those two the tray's
 * `+y` is used instead — which is the same rule the catalogue's own face order
 * leans on, where a ring is walked anticlockwise from the `+x` side.
 */
private class TextureFrame(
  val along: Vector3,
  private val up: Vector3,
  private val middle: Vector3,
  private val radius: Double,
) {
  /** Where [corner] sits around the face, for ordering its polygon. */
  fun angleOf(corner: Vector3): Double {
    val offset = corner - middle
    return Exact.atan2(offset dot up, offset dot along)
  }

  /** [corner] as a point in the atlas, inside [cell] of [grid]. */
  fun cell(
    corner: Vector3,
    grid: ShapeAtlas.Grid,
    cell: Pair<Int, Int>,
  ): TextureCoordinate {
    val offset = corner - middle
    val (column, row) = cell
    return TextureCoordinate(
      u = (column + HALF + (offset dot along) / (2 * radius)) / grid.columns,
      // Down the image is the way the rows are counted, and up the face is the
      // way the die is drawn, so one of them has to be turned over.
      v = (row + HALF - (offset dot up) / (2 * radius)) / grid.rows,
    )
  }

  companion object {
    fun on(
      normal: Vector3,
      corners: List<Vector3>,
    ): TextureFrame {
      val up = flattened(Vector3.Up, normal) ?: flattened(SIDEWAYS, normal) ?: error("no frame for $normal")
      val middle = corners.reduce(Vector3::plus) * (1.0 / corners.size)
      return TextureFrame(
        along = cross(up, normal),
        up = up,
        middle = middle,
        // The face's own circle, so every cell is filled the same way whatever
        // the polygon in it is: a triangle and a pentagon both touch the edges.
        radius = corners.maxOf { (it - middle).length },
      )
    }

    /** [direction] with the part along [normal] taken out, or null if nothing is left. */
    private fun flattened(
      direction: Vector3,
      normal: Vector3,
    ): Vector3? {
      val flat = direction - normal * (direction dot normal)
      return if (flat.length > FLAT_TOLERANCE) flat.normalised() else null
    }

    /** What a face pointing straight up or down is turned by instead. */
    private val SIDEWAYS = Vector3(0.0, 1.0, 0.0)

    private const val FLAT_TOLERANCE = 1e-6
    private const val HALF = 0.5
  }
}
