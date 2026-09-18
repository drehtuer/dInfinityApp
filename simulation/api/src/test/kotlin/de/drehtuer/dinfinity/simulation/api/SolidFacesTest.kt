package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The faces of the catalogue's solids, found by grouping corners onto face
 * planes (`SolidFaces`).
 *
 * The mesh the tray draws and the solid the face designer turns over are both
 * built from this, so what is asserted here is what both of them rely on: a
 * face is a polygon, it is wound the right way, every corner of it is on its
 * own plane, and its cell coordinates fill the cell the right way up.
 */
class SolidFacesTest {
  @Test
  fun `every shape has one face per readable position`() {
    DieShape.entries.forEach { shape ->
      assertEquals(ShapeGeometry.directionsOf(shape).size, SolidFaces.of(shape).size, shape.id)
      SolidFaces.of(shape).forEachIndexed { index, face ->
        assertEquals(index, face.index, "${shape.id} face $index keeps its place")
      }
    }
  }

  @Test
  fun `a face is a polygon, and every corner of it is on its plane`() {
    DieShape.entries.forEach { shape ->
      SolidFaces.of(shape).forEach { face ->
        assertTrue(face.corners.size >= TRIANGLE, "${shape.id} face ${face.index} is not a polygon")
        val height = face.corners.first() dot face.normal
        face.corners.forEach { corner ->
          assertEquals(
            height,
            corner dot face.normal,
            TOLERANCE,
            "${shape.id} face ${face.index} has a corner off its plane",
          )
        }
      }
    }
  }

  @Test
  fun `a face is wound anticlockwise as seen from outside`() {
    DieShape.entries.forEach { shape ->
      SolidFaces.of(shape).forEach { face ->
        face.corners.indices.forEach { corner ->
          val here = face.corners[corner]
          val next = face.corners[(corner + 1) % face.corners.size]
          assertTrue(
            (cross(here - face.centre, next - face.centre) dot face.normal) > 0,
            "${shape.id} face ${face.index} is wound inside out at corner $corner",
          )
        }
      }
    }
  }

  @Test
  fun `a d4's cell is the face opposite the corner it is read from`() {
    assertEquals(FaceRead.VertexUp, DieShape.Tetrahedron.naturalRead, "a tetrahedron is read from its corners")
    SolidFaces.of(DieShape.Tetrahedron).forEach { face ->
      val corner = ShapeGeometry.directionsOf(DieShape.Tetrahedron)[face.index].normalised()
      assertEquals(-1.0, face.normal dot corner, TOLERANCE, "cell ${face.index} is not opposite corner ${face.index}")
      assertTrue(
        face.corners.none { it.normalised().approximates(corner, TOLERANCE) },
        "the corner a d4 is read from is one of the corners of its own cell",
      )
    }
  }

  @Test
  fun `the frame is right-handed, and lies in the face`() {
    DieShape.entries.forEach { shape ->
      SolidFaces.of(shape).forEach { face ->
        val where = "${shape.id} face ${face.index}"
        assertEquals(0.0, face.along dot face.up, TOLERANCE, "$where has a skewed frame")
        assertEquals(0.0, face.along dot face.normal, TOLERANCE, "$where is not drawn flat on")
        assertEquals(0.0, face.up dot face.normal, TOLERANCE, "$where is not drawn flat on")
        assertTrue(cross(face.along, face.up).approximates(face.normal, TOLERANCE), "$where is turned inside out")
      }
    }
  }

  @Test
  fun `a face fills its own cell and does not spill out of it`() {
    DieShape.entries.forEach { shape ->
      SolidFaces.of(shape).forEach { face ->
        val reach =
          face.corners.map { corner ->
            val (u, v) = face.cellOf(corner)
            hypot(u - HALF, v - HALF)
          }
        assertEquals(HALF, reach.max(), TOLERANCE, "${shape.id} face ${face.index} does not fill its cell")
        assertTrue(reach.all { it <= HALF + TOLERANCE }, "${shape.id} face ${face.index} spills out of its cell")
      }
    }
  }

  @Test
  fun `a face is drawn the right way up`() {
    // Up on a face is up in the tray, and a cell counts its rows downwards —
    // so of a face that is not simply lying flat, the highest corner is the
    // one with the smallest `v`. A face that *is* lying flat has no up of its
    // own and is turned by the tray's `+y` instead.
    DieShape.entries.forEach { shape ->
      SolidFaces.of(shape).filterNot(::lyingFlat).forEach { face ->
        assertEquals(
          face.corners.maxOf { it.z },
          face.corners.minBy { face.cellOf(it).second }.z,
          TOLERANCE,
          "${shape.id} face ${face.index} is drawn upside down",
        )
      }
    }
  }

  @Test
  fun `a face's middle is inside its own corners, and its circle reaches them`() {
    DieShape.entries.forEach { shape ->
      SolidFaces.of(shape).forEach { face ->
        assertTrue(
          face.corners.all { it.length > face.centre.length },
          "${shape.id} face ${face.index} has its middle outside its own corners",
        )
        assertEquals(
          face.corners.maxOf { (it - face.centre).length },
          face.radius,
          TOLERANCE,
          "${shape.id} face ${face.index} has the wrong circle",
        )
      }
    }
  }

  @Test
  fun `a point on a face reads back as the offset it was given`() {
    val face = SolidFaces.of(DieShape.Cube).first()
    val corner = face.corners.first()

    val (across, upwards) = face.flatOf(corner)

    assertEquals(corner.x - face.centre.x, across, TOLERANCE, "the corner is not where the frame says")
    assertEquals(corner.y - face.centre.y, upwards, TOLERANCE, "the corner is not where the frame says")
  }

  @Test
  fun `the faces of a shape are worked out once and kept`() {
    assertSame(SolidFaces.of(DieShape.Cube), SolidFaces.of(DieShape.Cube), "a shape is re-solved every time")
    assertNotEquals(SolidFaces.of(DieShape.Cube), SolidFaces.of(DieShape.Octahedron), "two shapes share one answer")
  }

  private fun lyingFlat(face: SolidFace): Boolean =
    face.normal.approximates(Vector3.Up, FLAT) || face.normal.approximates(-Vector3.Up, FLAT)

  private companion object {
    const val TOLERANCE = 1e-9
    const val HALF = 0.5
    const val TRIANGLE = 3

    /** How near a face has to be to straight up before it counts as lying flat. */
    const val FLAT = 1e-6
  }
}
