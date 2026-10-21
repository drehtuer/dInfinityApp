package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.AtlasImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The arithmetic that keeps a dyed felt the colour its look names, and an oak
 * as rough as its look says (`docs/tables.md`, "Textures").
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
  fun `under the lamps an averaged felt draws as its colour once its sheen is added`() {
    // The Pixel 10a drew #1f5e3a as (44, 94, 65) while the colour was divided
    // by the picture's average and nothing else: the felt's own reflection of
    // the lamps, grey, on top.
    val green = Colour.of(0xFF1F5E3A.toInt())
    val mean = Colour(red = 0.36, green = 0.36, blue = 0.36, alpha = 1.0)
    val light = SurfaceLight.facingUp(roughness = 0.9)

    val tint = TableTint.colourFor(green, mean, averaged = true, light = light)
    val drawn =
      light.drawn(Colour(tint.red * mean.red, tint.green * mean.green, tint.blue * mean.blue, tint.alpha))

    assertEquals(light.baseFor(green).green, tint.green * mean.green, TOLERANCE)
    assertEquals(green.green, drawn.green, LIGHT_TOLERANCE)
    assertEquals(green.blue, drawn.blue, LIGHT_TOLERANCE)
    assertEquals(green.red, drawn.red, LIGHT_TOLERANCE)
  }

  @Test
  fun `a look that multiplies is not lifted out of its sheen`() {
    // A photograph is drawn as it was taken, sheen and all.
    val photo = Colour(1.0, 1.0, 1.0, 1.0)
    val mean = Colour(0.2, 0.3, 0.4, 1.0)

    assertSame(photo, TableTint.colourFor(photo, mean, averaged = false, light = SurfaceLight.facingUp(0.9)))
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

  @Test
  fun `a map's level is its red channel as stored, not decoded as a colour`() {
    // A roughness map is a measurement: 0x80 is 0.502, not the 0.216 an sRGB
    // decode would make of it, and green and blue are not read.
    val image = picture(listOf(0xFF80FF00.toInt(), 0xFF000000.toInt() or (0x40 shl RED_SHIFT)))

    assertEquals((0x80 + 0x40) / 2.0 / 255, TableTint.levelOf(image), TOLERANCE)
  }

  @Test
  fun `an averaged roughness map is moved to average out at the look's roughness`() {
    // Poly Haven's oak boards average 0.44; the oiled oak look says 0.75.
    val shift = TableTint.roughnessShift(roughness = 0.75, level = 0.44, averaged = true)

    assertEquals(0.31, shift, TOLERANCE)
    assertEquals(0.75, 0.44 + shift, TOLERANCE)
  }

  @Test
  fun `a map can be moved down as well as up`() {
    assertEquals(-0.2, TableTint.roughnessShift(roughness = 0.6, level = 0.8, averaged = true), TOLERANCE)
  }

  @Test
  fun `a roughness map that multiplies, or is not there, is not moved`() {
    // A photograph's roughness is the photograph's own.
    assertEquals(0.0, TableTint.roughnessShift(roughness = 0.75, level = 0.44, averaged = false), TOLERANCE)
    assertEquals(0.0, TableTint.roughnessShift(roughness = 0.75, level = null, averaged = true), TOLERANCE)
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

    /** Green felt's red is a hair under its sheen, and is drawn that hair over. */
    const val LIGHT_TOLERANCE = 1e-3
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val RED_SHIFT = 16
    const val GREEN_SHIFT = 8
    const val ALPHA_SHIFT = 24
    const val ALPHA = 3
  }
}
