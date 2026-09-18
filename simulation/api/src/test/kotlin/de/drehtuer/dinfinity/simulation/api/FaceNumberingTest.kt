package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A die is numbered the way a moulded one is: opposite faces add up
 * (`docs/dice-sets.md`, "Numbering").
 *
 * The rule is stated as arithmetic over the shape's own normals, so these
 * tests are about the solids rather than about a table somebody typed.
 */
class FaceNumberingTest {
  /** Every shape but the tetrahedron, which has no opposite faces at all. */
  private val paired = DieShape.entries.filter { it != DieShape.Tetrahedron }

  @Test
  fun `every face of a paired solid faces away from exactly one other`() {
    paired.forEach { shape ->
      val opposites = ShapeGeometry.oppositesOf(shape)
      assertEquals(shape.faceCount, opposites.size, "$shape")
      opposites.forEachIndexed { face, across ->
        assertNotNull(across, "$shape face $face faces nothing")
        assertTrue(across != face, "$shape face $face was called its own opposite")
        assertEquals(face, opposites[across], "$shape face $face and $across disagree")
      }
    }
  }

  @Test
  fun `an opposite really points the other way`() {
    paired.forEach { shape ->
      val directions = ShapeGeometry.directionsOf(shape).map(Vector3::normalised)
      ShapeGeometry.oppositesOf(shape).forEachIndexed { face, across ->
        val sum = directions[face] + directions[assertNotNull(across)]
        assertTrue(sum.length < TOLERANCE, "$shape face $face and $across are not opposite: $sum")
      }
    }
  }

  @Test
  fun `a tetrahedron has no opposites, because its numbers are corners`() {
    assertEquals(
      List(DieShape.Tetrahedron.faceCount) { null },
      ShapeGeometry.oppositesOf(DieShape.Tetrahedron),
    )
  }

  @Test
  fun `a plain die's opposite faces sum to one more than its face count`() {
    paired.forEach { shape ->
      val values = FaceNumbering.plain(shape)
      assertEquals((1..shape.faceCount).toList(), values.sorted(), "$shape is not 1 to n")
      ShapeGeometry.oppositesOf(shape).forEachIndexed { face, across ->
        assertEquals(
          shape.faceCount + 1,
          values[face] + values[assertNotNull(across)],
          "$shape faces $face and $across",
        )
      }
    }
  }

  @Test
  fun `a d4 keeps one to four at its corners`() {
    assertEquals(listOf(1, 2, 3, 4), FaceNumbering.plain(DieShape.Tetrahedron))
  }

  @Test
  fun `the first face still carries the lowest number, so counting starts where it did`() {
    DieShape.entries.forEach { shape -> assertEquals(1, FaceNumbering.plain(shape).first(), "$shape") }
  }

  @Test
  fun `a fudge die comes out a minus across from a plus and a blank across from a blank`() {
    val values = FaceNumbering.paired(DieShape.Cube, listOf(-1, -1, 0, 0, 1, 1))
    assertEquals(listOf(-1, -1, 0, 0, 1, 1), values.sorted())
    ShapeGeometry.oppositesOf(DieShape.Cube).forEachIndexed { face, across ->
      assertEquals(0, values[face] + values[assertNotNull(across)], "faces $face and $across")
    }
  }

  @Test
  fun `a tens d10 pairs nought with ninety`() {
    val values = FaceNumbering.paired(DieShape.PentagonalTrapezohedron, (0..9).map { it * 10 })
    ShapeGeometry.oppositesOf(DieShape.PentagonalTrapezohedron).forEachIndexed { face, across ->
      assertEquals(90, values[face] + values[assertNotNull(across)], "faces $face and $across")
    }
  }

  @Test
  fun `the order the values are handed over in makes no difference`() {
    assertEquals(
      FaceNumbering.plain(DieShape.Icosahedron),
      FaceNumbering.paired(DieShape.Icosahedron, (1..DieShape.Icosahedron.faceCount).reversed().toList()),
    )
  }

  @Test
  fun `a list that is not this shape's faces is refused rather than half laid out`() {
    assertFailsWith<IllegalArgumentException> { FaceNumbering.paired(DieShape.Cube, listOf(1, 2, 3)) }
  }

  @Test
  fun `a coin is two faces that add up like any other pair`() {
    assertEquals(listOf(1, 2), FaceNumbering.plain(DieShape.Coin))
    assertEquals(listOf(1, 0), ShapeGeometry.oppositesOf(DieShape.Coin))
  }

  @Test
  fun `a shape's opposites are the same list every time it is asked`() {
    assertEquals(ShapeGeometry.oppositesOf(DieShape.Cube), ShapeGeometry.oppositesOf(DieShape.Cube))
    assertNull(ShapeGeometry.oppositesOf(DieShape.Tetrahedron).first())
  }

  /** Slack for arithmetic, not for geometry: a real opposite is exact. */
  private companion object {
    const val TOLERANCE = 1e-9
  }
}
