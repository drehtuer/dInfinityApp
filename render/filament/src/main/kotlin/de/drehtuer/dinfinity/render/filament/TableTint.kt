package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.AtlasImage

/**
 * The colour a table's surface is drawn in, given the picture it is drawn from,
 * and how rough it is, given its roughness map (`docs/tables.md`, "Textures").
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
 *
 * **The roughness is averaged the same way.** A look in `average` names the
 * surface it is — green felt at 0.9, oiled oak at 0.75 — and its pictures add
 * only the grain. A roughness map that simply *replaced* the look's
 * `roughness` made the surface as glossy as the photograph's own: Poly Haven's
 * oak boards average 0.44, a lacquered floor, and under the studio's key
 * light that is a white glint across the middle of the tray that turned the
 * brown boards pinkish grey ([roughnessShift]).
 *
 * **And the colour is what is on the screen, sheen and all.** Even at the
 * look's roughness a dielectric reflects the lamps — grey, on top of its
 * colour — and on green felt's red that doubled the channel: the Pixel 10a
 * drew `#1f5e3a` as (44, 94, 65). The colour handed to the material is the
 * one that, with that reflection added, draws as the look's ([SurfaceLight]).
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
   * The average of [image]'s red channel as it is stored, nought to one: what
   * a roughness map averages out to on the GPU, which reads it linear
   * ([SurfaceMap.ROUGHNESS]) — a measurement, so not decoded as sRGB.
   */
  fun levelOf(image: AtlasImage): Double {
    val pixels = image.width * image.height
    var sum = 0L
    for (pixel in 0 until pixels) sum += image.pixels[pixel * AtlasImage.CHANNELS].toInt() and BYTE
    return sum.toDouble() / pixels / BYTE
  }

  /**
   * How far a roughness map averaging [level] is moved, everywhere alike, for
   * a surface the look says is [roughness] rough: so that it averages out at
   * [roughness] when the look asks for that ([averaged]), and not at all when
   * it multiplies — a photograph's roughness is the photograph's — or there
   * is no map.
   *
   * Moved rather than scaled, so the grain keeps its depth: oak's 0.34 to
   * 0.63 becomes 0.65 to 0.94 at 0.75, where a scale would have stretched it
   * to 0.57 and past one. The shader clamps the sum to nought and one
   * ([DiceMaterial.TABLE_SOURCE]), which can take the average a little under
   * [roughness] where a map reaches one; the felt's loses a few thousandths.
   */
  fun roughnessShift(
    roughness: Double,
    level: Double?,
    averaged: Boolean,
  ): Double = if (!averaged || level == null) 0.0 else roughness - level

  /**
   * What [colour] is drawn as over a picture whose average is [mean]: itself
   * when the look multiplies or there is no picture, and scaled to come out
   * at itself on average when the look asks for that ([averaged]).
   *
   * "Come out" is on the screen, under [light]: the surface reflects the
   * lamps on top of whatever colour it is, and a dark felt's darkest channel
   * is no bigger than that reflection, so the colour the material is given is
   * the one that draws as [colour] once its sheen is added
   * ([SurfaceLight.baseFor]). [SurfaceLight.MATTE] leaves [colour] as it is.
   */
  fun colourFor(
    colour: Colour,
    mean: Colour?,
    averaged: Boolean,
    light: SurfaceLight = SurfaceLight.MATTE,
  ): Colour {
    if (!averaged || mean == null) return colour
    val base = light.baseFor(colour)
    return Colour(
      red = base.red / mean.red.coerceAtLeast(LEAST_MEAN),
      green = base.green / mean.green.coerceAtLeast(LEAST_MEAN),
      blue = base.blue / mean.blue.coerceAtLeast(LEAST_MEAN),
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
