package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI

/**
 * The fill lamp, drawn as part of the room (`docs/physics-and-rendering.md`,
 * "Rendering (normal mode)"; `docs/architecture.md`, decision 89).
 *
 * Filament shades exactly one directional light per scene — the brightest; its
 * frame uniforms carry a single light direction and colour — and drops the
 * rest without a word. So the tray's second directional light, the fill that
 * keeps a die's shadowed faces readable, **was never drawn**, on any version
 * of this tray. The device showed it once the felt could be measured: where
 * the key was shadowed (across the rim band PCSS drew, in the gallery) the
 * felt came out at 0.40 of the lit felt, where key, fill and room predict
 * 0.50 and key and room alone 0.43; and the felt came out at about three
 * quarters of the light the exposure had been worked out for.
 *
 * The fill is now added to the room's irradiance, as the three-band spherical
 * harmonics of a distant lamp ([harmonics]): Filament's diffuse ambient *is*
 * three bands, and a lamp's clamped cosine in three bands is exact to a few
 * per cent. A fill has no highlight and casts no shadow, which is what a fill
 * is for. Everything here is arithmetic, checked on a JVM (`TrayLightingTest`).
 */
object FillLight {
  /**
   * The three-band harmonics of a distant lamp of [lux] shining from
   * [towards] (a direction pointing *at* the lamp), in Filament's
   * pre-scaled form: what a white matte surface facing `n` sends back is
   * `lux · max(0, n · towards) / π`, and these nine grey coefficients are
   * the clamped cosine's first three Legendre terms — 1/4, 1/2 and 5/16 —
   * written out on [StudioLight.evaluate]'s polynomials.
   *
   * Three bands are what Filament's diffuse ambient has, and they hold a
   * cosine to a few per cent: six over facing the lamp, a tenth of it
   * wrapping round to a face side on, and a few per cent under nought just
   * past that, which the room it is added to more than covers. A fill is
   * meant to be soft.
   */
  fun harmonics(
    towards: Vector3,
    lux: Double,
  ): FloatArray {
    val d = towards.normalised()
    val scale = lux / PI
    val coefficients =
      doubleArrayOf(
        CONSTANT,
        d.y * SLOPE,
        d.z * SLOPE,
        d.x * SLOPE,
        d.y * d.x * CROSS,
        d.y * d.z * CROSS,
        (THREE * d.z * d.z - 1.0) * ZONAL,
        d.z * d.x * CROSS,
        (d.x * d.x - d.y * d.y) * SECTORAL,
      )
    return FloatArray(coefficients.size * CHANNELS) { (coefficients[it / CHANNELS] * scale).toFloat() }
  }

  /**
   * The studio's irradiance with the fill in it, as Filament is given it:
   * [harmonics] turned into the panorama's frame
   * ([TrayLighting.studioRotation]) and divided by
   * [TrayLighting.studioIntensity], which Filament multiplies the whole room
   * by, added to [StudioLight.IRRADIANCE].
   */
  fun inStudio(): FloatArray {
    val towards = StudioLight.turned(TrayLighting.studioRotation(), -TrayLighting.FILL_DIRECTION)
    return added(StudioLight.IRRADIANCE, harmonics(towards, TrayLighting.FILL_LUX / TrayLighting.studioIntensity()))
  }

  /**
   * And the gradient's: its two bands, a third band of nothing, and the fill
   * in the tray's own frame — the gradient is not turned — over
   * [TrayLighting.gradientIntensity].
   */
  fun inGradient(): FloatArray =
    added(
      RoomLight.irradiance().copyOf(StudioLight.BANDS * StudioLight.BANDS * CHANNELS),
      harmonics(-TrayLighting.FILL_DIRECTION, TrayLighting.FILL_LUX / TrayLighting.gradientIntensity()),
    )

  /**
   * What the fill sends back off a white floor facing up, in candela per
   * square metre: [harmonics] evaluated, not the exact cosine, because the
   * harmonics are what Filament draws.
   */
  fun upward(): Double {
    val fill = harmonics(-TrayLighting.FILL_DIRECTION, TrayLighting.FILL_LUX)
    return RoomLight.luminance(StudioLight.evaluate(fill, TrayLighting.UP))
  }

  private fun added(
    room: FloatArray,
    fill: FloatArray,
  ): FloatArray {
    require(room.size == fill.size) { "${room.size} coefficients and ${fill.size} cannot be added" }
    return FloatArray(room.size) { room[it] + fill[it] }
  }

  /** The clamped cosine's Legendre terms (1/4, 1/2, 5/16), on Filament's polynomials. */
  private const val CONSTANT = 0.25
  private const val SLOPE = 0.5
  private const val CROSS = 15.0 / 16.0
  private const val ZONAL = 5.0 / 64.0
  private const val SECTORAL = 15.0 / 64.0
  private const val CHANNELS = 3
  private const val THREE = 3.0
}
