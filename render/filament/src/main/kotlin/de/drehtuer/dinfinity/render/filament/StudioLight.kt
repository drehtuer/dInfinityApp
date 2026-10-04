package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The room a tray is lit by, photographed (`docs/physics-and-rendering.md`,
 * "Rendering (normal mode)"; `docs/architecture.md`, decision 89).
 *
 * [RoomLight] made a room out of two colours and a direction. That is enough
 * to say a table is lit from above, and nowhere near enough for a polished die,
 * which mirrors *whatever is there*: a gradient has no window in it, no lamp
 * and no wall, so a lacquered die reflected a smear and looked like plastic
 * under a grey sky. This is a real room instead — a photographed panorama,
 * Poly Haven's **Brown Photostudio 02** ([SOURCE], CC0) — shipped as
 * [RESOURCE] and turned into Filament's image-based light on the device.
 *
 * The panorama goes to Filament twice, as [RoomLight]'s gradient did:
 *
 * - **as a cubemap**, for what a glossy surface reflects. The file is decoded
 *   by [Radiance] and folded into six faces by [StudioCube], both small and
 *   both tested on the JVM; the part that is genuinely hard — blurring a level
 *   of that cube per roughness for Filament's lighting model — is Filament's
 *   own prefilter, `Texture.generatePrefilterMipmap`, in the engine the app
 *   already ships (`FilamentEngine.studio`). Filament's `HDRLoader` and
 *   `IBLPrefilterContext` would do the first two on the GPU, but they live in
 *   `filament-utils-android`, whose native library drags in gltfio's: 17.5 MB
 *   over four ABIs for a header, a run-length code and a texture lookup
 *   (`docs/architecture.md`, decision 89);
 * - **as [IRRADIANCE]**, three bands of spherical harmonics, for what a matte
 *   surface integrates. Those are nine numbers, and nine numbers do not need a
 *   GPU, a decoder or any time at all on the device: they are worked out once
 *   from the same file by [irradianceOf] and written down here, and
 *   `StudioLightTest` works them out again from the shipped file on every
 *   build, so a changed panorama with stale numbers fails rather than lights
 *   the felt with a room that is not there.
 *
 * Two things a photograph does not know are decided here, and both are
 * arithmetic a JVM can check:
 *
 * - **which way is up.** The panorama is stored with `+y` up, as Filament
 *   reads every equirectangular image; this app's up is `+z` in the tray, the
 *   physics and the renderer. [rotation] turns one into the other.
 * - **which way the room faces.** The studio's big window is the brightest
 *   thing in it, and the key light is a lamp over the player's shoulder. A
 *   window on one side and a shadow thrown from the other is a picture of two
 *   suns, so the room is turned until the window is behind the key ([yawToward]),
 *   and the highlight a die mirrors sits where its shadow says the light is.
 *
 * **Nothing here touches Filament.** It is arithmetic over directions and
 * colours, which is the half worth testing on a JVM.
 */
object StudioLight {
  /** Where the panorama lives on the classpath, in this module's own resources. */
  const val RESOURCE: String = "/de/drehtuer/dinfinity/render/filament/brown_photostudio_02_1k.hdr"

  /** Where it came from: Poly Haven, released under CC0 (`docs/assets/README.md`). */
  const val SOURCE: String = "https://polyhaven.com/a/brown_photostudio_02"

  /** How many bands [IRRADIANCE] carries. Filament takes 1, 2 or 3. */
  const val BANDS: Int = 3

  /** Red, green and blue per coefficient. */
  private const val CHANNELS = 3

  /** Nine coefficients of three bands, as Filament's shader numbers them. */
  private const val COEFFICIENTS = BANDS * BANDS

  /**
   * What a matte surface facing any way in the studio receives, in the
   * panorama's own frame and units, ready for Filament's shader.
   *
   * Projected from [RESOURCE] by [irradianceOf], and checked against it by
   * `StudioLightTest`. Only the ratios matter: how bright the room is in the
   * tray is [TrayLighting.studioIntensity]'s to say.
   */
  val IRRADIANCE: FloatArray =
    floatArrayOf(
      0.734758f,
      0.715181f,
      0.706620f,
      0.395876f,
      0.398800f,
      0.420638f,
      0.477139f,
      0.466024f,
      0.457334f,
      -0.472071f,
      -0.468142f,
      -0.465089f,
      -0.448822f,
      -0.447303f,
      -0.447270f,
      0.457703f,
      0.456244f,
      0.451455f,
      -0.003141f,
      -0.006115f,
      -0.010310f,
      -0.427070f,
      -0.414244f,
      -0.403405f,
      -0.015944f,
      -0.020601f,
      -0.029688f,
    )

  /** The panorama's bytes as shipped, or null where the classpath has none. */
  fun bytes(): ByteArray? = StudioLight::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }

  /**
   * The direction the middle of pixel ([x], [y]) of a [width] × [height]
   * equirectangular panorama looks along, in the panorama's own frame (`+y`
   * up).
   *
   * Filament's mapping, the one its own `IBLPrefilterContext` folds a
   * panorama into a mirrored cubemap with: the top row is straight up, the
   * middle column looks along `+z`, and longitude runs towards `−x` — the
   * panorama seen from inside, the right way round. The irradiance and the
   * reflection cube ([StudioCube.faces], which inverts this) are both worked
   * out on this map, or a matte die would be lit from one side and mirror a
   * window on the other.
   */
  fun direction(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ): Vector3 {
    val latitude = (1.0 - 2.0 * (y + HALF) / height) * PI / 2
    val longitude = (2.0 * (x + HALF) / width - 1.0) * PI
    return Vector3(-cos(latitude) * sin(longitude), sin(latitude), cos(latitude) * cos(longitude))
  }

  /**
   * The irradiance of a whole panorama, as Filament's three-band harmonics.
   *
   * [pixels] is linear RGB radiance, three floats a pixel, the top row first.
   * Each coefficient is the radiance projected onto its harmonic, convolved
   * with a cosine — which is what turns *what the room looks like* into *how
   * much light a surface facing that way receives* — and divided by π, with
   * the harmonic's own normalisation folded in so the shader is left a plain
   * polynomial (`Irradiance_SphericalHarmonics`, evaluated by [evaluate]).
   * Three bands are enough for that, and not for the reflection: a cosine
   * lobe has almost nothing above band two, a mirror has everything.
   */
  fun irradianceOf(
    pixels: FloatArray,
    width: Int,
    height: Int,
  ): FloatArray {
    require(pixels.size == width * height * CHANNELS) {
      "a $width × $height panorama is ${width * height * CHANNELS} floats, not ${pixels.size}"
    }
    val sums = DoubleArray(COEFFICIENTS * CHANNELS)
    val polynomial = DoubleArray(COEFFICIENTS)
    for (y in 0 until height) {
      // The solid angle one pixel stands for, which shrinks towards the poles.
      val solidAngle = cos((1.0 - 2.0 * (y + HALF) / height) * PI / 2) * (2 * PI / width) * (PI / height)
      for (x in 0 until width) {
        polynomialAt(direction(x, y, width, height), polynomial)
        val at = (y * width + x) * CHANNELS
        for (index in sums.indices) {
          sums[index] += pixels[at + index % CHANNELS] * polynomial[index / CHANNELS] * solidAngle
        }
      }
    }
    return FloatArray(sums.size) { (sums[it] * SCALE[it / CHANNELS]).toFloat() }
  }

  /**
   * What Filament's shader makes of [harmonics] for a surface facing
   * [direction]: linear RGB, never below nought, in the panorama's units.
   */
  fun evaluate(
    harmonics: FloatArray,
    direction: Vector3,
  ): Colour {
    val polynomial = DoubleArray(COEFFICIENTS)
    polynomialAt(direction, polynomial)
    val rgb = DoubleArray(CHANNELS)
    for (coefficient in 0 until harmonics.size / CHANNELS) {
      for (channel in 0 until CHANNELS) {
        rgb[channel] += harmonics[coefficient * CHANNELS + channel] * polynomial[coefficient]
      }
    }
    return Colour(red = max(rgb[0], 0.0), green = max(rgb[1], 0.0), blue = max(rgb[2], 0.0), alpha = 1.0)
  }

  /**
   * Where the room is brightest, as a direction in its own frame: the slope
   * band one gives the irradiance, which points at the window.
   */
  fun brightest(harmonics: FloatArray = IRRADIANCE): Vector3 {
    fun slope(coefficient: Int) =
      RoomLight.luminance(
        Colour(
          red = harmonics[coefficient * CHANNELS].toDouble(),
          green = harmonics[coefficient * CHANNELS + 1].toDouble(),
          blue = harmonics[coefficient * CHANNELS + 2].toDouble(),
          alpha = 1.0,
        ),
      )
    // Filament adds band one up as y, z, x — the second, third and fourth.
    return Vector3(slope(BAND_ONE_X), slope(BAND_ONE_Y), slope(BAND_ONE_Z)).normalised()
  }

  /**
   * How far to turn the room about its own up so that its brightest side lies
   * the way [lightFrom] — a direction in the tray, pointing *at* the light —
   * leans. In radians, for [rotation].
   */
  fun yawToward(
    lightFrom: Vector3,
    harmonics: FloatArray = IRRADIANCE,
  ): Double {
    val window = brightest(harmonics)
    return atan2(-window.z, window.x) - atan2(lightFrom.y, lightFrom.x)
  }

  /**
   * The turn that takes a direction in the tray to the direction in the
   * panorama it should see: the tray's `+z` onto the panorama's `+y`, then
   * [yaw] about that up. A proper rotation, as Filament requires of an
   * indirect light's.
   *
   * Column-major, as `IndirectLight.Builder.rotation` takes it. Filament
   * applies it to the world it shades in, so a surface facing `n` in the tray
   * is lit by the room as seen along `R · n` ([turned] is that product).
   */
  fun rotation(yaw: Double): FloatArray {
    val c = cos(yaw)
    val s = sin(yaw)
    return floatArrayOf(
      // The tray's x…
      c.toFloat(),
      0f,
      (-s).toFloat(),
      // …its y…
      (-s).toFloat(),
      0f,
      (-c).toFloat(),
      // …and its up, which is the panorama's up whatever the yaw.
      0f,
      1f,
      0f,
    )
  }

  /** [direction] in the tray, as the room sees it through [rotation]. */
  fun turned(
    rotation: FloatArray,
    direction: Vector3,
  ): Vector3 =
    Vector3(
      rotation[0] * direction.x + rotation[ROW_COLUMN_1] * direction.y + rotation[ROW_COLUMN_2] * direction.z,
      rotation[1] * direction.x + rotation[ROW_COLUMN_1 + 1] * direction.y + rotation[ROW_COLUMN_2 + 1] * direction.z,
      rotation[2] * direction.x + rotation[ROW_COLUMN_1 + 2] * direction.y + rotation[ROW_COLUMN_2 + 2] * direction.z,
    )

  /**
   * The nine polynomials Filament's shader multiplies the coefficients by, in
   * its order: `1`, `y`, `z`, `x`, `yx`, `yz`, `3z² − 1`, `zx`, `x² − y²`.
   */
  private fun polynomialAt(
    d: Vector3,
    into: DoubleArray,
  ) {
    into[0] = 1.0
    into[BAND_ONE_Y] = d.y
    into[BAND_ONE_Z] = d.z
    into[BAND_ONE_X] = d.x
    into[YX] = d.y * d.x
    into[YZ] = d.y * d.z
    into[ZZ] = THREE * d.z * d.z - 1.0
    into[ZX] = d.z * d.x
    into[XX_YY] = d.x * d.x - d.y * d.y
  }

  private const val BAND_ONE_Y = 1
  private const val BAND_ONE_Z = 2
  private const val BAND_ONE_X = 3
  private const val YX = 4
  private const val YZ = 5
  private const val ZZ = 6
  private const val ZX = 7
  private const val XX_YY = 8
  private const val THREE = 3.0
  private const val HALF = 0.5

  /** Where a column-major 3 × 3 keeps its second and third columns. */
  private const val ROW_COLUMN_1 = 3
  private const val ROW_COLUMN_2 = 6

  /**
   * What each polynomial's integral is multiplied by: the cosine lobe's own
   * harmonic (π, 2π/3, π/4 by band), the square of the harmonic's
   * normalisation, and the 1/π of a Lambertian surface. A room of constant
   * radiance `L` comes out as `L` in band zero; a slope comes out at two
   * thirds of itself, which is what a cosine does to one (`StudioLightTest`).
   */
  private val SCALE: DoubleArray =
    doubleArrayOf(
      1.0 / (4 * PI),
      1.0 / (2 * PI),
      1.0 / (2 * PI),
      1.0 / (2 * PI),
      15.0 / (16 * PI),
      15.0 / (16 * PI),
      5.0 / (64 * PI),
      15.0 / (16 * PI),
      15.0 / (64 * PI),
    )
}
