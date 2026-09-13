package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross

/**
 * One flat surface of something drawn: a convex polygon, wound anticlockwise
 * as seen from the side its [normal] points at.
 *
 * A die's face and a stretch of the tray's wall are the same shape of thing to
 * a GPU, and saying so once means [GpuMesh] packs both rather than two packers
 * drifting apart.
 */
interface Surface {
  /** The corners, in winding order. */
  val positions: List<Vector3>

  /** Which way the surface faces, square to it. */
  val normal: Vector3

  /** Which way `u` increases across it, square to [normal]. */
  val tangent: Vector3

  /** One per corner, or empty where the surface takes a plain colour. */
  val uvs: List<TextureCoordinate>

  /** Indices into [positions], three per triangle. */
  val triangles: List<Int>
}

/**
 * A list of surfaces flattened into the arrays a GPU takes.
 *
 * Corners are **not** shared between surfaces. Two faces of a die meet at the
 * same point in space but disagree about which way they face and where they
 * sit in the texture, so each carries its own copy — which is what makes a die
 * read as a solid with edges rather than as something inflated.
 *
 * Nothing here talks to Filament. Packing arrays is where off-by-one lives,
 * and off-by-one in an index buffer is a triangle drawn through the middle of
 * a die — so it is done in Kotlin, on the JVM, where a test can look at the
 * numbers (`docs/architecture.md`, decision 40).
 */
data class GpuMesh(
  val positions: FloatArray,
  val tangents: FloatArray,
  val uvs: FloatArray,
  val indices: IntArray,
) {
  /** How many corners the buffers hold. */
  val vertexCount: Int get() = positions.size / POSITION_SIZE

  /** And how many triangles are drawn from them. */
  val triangleCount: Int get() = indices.size / TRIANGLE

  companion object {
    /** Three floats to a position. */
    const val POSITION_SIZE: Int = 3

    /** Four to a tangent frame: it is a quaternion, not a normal. */
    const val TANGENT_SIZE: Int = 4

    /** Two to a point in a texture. */
    const val UV_SIZE: Int = 2

    private const val TRIANGLE = 3

    /**
     * [surfaces] packed, in order.
     *
     * @param scale what to multiply every position by. A die's mesh is built
     *   one unit from the middle, so this is where it becomes a die of a size
     *   (`docs/tables.md`); the tray is already in millimetres and takes 1.
     */
    fun of(
      surfaces: List<Surface>,
      scale: Double = 1.0,
    ): GpuMesh {
      val corners = surfaces.sumOf { it.positions.size }
      val positions = FloatArray(corners * POSITION_SIZE)
      val tangents = FloatArray(corners * TANGENT_SIZE)
      val uvs = FloatArray(corners * UV_SIZE)
      val indices = IntArray(surfaces.sumOf { it.triangles.size })

      var corner = 0
      var index = 0
      surfaces.forEach { surface ->
        val frame = frameOf(surface)
        surface.positions.forEachIndexed { position, point ->
          write(positions, (corner + position) * POSITION_SIZE, point * scale)
          write(tangents, (corner + position) * TANGENT_SIZE, frame)
          // A surface with no texture still needs the corner to exist, and
          // where it points at is not read: its material has no sampler.
          val uv = surface.uvs.getOrElse(position) { TextureCoordinate(0.0, 0.0) }
          uvs[(corner + position) * UV_SIZE] = uv.u.toFloat()
          uvs[(corner + position) * UV_SIZE + 1] = uv.v.toFloat()
        }
        surface.triangles.forEach { indices[index++] = corner + it }
        corner += surface.positions.size
      }
      return GpuMesh(positions, tangents, uvs, indices)
    }

    /**
     * A surface's whole frame as the quaternion a vertex buffer carries.
     *
     * Filament wants the tangent frame rather than a bare normal, because a
     * lit surface needs to know which way the texture runs as well as which
     * way it faces. The frame is right-handed by construction — the tangent
     * and the normal both come from the same arithmetic that laid the texture
     * out — so the bitangent is the one that falls out, and the quaternion is
     * taken with a positive `w`, which is the half Filament reads.
     */
    private fun frameOf(surface: Surface): Quaternion {
      val turn =
        Quaternion.of(
          right = surface.tangent,
          up = cross(surface.normal, surface.tangent),
          forward = surface.normal,
        )
      return if (turn.w < 0) -turn else turn
    }

    private fun write(
      into: FloatArray,
      at: Int,
      vector: Vector3,
    ) {
      into[at] = vector.x.toFloat()
      into[at + 1] = vector.y.toFloat()
      into[at + 2] = vector.z.toFloat()
    }

    private fun write(
      into: FloatArray,
      at: Int,
      turn: Quaternion,
    ) {
      // x, y, z, w — the order Filament reads a tangent frame in, which is
      // not the order a quaternion is usually written in.
      into[at] = turn.x.toFloat()
      into[at + 1] = turn.y.toFloat()
      into[at + 2] = turn.z.toFloat()
      into[at + TANGENT_SIZE - 1] = turn.w.toFloat()
    }
  }

  // Arrays do not compare by content, and a mesh that is equal to another is
  // a thing the tests ask about constantly.
  override fun equals(other: Any?): Boolean =
    this === other ||
      (
        other is GpuMesh &&
          positions.contentEquals(other.positions) &&
          tangents.contentEquals(other.tangents) &&
          uvs.contentEquals(other.uvs) &&
          indices.contentEquals(other.indices)
      )

  override fun hashCode(): Int {
    var hash = positions.contentHashCode()
    hash = 31 * hash + tangents.contentHashCode()
    hash = 31 * hash + uvs.contentHashCode()
    hash = 31 * hash + indices.contentHashCode()
    return hash
  }
}
