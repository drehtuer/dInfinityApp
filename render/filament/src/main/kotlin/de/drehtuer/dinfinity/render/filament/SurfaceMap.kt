package de.drehtuer.dinfinity.render.filament

/**
 * Which of a table surface's pictures a texture is (`DiceMaterial.SurfaceMaps`).
 *
 * It decides how the picture is uploaded. A colour is what an eye sees and is
 * stored the way a screen encodes it, sRGB; a normal or a roughness map is a
 * measurement, and reading one as sRGB would bend every number in it.
 *
 * @param colour whether the picture is a colour, uploaded as sRGB.
 */
enum class SurfaceMap(
  val colour: Boolean,
) {
  /** The colour, multiplied by the look's own. */
  ALBEDO(colour = true),

  /** Which way the surface faces, picture by picture. */
  NORMAL(colour = false),

  /** How rough the surface is, in the red channel. */
  ROUGHNESS(colour = false),
  ;

  companion object {
    /**
     * How many levels a full mip chain has for a picture [width] by [height]:
     * the picture, and every halving down to a single pixel on its longer
     * side. A 1024 square has eleven; a 2048 by 1024 has twelve.
     */
    fun mipLevelsOf(
      width: Int,
      height: Int,
    ): Int = Integer.SIZE - Integer.numberOfLeadingZeros(maxOf(width, height, 1))
  }
}
