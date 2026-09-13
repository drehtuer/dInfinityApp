package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.fixtures.StandardDice
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
  fun `the Platonic solids have the proportions their closed forms give`() {
    // Measured off the corners this catalogue actually builds, not read back
    // out of a table of the same constants. Every solid here has its corners
    // on the unit sphere, so the circumradius is 1 and the closed form for
    // circumradius-per-edge is simply one over the edge length.
    assertEquals(sqrt(6.0) / 4, 1 / edgeOf(DieShape.Tetrahedron), 1e-12)
    assertEquals(sqrt(3.0) / 2, 1 / edgeOf(DieShape.Cube), 1e-12)
    assertEquals(sqrt(2.0) / 2, 1 / edgeOf(DieShape.Octahedron), 1e-12)
    assertEquals((sqrt(3.0) / 4) * (1 + sqrt(5.0)), 1 / edgeOf(DieShape.Dodecahedron), 1e-12)
    assertEquals(sqrt(10 + 2 * sqrt(5.0)) / 4, 1 / edgeOf(DieShape.Icosahedron), 1e-12)
  }

  @Test
  fun `a sixteen millimetre die is sixteen millimetres across, whatever shape it is`() {
    // What `size_mm` means, asserted once for every shape in the catalogue.
    // It used to mean the edge length, which a dice maker quotes and nobody
    // else means: it made a 16 mm d12 45 mm across (`docs/dice-sets.md`).
    StandardDice.all.forEach { die ->
      val sized = die.copy(material = die.material.copy(sizeMm = 16.0))

      assertEquals(
        8.0,
        ShapeGeometry.hullOf(sized).maxOf { it.length },
        1e-9,
        "a 16 mm ${die.shape.id} is not 16 mm across",
      )
    }
  }

  @Test
  fun `a die is shrunk by the scale the table asked for`() {
    val die = StandardDice.d6.let { it.copy(material = it.material.copy(sizeMm = 16.0)) }

    assertEquals(4.0, ShapeGeometry.hullOf(die, scale = 0.5).maxOf { it.length }, 1e-9)
  }

  /** The edge length of a solid whose corners are on the unit sphere. */
  private fun edgeOf(shape: DieShape): Double {
    val corners = ShapeGeometry.verticesOf(shape)
    return corners.indices
      .flatMap { a ->
        (a + 1 until corners.size).map { b -> (corners[a] - corners[b]).length }
      }.min()
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
  fun `every corner is outside every face, which is what makes a solid convex`() {
    // The face normals touch the insphere and the corners the circumsphere, so
    // no corner may lie inside a face's plane. A solid where one did would
    // collide and read as something other than what it is drawn as.
    DieShape.entries.forEach { shape ->
      val corners = ShapeGeometry.verticesOf(shape)
      ShapeGeometry.directionsOf(shape).forEach { face ->
        val insphere = corners.maxOf { it dot face }
        assertTrue(insphere > 0.0, "${shape.id} has a face with nothing behind it")
        assertTrue(insphere <= 1.0 + 1e-9, "${shape.id} has a corner outside its own bounding sphere")
      }
    }
  }
}
