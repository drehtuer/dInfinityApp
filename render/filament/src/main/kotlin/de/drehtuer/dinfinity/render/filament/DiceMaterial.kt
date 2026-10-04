package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.TableLook
import kotlin.math.ln

/**
 * The materials every surface of a roll is drawn with, and the values that
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
   * What every surface works out first, before anything decides how light
   * leaves it: the body colour, the label printed into it, the artwork over
   * that, and how much of the pixel is printed on rather than body.
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
  private const val SURFACE: String = """
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
            material.baseColor = vec4(colour, 1.0);
            material.metallic = materialParams.metallic;
    """

  /** The lacquer, and the end of the function both materials share. */
  private const val COAT: String = """
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

  /**
   * The opaque material, in Filament's own language.
   *
   * Deliberately dull: one lit, opaque, physically-based surface with a base
   * colour, a roughness and a metalness, with artwork laid over it. Dice are
   * dice. The tray is a tray. Nothing here is trying to be clever, and
   * everything a package is allowed to vary is a number going in rather than a
   * line of this changing (`docs/TODO.md`, After v1).
   */
  const val SOURCE: String =
    SURFACE + """
            material.roughness = materialParams.roughness;
    """ + COAT

  /**
   * The material of a die light passes through: the same surface, and resin
   * under it.
   *
   * Filament's *screen-space refraction*: the opaque scene is drawn first, and
   * this surface then looks into that picture along a ray bent by the resin's
   * index, through a body [Resin.thicknessMm] thick, losing colour to
   * [Resin.absorption] on the way. What it sees is the felt, the shadow and the
   * opaque dice actually behind it, moved the way a lens moves them — which is
   * what makes a die read as a solid lump of clear stuff rather than a
   * see-through picture of one (`docs/physics-and-rendering.md`, "A die you can
   * see into").
   *
   * The roughness is the resin's *scatter*, not its surface: Filament blurs
   * what is seen through a surface by its roughness, so a milky die is a rough
   * one inside a smooth lacquer, and the lacquer still gives the sharp
   * highlight a polished die has. Where something is printed the surface is
   * the author's roughness and lets nothing through: ink is paint on the
   * outside of the resin, and a face you cannot read is not a die.
   */
  const val RESIN_SOURCE: String =
    SURFACE + """
            float bare = 1.0 - printed;
            material.roughness = mix(materialParams.roughness, materialParams.scatter, bare);
            material.transmission = materialParams.transmission * bare;
            material.ior = materialParams.ior;
            material.thickness = materialParams.thickness;
            material.absorption = materialParams.absorption;
    """ + COAT

  /**
   * Which compiled material a surface is drawn with.
   *
   * How light leaves a surface — whether it is refracted, and so drawn after
   * the opaque scene and against a picture of it — is fixed when Filament
   * compiles a material, so a resin die is a second material rather than a
   * parameter of the first. [key] names it in [MaterialCache].
   */
  enum class Variant(
    val source: String,
    val key: String,
  ) {
    /** Every surface of the tray and every die nothing passes through. */
    OPAQUE(SOURCE, "opaque"),

    /** A die a set called translucent. */
    RESIN(RESIN_SOURCE, "resin"),
  }

  /** Which of the [Variant]s [parameters] are drawn with. */
  fun variantOf(parameters: Parameters): Variant = if (parameters.resin != null) Variant.RESIN else Variant.OPAQUE

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
   * @param scale what the throw's capacity rule shrank the die by, which is
   *   how thick the resin of a translucent one is drawn ([Resin.of]).
   */
  fun dieOf(
    material: DieMaterial,
    texturePath: String?,
    numbers: NumberField? = null,
    scale: Double = 1.0,
  ): Parameters =
    Parameters(
      colour = Colour.of(material.colorArgb),
      roughness = material.roughness,
      metallic = material.metallic,
      texturePath = texturePath,
      numbers = numbers,
      ink = Colour.of(material.numberColorArgb),
      resin = Resin.of(material, scale),
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
   * @param resin what light does inside a die it passes through, or null for
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
    val resin: Resin? = null,
    val clearCoat: Double = 0.0,
    val clearCoatRoughness: Double = DIE_COAT_ROUGHNESS,
  ) {
    /** True when this surface samples an atlas rather than taking a flat colour. */
    val textured: Boolean get() = texturePath != null

    /** True when this surface has the built-in font printed over it. */
    val numbered: Boolean get() = numbers != null
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
 * What light does inside a translucent die, worked out from the three numbers
 * a set already writes — `translucency`, `color` and `size_mm`
 * (`docs/dice-sets.md`). Nothing here is a field of its own in the format:
 * every resin a set can describe is one of these, and the constants below are
 * what turn a word an author understands into the ones Filament does.
 *
 * @param transmission how much of the light leaving the bare body came
 *   through it rather than off it: the set's translucency, as it stands.
 * @param scatter how blurred what is seen through the die is, as a roughness:
 *   a milky die scatters, a glassy one does not, and an author who made the
 *   die rough frosted it.
 * @param ior how sharply the resin bends light.
 * @param thicknessMm how far through the die a ray travels, in the scene's
 *   millimetres.
 * @param absorption how much of each colour the resin swallows per
 *   millimetre, which is what makes the middle of a die deeper in colour than
 *   its thin edges.
 */
data class Resin(
  val transmission: Double,
  val scatter: Double,
  val ior: Double,
  val thicknessMm: Double,
  val absorption: Absorption,
) {
  companion object {
    /**
     * Cast acrylic and epoxy, the two things dice are made of, are 1.49 to
     * 1.55. Half a hundredth either way is not something an eye can tell.
     */
    const val IOR: Double = 1.5

    /**
     * How blurred the view through a die at the very bottom of the
     * translucency scale is, as a roughness. A die that is barely translucent
     * is milky — light gets in and is scattered — and one that is wholly clear
     * is glass; between them the blur falls away in a straight line.
     */
    const val MILKY_SCATTER: Double = 0.6

    /**
     * How thick a die is to a ray through it, as a share of its size across.
     * A die is a polyhedron rather than the sphere Filament's model assumes,
     * and between a d6's inscribed sphere (0.58 of its corner-to-corner size)
     * and a d20's (0.79) this is the middle.
     */
    const val THICKNESS_OF_SIZE: Double = 0.7

    /**
     * How much the resin's own colour deepens a ray that crosses the whole
     * die, as a power of that colour. Filament already tints whatever is seen
     * through a die by the die's colour, once; this is on top of that, and a
     * half is what makes the middle of an amber die richer than its edges
     * without turning it to brown glass.
     */
    const val ABSORPTION_DEPTH: Double = 0.5

    /**
     * The least of a colour channel the resin is allowed to pass. A channel of
     * exactly nought would ask for infinite absorption.
     */
    const val LEAST_CHANNEL: Double = 0.02

    /**
     * The thinnest a die is ever taken to be, so that a throw scaled to
     * nothing cannot ask for an infinite absorption.
     */
    const val THINNEST_MM: Double = 1.0

    /** The resin of [material] at [scale], or null for a die nothing passes through. */
    fun of(
      material: DieMaterial,
      scale: Double,
    ): Resin? {
      val clamped = material.clampedToLimits()
      val translucency = clamped.translucency
      if (translucency <= 0.0) return null
      val thickness = (clamped.sizeMm * scale * THICKNESS_OF_SIZE).coerceAtLeast(THINNEST_MM)
      return Resin(
        transmission = translucency,
        scatter = maxOf(clamped.roughness, MILKY_SCATTER * (1.0 - translucency)),
        ior = IOR,
        thicknessMm = thickness,
        absorption = Absorption.of(Colour.of(material.colorArgb), thickness),
      )
    }
  }
}

/**
 * How much of each colour a material swallows per millimetre a ray travels
 * through it — Filament's `absorption`, which it applies as
 * `exp(-absorption × distance)`.
 */
data class Absorption(
  val red: Double,
  val green: Double,
  val blue: Double,
) {
  companion object {
    /**
     * What a resin of [colour] absorbs so that a ray crossing all of
     * [thicknessMm] keeps [Resin.ABSORPTION_DEPTH] of that colour as a power:
     * a channel the colour is full of is not absorbed at all, and one it has
     * none of is absorbed most.
     */
    fun of(
      colour: Colour,
      thicknessMm: Double,
    ): Absorption {
      fun channel(value: Double): Double =
        -ln(value.coerceIn(Resin.LEAST_CHANNEL, 1.0)) * Resin.ABSORPTION_DEPTH / thicknessMm
      return Absorption(channel(colour.red), channel(colour.green), channel(colour.blue))
    }
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
