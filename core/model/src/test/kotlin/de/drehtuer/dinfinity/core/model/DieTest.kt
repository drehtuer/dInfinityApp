package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DieTest {
  @Test
  fun `a standard die runs 1 up to its face count`() {
    val d20 = Die.standard("d20", DieShape.Icosahedron)
    assertEquals((1..20).toList(), d20.values())
    assertEquals(20, d20.maxValue)
    assertEquals(1, d20.minValue)
  }

  @Test
  fun `faces default to printing their own value`() {
    val d6 = Die.standard("d6", DieShape.Cube)
    assertEquals(listOf("1", "2", "3", "4", "5", "6"), d6.faces.map(Face::label))
  }

  @Test
  fun `a die takes its shape's natural read unless told otherwise`() {
    assertEquals(FaceRead.VertexUp, Die.standard("d4", DieShape.Tetrahedron).read)
    assertEquals(FaceRead.FaceUp, Die.standard("d6", DieShape.Cube).read)
  }

  @Test
  fun `a cube labelled 1,2,1,2,1,2 is a d2 with no special case`() {
    val d2 = fakeDie("d2-as-d6", DieShape.Cube, listOf(1, 2, 1, 2, 1, 2))
    assertEquals(2, d2.maxValue)
    assertEquals(1, d2.minValue)
    assertEquals(listOf(1, 2, 1, 2, 1, 2), d2.values())
  }

  @Test
  fun `a tens d10 scores in tens`() {
    val tens = fakeDie("d10-tens", DieShape.PentagonalTrapezohedron, (0..9).map { it * 10 })
    assertEquals(90, tens.maxValue)
    assertEquals(0, tens.minValue)
    assertEquals(30, tens.valueAt(3))
  }

  @Test
  fun `a fudge die is a cube of minus one, zero and one`() {
    val fudge = fakeDie("dF", DieShape.Cube, listOf(-1, -1, 0, 0, 1, 1))
    assertEquals(1, fudge.maxValue)
    assertEquals(-1, fudge.minValue)
  }

  @Test
  fun `too few faces for the shape is refused`() {
    val tooFew =
      assertFailsWith<IllegalArgumentException> {
        Die(id = "d6", shape = DieShape.Cube, faces = listOf(Face.labelled(0, 1)))
      }
    assertTrue("needs 6 faces" in tooFew.message.orEmpty(), tooFew.message.orEmpty())
  }

  @Test
  fun `too many faces for the shape is refused`() {
    assertFailsWith<IllegalArgumentException> {
      fakeDie("d6", DieShape.Cube, listOf(1, 2, 3, 4, 5, 6, 7))
    }
  }

  @Test
  fun `every standard die id is a legal slug`() {
    assertTrue(DiceSet.StandardDieIds.all { Die.IdPattern.matches(it) }, "${DiceSet.StandardDieIds}")
  }

  private fun fakeDie(
    id: String,
    shape: DieShape,
    values: List<Int>,
  ): Die =
    Die(
      id = id,
      shape = shape,
      faces = values.mapIndexed { index, value -> Face.labelled(index, value) },
    )
}
