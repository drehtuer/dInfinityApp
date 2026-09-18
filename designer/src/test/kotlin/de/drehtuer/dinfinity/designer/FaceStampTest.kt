package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.glyphs.LabelRoom
import de.drehtuer.dinfinity.core.glyphs.Typesetter
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.FaceRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stamp, and the one tap that numbers every face.
 *
 * The bug worth spending tests on is a die whose drawn faces and printed faces
 * disagree, so what is asserted is not a picture but the arithmetic both sides
 * share: the same solve, the same font, the same place
 * (`docs/face-designer.md`, "The stamp").
 */
class FaceStampTest {
  private val d6 = Die.standard("d6", DieShape.Cube)
  private val d20 = Die.standard("d20", DieShape.Icosahedron)
  private val d4 = Die.standard("d4", DieShape.Tetrahedron)
  private val coin = Die.standard("d2", DieShape.Coin)

  private fun middle(outline: FaceOutline) = FaceStamp.corners(outline)

  @Test
  fun `stamps a digit as closed rings`() {
    val stamp = requireNotNull(FaceStamp.at("1", Dot(0.5f, 0.5f), FaceOutline.Square, StampSize.Medium, Drawings.INK))

    assertEquals(1, stamp.rings.size)
    assertTrue("a ring of ${stamp.rings.first().size} dots", stamp.rings.first().size >= 3)
    assertEquals(stamp.rings.sumOf { it.size }, stamp.dots.size)
  }

  @Test
  fun `keeps the hole in a zero as a ring of its own`() {
    // A counter drawn as a shape of its own would be a blob where the hole is,
    // so the glyph is kept whole and drawn under the even-odd rule.
    val zero = requireNotNull(FaceStamp.at("0", Dot(0.5f, 0.5f), FaceOutline.Square, StampSize.Medium, Drawings.INK))

    assertEquals(2, zero.rings.size)
  }

  @Test
  fun `puts one glyph-string down as one mark`() {
    // `10` is four rings and one press of undo, one mark against the face's
    // two hundred, and one thing a turn carries whole.
    val ten = requireNotNull(FaceStamp.at("10", Dot(0.5f, 0.5f), FaceOutline.Triangle, StampSize.Medium, Drawings.INK))
    val drawing = FaceDrawing().draw(ten)

    assertEquals(1, drawing.marks.size)
    assertTrue("a two-digit stamp has more than one ring", ten.rings.size > 1)
  }

  @Test
  fun `stamps nothing the font cannot draw`() {
    assertNull(FaceStamp.at("💀", Dot(0.5f, 0.5f), FaceOutline.Square, StampSize.Medium, Drawings.INK))
    assertNull(FaceStamp.at("", Dot(0.5f, 0.5f), FaceOutline.Square, StampSize.Medium, Drawings.INK))
  }

  @Test
  fun `stamps where the finger went`() {
    val stamp =
      requireNotNull(FaceStamp.at("8", Dot(0.3f, 0.7f), FaceOutline.Square, StampSize.Medium, Drawings.INK))
    val across = stamp.dots.map { it.x }
    val down = stamp.dots.map { it.y }

    assertEquals(0.3f, (across.min() + across.max()) / 2f, 0.01f)
    assertEquals(0.7f, (down.min() + down.max()) / 2f, 0.01f)
  }

  @Test
  fun `makes a small stamp smaller and a large one larger`() {
    val sizes =
      StampSize.entries.map { size ->
        val stamp = requireNotNull(FaceStamp.at("8", Dot(0.5f, 0.5f), FaceOutline.Square, size, Drawings.INK))
        stamp.dots.maxOf { it.y } - stamp.dots.minOf { it.y }
      }

    assertTrue("$sizes is not three sizes", sizes[0] < sizes[1] && sizes[1] < sizes[2])
  }

  @Test
  fun `sizes a stamp against the face rather than the canvas`() {
    // A number that fills a d6's square would run off a d20's triangle, so the
    // middle size is what that die's own numbers would be printed at.
    val onSquare = requireNotNull(FaceStamp.at("8", Dot(0.5f, 0.5f), FaceOutline.Square, StampSize.Medium, 0))
    val onTriangle = requireNotNull(FaceStamp.at("8", Dot(0.5f, 0.5f), FaceOutline.Triangle, StampSize.Medium, 0))

    val square = onSquare.dots.maxOf { it.y } - onSquare.dots.minOf { it.y }
    val triangle = onTriangle.dots.maxOf { it.y } - onTriangle.dots.minOf { it.y }
    assertTrue("$triangle on a triangle against $square on a square", triangle < square)
  }

  @Test
  fun `keeps a stamp in the middle of a face on that face`() {
    // Every outline, because the sizes are multiples of what that face's own
    // number would be and "its own" is the whole point.
    FaceOutline.entries.forEach { outline ->
      val stamp = requireNotNull(FaceStamp.at("8", Dot(0.5f, 0.5f), outline, StampSize.Medium, Drawings.INK))
      val face = FaceStamp.corners(outline).map { (x, y) -> Dot(x.toFloat(), y.toFloat()) }
      stamp.dots.forEach { dot ->
        assertTrue("a $outline was stamped over its own edge at $dot", Polygon.contains(face, dot))
      }
    }
  }

  @Test
  fun `never stamps taller than the face has room for`() {
    FaceOutline.entries.forEach { outline ->
      val corners = middle(outline)
      val room = LabelRoom.heightAt(corners, Typesetter.inkWidth("8", 1.0), centreX = 0.5, centreY = 0.5)
      val stamp = requireNotNull(FaceStamp.at("8", Dot(0.5f, 0.5f), outline, StampSize.Large, Drawings.INK))
      val tall = stamp.dots.maxOf { it.y } - stamp.dots.minOf { it.y }

      assertTrue("a $tall stamp on a $outline with room for $room", tall <= room)
    }
  }

  @Test
  fun `numbers a face where the tray would print it`() {
    // The same solve over the same outline: what the designer draws and what
    // the tray prints cannot be two different answers.
    val stamped = FaceStamp.numbers(d20, cell = 5, colorArgb = Drawings.INK).single() as Stamp
    val placement = requireNotNull(LabelRoom.centred(middle(FaceOutline.Triangle), "6", marked = true))
    val drawn = requireNotNull(FaceStamp.of("6", placement, Drawings.INK))

    assertEquals(drawn, stamped)
  }

  @Test
  fun `marks a six on a die that also has a nine`() {
    // The mark is a trailing full stop, which is a ring of its own — so the
    // stamped `6` carries one more than the same `6` would without it. A d6
    // has no `9` and is left alone, which is what a moulded d6 does.
    val onD20 = (FaceStamp.numbers(d20, cell = 5, colorArgb = Drawings.INK).single() as Stamp).rings.size
    val onD6 = (FaceStamp.numbers(d6, cell = 5, colorArgb = Drawings.INK).single() as Stamp).rings.size

    assertEquals(onD6 + 1, onD20)
  }

  @Test
  fun `numbers a d4 at three corners, where its guide already showed them`() {
    val marks = FaceStamp.numbers(d4, cell = 0, colorArgb = Drawings.INK)

    assertEquals(3, marks.size)
    val spots =
      FaceGuide.cornersOf(d4, cell = 0).map { (_, spot) -> FaceShapes.spot(FaceOutline.Triangle, spot) }
    marks.forEachIndexed { index, mark ->
      val across = (mark.dots.minOf { it.x } + mark.dots.maxOf { it.x }) / 2f
      assertEquals("the number is not at the corner its guide is", spots[index].x, across, 0.05f)
    }
  }

  @Test
  fun `numbers a coin, whose cell is not a polygon the canvas draws`() {
    val marks = FaceStamp.numbers(coin, cell = 0, colorArgb = Drawings.INK)

    assertEquals(1, marks.size)
    assertEquals(24, FaceStamp.corners(FaceOutline.Circle).size)
  }

  @Test
  fun `has no number for a face an author left blank`() {
    val blank =
      Die(
        id = "fudge",
        shape = DieShape.Cube,
        faces = List(6) { Face(index = it, value = 0, label = "") },
        read = FaceRead.FaceUp,
      )

    assertTrue(FaceStamp.numbers(blank, cell = 0, colorArgb = Drawings.INK).isEmpty())
  }

  @Test
  fun `has no number for a cell the die does not have`() {
    assertTrue(FaceStamp.numbers(d6, cell = 6, colorArgb = Drawings.INK).isEmpty())
    assertEquals("", FaceStamp.textOn(d6, cell = 6))
  }

  @Test
  fun `fills every face in one tap`() {
    val filled = FaceStamp.fill(Draft(die = d6), Drawings.INK)

    val cells = filled.die.faces.indices
    val numbered = cells.count { cell -> filled.face(cell).marks.any { it is Stamp } }

    assertEquals(6, numbered)
    filled.die.faces.indices.forEach { cell ->
      assertTrue("face $cell cannot be taken back", filled.face(cell).canUndo)
    }
  }

  @Test
  fun `leaves a face that is already stamped alone`() {
    val once = FaceStamp.fill(Draft(die = d6), Drawings.INK)
    val twice = FaceStamp.fill(once, Drawings.RED)

    assertEquals(once, twice)
  }

  @Test
  fun `numbers a face that was drawn on, over the drawing`() {
    val drawn = Draft(die = d6).onFace(0) { it.draw(Drawings.line()) }
    val filled = FaceStamp.fill(drawn, Drawings.INK)

    assertEquals(2, filled.face(0).marks.size)
    assertTrue("the number did not land over the drawing", filled.face(0).marks.last() is Stamp)
  }

  @Test
  fun `refuses to fill a face that has no room left`() {
    val full =
      (0 until FaceDrawing.MAX_MARKS).fold(Draft(die = d6)) { draft, _ ->
        draft.onFace(0) { it.draw(Drawings.line()) }
      }
    val filled = FaceStamp.fill(full, Drawings.INK)

    assertEquals(FaceDrawing.MAX_MARKS, filled.face(0).marks.size)
    assertFalse("a full face took a stamp", filled.face(0).marks.any { it is Stamp })
    assertTrue("the rest of the die was not numbered", filled.face(1).marks.any { it is Stamp })
  }

  @Test
  fun `loads the stamp with the face's own number`() {
    assertEquals("3", FaceStamp.textOn(d6, cell = 2))
  }
}
