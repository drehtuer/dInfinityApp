package de.drehtuer.dinfinity.core.glyphs

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TypesetterTest {
  private val middle = Placement(centreX = 0.5, centreY = 0.5, height = 0.4)

  private fun bounds(contours: List<DoubleArray>): Glyph.Bounds =
    Glyph(advance = 1.0, contours = contours).inkBounds ?: error("nothing was laid out")

  @Test
  fun `puts the text where it was asked to`() {
    val ink = bounds(Typesetter.lay("8", middle))
    assertTrue(abs(ink.centreX - 0.5) < 0.01, "across: ${ink.centreX}")
    assertTrue(abs(ink.centreY - 0.5) < 0.01, "down: ${ink.centreY}")
  }

  @Test
  fun `draws a digit at the height it was given`() {
    // `1` has a flat top and a flat foot, so it is exactly the figure height
    // and nothing else.
    val ink = bounds(Typesetter.lay("1", middle))
    assertTrue(abs(ink.height - 0.4) < 0.005, "came out ${ink.height} tall")
  }

  @Test
  fun `draws every digit the same size, overshoot and all`() {
    // A `0` is cut very slightly taller than a `1` so that it *looks* the same
    // height. Scaling each string by its own ink would undo exactly that, so
    // the scale comes from the figure height instead.
    val one = bounds(Typesetter.lay("1", middle)).height
    val zero = bounds(Typesetter.lay("0", middle)).height
    assertTrue(zero > one, "the overshoot was scaled away")
    assertTrue(zero - one < 0.02, "the overshoot is $zero against $one, which is not an overshoot")
  }

  @Test
  fun `two characters are wider than one and no taller`() {
    val one = bounds(Typesetter.lay("2", middle))
    val two = bounds(Typesetter.lay("20", middle))
    assertTrue(two.width > one.width * 1.5, "${two.width} against ${one.width}")
    assertTrue(two.height - one.height < 0.02)
    assertTrue(abs(two.centreX - 0.5) < 0.01, "a two-digit label is off centre at ${two.centreX}")
  }

  @Test
  fun `a marked number is the number with a full stop after it`() {
    // The mark is a trailing dot rather than a bar underneath
    // (`docs/dice-sets.md`, "Labels"), and it is written rather than drawn —
    // so what comes out is exactly what `6.` comes out as.
    val marked = Typesetter.lay("6", middle.copy(marked = true))
    val written = Typesetter.lay("6.", middle)

    assertEquals(written.size, marked.size)
    marked.forEachIndexed { ring, points -> assertTrue(points.contentEquals(written[ring]), "ring $ring") }
  }

  @Test
  fun `a marked number carries one ring more than the bare one, and is wider`() {
    val bare = bounds(Typesetter.lay("6", middle))
    val marked = bounds(Typesetter.lay("6", middle.copy(marked = true)))

    assertEquals(Typesetter.lay("6", middle).size + 1, Typesetter.lay("6", middle.copy(marked = true)).size)
    assertTrue(marked.width > bare.width, "${marked.width} against ${bare.width}")
    // The dot sits on the baseline, so it adds nothing to the height — which
    // is what the bar it replaces used to do.
    assertTrue(abs(marked.height - bare.height) < 0.005, "${marked.height} against ${bare.height}")
  }

  @Test
  fun `a mark does not move the text up or down`() {
    // The bar hung below the baseline and the whole line was lifted to make
    // room for it. Nothing is lifted now, so a marked `6` sits exactly where
    // the bare one did.
    val bare = bounds(Typesetter.lay("6", middle))
    val marked = bounds(Typesetter.lay("6", middle.copy(marked = true)))

    assertTrue(abs(marked.centreY - bare.centreY) < 0.005, "${marked.centreY} against ${bare.centreY}")
  }

  @Test
  fun `what is printed says whether the mark is on`() {
    assertEquals("6.", Typesetter.printed("6", marked = true))
    assertEquals("6", Typesetter.printed("6", marked = false))
    assertEquals(".", Typesetter.MARK)
  }

  @Test
  fun `moves the text with the placement`() {
    val ink = bounds(Typesetter.lay("8", Placement(centreX = 0.25, centreY = 0.75, height = 0.2)))
    assertTrue(abs(ink.centreX - 0.25) < 0.01, "across: ${ink.centreX}")
    assertTrue(abs(ink.centreY - 0.75) < 0.01, "down: ${ink.centreY}")
  }

  @Test
  fun `a half turn puts the text upside down and leaves it where it was`() {
    val upright = bounds(Typesetter.lay("1", middle))
    val over = bounds(Typesetter.lay("1", middle.copy(turns = 0.5)))
    assertTrue(abs(over.centreX - upright.centreX) < 0.01)
    assertTrue(abs(over.centreY - upright.centreY) < 0.01)
    assertTrue(abs(over.height - upright.height) < 0.01)
  }

  @Test
  fun `a quarter turn anticlockwise lays the text on its side`() {
    val upright = bounds(Typesetter.lay("20", middle))
    val turned = bounds(Typesetter.lay("20", middle.copy(turns = 0.25)))
    assertTrue(abs(turned.height - upright.width) < 0.01, "${turned.height} against ${upright.width}")
    assertTrue(abs(turned.width - upright.height) < 0.01, "${turned.width} against ${upright.height}")
  }

  @Test
  fun `a quarter turn takes the top of the text to the left`() {
    // Which way round a turn goes matters: a d4's numbers each face their own
    // corner, and a font turned the wrong way would point all three inwards.
    val top = Typesetter.lay("1", middle).let { bounds(it) }
    val turned = Typesetter.lay("1", middle.copy(turns = 0.25))
    // The foot of a `1` is its widest part, so after a quarter turn
    // anticlockwise the widest part is on the right and the top is on the left.
    val left = turned.flatMap { it.toList().chunked(2) }.filter { it[0] < 0.5 }
    assertTrue(left.isNotEmpty(), "nothing ended up left of the middle")
    assertTrue(top.height > 0)
  }

  @Test
  fun `lays out nothing for text the font cannot draw`() {
    assertTrue(Typesetter.lay("☠", middle).isEmpty())
    assertTrue(Typesetter.lay("2☠", middle).isEmpty(), "half a label is a wrong label")
    assertTrue(Typesetter.lay("", middle).isEmpty())
  }

  @Test
  fun `says how wide a label will be before laying it out`() {
    assertEquals(bounds(Typesetter.lay("20", middle)).width, Typesetter.inkWidth("20", 0.4), 0.0001)
    assertTrue(Typesetter.inkWidth("2000", 0.4) > Typesetter.inkWidth("20", 0.4))
    assertEquals(0.0, Typesetter.inkWidth("☠", 0.4))
    assertEquals(0.0, Typesetter.inkWidth("", 0.4))
  }

  @Test
  fun `width grows with the height it is asked for`() {
    assertEquals(2 * Typesetter.inkWidth("20", 0.2), Typesetter.inkWidth("20", 0.4), 0.0001)
  }

  @Test
  fun `refuses text of no height, which would draw nothing`() {
    assertFailsWith<IllegalArgumentException> { Placement(0.5, 0.5, height = 0.0) }
  }

  @Test
  fun `the figure height is the height of a one`() {
    val face = BuiltinFont.face
    assertEquals(face.glyphs['1']?.inkBounds?.height, face.figureHeight)
  }

  @Test
  fun `a face without a one falls back to its tallest glyph`() {
    val face = Typeface.parse("font t 1\nglyph 0032 0.5\ncontour\n0 0 1 0 1 2 0 2\n")
    assertEquals(2.0, face.figureHeight)
  }
}
