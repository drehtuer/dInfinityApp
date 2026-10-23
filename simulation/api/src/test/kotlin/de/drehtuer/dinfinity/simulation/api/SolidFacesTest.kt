package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.LongDieShape
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
      (SolidFaces.of(shape) + SolidFaces.bareOf(shape)).forEach { face ->
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
      (SolidFaces.of(shape) + SolidFaces.bareOf(shape)).forEach { face ->
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
  fun `a long d4 has four long faces it is read from and eight ends it is not`() {
    val shape = DieShape.ElongatedSquareBipyramid
    val cells = SolidFaces.of(shape)
    val ends = SolidFaces.bareOf(shape)

    assertEquals(4, cells.size)
    assertTrue(cells.all { it.corners.size == 4 && it.numbered }, "a long face is a rectangle with a cell")
    assertEquals(8, ends.size)
    assertTrue(ends.all { it.corners.size == TRIANGLE && !it.numbered }, "an end is a triangle with no cell")
    // Every other shape is all cells: a coin's rim is built where it is drawn.
    DieShape.entries.filter { it != shape }.forEach { assertTrue(SolidFaces.bareOf(it).isEmpty(), it.id) }
  }

  @Test
  fun `a long face is as long and as wide as the catalogue says`() {
    SolidFaces.of(DieShape.ElongatedSquareBipyramid).forEach { face ->
      val sides =
        face.corners.indices
          .map { (face.corners[it] - face.corners[(it + 1) % face.corners.size]).length }
          .sorted()
      assertEquals(2 * LongDieShape.HALF_WIDTH, sides.first(), TOLERANCE, "face ${face.index} is the wrong width")
      assertEquals(2 * LongDieShape.BODY_HALF_LENGTH, sides.last(), TOLERANCE, "face ${face.index} is the wrong length")
    }
  }

  @Test
  fun `a long d4's faces and ends close the solid, every edge shared by two of them`() {
    val faces = SolidFaces.of(DieShape.ElongatedSquareBipyramid) + SolidFaces.bareOf(DieShape.ElongatedSquareBipyramid)
    val edges =
      faces.flatMap { face ->
        face.corners.indices.map { setOf(key(face.corners[it]), key(face.corners[(it + 1) % face.corners.size])) }
      }
    assertTrue(
      edges
        .groupingBy { it }
        .eachCount()
        .values
        .all { it == 2 },
      "an edge belongs to one face, or three",
    )
    // Euler: ten corners, twenty edges, twelve faces.
    assertEquals(2, ShapeGeometry.verticesOf(DieShape.ElongatedSquareBipyramid).size - edges.toSet().size + faces.size)
  }

  @Test
  fun `a long d4 cannot rest on an end, because its middle is never over one`() {
    // A die rests on a face when the point under its middle is inside that
    // face; for every end, that point is beyond the end's base, so the die
    // tips back onto a long face (`LongDieShape.BODY_HALF_LENGTH`).
    SolidFaces.bareOf(DieShape.ElongatedSquareBipyramid).forEach { end ->
      val foot = end.normal * (end.corners.first() dot end.normal)
      val inside =
        end.corners.indices.all { at ->
          val here = end.corners[at]
          val next = end.corners[(at + 1) % end.corners.size]
          (cross(here - foot, next - foot) dot end.normal) > 0
        }
      assertTrue(!inside, "a long d4 can balance on the end facing ${end.normal}")
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

  /** A corner as something two faces can agree on despite the last bit of rounding. */
  private fun key(corner: Vector3): Triple<Long, Long, Long> =
    Triple(Math.round(corner.x * KEY), Math.round(corner.y * KEY), Math.round(corner.z * KEY))

  private fun lyingFlat(face: SolidFace): Boolean =
    face.normal.approximates(Vector3.Up, FLAT) || face.normal.approximates(-Vector3.Up, FLAT)

  private companion object {
    const val TOLERANCE = 1e-9
    const val HALF = 0.5
    const val TRIANGLE = 3
    const val KEY = 1e6

    /** How near a face has to be to straight up before it counts as lying flat. */
    const val FLAT = 1e-6
  }
}
