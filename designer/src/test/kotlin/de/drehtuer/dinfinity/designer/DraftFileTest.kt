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

    assertEquals(drawn.face(2).marks, back?.face(2)?.marks)
  }

  @Test
  fun `the undo stack is not written down, because undo belongs to the sitting`() {
    // `docs/face-designer.md` promises undo "per face, unlimited within the
    // session". A history restored from disk would rewind a drawing past the
    // point somebody opened it.
    val drawn = Draft(die = d6).onFace(0) { it.draw(stroke()).draw(stroke()) }
    assertTrue("the fixture had nothing to undo", drawn.face(0).canUndo)

    val back = requireNotNull(DraftFile.read(DraftFile.write(drawn), d6))

    assertEquals(2, back.face(0).marks.size)
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

    assertEquals("the whole drawing was lost over one bad stroke", 1, back.face(0).marks.size)
  }

  @Test
  fun `a fill survives the round trip, region and all`() {
    val region = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.2f), Dot(0.5f, 0.9f))
    val drawn = Draft(die = d6).onFace(1) { it.draw(Fill(dots = region, colorArgb = RED)).draw(stroke()) }

    val back = requireNotNull(DraftFile.read(DraftFile.write(drawn), d6))

    assertEquals(drawn.face(1).marks, back.face(1).marks)
    assertEquals(Fill(dots = region, colorArgb = RED), back.face(1).marks.first())
  }

  @Test
  fun `a fill is told from a stroke by a field a stroke never carries`() {
    val written = DraftFile.write(Draft(die = d6).onFace(0) { it.draw(Fill(dots = FaceFill.FACE, colorArgb = RED)) })

    assertTrue("a fill went out looking like a stroke", written.contains(""""fill":true"""))
    assertTrue("a fill went out with a nib width", !written.contains(""""width""""))
  }

  @Test
  fun `a fill of fewer than three corners is not a region`() {
    val mangled =
      DraftFile
        .write(Draft(die = d6).onFace(0) { it.draw(Fill(dots = FaceFill.FACE, colorArgb = RED)).draw(stroke()) })
        .replace(""""dots":[0.0,0.0,1.0,0.0,1.0,1.0,0.0,1.0]""", """"dots":[0.0,0.0,1.0,1.0]""")

    val back = requireNotNull(DraftFile.read(mangled, d6))

    assertEquals("the drawing was lost over one bad fill", 1, back.face(0).marks.size)
    assertTrue(back.face(0).marks.single() is Stroke)
  }

  @Test
  fun `a drawing read back has its fills under its ink, whatever order the file had`() {
    // The file is a list and the rule is the drawing's, so it is re-made on
    // the way in rather than trusted from disk.
    val text =
      DraftFile.write(
        Draft(die = d6).copy(
          faces = mapOf(0 to FaceDrawing(marks = listOf(stroke(), Fill(dots = FaceFill.FACE, colorArgb = RED)))),
        ),
      )

    val back = requireNotNull(DraftFile.read(text, d6))

    assertTrue("a fill came back over the ink", back.face(0).marks.first() is Fill)
  }

  @Test
  fun `a stamp comes back with its rings the shape they went out`() {
    val glyph = stamp()
    val drawn = Draft(die = d6).onFace(2) { it.draw(glyph) }

    val back = requireNotNull(DraftFile.read(DraftFile.write(drawn), d6))

    assertEquals(glyph, back.face(2).marks.single())
  }

  @Test
  fun `a stamp is told from a stroke and a fill by a field neither carries`() {
    val written = DraftFile.write(Draft(die = d6).onFace(0) { it.draw(stamp()) })

    assertTrue("a stamp went out without its rings", written.contains(""""rings":[4,4]"""))
    assertTrue("a stamp went out looking like a stroke", !written.contains(""""width""""))
  }

  @Test
  fun `a stamp whose rings do not add up is dropped, and the drawing kept`() {
    // A reader that predates stamps drops them the same way, which is why the
    // format number was not bumped for one.
    val mangled =
      DraftFile
        .write(Draft(die = d6).onFace(0) { it.draw(stamp()).draw(stroke()) })
        .replace(""""rings":[4,4]""", """"rings":[4,5]""")

    val back = requireNotNull(DraftFile.read(mangled, d6))

    assertEquals("the drawing was lost over one bad stamp", 1, back.face(0).marks.size)
    assertTrue(back.face(0).marks.single() is Stroke)
  }

  @Test
  fun `a ring of fewer than three dots is not a glyph`() {
    val mangled =
      DraftFile
        .write(Draft(die = d6).onFace(0) { it.draw(stamp()).draw(stroke()) })
        .replace(""""rings":[4,4]""", """"rings":[8,0]""")

    val back = requireNotNull(DraftFile.read(mangled, d6))

    assertEquals(1, back.face(0).marks.size)
  }

  private fun stroke() = Stroke(dots = listOf(Dot(0.1f, 0.2f), Dot(0.3f, 0.4f)), colorArgb = INK, width = 0.02f)

  /** A glyph with a hole in it: an outer ring and a counter. */
  private fun stamp() =
    Stamp(
      rings =
        listOf(
          listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.2f), Dot(0.8f, 0.8f), Dot(0.2f, 0.8f)),
          listOf(Dot(0.4f, 0.4f), Dot(0.6f, 0.4f), Dot(0.6f, 0.6f), Dot(0.4f, 0.6f)),
        ),
      colorArgb = RED,
    )

  private companion object {
    const val INK = 0xFF000000.toInt()
    const val RED = 0xFFCC0000.toInt()
  }
}
