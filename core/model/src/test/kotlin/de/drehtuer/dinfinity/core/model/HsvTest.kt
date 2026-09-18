package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The colour picker's arithmetic (`ui/common`'s `ColourPicker`;
 * `docs/face-designer.md`, "A colour beyond the twelve").
 *
 * Three sliders are three numbers, and what turns them into a colour is here
 * rather than in the sheet — which is what leaves the sheet with nothing in it
 * that can be wrong, and what lets one JVM test cover the picker all three
 * screens draw.
 */
class HsvTest {
  @Test
  fun `the corners of the wheel are the colours they are meant to be`() {
    assertEquals(0xFFFF0000.toInt(), Hsv(0f, 1f, 1f).argb)
    assertEquals(0xFFFFFF00.toInt(), Hsv(60f, 1f, 1f).argb)
    assertEquals(0xFF00FF00.toInt(), Hsv(120f, 1f, 1f).argb)
    assertEquals(0xFF00FFFF.toInt(), Hsv(180f, 1f, 1f).argb)
    assertEquals(0xFF0000FF.toInt(), Hsv(240f, 1f, 1f).argb)
    assertEquals(0xFFFF00FF.toInt(), Hsv(300f, 1f, 1f).argb)
  }

  @Test
  fun `no depth is a grey, and no brightness is black`() {
    assertEquals(0xFFFFFFFF.toInt(), Hsv(200f, 0f, 1f).argb)
    assertEquals(0xFF808080.toInt(), Hsv(200f, 0f, 0.502f).argb)
    assertEquals(0xFF000000.toInt(), Hsv(200f, 1f, 0f).argb)
  }

  @Test
  fun `every colour a picker can make is opaque`() {
    // A colour at less than full alpha has no contrast ratio of its own, and
    // a stroke at less than full alpha depends on a surface nobody here sees.
    assertEquals(Hsv.OPAQUE, Hsv(0f, 0f, 0f).argb and Hsv.OPAQUE)
    assertEquals(Hsv.OPAQUE, Hsv(123f, 0.4f, 0.9f).argb and Hsv.OPAQUE)
  }

  @Test
  fun `a hue past either end of the wheel comes back onto it`() {
    assertEquals(Hsv(20f, 1f, 1f).argb, Hsv(380f, 1f, 1f).argb)
    assertEquals(Hsv(340f, 1f, 1f).argb, Hsv(-20f, 1f, 1f).argb)
  }

  @Test
  fun `a slider that overshoots is pulled into range rather than refused`() {
    assertEquals(Hsv(0f, 1f, 1f).argb, Hsv(0f, 2f, 5f).argb)
    assertEquals(Hsv(0f, 0f, 0f).argb, Hsv(0f, -1f, -1f).argb)
  }

  @Test
  fun `a colour survives the trip out to the sliders and back`() {
    // What opens the picker on the colour somebody is already drawing with.
    listOf(0xFFEC3013, 0xFF1F92CC, 0xFF7ED321, 0xFF8B572A, 0xFFFFFFFF, 0xFF000000).forEach { colour ->
      val argb = colour.toInt()
      assertEquals(Hex.of(argb), Hex.of(Hsv.of(argb).argb))
    }
  }

  @Test
  fun `each of the three numbers is read back the way it went in`() {
    val hsv = Hsv.of(0xFF3F8F29.toInt())

    assertEquals(107.1f, hsv.hue, 0.5f)
    assertEquals(0.71f, hsv.saturation, 0.01f)
    assertEquals(0.56f, hsv.value, 0.01f)
  }

  @Test
  fun `grey has no hue to speak of, and black has no depth`() {
    assertEquals(0f, Hsv.of(0xFF808080.toInt()).hue, 1e-6f)
    assertEquals(0f, Hsv.of(0xFF000000.toInt()).saturation, 1e-6f)
    assertEquals(0f, Hsv.of(0xFF000000.toInt()).value, 1e-6f)
  }

  @Test
  fun `a hue read back from below red comes back on the wheel`() {
    // Magenta's hue is computed as a negative number before it is wrapped.
    assertEquals(300f, Hsv.of(0xFFFF00FF.toInt()).hue, 0.01f)
    assertEquals(240f, Hsv.of(0xFF0000FF.toInt()).hue, 0.01f)
    assertEquals(120f, Hsv.of(0xFF00FF00.toInt()).hue, 0.01f)
  }

  @Test
  fun `the wheel is a full turn, which is what the hue slider is long`() {
    assertEquals(360f, Hsv.ROUND, 0f)
    assertEquals(Hsv(0f, 1f, 1f).argb, Hsv(Hsv.ROUND, 1f, 1f).argb)
  }

  @Test
  fun `three numbers are a colour on their own, which is what the sliders hand over`() {
    assertEquals(0xFFFF0000.toInt(), Hsv(hue = 0f, saturation = 1f, value = 1f).argb)
    assertNotEquals(0xFFEC3013.toInt(), Hsv(hue = 150f, saturation = 0.5f, value = 0.5f).argb)
  }
}
