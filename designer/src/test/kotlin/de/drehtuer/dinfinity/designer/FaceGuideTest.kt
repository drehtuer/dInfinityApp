package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.glyphs.BuiltinFont
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import de.drehtuer.dinfinity.simulation.api.Vector3
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

    assertEquals(listOf(4), marks.map(GuideMark::value))
    assertEquals(listOf(GuideSpot.Middle), marks.map(GuideMark::spot))
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
    assertEquals(setOf(2, 3, 4), marks.map(GuideMark::value).toSet())
    assertEquals(listOf(GuideSpot.FirstCorner, GuideSpot.SecondCorner, GuideSpot.ThirdCorner), marks.map { it.spot })
  }

  @Test
  fun `every cell of a d4 carries the three corners that are not its own`() {
    (0 until 4).forEach { cell ->
      val expected = (0 until 4).filter { it != cell }.map { it + 1 }.toSet()
      assertEquals("cell $cell", expected, FaceGuide.of(d4, cell).map(GuideMark::value).toSet())
    }
  }

  @Test
  fun `every corner of a d4's cell carries the number of the corner it is`() {
    // The whole of the d4 rule, and the one thing the old guide got wrong: it
    // handed the three remaining face indices to the three spots in order,
    // which is arithmetic rather than geometry and happens to be right for
    // two of the four cells. Here the claim is made against the solid — take
    // the canvas corner a number is drawn at, carry it onto the real triangle
    // the way the atlas does, and the corner of the tetrahedron it lands
    // beside must be the corner whose number that is.
    val vertices = ShapeGeometry.verticesOf(DieShape.Tetrahedron)

    (0 until 4).forEach { cell ->
      FaceGuide.cornersOf(d4, cell).forEach { (face, spot) ->
        val landed = onSolid(cell, spot)
        val nearest = vertices.indices.minBy { (vertices[it] - landed).length }
        assertEquals("cell $cell draws face $face at its $spot, which is corner $nearest", face, nearest)
      }
    }
  }

  @Test
  fun `two cells sharing an edge agree along it, and cannot be made to disagree`() {
    // The rule that makes a d4 readable: when a corner points up, all three
    // faces you can see carry that corner's number. Six edges, and along each
    // of them the two cells must put the same number at the same *end* — not
    // merely carry the same two numbers somewhere, which is all the old test
    // asked and which every shuffle of three corners satisfies.
    //
    // So the numbers are followed onto the solid and measured: the two copies
    // of one corner's number land beside each other, far closer than the edge
    // they sit along is long. It is not a check the designer performs — it is
    // what falls out of reading both from `SolidFaces`, which is what
    // `docs/TODO.md` means by "hard to do by accident rather than a warning
    // afterwards".
    (0 until 4).forEach { one ->
      (one + 1 until 4).forEach { other ->
        val here = FaceGuide.cornersOf(d4, one).associate { (face, spot) -> face to onSolid(one, spot) }
        val there = FaceGuide.cornersOf(d4, other).associate { (face, spot) -> face to onSolid(other, spot) }
        val shared = (here.keys intersect there.keys).toList()

        assertEquals("cells $one and $other meet along one edge", 2, shared.size)
        val edge = (here.getValue(shared[0]) - here.getValue(shared[1])).length
        shared.forEach { face ->
          val apart = (here.getValue(face) - there.getValue(face)).length
          assertTrue(
            "cells $one and $other put face $face $apart apart across an edge $edge long",
            apart < edge / 2,
          )
        }
      }
    }
  }

  /**
   * Where the canvas corner at [spot] lands on the real triangle of cell
   * [cell], in the tray's own coordinates.
   *
   * The inverse of what the exporter does: a drawing is copied into its cell
   * whole ([AtlasCell.at]), and a cell is the face's own circle flattened onto
   * it ([de.drehtuer.dinfinity.simulation.api.SolidFace.cellOf]) — so a point
   * on the canvas is a point on the face, and where it comes out is a fact
   * about the solid rather than about the guide.
   */
  private fun onSolid(
    cell: Int,
    spot: GuideSpot,
  ): Vector3 {
    val face = SolidFaces.of(DieShape.Tetrahedron)[cell]
    val at = FaceShapes.corner(FaceOutline.Triangle, spot)
    // The middle of the canvas is the middle of the cell, and the canvas
    // counts down the screen where the face counts up.
    val middle = 0.5
    return face.centre +
      face.along * ((at.x - middle) * 2 * face.radius) +
      face.up * ((middle - at.y) * 2 * face.radius)
  }

  @Test
  fun `a d4 painted to be read face up is an ordinary die again`() {
    // A set may do that, and then its cells carry one number each like any
    // other. The shape alone does not decide it; the die's own read does.
    val faceUp = d4.copy(read = FaceRead.FaceUp)

    assertFalse(FaceGuide.isCornerRead(faceUp))
    assertEquals(listOf(1), FaceGuide.of(faceUp, cell = 0).map(GuideMark::value))
    assertEquals(listOf(GuideSpot.Middle), FaceGuide.of(faceUp, cell = 0).map(GuideMark::spot))
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
    // Cell 3 carries corners 0, 1 and 2, and which of them is at which spot
    // is the solid's answer: the apex is corner 0, and 1 and 2 go round the
    // other way from the order they are numbered in.
    assertEquals(listOf(7, 9, 7), FaceGuide.of(odd, cell = 3).map(GuideMark::value))
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
    // Each of the three spots is used once and the cell's own face is not
    // among them. *Which* face is at which spot is geometry, and is asserted
    // against the solid above rather than written down here — a list of
    // indices is exactly the kind of expectation that pinned the bug this
    // test file now exists to catch.
    (0 until 4).forEach { cell ->
      val corners = FaceGuide.cornersOf(d4, cell)

      assertEquals("cell $cell", setOf(0, 1, 2, 3) - cell, corners.map { (face, _) -> face }.toSet())
      assertEquals(
        "cell $cell",
        listOf(GuideSpot.FirstCorner, GuideSpot.SecondCorner, GuideSpot.ThirdCorner),
        corners.map { (_, spot) -> spot },
      )
    }
  }

  @Test
  fun `has no corners for a die read face-up, or for a cell it does not have`() {
    assertTrue(FaceGuide.cornersOf(d6, cell = 0).isEmpty())
    assertTrue(FaceGuide.cornersOf(d4, cell = 4).isEmpty())
  }

  @Test
  fun `draws the numeral rather than a dot where the numeral goes`() {
    // What the bullet in `docs/TODO.md` asked for: the guide used to be a dot
    // because text in a `Canvas` wants a measurer. `core/glyphs` is that
    // measurer, so the guide is the number itself.
    val mark = FaceGuide.of(d6, cell = 5).single()

    assertTrue("a numeral is more than one ring of ink", mark.rings.isNotEmpty())
    assertTrue("a ring encloses something", mark.rings.all { it.size >= 3 })
    // A dot would be a single ring of DOT_SIDES points about the middle; a
    // `6` is not, and reaches further across the face than a dot's tenth.
    val across = mark.rings.flatten().maxOf { it.x } - mark.rings.flatten().minOf { it.x }
    assertTrue("a numeral $across across is a dot", across > 0.2f)
  }

  @Test
  fun `traces exactly what filling the face with numbers would stamp`() {
    // The point of drawing the numeral rather than a dot: somebody who traces
    // the guide and somebody who presses "fill all with numbers" get ink in
    // the same place, because both come from `FaceStamp.printed`.
    listOf(d6, d20, d4).forEach { die ->
      die.faces.indices.forEach { cell ->
        val stamped = FaceStamp.numbers(die, cell, colorArgb = Drawings.INK).map { (it as Stamp).rings }

        assertEquals("${die.id} cell $cell", stamped, FaceGuide.of(die, cell).map(GuideMark::rings))
      }
    }
  }

  @Test
  fun `underlines the guide wherever the tray would underline the number`() {
    // A `6` on a die that also has a `9` gets its bar, and tracing the guide
    // has to put the bar on the drawing too — otherwise a traced die reads
    // upside down.
    val onD20 = ringsOn(d20, cell = 5)
    val onD6 = ringsOn(d6, cell = 5)

    assertEquals(onD6 + 1, onD20)
  }

  @Test
  fun `shows a dot where the face has nothing printed on it`() {
    // A face an author deliberately left blank — half a Fudge die. There is no
    // number to trace and the place is still worth showing.
    val fudge =
      Die(
        id = "df",
        shape = DieShape.Cube,
        faces = List(6) { Face(index = it, value = 0, label = "") },
        read = FaceRead.FaceUp,
      )

    val mark = FaceGuide.of(fudge, cell = 0).single()

    assertEquals(0, mark.value)
    assertEquals(1, mark.rings.size)
    assertTrue("a dot sits where the number would", mark.rings.single().all { it.x in 0.4f..0.6f })
  }

  @Test
  fun `shows a dot for a numeral the typeface cannot draw`() {
    // The other way there is nothing to trace: a font without the glyph. It
    // cannot happen with the built-in one, which has every digit — but the
    // guide is not allowed to come out empty, because an empty guide is a
    // guide that was turned off.
    val ones = BuiltinFont.face.let { it.copy(glyphs = it.glyphs.filterKeys { glyph -> glyph == '1' }) }

    val mark = FaceGuide.of(d6, cell = 5, face = ones).single()

    assertEquals(1, mark.rings.size)
    assertEquals(6, mark.value)
  }

  @Test
  fun `gives a d4 three numerals, one at each of its corners`() {
    val marks = FaceGuide.of(d4, cell = 0)

    assertEquals(3, marks.size)
    marks.forEach { mark -> assertTrue(mark.rings.isNotEmpty()) }
    // Each sits nearer its own corner than the middle of the cell is.
    marks.forEach { mark ->
      val spot = FaceShapes.spot(FaceOutline.Triangle, mark.spot)
      val middleX = (mark.rings.flatten().minOf { it.x } + mark.rings.flatten().maxOf { it.x }) / 2f
      assertEquals("the ${mark.spot} numeral is not where its guide spot is", spot.x, middleX, 0.08f)
    }
  }

  private val d20 = die(DieShape.Icosahedron, (1..20).toList())

  /** How many rings the guide draws on one cell — an underline is one more. */
  private fun ringsOn(
    die: Die,
    cell: Int,
  ): Int {
    val mark = FaceGuide.of(die, cell).single()
    return mark.rings.size
  }
}
