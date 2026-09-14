package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    assertEquals(1, drawn.face(2).strokes.size)
    assertTrue(drawn.face(0).blank)
    assertFalse(drawn.blank)
  }

  @Test
  fun `undo takes back one stroke, and redo puts it back`() {
    val drawn = draft().onFace(0) { it.draw(stroke()).draw(stroke()) }

    val undone = drawn.onFace(0) { it.undo() }
    assertEquals(1, undone.face(0).strokes.size)
    assertTrue(undone.face(0).canRedo)

    val redone = undone.onFace(0) { it.redo() }
    assertEquals(2, redone.face(0).strokes.size)
    assertFalse(redone.face(0).canRedo)
  }

  @Test
  fun `drawing after an undo throws the redo away`() {
    // What every drawing program does, and what a finger expects: the branch
    // you left is not waiting for you.
    val drawn = draft().onFace(0) { it.draw(stroke()).undo().draw(stroke()) }

    assertFalse(drawn.face(0).canRedo)
    assertEquals(1, drawn.face(0).strokes.size)
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

    assertEquals(1, undone.face(0).strokes.size)
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
        .strokes.size,
    )
    // And forward again, to the cleared face.
    assertTrue(drawn.onFace(0) { it.undo().redo() }.face(0).blank)
  }

  @Test
  fun `a face stops taking strokes at the limit, and says so before it does`() {
    // The screen warns before this; the model refuses rather than throwing,
    // because a finger is already on the glass by then.
    val full = (1..FaceDrawing.MAX_STROKES).fold(draft()) { d, _ -> d.onFace(0) { it.draw(stroke()) } }

    assertTrue(full.face(0).full)
    assertEquals(
      FaceDrawing.MAX_STROKES,
      full
        .onFace(0) { it.draw(stroke()) }
        .face(0)
        .strokes.size,
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

    val kept = drawn.face(0).strokes.single()
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
      drawn
        .face(0)
        .strokes
        .last()
        .erases,
    )
    assertEquals(
      1,
      drawn
        .onFace(0) { it.undo() }
        .face(0)
        .strokes.size,
    )
  }

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
}
