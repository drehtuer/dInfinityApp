package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The mesh against the solid it is supposed to be.
 *
 * Nothing here compares a picture to another picture. Every check is against
 * `simulation/api`'s own account of the shape — the same account the solver
 * collides and the face reader reads — because the bug worth spending tests on
 * is not a die that looks wrong. It is a die whose printed face and scored
 * face are different faces, which looks like the physics cheating and is the
 * one thing this app cannot be caught doing (`docs/architecture.md`, goal 1).
 */
class DieMeshTest {
  @Test
  fun `every catalogue shape has a surface for every cell of its atlas`() {
    DieShape.entries.forEach { shape ->
      val numbered = DieMesh.of(shape).faces.mapNotNull(MeshFace::index)

      assertEquals(
        "${shape.id} draws ${numbered.size} of its ${shape.faceCount} faces, in this order",
        (0 until shape.faceCount).toList(),
        numbered,
      )
    }
  }

  @Test
  fun `a face is drawn facing the way the die is read from`() {
    // The whole point of building the mesh out of `ShapeGeometry` rather than
    // out of a model: face i of the picture is face i of the roll, by
    // construction rather than by inspection.
    DieShape.entries.forEach { shape ->
      val directions = ShapeGeometry.directionsOf(shape)
      DieMesh.of(shape).faces.filter { it.index != null }.forEach { face ->
        val read = directions[face.index!!].normalised()
        val expected = if (shape.naturalRead == FaceRead.FaceUp) read else -read
        assertTrue(
          "${shape.id} face ${face.index} is drawn facing ${face.normal} but read from $read",
          face.normal.approximates(expected, TOLERANCE),
        )
      }
    }
  }

  @Test
  fun `every corner of every face is a corner of the solid`() {
    DieShape.entries.forEach { shape ->
      val corners = ShapeGeometry.verticesOf(shape)
      DieMesh.of(shape).positions.forEach { position ->
        assertTrue(
          "${shape.id} is drawn through $position, which is not one of its corners",
          corners.any { it.approximates(position, TOLERANCE) },
        )
      }
    }
  }

  @Test
  fun `every corner sits on the same sphere, so a die scales by one number`() {
    DieShape.entries.forEach { shape ->
      DieMesh.of(shape).positions.forEach { position ->
        assertEquals("${shape.id} is drawn out of round", 1.0, position.length, TOLERANCE)
      }
    }
  }

  @Test
  fun `a face is flat`() {
    DieShape.entries.forEach { shape ->
      DieMesh.of(shape).faces.forEach { face ->
        val height = face.positions.first() dot face.normal
        face.positions.forEach { corner ->
          assertEquals(
            "${shape.id} face ${face.index} is not flat",
            height,
            corner dot face.normal,
            TOLERANCE,
          )
        }
      }
    }
  }

  @Test
  fun `every triangle faces outwards`() {
    // A die wound the other way is drawn inside out: the renderer culls the
    // faces you should see and draws the ones you should not.
    DieShape.entries.forEach { shape ->
      DieMesh.of(shape).faces.forEach { face ->
        face.triangles.chunked(TRIANGLE).forEach { (a, b, c) ->
          val wound = cross(face.positions[b] - face.positions[a], face.positions[c] - face.positions[a])
          assertTrue(
            "${shape.id} face ${face.index} has a triangle with no area",
            wound.length > TOLERANCE,
          )
          assertTrue(
            "${shape.id} face ${face.index} has a triangle wound inwards",
            (wound.normalised() dot face.normal) > 0,
          )
        }
      }
    }
  }

  @Test
  fun `a die is closed, with no seam and no hole`() {
    // Every edge belongs to exactly two triangles. It is the cheapest way to
    // ask "is this a solid", and it is what notices a coin whose rim was never
    // built at all — its two flats alone pass every other check here.
    DieShape.entries.forEach { shape ->
      val open =
        DieMesh
          .of(shape)
          .faces
          .flatMap(::edgesOf)
          .groupingBy { it }
          .eachCount()
          .filterValues { it != 2 }
      assertTrue(
        "${shape.id} has ${open.size} edges that are not shared by two triangles",
        open.isEmpty(),
      )
    }
  }

  @Test
  fun `a face is drawn inside its own cell of the atlas, and fills it`() {
    DieShape.entries.forEach { shape ->
      val grid = ShapeAtlas.gridFor(shape)
      DieMesh.of(shape).faces.filter { it.index != null }.forEach { face ->
        val (column, row) = ShapeAtlas.cellOf(shape, face.index!!)
        val left = column.toDouble() / grid.columns
        val top = row.toDouble() / grid.rows
        face.uvs.forEach { uv ->
          assertTrue(
            "${shape.id} face ${face.index} is drawn at u=${uv.u}, outside column $column",
            uv.u >= left - TOLERANCE && uv.u <= left + 1.0 / grid.columns + TOLERANCE,
          )
          assertTrue(
            "${shape.id} face ${face.index} is drawn at v=${uv.v}, outside row $row",
            uv.v >= top - TOLERANCE && uv.v <= top + 1.0 / grid.rows + TOLERANCE,
          )
        }
        // The face's own circle is what fills the cell, not its outline: a
        // triangle and a pentagon both touch the edges, which is what keeps
        // cells comparable when an author draws across a whole strip. A kite
        // has corners nearer the middle than its widest pair, so the check is
        // that the furthest corner is on the circle and none is outside it.
        val reach = face.uvs.map { hypot(grid, it, column, row) }
        assertEquals(
          "${shape.id} face ${face.index} does not fill its cell",
          HALF,
          reach.max(),
          TOLERANCE,
        )
      }
    }
  }

  @Test
  fun `a face is drawn the right way up`() {
    // Up on the face is up in the tray. So of the corners of a face that is
    // not simply lying flat, the highest one is the one nearest the top of the
    // cell — and the top of a cell is the *smallest* v, because images count
    // their rows downwards.
    DieShape.entries.forEach { shape ->
      DieMesh.of(shape).faces.filter { upright(it) }.forEach { face ->
        val highest = face.positions.maxOf { it.z }
        val nearestTheTop = face.uvs.indices.minBy { face.uvs[it].v }
        assertEquals(
          "${shape.id} face ${face.index} is drawn upside down or on its side",
          highest,
          face.positions[nearestTheTop].z,
          TOLERANCE,
        )
      }
    }
  }

  @Test
  fun `the rim of a coin carries no number and no texture`() {
    val rim = DieMesh.of(DieShape.Coin).faces.filter { it.index == null }

    assertEquals("a coin's rim is one quad per segment of its hull", COIN_SEGMENTS, rim.size)
    rim.forEach { segment ->
      assertTrue(
        "the rim of a coin has no cell in the atlas to be drawn from",
        segment.uvs.isEmpty(),
      )
      assertTrue(
        "the rim of a coin points sideways, not up",
        abs(segment.normal dot Vector3.Up) < TOLERANCE,
      )
    }
  }

  @Test
  fun `a shape is drawn the same way every time it is asked for`() {
    DieShape.entries.forEach { shape ->
      assertEquals("${shape.id} is not drawn deterministically", DieMesh.of(shape), DieMesh.of(shape))
    }
  }

  @Test
  fun `a d4 carries its cells on the faces opposite its corners`() {
    // A tetrahedron is read from the corner pointing up, so its four cells
    // have to be paired with its four faces somehow. This is the pairing, and
    // it is a decision rather than a fact (`docs/dice-sets.md`).
    val corners = ShapeGeometry.directionsOf(DieShape.Tetrahedron)

    DieMesh.of(DieShape.Tetrahedron).faces.forEach { face ->
      assertTrue(
        "cell ${face.index} is not on the face opposite corner ${face.index}",
        face.normal.approximates(-corners[face.index!!].normalised(), TOLERANCE),
      )
      assertEquals("a tetrahedron's faces are triangles", TRIANGLE, face.positions.size)
    }
  }

  @Test
  fun `each solid's faces have the number of corners that solid's faces have`() {
    CORNERS_PER_FACE.forEach { (shape, corners) ->
      DieMesh.of(shape).faces.filter { it.index != null }.forEach { face ->
        assertEquals("${shape.id} face ${face.index}", corners, face.positions.size)
      }
    }
  }

  /** Every triangle edge of [face], each written the same way round both times. */
  private fun edgesOf(face: MeshFace): List<Pair<Corner, Corner>> =
    face.triangles.chunked(TRIANGLE).flatMap { triangle ->
      triangle.indices.map { step ->
        val from = corner(face.positions[triangle[step]])
        val to = corner(face.positions[triangle[(step + 1) % TRIANGLE]])
        if (from <= to) from to to else to to from
      }
    }

  /** How far a corner sits from the middle of its cell, in cells. */
  private fun hypot(
    grid: ShapeAtlas.Grid,
    uv: TextureCoordinate,
    column: Int,
    row: Int,
  ): Double {
    val across = uv.u * grid.columns - column - HALF
    val down = uv.v * grid.rows - row - HALF
    return sqrt(across * across + down * down)
  }

  /** True when this face is steep enough to have an "up" of its own to check. */
  private fun upright(face: MeshFace): Boolean = face.index != null && abs(face.normal dot Vector3.Up) < LYING_FLAT

  /** A corner rounded to a key, so two faces meeting at one agree it is one. */
  private data class Corner(
    val x: Long,
    val y: Long,
    val z: Long,
  ) : Comparable<Corner> {
    override fun compareTo(other: Corner): Int = compareValuesBy(this, other, Corner::x, Corner::y, Corner::z)
  }

  private fun corner(position: Vector3): Corner =
    Corner(
      x = Math.round(position.x * ROUNDING),
      y = Math.round(position.y * ROUNDING),
      z = Math.round(position.z * ROUNDING),
    )

  private companion object {
    const val TOLERANCE = 1e-9
    const val TRIANGLE = 3
    const val COIN_SEGMENTS = 24
    const val ROUNDING = 1e6
    const val HALF = 0.5

    /** Past this, a face is lying flat and has no "up" of its own to check. */
    const val LYING_FLAT = 0.999

    val CORNERS_PER_FACE =
      mapOf(
        DieShape.Coin to COIN_SEGMENTS,
        DieShape.Tetrahedron to TRIANGLE,
        DieShape.Cube to 4,
        DieShape.Octahedron to TRIANGLE,
        DieShape.PentagonalTrapezohedron to 4,
        DieShape.Dodecahedron to 5,
        DieShape.EnneagonalTrapezohedron to 4,
        DieShape.Icosahedron to TRIANGLE,
      )
  }
}
