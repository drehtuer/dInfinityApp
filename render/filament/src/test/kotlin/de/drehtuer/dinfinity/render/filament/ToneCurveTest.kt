package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The curve light past white is bent by ([ToneCurve]), and the frame it is read from ([TonePass]). */
class ToneCurveTest {
  @Test
  fun `below the knee every colour is drawn exactly as it is lit`() {
    // Green felt, oak, the bone die's shaded faces: what a package wrote is
    // what is drawn, which is why the mapper is linear at all.
    listOf(
      doubleArrayOf(0.0137, 0.1144, 0.0423),
      doubleArrayOf(0.1, 0.05, 0.02),
      doubleArrayOf(0.79, 0.7, 0.6),
      doubleArrayOf(0.0, 0.0, 0.0),
    ).forEach { colour -> assertArrayEquals(colour, ToneCurve.shown(colour[0], colour[1], colour[2]), 0.0) }
  }

  @Test
  fun `the shoulder meets the line at the knee, slope and all`() {
    val k = ToneCurve.KNEE
    val step = 1e-6
    val at = ToneCurve.shown(k, k, k)[0]
    val above = ToneCurve.shown(k + step, k + step, k + step)[0]
    assertEquals(k, at, 1e-12)
    assertEquals("the slope jumps at the knee", 1.0, (above - at) / step, 1e-4)
  }

  @Test
  fun `light past white never reaches white, and more light is always brighter`() {
    var last = 0.0
    listOf(0.85, 1.0, 1.5, 2.0, ToneCurve.HEADROOM, 100.0).forEach { level ->
      val shown = ToneCurve.shown(level, level, level)[0]
      assertTrue("$level drew as $shown", shown < 1.0 && shown > last)
      last = shown
    }
  }

  @Test
  fun `a coloured highlight keeps its hue a little way past the knee and goes to white far past it`() {
    // Ivory metal at its highlight: cream, not white, and not yellow either.
    val ivory = ToneCurve.shown(1.3, 1.15, 0.9)
    assertTrue("red is no longer the brightest: ${ivory.toList()}", ivory[0] >= ivory[1] && ivory[1] >= ivory[2])
    assertTrue("nothing reached white: ${ivory.toList()}", ivory.all { it < 1.0 })
    // The lamp's own reflection, far past white, has let go of its colour.
    val lamp = ToneCurve.shown(40.0, 30.0, 20.0)
    assertTrue("the lamp kept its colour: ${lamp.toList()}", lamp[0] - lamp[2] < ivory[0] - ivory[2])
  }

  @Test
  fun `the grading cut lands at the headroom`() {
    assertEquals(-2.0f, ToneCurve.GRADING_EV, 1e-6f)
  }

  @Test
  fun `the sRGB transfer function goes there and back`() {
    listOf(0.0, 0.002, 0.0031308, 0.04, 0.18, 0.5, 1.0).forEach { linear ->
      assertEquals(linear, ToneCurve.decoded(ToneCurve.encoded(linear)), 1e-9)
    }
    assertEquals(0.5, ToneCurve.encoded(0.214041), 1e-5)
    assertEquals(1.0, ToneCurve.encoded(7.0), 1e-12)
    assertEquals(0.0, ToneCurve.decoded(-1.0), 0.0)
  }

  @Test
  fun `the shader is written from the same numbers`() {
    listOf("${ToneCurve.KNEE}", "${ToneCurve.DESATURATION}", "${ToneCurve.HEADROOM}", "materialParams_frame").forEach {
      assertTrue("the tone pass does not say $it", ToneCurve.SOURCE.contains(it))
    }
  }

  @Test
  fun `a half float is the float it is`() {
    assertEquals(0f, TonePass.halfToFloat(0x0000), 0f)
    assertEquals(1f, TonePass.halfToFloat(0x3C00), 0f)
    assertEquals(-2f, TonePass.halfToFloat(0xC000.toShort()), 0f)
    assertEquals(0.5f, TonePass.halfToFloat(0x3800), 0f)
    assertEquals(65504f, TonePass.halfToFloat(0x7BFF), 0f)
    assertEquals(5.9604645e-8f, TonePass.halfToFloat(0x0001), 1e-12f)
    assertEquals(Float.POSITIVE_INFINITY, TonePass.halfToFloat(0x7C00), 0f)
    assertTrue(TonePass.halfToFloat(0x7E00).isNaN())
  }

  @Test
  fun `a frame read back is light, brought back up by the headroom`() {
    // One pixel stored as Filament's grading writes it: a quarter of the
    // light, sRGB-encoded, in half floats; alpha is skipped.
    val stored = ToneCurve.encoded(1.0 / ToneCurve.HEADROOM)
    val half = floatToHalf(stored.toFloat())
    val buffer = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
    buffer.asShortBuffer().put(shortArrayOf(half, half, 0, 0x3C00))
    val light = TonePass.lightOf(buffer, pixels = 1)
    assertEquals(1.0f, light[0], 2e-3f)
    assertEquals(1.0f, light[1], 2e-3f)
    assertEquals(0.0f, light[2], 0f)
  }

  /** Enough of a half-float encoder for positive normal numbers, to build a frame. */
  private fun floatToHalf(value: Float): Short {
    val bits = java.lang.Float.floatToIntBits(value)
    val exponent = ((bits shr 23) and 0xFF) - 127 + 15
    val mantissa = (bits shr 13) and 0x3FF
    return ((exponent shl 10) or mantissa).toShort()
  }
}
