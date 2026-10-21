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
  private val d10 = Die.standard(id = "d10", shape = DieShape.PentagonalTrapezohedron)

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
  fun `a die named by anything but its id as text names no die`() {
    // A number that happens to print as the id, or an object, is not the die
    // the drawing was made on; reading it as one would be a guess.
    assertNull(DraftFile.dieOf("""{"format":2,"die":6,"faces":[]}"""))
    assertNull(DraftFile.dieOf("""{"format":2,"die":{"id":"d6"},"faces":[]}"""))
    assertNull(DraftFile.read("""{"format":2,"die":{"id":"d6"},"faces":[]}""", d6))
  }

  @Test
  fun `a face entry that is not a face is skipped, and the faces around it kept`() {
    // Somebody's file edited by hand, or written by a build with a bug in it.
    // Every entry here is wrong in its own way; the one good face still opens.
    val good = """{"cell":1,"strokes":[$GOOD_STROKE]}"""
    val text =
      """{"format":2,"die":"d6","faces":[
        "not a face",
        {"strokes":[$GOOD_STROKE]},
        {"cell":"two","strokes":[$GOOD_STROKE]},
        {"cell":3,"strokes":"not a list"},
        {"cell":4},
        $good
      ]}"""

    val back = requireNotNull(DraftFile.read(text, d6))

    assertEquals(setOf(1), back.faces.keys)
    assertEquals(listOf(stroke()), back.face(1).marks)
  }

  @Test
  fun `a file with no list of faces is a blank drawing on its die, not a refusal`() {
    val back = requireNotNull(DraftFile.read("""{"format":2,"die":"d6","faces":"none"}""", d6))

    assertTrue(back.blank)
  }

  @Test
  fun `a mark missing its ink, its nib or its dots is dropped, and the marks around it kept`() {
    val broken =
      listOf(
        "\"not a mark\"",
        """{"width":0.02,"dots":[0.1,0.2,0.3,0.4]}""",
        """{"color":"red","width":0.02,"dots":[0.1,0.2,0.3,0.4]}""",
        """{"color":$INK,"dots":[0.1,0.2,0.3,0.4]}""",
        """{"color":$INK,"width":"thin","dots":[0.1,0.2,0.3,0.4]}""",
        """{"color":$INK,"width":0.02}""",
        """{"color":$INK,"width":0.02,"dots":"0.1,0.2,0.3,0.4"}""",
        """{"color":$INK,"width":0.02,"dots":[0.1,0.2,0.3]}""",
        """{"color":$INK,"width":0.02,"dots":[0.1,"a",0.3,0.4]}""",
      )
    val text = """{"format":2,"die":"d6","faces":[{"cell":0,"strokes":[${(broken + GOOD_STROKE).joinToString(
      ",",
    )}]}]}"""

    val back = requireNotNull(DraftFile.read(text, d6))

    assertEquals("a broken mark was read, or the good one was lost with them", listOf(stroke()), back.face(0).marks)
  }

  @Test
  fun `a flag that is not plainly true or false reads as not set`() {
    // `erases` and `fill` are written as JSON booleans. Anything else is not
    // a yes: an eraser that is not one draws ink, and a fill that is not one
    // is read as the stroke its other fields describe.
    val text =
      """{"format":2,"die":"d6","faces":[{"cell":0,"strokes":[
        {"color":$INK,"width":0.02,"erases":"yes","fill":"maybe","dots":[0.1,0.2,0.3,0.4]}
      ]}]}"""

    val back = requireNotNull(DraftFile.read(text, d6))

    assertEquals(listOf(stroke()), back.face(0).marks)
  }

  @Test
  fun `a ring length that is not a number is a ring of nothing, which no glyph has`() {
    val mangled =
      DraftFile
        .write(Draft(die = d6).onFace(0) { it.draw(stamp()).draw(stroke()) })
        .replace(""""rings":[4,4]""", """"rings":[4,"four"]""")

    val back = requireNotNull(DraftFile.read(mangled, d6))

    assertEquals(listOf(stroke()), back.face(0).marks)
  }

  @Test
  fun `a file from a format this one does not know is not read`() {
    // The alternative is guessing, and a guess about somebody's drawing is
    // worse than a blank canvas.
    val written = DraftFile.write(Draft(die = d6).onFace(0) { it.draw(stroke()) })

    assertTrue("the fixture did not say what format it was", written.contains(""""format":2"""))
    assertNull(DraftFile.read(written.replace(""""format":2""", """"format":3"""), d6))
    assertNull(DraftFile.read(written.replace(""""format":2""", """"format":0"""), d6))
    assertNull(DraftFile.read(written.replace(""""format":2,""", ""), d6))
  }

  @Test
  fun `a format-1 draft still reads, and off a kite die it reads unchanged`() {
    // Format 1 and 2 differ only on the two trapezohedra, so every other
    // drawing written before the kites were split comes back as it was.
    val drawn = Draft(die = d6).onFace(1) { it.draw(stroke()).draw(stamp()) }
    val old = DraftFile.write(drawn).replace(""""format":2""", """"format":1""")

    assertEquals(drawn.face(1).marks, DraftFile.read(old, d6)?.face(1)?.marks)
  }

  @Test
  fun `a format-1 drawing on a d10 is carried off the shared kite`() {
    // The marks were fractions of the one kite both trapezohedra shared, so
    // they are moved to where that kite's exporter put them on the die.
    val drawn = Draft(die = d10).onFace(3) { it.draw(stroke()) }
    val old = DraftFile.write(drawn).replace(""""format":2""", """"format":1""")

    val back = requireNotNull(DraftFile.read(old, d10)).face(3).marks

    assertEquals(SharedKite.carried(listOf(stroke()), d10.shape), back)
    assertTrue("the drawing was not moved", back != listOf(stroke()))
  }

  @Test
  fun `and once it is written again it is format 2 and is not carried twice`() {
    val drawn = Draft(die = d10).onFace(3) { it.draw(stroke()) }
    val once = requireNotNull(DraftFile.read(DraftFile.write(drawn).replace(""""format":2""", """"format":1"""), d10))

    val again = requireNotNull(DraftFile.read(DraftFile.write(once), d10))

    assertEquals(once.face(3).marks, again.face(3).marks)
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

  @Test
  fun `pips come back as pips rather than as a stamp of circles`() {
    // The rings alone cannot say which it is, and the difference matters:
    // "fill all with eyes" and "fill all with numbers" both look for pips
    // (`docs/face-designer.md`, "Fill all with eyes").
    val pips = requireNotNull(FaceEyes.of(3, RED))
    val drawn = Draft(die = d6).onFace(2) { it.draw(pips) }

    val back = requireNotNull(DraftFile.read(DraftFile.write(drawn), d6))

    assertEquals(pips, back.face(2).marks.single())
  }

  @Test
  fun `pips are told from a stamp by a field a stamp never carries`() {
    val eyes = DraftFile.write(Draft(die = d6).onFace(0) { it.draw(requireNotNull(FaceEyes.of(1, INK))) })
    val glyph = DraftFile.write(Draft(die = d6).onFace(0) { it.draw(stamp()) })

    assertTrue("pips went out without saying they were pips", eyes.contains(""""eyes":true"""))
    assertTrue("a stamp went out claiming to be pips", !glyph.contains(""""eyes""""))
  }

  @Test
  fun `pips whose rings do not add up are dropped, and the drawing kept`() {
    val mangled =
      DraftFile
        .write(Draft(die = d6).onFace(0) { it.draw(requireNotNull(FaceEyes.of(1, INK))).draw(stroke()) })
        .replace(""""rings":[24]""", """"rings":[23]""")

    val back = requireNotNull(DraftFile.read(mangled, d6))

    assertEquals(1, back.face(0).marks.size)
    assertTrue(back.face(0).marks.single() is Stroke)
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

    /** [stroke] as the file writes it. */
    const val GOOD_STROKE = """{"color":$INK,"width":0.02,"erases":false,"dots":[0.1,0.2,0.3,0.4]}"""
  }
}
