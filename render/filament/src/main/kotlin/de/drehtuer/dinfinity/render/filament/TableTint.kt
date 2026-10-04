package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.AtlasImage

/**
 * The colour a table's surface is drawn in, given the picture it is drawn from
 * (`docs/tables.md`, "Textures").
 *
 * The material always *multiplies* the picture by a colour
 * ([DiceMaterial.TABLE_SOURCE]). A look in `color_mode = "average"` wants its
 * colour to be what the floor averages out to, so the colour handed to the
 * material is scaled, channel by channel, by one over the picture's own
 * average: the felt's grain survives, and green felt is the green the look
 * names — and the swatch the picker falls back to tells the truth about it.
 *
 * Linear throughout, because that is what light adds up in and what the GPU
 * averages an sRGB picture in.
 */
object TableTint {
  /**
   * The darkest average a picture is taken to have. A picture of nearly black
   * would otherwise ask for its colour to be multiplied by hundreds, and the
   * few pixels in it that are not black would blow out to white; sixteen
   * times is as far as a picture is ever brightened.
   */
  const val LEAST_MEAN: Double = 1.0 / 16

  /** The average of [image], linear, alpha ignored. */
  fun meanOf(image: AtlasImage): Colour {
    val sums = DoubleArray(RGB)
    val pixels = image.width * image.height
    for (pixel in 0 until pixels) {
      for (channel in 0 until RGB) {
        sums[channel] += LINEAR[image.pixels[pixel * AtlasImage.CHANNELS + channel].toInt() and BYTE]
      }
    }
    return Colour(red = sums[0] / pixels, green = sums[1] / pixels, blue = sums[2] / pixels, alpha = 1.0)
  }

  /**
   * What [colour] is drawn as over a picture whose average is [mean]: itself
   * when the look multiplies or there is no picture, and scaled to come out
   * at itself on average when the look asks for that ([averaged]).
   */
  fun colourFor(
    colour: Colour,
    mean: Colour?,
    averaged: Boolean,
  ): Colour {
    if (!averaged || mean == null) return colour
    return Colour(
      red = colour.red / mean.red.coerceAtLeast(LEAST_MEAN),
      green = colour.green / mean.green.coerceAtLeast(LEAST_MEAN),
      blue = colour.blue / mean.blue.coerceAtLeast(LEAST_MEAN),
      alpha = colour.alpha,
    )
  }

  private const val RGB = 3
  private const val BYTE = 0xFF
  private const val OPAQUE = 0xFF000000.toInt()
  private const val RED_SHIFT = 16

  /** Every byte of sRGB, linear: the same conversion [Colour.of] makes. */
  private val LINEAR = DoubleArray(BYTE + 1) { Colour.of(OPAQUE or (it shl RED_SHIFT)).red }
}
