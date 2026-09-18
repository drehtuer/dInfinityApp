package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.SolidFaces
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
 * is `simulation/api`'s [de.drehtuer.dinfinity.simulation.api.SolidFaces] —
 * `ShapeGeometry`'s corners grouped onto `ShapeGeometry`'s face directions —
 * and face *i* of this mesh is face *i* of the catalogue by construction. The
 * grouping lives there rather than here because the face designer's Solid tab
 * turns the same polyhedron over and a second account of it would come apart
 * the first time either was touched (`docs/face-designer.md`).
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
    /** The mesh of [shape], one unit from the middle to every corner. */
    fun of(shape: DieShape): DieMesh {
      val grid = ShapeAtlas.gridFor(shape)
      val faces = SolidFaces.of(shape).map { solid -> face(shape, solid, grid) }
      return DieMesh(shape, faces + rimOf(shape, faces))
    }

    /**
     * One catalogue face as something a renderer can pour into a buffer.
     *
     * The polygon and its frame are `simulation/api`'s
     * ([SolidFaces]) — the same grouping of corners onto faces the face
     * designer's stage is built from, so there is one answer to "which corners
     * make up face 7" rather than two that could drift
     * (`docs/architecture.md`, decision 35). What is added here is the only
     * thing a mesh needs and a solid does not: where each corner sits in the
     * *atlas*, which is its place in its own cell shifted into that cell's
     * square of the grid.
     */
    private fun face(
      shape: DieShape,
      solid: SolidFace,
      grid: ShapeAtlas.Grid,
    ): MeshFace {
      val (column, row) = ShapeAtlas.cellOf(shape, solid.index)
      return MeshFace(
        index = solid.index,
        positions = solid.corners,
        reads = solid.cornerReads,
        normal = solid.normal,
        tangent = solid.along,
        uvs =
          solid.corners.map { corner ->
            val (u, v) = solid.cellOf(corner)
            TextureCoordinate(u = (column + u) / grid.columns, v = (row + v) / grid.rows)
          },
        triangles = fan(solid.corners.size),
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
 * @param reads which readable position of the solid each of [positions] is,
 *   in the same order — `simulation/api`'s answer, carried here so that what
 *   is printed at a corner is chosen by the same list the corner came from
 *   ([de.drehtuer.dinfinity.simulation.api.SolidFace.cornerReads]). Empty for
 *   a face-read solid and for a rim, neither of which reads from a corner.
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
  val reads: List<Int> = emptyList(),
) : Surface {
  init {
    require(uvs.isEmpty() || uvs.size == positions.size) {
      "a face has a texture coordinate per corner or none at all, not ${uvs.size} for ${positions.size}"
    }
    require(reads.isEmpty() || reads.size == positions.size) {
      "a face reads one position per corner or none at all, not ${reads.size} for ${positions.size}"
    }
  }
}

/** A point in a die's texture. `(0, 0)` is the top-left of the atlas. */
data class TextureCoordinate(
  val u: Double,
  val v: Double,
)
