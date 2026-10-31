package de.drehtuer.dinfinity.render.filament

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * How light brighter than white becomes a pixel (`docs/physics-and-rendering.md`,
 * "Highlights past white are bent, not cut"; `docs/architecture.md`, decision 108).
 *
 * The tone mapper is linear ([FilamentEngine.colorGrading] says why), and a
 * linear mapper stops dead at one: a metal die's highlight at 1.8 of white and
 * a white face at 1.02 both came out 255, a flat patch with nothing in it.
 * Filament draws in floating point, so the light past white exists right up
 * to that cut. This is what is done with it instead.
 *
 * **Below [KNEE] nothing changes.** Every table, every felt, every set's
 * colour is far under it, so the colour a package wrote is still the colour
 * drawn — which is the one property the linear mapper was chosen for, and
 * the one ACES, AgX and PBR Neutral each broke in their own way.
 *
 * **Above it, the brightest channel is rolled off and the rest follow it.**
 * This is the shoulder of Khronos' *PBR Neutral* tone mapper and nothing
 * else of it: the peak channel is bent so it meets one only at infinity, the
 * other channels are scaled by the same ratio so the hue holds, and the
 * further the light was past the bend the more of it is let go to white —
 * a highlight on ivory metal is cream, and the lamp's own reflection is
 * white. PBR Neutral's *toe* is not here: it subtracted up to four per cent
 * of white from every colour's smallest channel, and that is what drew green
 * felt as (0, 70, 22). Without it the shoulder starts at 0.8, where PBR
 * Neutral's 0.76 plus its 0.04 offset put it.
 *
 * **How far past white is kept** is [HEADROOM]: Filament's grading is run
 * that many times darker ([GRADING_EV]), so its own linear cut lands at four
 * times white instead of at white, into a half-float frame; [SOURCE] brings it
 * back up, bends it and writes the screen. Past four times white everything
 * is the curve's 0.99 and up, which is white.
 */
object ToneCurve {
  /** Where the shoulder starts, in the units the screen shows: one is white. */
  const val KNEE: Double = 0.8

  /** How quickly light far past the knee is let go to white: PBR Neutral's own. */
  const val DESATURATION: Double = 0.15

  /** How many times white the frame keeps before it is bent. */
  const val HEADROOM: Double = 4.0

  /** The exposure Filament's grading is given so its cut lands at [HEADROOM], in stops. */
  val GRADING_EV: Float = (-ln(HEADROOM) / ln(2.0)).toFloat()

  /**
   * What light of [red], [green] and [blue] — linear, one being white — is
   * shown as, linear. The same arithmetic as [SOURCE], which is what draws it.
   */
  fun shown(
    red: Double,
    green: Double,
    blue: Double,
  ): DoubleArray {
    val peak = max(red, max(green, blue))
    if (peak < KNEE) return doubleArrayOf(red, green, blue)
    val reach = 1.0 - KNEE
    val bent = 1.0 - reach * reach / (peak + reach - KNEE)
    val scale = bent / peak
    val toWhite = 1.0 - 1.0 / (DESATURATION * (peak - bent) + 1.0)
    return doubleArrayOf(red, green, blue)
      .map { channel ->
        val scaled = channel * scale
        scaled + (bent - scaled) * toWhite
      }.toDoubleArray()
  }

  /** The sRGB transfer function, linear light to the value a screen is sent. */
  fun encoded(linear: Double): Double {
    val clamped = linear.coerceIn(0.0, 1.0)
    return if (clamped <= LINEAR_KNEE) {
      clamped * KNEE_SLOPE
    } else {
      (1.0 + OFFSET) * clamped.pow(1.0 / EXPONENT) - OFFSET
    }
  }

  /** And back: a value a screen is sent, to linear light. */
  fun decoded(encoded: Double): Double {
    val clamped = encoded.coerceIn(0.0, 1.0)
    return if (clamped <= ENCODED_KNEE) clamped / KNEE_SLOPE else ((clamped + OFFSET) / (1.0 + OFFSET)).pow(EXPONENT)
  }

  /**
   * The halo round a glowing die, in the tone pass's language: the glow mask's
   * blurs from the first halving down, filtered, each by its weight
   * ([Glow.HALO_WEIGHTS], [GlowPass]). Added to the light before it is bent,
   * so a halo over a bright face rolls off with the face.
   */
  private val HALO: String =
    Glow.HALO_WEIGHTS
      .mapIndexed { level, weight -> "textureLod(materialParams_glow, uv, ${level + 1}.0).rgb * $weight" }
      .joinToString(" + ")

  /**
   * The full-screen pass, in Filament's material language: the half-float
   * frame Filament graded [HEADROOM] times darker, read pixel for pixel,
   * brought back to its light, bent by [shown]'s curve, encoded for the
   * screen and dithered.
   *
   * Pixel for pixel by `texelFetch` at the fragment's own position, not by a
   * texture coordinate: the frame and the screen are the same size, and a
   * sampled coordinate is one more place for a half-texel or a flipped row to
   * creep in. The dither is the one Filament's post pass would have applied
   * and this pass replaces: half an 8-bit step either way, from a hash of the
   * pixel, so a slow gradient across the felt does not band.
   */
  val SOURCE: String = """
        vec3 toneDecoded(vec3 c) {
            vec3 low = c / $KNEE_SLOPE;
            vec3 high = pow((c + $OFFSET) / ${1.0 + OFFSET}, vec3($EXPONENT));
            return mix(high, low, vec3(lessThanEqual(c, vec3($ENCODED_KNEE))));
        }

        vec3 toneEncoded(vec3 c) {
            c = clamp(c, 0.0, 1.0);
            vec3 low = c * $KNEE_SLOPE;
            vec3 high = ${1.0 + OFFSET} * pow(c, vec3(${1.0 / EXPONENT})) - $OFFSET;
            return mix(high, low, vec3(lessThanEqual(c, vec3($LINEAR_KNEE))));
        }

        vec3 toneShown(vec3 light) {
            float peak = max(light.r, max(light.g, light.b));
            if (peak < $KNEE) return light;
            float reach = ${1.0 - KNEE};
            float bent = 1.0 - reach * reach / (peak + reach - $KNEE);
            vec3 scaled = light * (bent / peak);
            float toWhite = 1.0 - 1.0 / ($DESATURATION * (peak - bent) + 1.0);
            return mix(scaled, vec3(bent), toWhite);
        }

        void material(inout MaterialInputs material) {
            prepareMaterial(material);
            vec3 stored = texelFetch(materialParams_frame, ivec2(gl_FragCoord.xy), 0).rgb;
            vec3 light = toneDecoded(stored) * $HEADROOM;
            if (materialParams.halo > 0.0) {
                vec2 uv = gl_FragCoord.xy / vec2(textureSize(materialParams_frame, 0));
                light += ($HALO) * materialParams.halo;
            }
            vec3 shown = toneEncoded(toneShown(light));
            float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
            shown += (noise - 0.5) * ${DITHER_STEPS / BYTE};
            material.baseColor = vec4(shown, 1.0);
        }
    """

  private const val LINEAR_KNEE = 0.0031308
  private const val ENCODED_KNEE = 0.04045
  private const val KNEE_SLOPE = 12.92
  private const val OFFSET = 0.055
  private const val EXPONENT = 2.4

  /** Half a step of an 8-bit channel either way. */
  private const val DITHER_STEPS = 1.0
  private const val BYTE = 255.0
}
