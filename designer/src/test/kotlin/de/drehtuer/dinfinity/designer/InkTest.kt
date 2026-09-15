package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The colour picker's arithmetic (`docs/face-designer.md`, "A colour beyond
 * the twelve").
 *
 * Three sliders are three numbers, and what turns them into ink is here rather
 * than in the dialog — which is what leaves the dialog with nothing in it that
 * can be wrong.
 */
class InkTest {
  @Test
  fun `the corners of the wheel are the colours they are meant to be`() {
    assertEquals(0xFFFF0000.toInt(), Ink.argb(0f, 1f, 1f))
    assertEquals(0xFFFFFF00.toInt(), Ink.argb(60f, 1f, 1f))
    assertEquals(0xFF00FF00.toInt(), Ink.argb(120f, 1f, 1f))
    assertEquals(0xFF00FFFF.toInt(), Ink.argb(180f, 1f, 1f))
    assertEquals(0xFF0000FF.toInt(), Ink.argb(240f, 1f, 1f))
    assertEquals(0xFFFF00FF.toInt(), Ink.argb(300f, 1f, 1f))
  }

  @Test
  fun `no depth is grey and no brightness is black`() {
    assertEquals(0xFFFFFFFF.toInt(), Ink.argb(200f, 0f, 1f))
    assertEquals(0xFF808080.toInt(), Ink.argb(200f, 0f, 0.502f))
    assertEquals(0xFF000000.toInt(), Ink.argb(200f, 1f, 0f))
  }

  @Test
  fun `ink is opaque whatever it is asked for`() {
    // A half-transparent stroke on a die face is a stroke whose colour depends
    // on the die under it, which the designer never sees.
    assertEquals(Ink.OPAQUE, Ink.argb(0f, 0f, 0f) and Ink.OPAQUE)
    assertEquals(Ink.OPAQUE, Ink.argb(123f, 0.4f, 0.9f) and Ink.OPAQUE)
  }

  @Test
  fun `a hue past the end of the wheel is the same hue again`() {
    assertEquals(Ink.argb(20f, 1f, 1f), Ink.argb(380f, 1f, 1f))
    assertEquals(Ink.argb(340f, 1f, 1f), Ink.argb(-20f, 1f, 1f))
  }

  @Test
  fun `sliders that run past their ends are pulled back rather than refused`() {
    assertEquals(Ink.argb(0f, 1f, 1f), Ink.argb(0f, 2f, 5f))
    assertEquals(Ink.argb(0f, 0f, 0f), Ink.argb(0f, -1f, -1f))
  }

  @Test
  fun `a colour survives the trip out to the sliders and back`() {
    // What opens the picker on the colour somebody is already drawing with.
    listOf(0xFFEC3013, 0xFF1F92CC, 0xFF7ED321, 0xFF8B572A, 0xFFFFFFFF, 0xFF000000).forEach { colour ->
      val argb = colour.toInt()
      assertEquals(Ink.hex(argb), Ink.hex(Ink.hsv(argb).argb))
    }
  }

  @Test
  fun `each of the three numbers is read back the way it went in`() {
    val hsv = Ink.hsv(0xFF3F8F29.toInt())

    assertEquals(107.1f, hsv.hue, 0.5f)
    assertEquals(0.71f, hsv.saturation, 0.01f)
    assertEquals(0.56f, hsv.value, 0.01f)
  }

  @Test
  fun `grey has no hue to speak of, and black has no depth`() {
    assertEquals(0f, Ink.hsv(0xFF808080.toInt()).hue, 1e-6f)
    assertEquals(0f, Ink.hsv(0xFF000000.toInt()).saturation, 1e-6f)
    assertEquals(0f, Ink.hsv(0xFF000000.toInt()).value, 1e-6f)
  }

  @Test
  fun `a hue read back from below red comes back on the wheel`() {
    // Magenta's hue is computed as a negative number before it is wrapped.
    assertEquals(300f, Ink.hsv(0xFFFF00FF.toInt()).hue, 0.01f)
    assertEquals(240f, Ink.hsv(0xFF0000FF.toInt()).hue, 0.01f)
    assertEquals(120f, Ink.hsv(0xFF00FF00.toInt()).hue, 0.01f)
  }

  @Test
  fun `a colour is written down without its alpha`() {
    assertEquals("#EC3013", Ink.hex(0xFFEC3013.toInt()))
    assertEquals("#000000", Ink.hex(0xFF000000.toInt()))
    assertEquals("#FFFFFF", Ink.hex(0xFFFFFFFF.toInt()))
  }

  @Test
  fun `three numbers are ink on their own, which is what the sliders hand over`() {
    assertEquals(0xFFFF0000.toInt(), Hsv(hue = 0f, saturation = 1f, value = 1f).argb)
    assertNotEquals(0xFFEC3013.toInt(), Hsv(hue = 150f, saturation = 0.5f, value = 0.5f).argb)
  }
}
