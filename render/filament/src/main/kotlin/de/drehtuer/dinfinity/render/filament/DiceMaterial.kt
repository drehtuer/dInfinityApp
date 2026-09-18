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
   * colour, a roughness and a metalness, with artwork laid over it. Dice are
   * dice. The tray is a tray. Nothing here is trying to be clever, and
   * everything a package is allowed to vary is a number going in rather than a
   * line of this changing (`docs/TODO.md`, After v1).
   *
   * **The artwork is composited, not multiplied.** The body is worked out
   * first — the die's colour with its label printed into it — and the atlas is
   * then laid over it *by its own alpha*. Where an author drew something, that
   * is what the face carries; where they left the cell clear, the label shows
   * through, which is what `docs/dice-sets.md` ("Textures") has always
   * promised and what a plain multiply could not do: multiplying by a
   * transparent pixel gives black, not the die.
   *
   * Where the artwork *is* opaque the result is the old one exactly —
   * `baseColor * atlas` — which is what keeps a table's floor texture tinted
   * by its floor colour (`docs/tables.md`, "Table looks").
   */
  const val SOURCE: String = """
        void material(inout MaterialInputs material) {
            prepareMaterial(material);
            vec4 base = materialParams.baseColor;
            vec3 body = base.rgb;
            // How much of this pixel is *printed on* rather than body. It is
            // what holds a numeral opaque on a die you can see through: ink is
            // paint on the outside of the resin, and paint does not go clear
            // because the die did.
            float printed = 0.0;
            if (materialParams.numbered > 0.5) {
                // A signed distance field, not a picture of a number: the
                // edge is wherever the field crosses a half, and how wide the
                // crossing is on screen is what a derivative says. That is
                // what keeps a 20 sharp when the player pinches all the way
                // in (`core/glyphs`'s SignedDistanceField).
                float ink = texture(materialParams_glyphs, getUV0()).r;
                float soft = max(fwidth(ink), 0.0001);
                printed = smoothstep(0.5 - soft, 0.5 + soft, ink);
                body = mix(body, materialParams.inkColor.rgb, printed);
            }
            vec3 colour = body;
            if (materialParams.textured > 0.5) {
                // Straight alpha, uploaded unpremultiplied on purpose: a cell
                // an author left clear is a cell the label shows through.
                vec4 art = texture(materialParams_atlas, getUV0());
                colour = mix(body, base.rgb * art.rgb, art.a);
                printed = max(printed, art.a);
            }
            // A surface that lets light through is `opacity` covered where it
            // is bare body and wholly covered where something is printed on
            // it. For every opaque surface `opacity` is one, so the mix is one
            // too and the multiply below is by one — the same picture the
            // opaque material drew before any of this existed.
            float cover = mix(materialParams.opacity, 1.0, printed);
            // Filament's transparent blending wants the colour already
            // multiplied by its coverage. Doing it here rather than at the
            // blend is what keeps a half-clear die from glowing where the
            // felt behind it is bright.
            material.baseColor = vec4(colour * cover, cover);
            material.roughness = materialParams.roughness;
            material.metallic = materialParams.metallic;
            if (materialParams.clearCoat > 0.0) {
                // The lacquer on a die, which is a thin smooth layer over a
                // body that is not smooth at all. Without it the only way to
                // make a die shine is to make the resin itself glossy, and
                // glossy resin reflects its own colour where a varnish
                // reflects the room.
                material.clearCoat = materialParams.clearCoat;
                material.clearCoatRoughness = materialParams.clearCoatRoughness;
            }
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
   * A die is drawn in its own colour with its labels printed over it in
   * [DieMaterial.numberColorArgb], and its author's artwork laid on top of
   * that where the author drew any. The two are not alternatives: an atlas may
   * leave a face's cell clear, and that face is then printed exactly as a die
   * with no atlas at all would be (`docs/dice-sets.md`, "Textures"). So
   * [numbers] is supplied for a textured die too, and which of the two a face
   * ends up showing is the artwork's alpha's to say, per pixel, in [SOURCE].
   *
   * @param texturePath the atlas, as an [AtlasKey] — the package and the path
   *   inside it — or null for a die whose author supplied none.
   */
  fun dieOf(
    material: DieMaterial,
    texturePath: String?,
    numbers: NumberField? = null,
  ): Parameters =
    Parameters(
      colour = Colour.of(material.colorArgb),
      roughness = material.roughness,
      metallic = material.metallic,
      texturePath = texturePath,
      numbers = numbers,
      ink = Colour.of(material.numberColorArgb),
      opacity = material.opacity,
      clearCoat = DIE_COAT,
    )

  /**
   * What one surface's material instance is set to.
   *
   * @param texturePath the atlas to sample, as an [AtlasKey] for a die and as
   *   a bare path for a table look — which is why a table's floor is still
   *   drawn in its colour alone (`docs/TODO.md`, "Open questions"). Null for a
   *   surface that takes [colour] alone.
   * @param numbers the die's labels as a distance field, or null for a surface
   *   with nothing printed on it — which is every surface of the tray.
   * @param ink what [numbers] is printed in.
   * @param opacity how much of what is behind this surface it hides, one for
   *   everything but a die a set called translucent (`docs/dice-sets.md`).
   * @param clearCoat the lacquer over the body, nought for a surface with
   *   none. A tray has none: varnished felt is a table nobody owns.
   * @param clearCoatRoughness how polished that lacquer is.
   */
  data class Parameters(
    val colour: Colour,
    val roughness: Double,
    val metallic: Double,
    val texturePath: String?,
    val numbers: NumberField? = null,
    val ink: Colour = Colour.of(DieMaterial.DEFAULT_NUMBER_COLOR_ARGB),
    val opacity: Double = 1.0,
    val clearCoat: Double = 0.0,
    val clearCoatRoughness: Double = DIE_COAT_ROUGHNESS,
  ) {
    /** True when this surface samples an atlas rather than taking a flat colour. */
    val textured: Boolean get() = texturePath != null

    /** True when this surface has the built-in font printed over it. */
    val numbered: Boolean get() = numbers != null

    /**
     * True when this surface has to be *blended* rather than simply drawn.
     *
     * Which material it is drawn with, not which parameter it is given:
     * blending is fixed when a material is compiled, so a translucent die and
     * an opaque one are two materials and this is the question that picks
     * (`FilamentEngine`).
     */
    val blended: Boolean get() = opacity < 1.0
  }

  /**
   * How polished a die's lacquer is.
   *
   * Not nought: a perfect mirror reflects the room as a hard-edged picture of
   * it, and a die is a moulded thing that has been in a bag with other dice.
   * Low enough that the reflection is still a reflection rather than a sheen.
   */
  const val DIE_COAT_ROUGHNESS: Double = 0.12

  /** And how much of one there is. A die is varnished; nothing else here is. */
  const val DIE_COAT: Double = 1.0
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
