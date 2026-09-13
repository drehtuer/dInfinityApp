package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.TableLook

/**
 * The one material every surface of a roll is drawn with, and the values that
 * make each of them look different (`docs/tables.md`, "Table looks").
 *
 * Filament ships no default material: every surface needs one compiled from
 * source, and this app compiles its own on the device at launch
 * (`docs/architecture.md`, decision 46). The source is here as text, and the
 * *parameters* — which are the whole of what a dice set or a table look may
 * vary in v1 — are computed here as plain numbers.
 *
 * That split is what lets the interesting half be tested on a JVM. Whether a
 * green felt table comes out green is arithmetic over a colour; whether the
 * shader compiles is a question for a GPU.
 */
object DiceMaterial {
  /**
   * The material, in Filament's own language.
   *
   * Deliberately dull: one lit, opaque, physically-based surface with a base
   * colour, a roughness and a metalness, optionally multiplied by a texture.
   * Dice are dice. The tray is a tray. Nothing here is trying to be clever,
   * and everything a package is allowed to vary is a number going in rather
   * than a line of this changing (`docs/TODO.md`, After v1).
   */
  const val SOURCE: String = """
        void material(inout MaterialInputs material) {
            prepareMaterial(material);
            vec4 colour = materialParams.baseColor;
            if (materialParams.textured > 0.5) {
                colour *= texture(materialParams_atlas, getUV0());
            }
            material.baseColor = colour;
            material.roughness = materialParams.roughness;
            material.metallic = materialParams.metallic;
        }
    """

  /** What a surface of the tray's floor is drawn with. */
  fun floorOf(look: TableLook): Parameters =
    Parameters(
      colour = Colour.of(look.floorColorArgb),
      roughness = look.roughness,
      metallic = look.metallic,
      texturePath = look.floorTexturePath,
    )

  /** And its walls, including the rim, which is the wall seen end-on. */
  fun wallOf(look: TableLook): Parameters =
    Parameters(
      colour = Colour.of(look.wallColorArgb),
      roughness = look.roughness,
      metallic = look.metallic,
      texturePath = look.wallTexturePath,
    )

  /**
   * And a die.
   *
   * A die with no atlas is drawn in its own colour and its numbers are put on
   * afterwards; a die with one is drawn through it. Either way the surface is
   * the same material with different numbers in it.
   */
  fun dieOf(
    material: DieMaterial,
    texturePath: String?,
  ): Parameters =
    Parameters(
      colour = Colour.of(material.colorArgb),
      roughness = material.roughness,
      metallic = material.metallic,
      texturePath = texturePath,
    )

  /**
   * What one surface's material instance is set to.
   *
   * @param texturePath the atlas to sample, relative to the package folder, or
   *   null to use [colour] alone.
   */
  data class Parameters(
    val colour: Colour,
    val roughness: Double,
    val metallic: Double,
    val texturePath: String?,
  ) {
    /** True when this surface samples an atlas rather than taking a flat colour. */
    val textured: Boolean get() = texturePath != null
  }
}

/**
 * A colour as the renderer wants it: linear, nought to one, with its alpha.
 *
 * A package writes `#1f5e3a`, which is sRGB — the space a screen shows and a
 * person picks colours in. A renderer lights linear colours, because that is
 * the space light actually adds up in, and handing it sRGB makes everything
 * washed out in the midtones in a way nobody can point at but everybody sees.
 * The conversion is the standard sRGB transfer function, and it belongs here
 * rather than in a shader so that it can be checked against known values.
 */
data class Colour(
  val red: Double,
  val green: Double,
  val blue: Double,
  val alpha: Double,
) {
  companion object {
    /** The colour packed into [argb], converted out of sRGB. */
    fun of(argb: Int): Colour =
      Colour(
        red = linear(channel(argb, RED_SHIFT)),
        green = linear(channel(argb, GREEN_SHIFT)),
        blue = linear(channel(argb, BLUE_SHIFT)),
        // Alpha is coverage rather than colour, so it is not a light level and
        // is not converted.
        alpha = channel(argb, ALPHA_SHIFT),
      )

    private fun channel(
      argb: Int,
      shift: Int,
    ): Double = ((argb shr shift) and BYTE) / BYTE.toDouble()

    /** The sRGB transfer function, which is a line near black and a curve above it. */
    private fun linear(value: Double): Double =
      if (value <= KNEE) value / KNEE_SLOPE else StrictMath.pow((value + OFFSET) / (1 + OFFSET), EXPONENT)

    private const val BYTE = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0

    private const val KNEE = 0.04045
    private const val KNEE_SLOPE = 12.92
    private const val OFFSET = 0.055
    private const val EXPONENT = 2.4
  }
}
