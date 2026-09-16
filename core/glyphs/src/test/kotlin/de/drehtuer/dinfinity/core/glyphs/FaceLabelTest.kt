package de.drehtuer.dinfinity.core.glyphs

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a face is printed with, and when it needs a bar under it. */
class FaceLabelTest {
  private val d6 = Die.standard("d6", DieShape.Cube)
  private val d20 = Die.standard("d20", DieShape.Icosahedron)

  @Test
  fun `prints the label an author wrote rather than the value`() {
    // A d% is labelled `10`, `20`, `30` over values of 10, 20, 30 — and a set
    // may label any face with anything the font can draw.
    assertEquals("%", FaceLabel.textOf(Face(index = 0, value = 100, label = "%")))
  }

  @Test
  fun `prints the value when the font cannot draw the label`() {
    // A set may label a face with a skull or with `crit`, and the font has
    // neither — it draws digits and a few signs. A row of blanks would make
    // the die unreadable and a box would be a lie about what the author wrote.
    assertEquals("1", FaceLabel.textOf(Face(index = 0, value = 1, label = "💀")))
    assertEquals("20", FaceLabel.textOf(Face(index = 0, value = 20, label = "crit")))
  }

  @Test
  fun `leaves a blank face blank`() {
    // Half a Fudge die is a face an author asked for.
    assertEquals("", FaceLabel.textOf(Face(index = 0, value = 0, label = "")))
  }

  @Test
  fun `underlines a six on a die that also has a nine`() {
    assertTrue(FaceLabel.isAmbiguous("6", d20))
    assertTrue(FaceLabel.isAmbiguous("9", d20))
  }

  @Test
  fun `leaves a d6's six alone, because a d6 has no nine`() {
    assertFalse(FaceLabel.isAmbiguous("6", d6))
  }

  @Test
  fun `never underlines what turns into itself, or into nothing`() {
    assertFalse(FaceLabel.isAmbiguous("8", d20))
    assertFalse(FaceLabel.isAmbiguous("2", d20))
  }
}
