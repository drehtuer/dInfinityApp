package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.AtlasImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The arithmetic that keeps a dyed felt the colour its look names
 * (`docs/tables.md`, "Textures").
 */
class TableTintTest {
  @Test
  fun `a picture's average is taken in light's units, not the encoded ones`() {
    // Half black and half white averages to a half in light, which is 188 of
    // 255 once encoded — not the 128 an average of the bytes would say.
    val image = picture(listOf(BLACK, WHITE))

    val mean = TableTint.meanOf(image)

    assertEquals(0.5, mean.red, TOLERANCE)
    assertEquals(0.5, mean.green, TOLERANCE)
    assertEquals(0.5, mean.blue, TOLERANCE)
  }

  @Test
  fun `each channel is averaged on its own, and alpha is not a colour`() {
    val image = picture(listOf(0x00FF0000, 0xFF0000FF.toInt()))

    val mean = TableTint.meanOf(image)

    assertEquals(0.5, mean.red, TOLERANCE)
    assertEquals(0.0, mean.green, TOLERANCE)
    assertEquals(0.5, mean.blue, TOLERANCE)
    assertEquals(1.0, mean.alpha, TOLERANCE)
  }

  @Test
  fun `an averaged colour times its picture's average is the colour again`() {
    val green = Colour.of(0xFF1F5E3A.toInt())
    val mean = Colour(red = 0.36, green = 0.36, blue = 0.36, alpha = 1.0)

    val drawn = TableTint.colourFor(green, mean, averaged = true)

    assertEquals(green.red, drawn.red * mean.red, TOLERANCE)
    assertEquals(green.green, drawn.green * mean.green, TOLERANCE)
    assertEquals(green.blue, drawn.blue * mean.blue, TOLERANCE)
    assertEquals(green.alpha, drawn.alpha, TOLERANCE)
  }

  @Test
  fun `a coloured picture is evened out channel by channel`() {
    // Oak's own brown, made to average out at a neutral grey: the ebonised
    // walls of the black felt.
    val grey = Colour.of(0xFF2B2B2B.toInt())
    val oak = Colour(red = 0.40, green = 0.20, blue = 0.10, alpha = 1.0)

    val drawn = TableTint.colourFor(grey, oak, averaged = true)

    assertEquals(drawn.red * oak.red, drawn.blue * oak.blue, TOLERANCE)
    assertEquals(drawn.green * oak.green, drawn.blue * oak.blue, TOLERANCE)
  }

  @Test
  fun `a nearly black picture is brightened sixteen times and no more`() {
    val white = Colour(1.0, 1.0, 1.0, 1.0)
    val black = Colour(red = 0.001, green = 0.0, blue = 1.0, alpha = 1.0)

    val drawn = TableTint.colourFor(white, black, averaged = true)

    assertEquals(1.0 / TableTint.LEAST_MEAN, drawn.red, TOLERANCE)
    assertEquals(1.0 / TableTint.LEAST_MEAN, drawn.green, TOLERANCE)
    assertEquals(1.0, drawn.blue, TOLERANCE)
  }

  @Test
  fun `a colour that multiplies, or has no picture, is handed on as it is`() {
    val photo = Colour(1.0, 1.0, 1.0, 1.0)
    val mean = Colour(0.2, 0.3, 0.4, 1.0)

    assertSame(photo, TableTint.colourFor(photo, mean, averaged = false))
    assertSame(photo, TableTint.colourFor(photo, mean = null, averaged = true))
  }

  /** A picture one pixel high, of these ARGB pixels, as an atlas holds them. */
  private fun picture(argb: List<Int>): AtlasImage {
    val bytes = ByteArray(argb.size * AtlasImage.CHANNELS)
    argb.forEachIndexed { index, pixel ->
      bytes[index * AtlasImage.CHANNELS] = (pixel shr RED_SHIFT).toByte()
      bytes[index * AtlasImage.CHANNELS + 1] = (pixel shr GREEN_SHIFT).toByte()
      bytes[index * AtlasImage.CHANNELS + 2] = pixel.toByte()
      bytes[index * AtlasImage.CHANNELS + ALPHA] = (pixel shr ALPHA_SHIFT).toByte()
    }
    return AtlasImage(argb.size, 1, bytes)
  }

  private companion object {
    const val TOLERANCE = 1e-9
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val RED_SHIFT = 16
    const val GREEN_SHIFT = 8
    const val ALPHA_SHIFT = 24
    const val ALPHA = 3
  }
}
