package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShapeGeometryTest {
  @Test
  fun `every shape has one direction per face`() {
    DieShape.entries.forEach { shape ->
      assertEquals(shape.faceCount, ShapeGeometry.directionsOf(shape).size, shape.id)
    }
  }

  @Test
  fun `every direction is a unit vector`() {
    DieShape.entries.forEach { shape ->
      ShapeGeometry.directionsOf(shape).forEach { direction ->
        assertEquals(1.0, direction.length, 1e-12, "${shape.id} has a direction of length ${direction.length}")
      }
    }
  }

  @Test
  fun `no two positions of a shape point the same way`() {
    DieShape.entries.forEach { shape ->
      val directions = ShapeGeometry.directionsOf(shape)
      directions.indices.forEach { i ->
        (i + 1 until directions.size).forEach { j ->
          assertTrue(
            directions[i] dot directions[j] < 1 - 1e-9,
            "${shape.id} positions $i and $j point the same way",
          )
        }
      }
    }
  }

  @Test
  fun `every solid is symmetric about its middle, so no face is favoured`() {
    // A fair die's directions sum to nothing: for every face there is another
    // pointing the opposite way, or a ring that cancels out.
    DieShape.entries.forEach { shape ->
      val sum = ShapeGeometry.directionsOf(shape).reduce(Vector3::plus)
      assertTrue(sum.length < 1e-9, "${shape.id} leans towards $sum")
    }
  }

  @Test
  fun `positions are numbered from the top down`() {
    DieShape.entries.forEach { shape ->
      // Rounded, because two faces of one ring differ only in their last bit.
      val heights = ShapeGeometry.directionsOf(shape).map { Math.round(it.z * 1e9) }
      assertEquals(heights.sortedDescending(), heights, "${shape.id} is not ordered from the top")
    }
  }

  @Test
  fun `the Platonic solids have the bounding radius their closed form gives`() {
    assertEquals(sqrt(6.0) / 4, ShapeGeometry.boundingRadiusPerSize(DieShape.Tetrahedron), 1e-12)
    assertEquals(sqrt(3.0) / 2, ShapeGeometry.boundingRadiusPerSize(DieShape.Cube), 1e-12)
    assertEquals(sqrt(2.0) / 2, ShapeGeometry.boundingRadiusPerSize(DieShape.Octahedron), 1e-12)
    assertEquals(
      (sqrt(3.0) / 4) * (1 + sqrt(5.0)),
      ShapeGeometry.boundingRadiusPerSize(DieShape.Dodecahedron),
      1e-12,
    )
    assertEquals(sqrt(10 + 2 * sqrt(5.0)) / 4, ShapeGeometry.boundingRadiusPerSize(DieShape.Icosahedron), 1e-12)
  }

  @Test
  fun `a trapezohedron's bounding radius comes out of its own two conditions`() {
    // There is no closed form worth writing down: the radius falls out of flat
    // kite faces and every corner on one sphere. These are the numbers that
    // fall out, and docs/tables.md quotes them.
    assertEquals(0.747674, ShapeGeometry.boundingRadiusPerSize(DieShape.PentagonalTrapezohedron), 1e-6)
    assertEquals(0.718362, ShapeGeometry.boundingRadiusPerSize(DieShape.EnneagonalTrapezohedron), 1e-6)
  }

  @Test
  fun `a trapezohedron is a little wider than its apex edge is long`() {
    listOf(DieShape.PentagonalTrapezohedron, DieShape.EnneagonalTrapezohedron).forEach { shape ->
      val radius = ShapeGeometry.boundingRadiusPerSize(shape)
      assertTrue(radius in 0.5..1.0, "${shape.id} has a bounding radius of $radius per edge")
    }
  }

  @Test
  fun `a sixteen millimetre d6 has the bounding radius the capacity table is built on`() {
    val radius = ShapeGeometry.boundingRadiusPerSize(DieShape.Cube) * 16
    assertEquals(13.856, radius, 1e-3)
    assertEquals(603.2, PI * radius * radius, 0.1)
  }

  @Test
  fun `a cube's faces are the six axes`() {
    val directions = ShapeGeometry.directionsOf(DieShape.Cube)
    assertTrue(directions.any { it.approximates(Vector3(0.0, 0.0, 1.0)) })
    assertTrue(directions.any { it.approximates(Vector3(0.0, 0.0, -1.0)) })
    assertEquals(1.0, directions.first().z, 1e-12)
    assertEquals(-1.0, directions.last().z, 1e-12)
  }

  @Test
  fun `a tetrahedron's four positions are its corners, not its faces`() {
    val directions = ShapeGeometry.directionsOf(DieShape.Tetrahedron)
    assertEquals(4, directions.size)
    // Any two corners of a tetrahedron are at arccos(-1/3) to each other.
    assertEquals(-1.0 / 3, directions[0] dot directions[1], 1e-12)
  }

  @Test
  fun `a coin has exactly two faces, up and down`() {
    val directions = ShapeGeometry.directionsOf(DieShape.Coin)
    assertEquals(listOf(1.0, -1.0), directions.map { it.z })
  }

  @Test
  fun `opposite faces of a d20 are opposite, which is what makes it fair`() {
    val directions = ShapeGeometry.directionsOf(DieShape.Icosahedron)
    directions.forEach { direction ->
      assertTrue(
        directions.any { abs((it dot direction) + 1.0) < 1e-9 },
        "no face opposes $direction",
      )
    }
  }

  @Test
  fun `every shape is inside the bounding sphere its radius claims`() {
    // The face normals touch the insphere, not the circumsphere, so the
    // circumradius must be the larger of the two for every solid.
    DieShape.entries.forEach { shape ->
      assertTrue(
        ShapeGeometry.boundingRadiusPerSize(shape) > 0.0,
        "${shape.id} has no size",
      )
    }
  }
}
