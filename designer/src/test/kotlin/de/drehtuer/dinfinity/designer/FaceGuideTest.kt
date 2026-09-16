package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.FaceRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the guide shows under a face being drawn
 * (`docs/face-designer.md`; `docs/dice-sets.md`, "The d4").
 *
 * Most of this file is about the d4, because most of the rule is. Its numbers
 * belong to corners rather than faces, and the thing `docs/TODO.md` asks for —
 * that two faces sharing an edge cannot disagree along it — is the one claim
 * here worth proving rather than asserting once.
 */
class FaceGuideTest {
  @Test
  fun `an ordinary die shows the one number that face scores`() {
    val marks = FaceGuide.of(d6, cell = 3)

    assertEquals(listOf(GuideMark(4, GuideSpot.Middle)), marks)
  }

  @Test
  fun `a cell that is not a cell of this die has no guide at all`() {
    // The cell index comes from a strip the finger swipes, so it can be asked
    // for out of range while a die is being swapped.
    assertEquals(emptyList<GuideMark>(), FaceGuide.of(d6, cell = 6))
    assertEquals(emptyList<GuideMark>(), FaceGuide.of(d6, cell = -1))
  }

  @Test
  fun `a d4 shows three numbers, because its numbers belong to corners`() {
    // Cell 0 is the triangle opposite corner 0, so it carries corners 1, 2, 3.
    val marks = FaceGuide.of(d4, cell = 0)

    assertEquals(3, marks.size)
    assertEquals(listOf(2, 3, 4), marks.map(GuideMark::value))
    assertEquals(listOf(GuideSpot.FirstCorner, GuideSpot.SecondCorner, GuideSpot.ThirdCorner), marks.map { it.spot })
  }

  @Test
  fun `every cell of a d4 carries the three corners that are not its own`() {
    (0 until 4).forEach { cell ->
      val expected = (0 until 4).filter { it != cell }.map { it + 1 }
      assertEquals("cell $cell", expected, FaceGuide.of(d4, cell).map(GuideMark::value))
    }
  }

  @Test
  fun `two cells sharing an edge agree along it, and cannot be made to disagree`() {
    // The rule that makes a d4 readable: when a corner points up, all three
    // faces you can see carry that corner's number. Cells 0 and 1 share the
    // edge between corners 2 and 3, so both must show 3 and 4.
    //
    // This is not a check the designer performs — it is what falls out of
    // reading the value from the corner. There is no second copy to disagree
    // with, which is what `docs/TODO.md` means by "hard to do by accident
    // rather than a warning afterwards".
    val shared = FaceGuide.of(d4, 0).map(GuideMark::value).intersect(FaceGuide.of(d4, 1).map(GuideMark::value).toSet())

    assertEquals(setOf(3, 4), shared)
  }

  @Test
  fun `every pair of cells shares exactly the two corners their edge joins`() {
    // Four triangles, six edges, and each pair of cells meets along one.
    (0 until 4).forEach { one ->
      (one + 1 until 4).forEach { other ->
        val shared =
          FaceGuide.of(d4, one).map(GuideMark::value).toSet() intersect
            FaceGuide.of(d4, other).map(GuideMark::value).toSet()
        assertEquals("cells $one and $other", 2, shared.size)
      }
    }
  }

  @Test
  fun `a d4 painted to be read face up is an ordinary die again`() {
    // A set may do that, and then its cells carry one number each like any
    // other. The shape alone does not decide it; the die's own read does.
    val faceUp = d4.copy(read = FaceRead.FaceUp)

    assertFalse(FaceGuide.isCornerRead(faceUp))
    assertEquals(listOf(GuideMark(1, GuideSpot.Middle)), FaceGuide.of(faceUp, cell = 0))
  }

  @Test
  fun `the corner read is the tetrahedron's and no other shape's`() {
    assertTrue(FaceGuide.isCornerRead(d4))
    assertFalse(FaceGuide.isCornerRead(d6))
  }

  @Test
  fun `a d4 whose corners are labelled oddly still agrees with itself`() {
    // Values are inherited from the die being copied, so they need not be
    // 1..4 — a set may label a d4 with anything. The rule is about corners,
    // not about counting.
    val odd = die(DieShape.Tetrahedron, listOf(7, 7, 9, -2), FaceRead.VertexUp)

    assertEquals(listOf(7, 9, -2), FaceGuide.of(odd, cell = 0).map(GuideMark::value))
    assertEquals(listOf(7, 7, 9), FaceGuide.of(odd, cell = 3).map(GuideMark::value))
  }

  private fun die(
    shape: DieShape,
    values: List<Int>,
    read: FaceRead = shape.naturalRead,
  ) = Die(
    id = shape.id,
    shape = shape,
    faces = values.mapIndexed { index, value -> Face(index = index, value = value, label = value.toString()) },
    read = read,
  )

  private val d4 = die(DieShape.Tetrahedron, listOf(1, 2, 3, 4))
  private val d6 = die(DieShape.Cube, listOf(1, 2, 3, 4, 5, 6))

  @Test
  fun `says which face each corner of a cell reads, so a stamp can print its label`() {
    // The guide reduces a corner to a number; what is *printed* there is the
    // face's label, so the faces themselves are what is offered
    // (`FaceStamp.numbers`).
    val corners = FaceGuide.cornersOf(d4, cell = 0)

    assertEquals(listOf(1, 2, 3), corners.map { (face, _) -> face })
    assertEquals(
      listOf(GuideSpot.FirstCorner, GuideSpot.SecondCorner, GuideSpot.ThirdCorner),
      corners.map { (_, spot) -> spot },
    )
  }

  @Test
  fun `has no corners for a die read face-up, or for a cell it does not have`() {
    assertTrue(FaceGuide.cornersOf(d6, cell = 0).isEmpty())
    assertTrue(FaceGuide.cornersOf(d4, cell = 4).isEmpty())
  }
}
