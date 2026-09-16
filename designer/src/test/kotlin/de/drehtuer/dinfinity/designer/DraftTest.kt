package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A die being drawn (`docs/face-designer.md`).
 *
 * Strokes are vectors in fractions of the canvas, because a draft outlives the
 * screen it was drawn on and is re-rendered at export resolution. What is
 * tested here is the taking-back — undo, redo and the limit — which is where a
 * drawing program is judged.
 */
class DraftTest {
  @Test
  fun `a fresh draft has nothing on any face`() {
    assertTrue(draft().blank)
    assertTrue(draft().face(0).blank)
  }

  @Test
  fun `a stroke lands on the face it was drawn on and nowhere else`() {
    val drawn = draft().onFace(2) { it.draw(stroke()) }

    assertEquals(1, drawn.face(2).marks.size)
    assertTrue(drawn.face(0).blank)
    assertFalse(drawn.blank)
  }

  @Test
  fun `undo takes back one stroke, and redo puts it back`() {
    val drawn = draft().onFace(0) { it.draw(stroke()).draw(stroke()) }

    val undone = drawn.onFace(0) { it.undo() }
    assertEquals(1, undone.face(0).marks.size)
    assertTrue(undone.face(0).canRedo)

    val redone = undone.onFace(0) { it.redo() }
    assertEquals(2, redone.face(0).marks.size)
    assertFalse(redone.face(0).canRedo)
  }

  @Test
  fun `drawing after an undo throws the redo away`() {
    // What every drawing program does, and what a finger expects: the branch
    // you left is not waiting for you.
    val drawn = draft().onFace(0) { it.draw(stroke()).undo().draw(stroke()) }

    assertFalse(drawn.face(0).canRedo)
    assertEquals(1, drawn.face(0).marks.size)
  }

  @Test
  fun `undo on a blank face does nothing rather than failing`() {
    val untouched = draft().onFace(0) { it.undo().redo() }

    assertTrue(untouched.face(0).blank)
  }

  @Test
  fun `undo and redo are per face, because that is how somebody draws`() {
    // Undoing on the face in front of you should not reach back into one you
    // finished ten faces ago.
    val drawn = draft().onFace(0) { it.draw(stroke()) }.onFace(1) { it.draw(stroke()) }

    val undone = drawn.onFace(1) { it.undo() }

    assertEquals(1, undone.face(0).marks.size)
    assertTrue(undone.face(1).blank)
  }

  @Test
  fun `clearing a face can itself be undone`() {
    // It is the most destructive thing on the screen, so it is the one that
    // most needs taking back.
    val drawn = draft().onFace(0) { it.draw(stroke()).draw(stroke()).clear() }

    assertTrue(drawn.face(0).blank)
    // One step back brings all of it, because clearing is one action rather
    // than two strokes taken away.
    assertEquals(
      2,
      drawn
        .onFace(0) { it.undo() }
        .face(0)
        .marks.size,
    )
    // And forward again, to the cleared face.
    assertTrue(drawn.onFace(0) { it.undo().redo() }.face(0).blank)
  }

  @Test
  fun `a face stops taking strokes at the limit, and says so before it does`() {
    // The screen warns before this; the model refuses rather than throwing,
    // because a finger is already on the glass by then.
    val full = (1..FaceDrawing.MAX_MARKS).fold(draft()) { d, _ -> d.onFace(0) { it.draw(stroke()) } }

    assertTrue(full.face(0).full)
    assertEquals(
      FaceDrawing.MAX_MARKS,
      full
        .onFace(0) { it.draw(stroke()) }
        .face(0)
        .marks.size,
    )
  }

  @Test
  fun `a cell the die does not have is left alone rather than invented`() {
    // The cell comes from a strip the finger swipes, and a key no face has
    // would be a drawing nothing ever shows.
    val drawn = draft().onFace(99) { it.draw(stroke()) }

    assertTrue(drawn.blank)
    assertEquals(emptyMap<Int, FaceDrawing>(), drawn.faces)
  }

  @Test
  fun `a stroke keeps what it was drawn with`() {
    // The draft is what gets rasterised at export resolution, so what it
    // stores has to be what the pen had: the path, the ink and the width.
    val drawn = draft().onFace(0) { it.draw(stroke()) }

    val kept = drawn.face(0).marks.single() as Stroke
    assertEquals(listOf(Dot(0.1f, 0.1f), Dot(0.9f, 0.9f)), kept.dots)
    assertEquals(0xFF000000.toInt(), kept.colorArgb)
    assertEquals(0.02f, kept.width, 1e-6f)
  }

  @Test
  fun `a draft hands out the guide for the face being asked about`() {
    // One number for a d6; a d4's three are `FaceGuideTest`'s.
    assertEquals(listOf(3), draft().guide(cell = 2).map(GuideMark::value))
    assertEquals(emptyList<GuideMark>(), draft().guide(cell = 99))
  }

  @Test
  fun `a draft knows the outline its cells are masked into, and how many there are`() {
    assertEquals(FaceOutline.Square, draft().outline)
    assertEquals(6, draft().cells)
  }

  @Test
  fun `the eraser is a stroke like any other, which is why it can be undone`() {
    val drawn = draft().onFace(0) { it.draw(stroke()).draw(stroke(erases = true)) }

    assertTrue(
      (
        drawn
          .face(0)
          .marks
          .last() as Stroke
      ).erases,
    )
    assertEquals(
      1,
      drawn
        .onFace(0) { it.undo() }
        .face(0)
        .marks.size,
    )
  }

  @Test
  fun `what was taken back waits to be put forward again`() {
    // The redo stack is the drawing's, not the screen's: undo puts the face
    // that was there on it, and drawing again drops it.
    val undone = draft().onFace(0) { it.draw(stroke()).undo() }

    assertEquals(1, undone.face(0).future.size)
    assertEquals(emptyList<List<Mark>>(), undone.onFace(0) { it.draw(stroke()) }.face(0).future)
  }

  @Test
  fun `a fill is a mark like any other, and one undo takes it back`() {
    val drawn = draft().onFace(0) { it.draw(stroke()).draw(fill()) }

    assertEquals(2, drawn.face(0).marks.size)
    assertEquals(
      1,
      drawn
        .onFace(0) { it.undo() }
        .face(0)
        .marks.size,
    )
  }

  @Test
  fun `fills sink under the ink, whenever they were made`() {
    // A bucket colours the paper, not the line: a fill that landed on top
    // would hide the drawing it was aimed at.
    val drawn = draft().onFace(0) { it.draw(stroke()).draw(fill()).draw(stroke()) }

    val marks = drawn.face(0).marks
    assertTrue("a fill was left over the ink", marks.first() is Fill)
    assertTrue("the ink sank too", marks.drop(1).all { it is Stroke })
  }

  @Test
  fun `two fills keep the order they were made in`() {
    val first = fill(0xFFEC3013.toInt())
    val second = fill(0xFF1F92CC.toInt())

    val drawn = draft().onFace(0) { it.draw(first).draw(stroke()).draw(second) }

    assertEquals(listOf(first, second), drawn.face(0).marks.filterIsInstance<Fill>())
  }

  @Test
  fun `a paste lands on what is already there, in one step`() {
    val pasted = listOf(stroke(), fill())

    val drawn = draft().onFace(0) { it.draw(stroke()).paste(pasted) }

    assertEquals(3, drawn.face(0).marks.size)
    assertTrue("the paste did not sink its fill", drawn.face(0).marks.first() is Fill)
    assertEquals(
      "a paste is one action, not one per mark",
      1,
      drawn
        .onFace(0) { it.undo() }
        .face(0)
        .marks.size,
    )
  }

  @Test
  fun `pasting nothing is not a step`() {
    val drawn = draft().onFace(0) { it.draw(stroke()).paste(emptyList()) }

    assertEquals(1, drawn.face(0).marks.size)
    assertEquals("an idle press left a step to undo", 1, drawn.face(0).past.size)
  }

  @Test
  fun `a paste that would not fit is refused whole`() {
    // Half of what was copied is not what was copied.
    val nearly = (1..FaceDrawing.MAX_MARKS - 1).fold(draft()) { d, _ -> d.onFace(0) { it.draw(stroke()) } }

    val pasted = nearly.onFace(0) { it.paste(listOf(stroke(), stroke())) }

    assertEquals(FaceDrawing.MAX_MARKS - 1, pasted.face(0).marks.size)
  }

  private fun fill(colorArgb: Int = 0xFFEC3013.toInt()) = Fill(dots = FaceFill.FACE, colorArgb = colorArgb)

  private fun stroke(erases: Boolean = false) =
    Stroke(
      dots = listOf(Dot(0.1f, 0.1f), Dot(0.9f, 0.9f)),
      colorArgb = 0xFF000000.toInt(),
      width = 0.02f,
      erases = erases,
    )

  private fun draft() =
    Draft(
      die =
        Die(
          id = "d6",
          shape = DieShape.Cube,
          faces = (1..6).map { Face(index = it - 1, value = it, label = it.toString()) },
        ),
    )

  @Test
  fun `a stamp is one mark however many rings it has`() {
    val glyph = stamp()

    val drawing = FaceDrawing().draw(glyph)

    assertEquals(1, drawing.marks.size)
    assertEquals(glyph.rings.sumOf { it.size }, glyph.dots.size)
  }

  @Test
  fun `a stamp moves ring by ring, so its hole moves with it`() {
    val glyph = stamp()

    val moved = glyph.at(glyph.dots.map { Dot(it.x + 0.1f, it.y) })

    assertEquals(glyph.rings.map { it.size }, moved.rings.map { it.size })
    assertEquals(glyph.rings[1].first().x + 0.1f, moved.rings[1].first().x, 1e-6f)
  }

  @Test
  fun `a stamp handed the wrong number of dots is left where it is`() {
    // Every transform is one dot in and one dot out, in order; a list of
    // another length is not this mark moved.
    val glyph = stamp()

    assertEquals(glyph, glyph.at(listOf(Dot(0f, 0f))))
  }

  @Test
  fun `a stamp sits with the ink rather than with the paper`() {
    val sunk = FaceDrawing.sunk(listOf(stamp(), Fill(dots = FaceFill.FACE, colorArgb = 0)))

    assertTrue("a fill came out over the ink", sunk.first() is Fill)
    assertTrue("a stamp sank under the paper", sunk.last() is Stamp)
  }

  @Test
  fun `a stamp with a ring of fewer than three dots cannot be made`() {
    refuses { Stamp(rings = listOf(listOf(Dot(0f, 0f))), colorArgb = 0) }
    refuses { Stamp(rings = emptyList(), colorArgb = 0) }
  }

  /** A shape that cannot be drawn is better made impossible than documented. */
  private fun refuses(make: () -> Stamp) {
    try {
      make()
      fail("a stamp was made out of rings that enclose nothing")
    } catch (refused: IllegalArgumentException) {
      assertTrue(refused.message.orEmpty().contains("closed rings"))
    }
  }

  /** A glyph with a hole in it: an outer ring and a counter. */
  private fun stamp() =
    Stamp(
      rings =
        listOf(
          listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.2f), Dot(0.8f, 0.8f), Dot(0.2f, 0.8f)),
          listOf(Dot(0.4f, 0.4f), Dot(0.6f, 0.4f), Dot(0.6f, 0.6f), Dot(0.4f, 0.6f)),
        ),
      colorArgb = 0xFF000000.toInt(),
    )
}
