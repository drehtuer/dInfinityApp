package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.glyphs.BuiltinFont
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.FaceRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the guide shows under a face being drawn (`docs/face-designer.md`):
 * one number in the middle of the face, on every die.
 */
class FaceGuideTest {
  @Test
  fun `an ordinary die shows the one number that face scores`() {
    val marks = FaceGuide.of(d6, cell = 3)

    assertEquals(listOf(4), marks.map(GuideMark::value))
    assertEquals(listOf(GuideSpot.Middle), marks.map(GuideMark::spot))
  }

  @Test
  fun `a d4 shows one number in the middle of its long face, like any die`() {
    // Since it became a long die (decision 109): no corners, no three guides.
    d4.faces.indices.forEach { cell ->
      val marks = FaceGuide.of(d4, cell)
      assertEquals(listOf(cell + 1), marks.map(GuideMark::value))
      assertEquals(listOf(GuideSpot.Middle), marks.map(GuideMark::spot))
    }
  }

  @Test
  fun `a cell that is not a cell of this die has no guide at all`() {
    // The cell index comes from a strip the finger swipes, so it can be asked
    // for out of range while a die is being swapped.
    assertEquals(emptyList<GuideMark>(), FaceGuide.of(d6, cell = 6))
    assertEquals(emptyList<GuideMark>(), FaceGuide.of(d6, cell = -1))
  }

  private fun die(
    shape: DieShape,
    values: List<Int>,
  ) = Die(
    id = shape.id,
    shape = shape,
    faces = values.mapIndexed { index, value -> Face(index = index, value = value, label = value.toString()) },
    read = shape.naturalRead,
  )

  private val d4 = die(DieShape.ElongatedSquareBipyramid, listOf(1, 2, 3, 4))
  private val d6 = die(DieShape.Cube, listOf(1, 2, 3, 4, 5, 6))

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
