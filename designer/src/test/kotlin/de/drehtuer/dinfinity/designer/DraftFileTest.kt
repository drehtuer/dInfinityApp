package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A drawing written down and read back
 * (`docs/face-designer.md`, "Drawing tools").
 *
 * What the file has to hold is a drawing that is *the same drawing* — the
 * strokes in fractions of the canvas, their ink and their width — and what it
 * must not hold is the undo stack, which belongs to the sitting it was made
 * in.
 */
class DraftFileTest {
  private val d6 = Die.standard(id = "d6", shape = DieShape.Cube)
  private val d4 = Die.standard(id = "d4", shape = DieShape.Tetrahedron)

  @Test
  fun `a drawing survives the round trip, stroke for stroke`() {
    val drawn =
      Draft(die = d6).onFace(2) {
        it
          .draw(Stroke(dots = listOf(Dot(0.1f, 0.2f), Dot(0.3f, 0.4f)), colorArgb = INK, width = 0.02f))
          .draw(Stroke(dots = listOf(Dot(0.5f, 0.5f), Dot(0.9f, 0.1f)), colorArgb = RED, width = 0.05f, erases = true))
      }

    val back = DraftFile.read(DraftFile.write(drawn), d6)

    assertEquals(drawn.face(2).strokes, back?.face(2)?.strokes)
  }

  @Test
  fun `the undo stack is not written down, because undo belongs to the sitting`() {
    // `docs/face-designer.md` promises undo "per face, unlimited within the
    // session". A history restored from disk would rewind a drawing past the
    // point somebody opened it.
    val drawn = Draft(die = d6).onFace(0) { it.draw(stroke()).draw(stroke()) }
    assertTrue("the fixture had nothing to undo", drawn.face(0).canUndo)

    val back = requireNotNull(DraftFile.read(DraftFile.write(drawn), d6))

    assertEquals(2, back.face(0).strokes.size)
    assertTrue("an undo stack came back from disk", !back.face(0).canUndo && !back.face(0).canRedo)
  }

  @Test
  fun `a face nobody drew on costs nothing in the file`() {
    val drawn = Draft(die = d6).onFace(0) { it.draw(stroke()) }

    val back = requireNotNull(DraftFile.read(DraftFile.write(drawn), d6))

    assertEquals("blank faces were written down", setOf(0), back.faces.keys)
  }

  @Test
  fun `a face cleared to blank does not come back as an empty face`() {
    // `clear` leaves an entry in the map with no strokes in it, which is a
    // face for the undo stack's sake and nothing at all for the file's.
    val drawn = Draft(die = d6).onFace(0) { it.draw(stroke()).clear() }

    val back = requireNotNull(DraftFile.read(DraftFile.write(drawn), d6))

    assertTrue(back.blank)
  }

  @Test
  fun `a draft is only ever read back onto the die it was drawn on`() {
    // The file names its die, and the caller says which die it is opening. A
    // d6's drawing on a d4 would be two faces of strokes the die does not have.
    val drawn = Draft(die = d6).onFace(0) { it.draw(stroke()) }

    assertNull(DraftFile.read(DraftFile.write(drawn), d4))
    assertEquals("d6", DraftFile.dieOf(DraftFile.write(drawn)))
  }

  @Test
  fun `a cell the die no longer has is dropped rather than losing the drawing`() {
    // The die an update changed under the draft: same id, fewer faces. The
    // four faces that still exist are still somebody's drawing, and refusing
    // the file over the other two would throw all six away.
    val drawn = Draft(die = d6).onFace(0) { it.draw(stroke()) }.onFace(5) { it.draw(stroke()) }
    val shrunk = Die.standard(id = "d6", shape = DieShape.Tetrahedron)

    val back = requireNotNull(DraftFile.read(DraftFile.write(drawn), shrunk))

    assertEquals(setOf(0), back.faces.keys)
  }

  @Test
  fun `text that is not a draft is not a draft`() {
    assertNull(DraftFile.read("", d6))
    assertNull(DraftFile.read("not json at all", d6))
    assertNull(DraftFile.read("[]", d6))
    assertNull(DraftFile.read("""{"format":1}""", d6))
    assertNull(DraftFile.dieOf("not json at all"))
  }

  @Test
  fun `a file from a format this one does not know is not read`() {
    // The alternative is guessing, and a guess about somebody's drawing is
    // worse than a blank canvas.
    val written = DraftFile.write(Draft(die = d6).onFace(0) { it.draw(stroke()) })

    assertTrue("the fixture did not say what format it was", written.contains(""""format":1"""))
    assertNull(DraftFile.read(written.replace(""""format":1""", """"format":2"""), d6))
  }

  @Test
  fun `a stroke that is not a path is dropped and the rest of the drawing is not`() {
    val written = DraftFile.write(Draft(die = d6).onFace(0) { it.draw(stroke()).draw(stroke()) })

    // One dot is half a stroke: a tap, not a mark.
    assertTrue("the fixture did not have two strokes to mangle", written.contains(""""dots":[0.1,0.2,0.3,0.4]"""))
    val mangled = written.replaceFirst(""""dots":[0.1,0.2,0.3,0.4]""", """"dots":[0.1]""")

    val back = requireNotNull(DraftFile.read(mangled, d6))

    assertEquals("the whole drawing was lost over one bad stroke", 1, back.face(0).strokes.size)
  }

  private fun stroke() = Stroke(dots = listOf(Dot(0.1f, 0.2f), Dot(0.3f, 0.4f)), colorArgb = INK, width = 0.02f)

  private companion object {
    const val INK = 0xFF000000.toInt()
    const val RED = 0xFFCC0000.toInt()
  }
}
