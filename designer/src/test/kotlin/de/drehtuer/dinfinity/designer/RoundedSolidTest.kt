package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.HullMargin
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rounded die the turning die is drawn as (`docs/face-designer.md`,
 * "Material, colour and edges").
 */
class RoundedSolidTest {
  private fun rounded(
    shape: DieShape,
    share: Double = DieMaterial.DEFAULT_EDGE_ROUNDING,
  ) = RoundedSolid(SolidFaces.of(shape), DieMaterial(edgeRounding = share))

  @Test
  fun `the radius is what the die asks for, in units of half its size`() {
    // 3 % of the size is 6 % of half of it.
    assertEquals(0.06, rounded(DieShape.Cube).radius, 1e-12)
    assertEquals(0.24, rounded(DieShape.Icosahedron, 0.12).radius, 1e-12)
  }

  @Test
  fun `a d4's points are cut back as the solver cuts them, to about half`() {
    val d4 = rounded(DieShape.Tetrahedron)
    val asked = HullMargin.requestedMm(DieMaterial()) / (DieMaterial().sizeMm / 2)

    assertTrue("a d4 was rounded as much as it asked", d4.radius < asked * 0.6)
    assertTrue(d4.radius > asked * 0.4)
  }

  @Test
  fun `every flat part is on its face's plane and inside the sharp face`() {
    DieShape.entries.forEach { shape ->
      val solid = rounded(shape, 0.12)
      SolidFaces.of(shape).forEach { face ->
        val flat = solid.flats.getValue(face.index)
        val plane = face.normal dot face.corners.first()
        flat.forEach { corner ->
          assertEquals("$shape face ${face.index} left its plane", plane, face.normal dot corner, 1e-9)
          assertTrue(
            "$shape face ${face.index} grew past its middle-to-corner reach",
            (corner - face.centre).length <= face.radius + 1e-9,
          )
        }
      }
    }
  }

  @Test
  fun `a rounder die has smaller flat parts`() {
    val sharp = rounded(DieShape.Cube, 0.015).flats.getValue(0)
    val round = rounded(DieShape.Cube, 0.12).flats.getValue(0)

    assertTrue(spanOf(round) < spanOf(sharp))
  }

  @Test
  fun `there is a band for every edge and a patch for every corner`() {
    assertEquals(12, rounded(DieShape.Cube).bands.size)
    assertEquals(8, rounded(DieShape.Cube).patches.size)
    assertEquals(30, rounded(DieShape.Icosahedron).bands.size)
    assertEquals(12, rounded(DieShape.Icosahedron).patches.size)
    assertEquals(6, rounded(DieShape.Tetrahedron).bands.size)
    rounded(DieShape.Cube).bands.forEach { assertEquals(2, it.faces.size) }
    rounded(DieShape.Cube).patches.forEach { assertEquals(3, it.faces.size) }
  }

  @Test
  fun `the rounded die stays inside the sharp one`() {
    DieShape.entries.forEach { shape ->
      val faces = SolidFaces.of(shape)
      rounded(shape, 0.12).outlinePoints.forEach { point ->
        faces.forEach { face ->
          assertTrue(
            "$shape stands outside face ${face.index}",
            (face.normal dot point) <= (face.normal dot face.corners.first()) + 1e-9,
          )
        }
      }
    }
  }

  @Test
  fun `a coin's rim belongs to no face and is not rounded`() {
    val coin = rounded(DieShape.Coin)

    assertTrue(coin.bands.isEmpty())
    assertTrue(coin.patches.isEmpty())
    SolidFaces.of(DieShape.Coin).forEach { face ->
      assertEquals(face.corners, coin.flats.getValue(face.index))
    }
  }

  private fun spanOf(points: List<Vector3>): Double = points.maxOf { a -> points.maxOf { b -> (a - b).length } }
}
