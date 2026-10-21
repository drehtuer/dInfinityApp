package de.drehtuer.dinfinity.render.filament

import com.twelvemonkeys.imageio.plugins.hdr.HDRImageReadParam
import com.twelvemonkeys.imageio.plugins.hdr.tonemap.NullToneMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max

/**
 * The app's own Radiance decoder: against an independent one on the file that
 * ships, and against hand-made files for every rule it reads by — and every
 * way a file can lie to it, each of which must be null (the gradient), never
 * an exception on the roll thread or a read past the end.
 */
class RadianceTest {
  @Test
  fun `the shipped panorama decodes, at the size Poly Haven made it`() {
    val image = Radiance.decode(requireNotNull(StudioLight.bytes()))
    assertNotNull(image)
    assertEquals(PANORAMA_WIDTH, image!!.width)
    assertEquals(PANORAMA_HEIGHT, image.height)
  }

  @Test
  fun `it reads the shipped panorama as TwelveMonkeys does, to half a step of the mantissa`() {
    val ours = requireNotNull(Radiance.decode(requireNotNull(StudioLight.bytes())))
    val theirs = independently()
    assertEquals(theirs.size, ours.pixels.size)
    for (pixel in 0 until theirs.size / Radiance.CHANNELS) {
      val at = pixel * Radiance.CHANNELS
      // A step of an RGBE mantissa is 1/256 of the pixel's exponent, which is
      // set by its brightest channel; decoders differ about the half step.
      val step = max(theirs[at], max(theirs[at + 1], theirs[at + 2])) / HALF_STEPS + TINY
      repeat(Radiance.CHANNELS) {
        assertTrue("pixel $pixel, channel $it", abs(ours.pixels[at + it] - theirs[at + it]) <= step)
      }
    }
  }

  @Test
  fun `a flat file is four bytes a pixel, a mantissa over 256 times two to the exponent less 128`() {
    // 128 · 2^(129 − 136) = 1 (plus Ward's half step); exponent 0 is black.
    val pixels = byteArrayOf(128.toByte(), 64, 0, 129.toByte(), 9, 9, 9, 0)
    val image = requireNotNull(Radiance.decode(file(width = 2, height = 1, body = pixels)))
    assertEquals(HALF_UP, image.at(0, 0, 0), EXACT)
    assertEquals((64 + 0.5f) / 128, image.at(0, 0, 1), EXACT)
    assertEquals(0.5f / 128, image.at(0, 0, 2), EXACT)
    repeat(Radiance.CHANNELS) { assertEquals(0f, image.at(1, 0, it), 0f) }
  }

  @Test
  fun `a run-length row repeats a byte above 128 and copies up to 128 literally`() {
    val width = RLE_WIDTH
    val row = ByteArrayOutputStream()
    row.write(byteArrayOf(2, 2, 0, width.toByte()))
    // Red: a run of six 128s, then two literals.
    row.write(byteArrayOf((128 + 6).toByte(), 128.toByte(), 2, 10, 20))
    // Green and blue: a run of eight noughts each.
    repeat(2) { row.write(byteArrayOf((128 + width).toByte(), 0)) }
    // Exponent: a run of eight 129s.
    row.write(byteArrayOf((128 + width).toByte(), 129.toByte()))
    val image = requireNotNull(Radiance.decode(file(width = width, height = 1, body = row.toByteArray())))
    assertEquals(HALF_UP, image.at(0, 0, 0), EXACT)
    assertEquals(HALF_UP, image.at(5, 0, 0), EXACT)
    assertEquals((10 + 0.5f) / 128, image.at(6, 0, 0), EXACT)
    assertEquals((20 + 0.5f) / 128, image.at(7, 0, 0), EXACT)
    assertEquals(0.5f / 128, image.at(7, 0, 1), EXACT)
  }

  @Test
  fun `the older magic and a header with no format line are both read`() {
    val pixel = byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 129.toByte())
    val rgbe = "#?RGBE\n\n-Y 1 +X 1\n".toByteArray() + pixel
    assertNotNull(Radiance.decode(rgbe))
  }

  @Test
  fun `anything that is not a Radiance file is not a panorama`() {
    listOf(
      "not a panorama".toByteArray(),
      ByteArray(0),
      "#?RADIANCE\nFORMAT=32-bit_rle_xyze\n\n-Y 1 +X 1\n".toByteArray() + ByteArray(4),
      // A header that never ends.
      "#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n".toByteArray(),
      // A header line longer than any real one.
      "#?RADIANCE\n".toByteArray() + ByteArray(LONG_LINE) { 'x'.code.toByte() },
    ).forEach { assertNull(String(it.take(PREVIEW).toByteArray()), Radiance.decode(it)) }
  }

  @Test
  fun `only rows top first and columns left to right are read`() {
    listOf("+Y 1 +X 1", "-Y 1 -X 1", "+X 1 -Y 1", "-Y 1", "-Y one +X 1", "-Y 1 +X one").forEach { resolution ->
      val bytes = "#?RADIANCE\n\n$resolution\n".toByteArray() + ByteArray(4)
      assertNull(resolution, Radiance.decode(bytes))
    }
  }

  @Test
  fun `a size past the limit, or no size at all, is refused before anything is allocated`() {
    listOf("-Y 1 +X ${Radiance.MAX_WIDTH + 1}", "-Y ${Radiance.MAX_HEIGHT + 1} +X 1", "-Y 0 +X 1", "-Y 1 +X 0")
      .forEach { resolution ->
        assertNull(resolution, Radiance.decode("#?RADIANCE\n\n$resolution\n".toByteArray()))
      }
  }

  @Test
  fun `a file that ends early is not a panorama`() {
    // Two flat pixels promised, one given; and a row that stops mid-header.
    assertNull(Radiance.decode(file(width = 2, height = 1, body = ByteArray(4))))
    assertNull(Radiance.decode(file(width = 2, height = 1, body = ByteArray(2))))
    // A run-length row whose literal runs off the end of the file.
    val cut = byteArrayOf(2, 2, 0, RLE_WIDTH.toByte(), 8, 1, 2)
    assertNull(Radiance.decode(file(width = RLE_WIDTH, height = 1, body = cut)))
    // And one that ends between a run's count and its byte.
    val noValue = byteArrayOf(2, 2, 0, RLE_WIDTH.toByte(), (128 + RLE_WIDTH).toByte())
    assertNull(Radiance.decode(file(width = RLE_WIDTH, height = 1, body = noValue)))
    // And one that ends before its first count.
    assertNull(Radiance.decode(file(width = RLE_WIDTH, height = 1, body = byteArrayOf(2, 2, 0, RLE_WIDTH.toByte()))))
  }

  @Test
  fun `a run-length row that lies about its width or overruns it is not a panorama`() {
    val wrongWidth = byteArrayOf(2, 2, 0, (RLE_WIDTH + 1).toByte()) + ByteArray(RLE_WIDTH * 4)
    assertNull(Radiance.decode(file(width = RLE_WIDTH, height = 1, body = wrongWidth)))
    val longRun = byteArrayOf(2, 2, 0, RLE_WIDTH.toByte(), (128 + RLE_WIDTH + 1).toByte(), 0)
    assertNull(Radiance.decode(file(width = RLE_WIDTH, height = 1, body = longRun)))
    val longLiteral = byteArrayOf(2, 2, 0, RLE_WIDTH.toByte(), (RLE_WIDTH + 1).toByte()) + ByteArray(RLE_WIDTH + 1)
    assertNull(Radiance.decode(file(width = RLE_WIDTH, height = 1, body = longLiteral)))
    val zero = byteArrayOf(2, 2, 0, RLE_WIDTH.toByte(), 0) + ByteArray(RLE_WIDTH * 4)
    assertNull(Radiance.decode(file(width = RLE_WIDTH, height = 1, body = zero)))
  }

  @Test
  fun `an image is exactly as many floats as its size says`() {
    assertThrows(IllegalArgumentException::class.java) { Radiance.Image(2, 2, FloatArray(2 * 2 * 3 - 1)) }
  }

  private fun file(
    width: Int,
    height: Int,
    body: ByteArray,
  ): ByteArray = "#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y $height +X $width\n".toByteArray() + body

  /** The shipped panorama read by TwelveMonkeys, untouched by any tone mapping. */
  private fun independently(): FloatArray {
    val reader = ImageIO.getImageReadersByFormatName("hdr").next()
    ImageIO.createImageInputStream(ByteArrayInputStream(StudioLight.bytes())).use { input ->
      reader.input = input
      val parameters = (reader.defaultReadParam as HDRImageReadParam).apply { toneMapper = NullToneMapper() }
      val raster = reader.read(0, parameters).raster
      val pixels = FloatArray(raster.width * raster.height * Radiance.CHANNELS)
      raster.getPixels(0, 0, raster.width, raster.height, pixels)
      reader.dispose()
      return pixels
    }
  }

  private companion object {
    const val PANORAMA_WIDTH = 1024
    const val PANORAMA_HEIGHT = 512
    const val RLE_WIDTH = 8

    /** 128 and a half, over 128. */
    const val HALF_UP = 128.5f / 128
    const val EXACT = 1e-6f
    const val HALF_STEPS = 128f
    const val TINY = 1e-6f
    const val LONG_LINE = 2_000
    const val PREVIEW = 24
  }
}
