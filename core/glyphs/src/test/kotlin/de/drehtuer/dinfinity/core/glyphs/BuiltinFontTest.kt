package de.drehtuer.dinfinity.core.glyphs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuiltinFontTest {
  @Test
  fun `loads off the classpath`() {
    val face = BuiltinFont.load()
    assertEquals("archivo", face.family)
    assertTrue(face.weight > 0)
  }

  @Test
  fun `draws every character a value can be written in`() {
    ("0123456789" + '-').forEach { assertNotNull(BuiltinFont.glyph(it), "no glyph for '$it'") }
  }

  @Test
  fun `draws the signs the built-in set labels a fudge die with`() {
    // The bundled `df` is labelled with a real minus sign rather than a
    // hyphen, and a font that could not draw it would print `-1` instead.
    assertNotNull(BuiltinFont.glyph('−'))
    assertNotNull(BuiltinFont.glyph('+'))
  }

  @Test
  fun `has no glyph for a character it was never given`() {
    assertNull(BuiltinFont.glyph('☠'))
  }

  @Test
  fun `can draw a label only when it has every character of it`() {
    assertTrue(BuiltinFont.canDraw("20"))
    assertTrue(BuiltinFont.canDraw("00"))
    assertFalse(BuiltinFont.canDraw("☠"))
    assertFalse(BuiltinFont.canDraw("2☠"))
  }

  @Test
  fun `cannot draw nothing, because there would be nothing to draw`() {
    assertFalse(BuiltinFont.canDraw(""))
  }

  @Test
  fun `the zero has a counter, and the eight has two`() {
    // The hole in a `0` is a contour of its own. If the generator ever lost
    // them the digits would come out as solid blobs, which is exactly the kind
    // of thing nobody notices in a diff.
    assertEquals(2, BuiltinFont.glyph('0')?.contours?.size)
    assertEquals(3, BuiltinFont.glyph('8')?.contours?.size)
  }

  @Test
  fun `every glyph carries ink and an advance`() {
    BuiltinFont.face.glyphs.forEach { (character, glyph) ->
      assertTrue(glyph.advance > 0, "'$character' advances by nothing")
      val ink = assertNotNull(glyph.inkBounds, "'$character' has no ink")
      assertTrue(ink.width > 0 && ink.height > 0, "'$character' has empty ink")
    }
  }

  @Test
  fun `the digits are all the same width, because the font is tabular`() {
    // A d20 printing `1` narrower than `8` would wobble face to face. Archivo's
    // figures are tabular, and this is the assertion that keeps a future font
    // swap honest. They agree to a thousandth rather than exactly, because the
    // advances come off a font whose own units are thousandths of an em.
    val advances = "0123456789".mapNotNull { BuiltinFont.glyph(it)?.advance }
    assertEquals(10, advances.size)
    assertTrue(advances.max() - advances.min() < 0.005, "the digits advance by $advances")
  }
}

class TypefaceParseTest {
  @Test
  fun `reads a font, ignoring comments and blank lines`() {
    val face =
      Typeface.parse(
        """
        # a comment
        font test 400

        glyph 0031 0.5
        contour
        0 0 1 0
        1 1 0 1
        """.trimIndent(),
      )
    assertEquals("test", face.family)
    assertEquals(400, face.weight)
    val one = requireNotNull(face.glyphs['1'])
    assertEquals(0.5, one.advance)
    assertEquals(1, one.contours.size)
    assertEquals(8, one.contours.first().size)
  }

  @Test
  fun `a row of numbers may be folded over as many lines as it takes`() {
    val folded = Typeface.parse("font t 1\nglyph 0031 0.5\ncontour\n0 0\n1 0\n1 1\n")
    val whole = Typeface.parse("font t 1\nglyph 0031 0.5\ncontour\n0 0 1 0 1 1\n")
    assertTrue(folded.glyphs['1']!!.contours[0].contentEquals(whole.glyphs['1']!!.contours[0]))
  }

  @Test
  fun `a second glyph line ends the one before it`() {
    val face = Typeface.parse("font t 1\nglyph 0031 0.5\ncontour\n0 0 1 0 1 1\nglyph 0032 0.6\ncontour\n0 0 1 0 1 1\n")
    assertEquals(setOf('1', '2'), face.glyphs.keys)
  }

  @Test
  fun `refuses a file with no font line`() {
    assertFailsWith<IllegalStateException> { Typeface.parse("glyph 0031 0.5\ncontour\n0 0 1 0 1 1\n") }
  }

  @Test
  fun `refuses a font line that is not a family and a weight`() {
    assertFailsWith<IllegalArgumentException> { Typeface.parse("font test\n") }
  }

  @Test
  fun `refuses a weight that is not a number`() {
    assertFailsWith<IllegalStateException> { Typeface.parse("font test bold\n") }
  }

  @Test
  fun `refuses a glyph line that is not a code point and an advance`() {
    assertFailsWith<IllegalArgumentException> { Typeface.parse("font t 1\nglyph 0031\n") }
  }

  @Test
  fun `refuses a code point that is not hexadecimal`() {
    assertFailsWith<IllegalStateException> { Typeface.parse("font t 1\nglyph zzzz 0.5\n") }
  }

  @Test
  fun `refuses an advance that is not a number`() {
    assertFailsWith<IllegalStateException> { Typeface.parse("font t 1\nglyph 0031 wide\n") }
  }

  @Test
  fun `refuses a contour with an odd number of numbers`() {
    assertFailsWith<IllegalArgumentException> { Typeface.parse("font t 1\nglyph 0031 0.5\ncontour\n0 0 1 0 1\n") }
  }

  @Test
  fun `refuses a point that is not a number`() {
    assertFailsWith<IllegalStateException> { Typeface.parse("font t 1\nglyph 0031 0.5\ncontour\n0 0 x 0\n") }
  }

  @Test
  fun `refuses a typeface with nothing in it`() {
    assertFailsWith<IllegalArgumentException> { Typeface.parse("font test 400\n") }
  }
}
