package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * What the lamps make of a surface of the tray seen straight down: how much
 * of its colour it shows ([diffuse]), and how much light it reflects *on top*
 * of its colour, whatever that colour is ([sheen]) — `docs/tables.md`, "What
 * the floors draw as".
 *
 * A dielectric reflects four per cent of the light that falls on it straight
 * on, and more at a glance; a rough one spreads that over a wide lobe instead
 * of a highlight. Under the studio's key and room a felt at roughness 0.9
 * gives back about 1.4 % of white that way — grey, because the lamps are —
 * and a channel as dark as green felt's red (`#1f`, 1.4 % in light) comes out
 * twice as bright as it was named. That is no fault of the textures: it is
 * what the floor's own material says, and what the Pixel 10a drew. A look in
 * `color_mode = "average"` says its colour is what the floor averages out to,
 * so [baseFor] takes the sheen back out of the colour the material is given.
 *
 * Everything here is Filament's own lighting model, written out for one view
 * — looking straight down at a surface facing up, the Table view a player
 * starts with — so that a JVM can check it:
 *
 * - the **key** ([TrayLighting.KEY_LUX] along [TrayLighting.KEY_DIRECTION])
 *   through Filament's GGX: its distribution, its fast Smith visibility (the
 *   one a material compiled for a phone uses) and Schlick's Fresnel;
 * - the **room** through the split sum Filament draws a room's reflection
 *   with: the directional albedo of that lobe ([albedoLookingDown]) times
 *   what the room sends down. The room is taken as even, at the brightness
 *   its harmonics give a surface facing up — exact for a matte lobe, and
 *   within one and a half per cent of the photographed room at 0.9 and ten
 *   at 0.75, where the reflection is tighter and sees more of the dim
 *   ceiling straight above;
 * - Filament's **energy compensation** for the light a rough lobe loses
 *   between its facets, on both;
 * - and the **diffuse** share: the key in full, the room and the fill less
 *   what the reflection took ([TrayLighting.whiteFloorLuminance] is the
 *   same sum for a white floor with no reflection at all).
 *
 * The surface is taken to be a dielectric, as every table this app ships is.
 */
data class SurfaceLight(
  val diffuse: Double,
  val sheen: Colour,
) {
  /**
   * The colour to hand the material for a surface to be drawn, under this
   * light, as [target].
   *
   * Where [target] is brighter than the sheen in every channel, that is
   * `(target − sheen) / diffuse`, and the surface draws as [target]. Where it
   * is not — a black felt is darker than the light any dielectric gives back
   * — no colour reaches it, because nothing darker than the sheen can be
   * drawn. Clamping each channel on its own would then change the colour's
   * hue: a dark brown whose blue is under the sheen would lose its blue and
   * keep its red and green, and come out maroon. So the target is lifted by
   * the least grey that brings its darkest channel up to the sheen, and the
   * differences between its channels — what makes it the colour it is — are
   * kept. Black felt comes out as its sheen, as dark as a felt can be under
   * these lamps.
   */
  fun baseFor(target: Colour): Colour {
    val lift = maxOf(0.0, sheen.red - target.red, sheen.green - target.green, sheen.blue - target.blue)
    return Colour(
      red = ((target.red + lift - sheen.red) / diffuse).coerceAtLeast(0.0),
      green = ((target.green + lift - sheen.green) / diffuse).coerceAtLeast(0.0),
      blue = ((target.blue + lift - sheen.blue) / diffuse).coerceAtLeast(0.0),
      alpha = target.alpha,
    )
  }

  /** What a surface of colour [base] draws as under this light: the other way round from [baseFor]. */
  fun drawn(base: Colour): Colour =
    Colour(
      red = base.red * diffuse + sheen.red,
      green = base.green * diffuse + sheen.green,
      blue = base.blue * diffuse + sheen.blue,
      alpha = base.alpha,
    )

  companion object {
    /** No lamps at all: a colour shows as itself. What a look that multiplies is drawn as. */
    val MATTE: SurfaceLight = SurfaceLight(diffuse = 1.0, sheen = Colour(0.0, 0.0, 0.0, 1.0))

    /**
     * A dielectric's reflectance looking straight on: Filament's default
     * `reflectance` of a half, which is `0.16 · 0.5²`.
     */
    const val DIELECTRIC_F0: Double = 0.04

    /**
     * The smoothest Filament draws on a phone: its shader clamps a perceptual
     * roughness to this (`MIN_PERCEPTUAL_ROUGHNESS`, mobile).
     */
    const val LEAST_ROUGHNESS: Double = 0.089

    /**
     * What the lamps make of a surface facing up, [roughness] rough (perceptual,
     * as a look writes it), looked at straight down, in the units the screen
     * shows: a white matte floor is [TrayLighting.WHITE_LEVEL].
     */
    fun facingUp(roughness: Double): SurfaceLight {
      val perceptual = roughness.coerceIn(LEAST_ROUGHNESS, 1.0)
      val alpha = perceptual * perceptual
      val albedo = albedoLookingDown(alpha)
      val reflected = (1.0 - DIELECTRIC_F0) * albedo.grazing + DIELECTRIC_F0 * albedo.white
      val compensation = 1.0 + DIELECTRIC_F0 * (1.0 / albedo.white - 1.0)
      val exposure = TrayLighting.exposure()
      val key = keyLookingDown(alpha) * TrayLighting.KEY_LUX * compensation * exposure
      val room = StudioLight.evaluate(StudioLight.IRRADIANCE, PANORAMA_UP)
      val roomScale = TrayLighting.studioIntensity() * reflected * compensation * exposure
      val towardsKey = (-TrayLighting.KEY_DIRECTION).normalised()
      val diffuse =
        exposure *
          (
            TrayLighting.KEY_LUX * max(0.0, towardsKey dot TrayLighting.UP) / PI +
              (TrayLighting.ambientUpward() + FillLight.upward()) * (1.0 - reflected)
          )
      return SurfaceLight(
        diffuse = diffuse,
        sheen =
          Colour(
            red = key + room.red * roomScale,
            green = key + room.green * roomScale,
            blue = key + room.blue * roomScale,
            alpha = 1.0,
          ),
      )
    }

    /**
     * The two halves of Filament's split-sum table (its `DFG`) for a view
     * straight along the normal, for a lobe [alpha] wide (`roughness²`):
     * what a white reflector gives back of an even room ([Albedo.white]), and
     * the part of that Schlick's Fresnel adds at a glance ([Albedo.grazing]).
     * A surface whose reflectance straight on is `f0` gives back
     * `(1 − f0) · grazing + f0 · white`.
     *
     * Looking straight down, the half vector's angle is the whole of the
     * geometry — the light leaves at twice it — and the integral is taken
     * over GGX's own share of facets, `u`, the variable Filament importance-
     * samples the table with: facets are spread evenly in `u` however narrow
     * the lobe, so Simpson's rule over it is as good for a polished surface as
     * for felt. In it the albedo is `∫ 4 · V · NoL du`, up to the facet that
     * sends the light along the surface (`NoL = 0`, at `u = 1 / (1 + α²)`).
     */
    fun albedoLookingDown(alpha: Double): Albedo {
      val a2 = alpha * alpha
      val to = 1.0 / (1.0 + a2)
      val step = to / SIMPSON_STEPS
      var white = 0.0
      var grazing = 0.0
      for (i in 0..SIMPSON_STEPS) {
        val u = i * step
        val cosHalfSquared = (1.0 - u) / (1.0 + (a2 - 1.0) * u)
        val cosLight = (2.0 * cosHalfSquared - 1.0).coerceAtLeast(0.0)
        val g = PDF_TO_BRDF * correlatedVisibility(cosLight, alpha) * cosLight
        val weight =
          if (i == 0 || i == SIMPSON_STEPS) {
            1.0
          } else if (i % 2 == 1) {
            SIMPSON_ODD
          } else {
            2.0
          }
        white += weight * g
        grazing += weight * g * (1.0 - sqrt(cosHalfSquared)).pow(FRESNEL_POWER)
      }
      return Albedo(white = white * step / 3.0, grazing = grazing * step / 3.0)
    }

    /**
     * How much of the key's illuminance a dielectric [alpha] wide sends
     * straight up, per lux: GGX's `D · V · F · NoL`, as Filament's direct
     * lighting has it.
     */
    fun keyLookingDown(alpha: Double): Double {
      val towardsKey = (-TrayLighting.KEY_DIRECTION).normalised()
      val cosLight = towardsKey dot TrayLighting.UP
      if (cosLight <= 0.0) return 0.0
      val half = (towardsKey + TrayLighting.UP).normalised()
      val cosHalf = half dot TrayLighting.UP
      val fresnel = DIELECTRIC_F0 + (1.0 - DIELECTRIC_F0) * (1.0 - (half dot towardsKey)).pow(FRESNEL_POWER)
      return distribution(cosHalf, alpha) * fastVisibility(cosLight, alpha) * fresnel * cosLight
    }

    /** GGX's distribution of facets at [cosHalf] to the normal. */
    internal fun distribution(
      cosHalf: Double,
      alpha: Double,
    ): Double {
      val a2 = alpha * alpha
      val d = cosHalf * cosHalf * (a2 - 1.0) + 1.0
      return a2 / (PI * d * d)
    }

    /** Smith's height-correlated visibility, looking straight down: what Filament's table was made with. */
    internal fun correlatedVisibility(
      cosLight: Double,
      alpha: Double,
    ): Double {
      val a2 = alpha * alpha
      return HALF / (cosLight + sqrt(cosLight * cosLight * (1.0 - a2) + a2))
    }

    /** And the fast approximation of it a phone's material draws its lamps with, straight down. */
    internal fun fastVisibility(
      cosLight: Double,
      alpha: Double,
    ): Double = HALF / (2.0 * cosLight + (cosLight + 1.0 - 2.0 * cosLight) * alpha)

    /** Filament's harmonics are in the panorama's frame, whose up is `+y`. */
    private val PANORAMA_UP = Vector3(0.0, 1.0, 0.0)

    private const val HALF = 0.5
    private const val FRESNEL_POWER = 5
    private const val SIMPSON_STEPS = 1024

    /** Simpson's rule weighs every odd sample four times, every inner even one twice. */
    private const val SIMPSON_ODD = 4.0

    /** The 4 in GGX's `4 · V · NoL` when its half-vector density is turned into a light direction. */
    private const val PDF_TO_BRDF = 4.0
  }

  /** A lobe's directional albedo, for a white reflector and for the part Fresnel adds at a glance. */
  data class Albedo(
    val white: Double,
    val grazing: Double,
  )
}
