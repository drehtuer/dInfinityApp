package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
import kotlin.math.max

/**
 * How bright everything over the tray is, and how it is photographed
 * (`docs/physics-and-rendering.md`, "Rendering (normal mode)";
 * `docs/architecture.md`, decision 89).
 *
 * The lamps used to be constants inside `FilamentStage`, where nothing could
 * reach them but a GPU. They are here because the decisions made about them
 * are arithmetic that has to come out right, and only a JVM can say whether
 * it does:
 *
 * - **the studio is as bright as the gradient was** ([studioIntensity]). A
 *   photographed room comes in its own units, and swapping the room must not
 *   make the table darker or wash it out. What is held equal is what the felt
 *   receives from the room — the felt faces up, it is most of the picture, and
 *   a player compares tables by it — so the studio's intensity is whatever
 *   makes its upward irradiance the gradient's ([ambientUpward]).
 * - **the fill is part of the room** ([FillLight]). Filament draws one
 *   directional light per scene — the brightest; its frame uniforms carry a
 *   single light direction — so a second directional "fill" was never drawn
 *   at all, on any version of this tray. The device showed it: felt the key
 *   did not reach was 0.40 of the lit felt where key, fill and room predict
 *   0.50 and key and room alone 0.43, and the felt came out at three quarters
 *   of the brightness the exposure was set for. The fill is now added to the
 *   room's irradiance as the three-band harmonics of a distant lamp, which is
 *   what Filament's diffuse ambient is made of anyway and which a JVM can
 *   check.
 * - **a table's colour is the colour its package wrote** ([exposure]). The
 *   tone mapper is linear ([FilamentEngine.colorGrading] says why PBR Neutral
 *   is not), so a felt lit to [WHITE_LEVEL] of white shows its base colour at
 *   that level, and nothing between the light and the screen bends it.
 *
 * The rest is what the view does with a frame, written down where its reasons
 * can be.
 */
object TrayLighting {
  /** A key light bright enough to read a die by, in lux. */
  const val KEY_LUX: Double = 80_000.0

  /**
   * And a fill that keeps the shadowed faces off black, where a number cannot
   * be read — drawn as part of the room ([FillLight]), not as a lamp.
   */
  const val FILL_LUX: Double = 25_000.0

  /** Neutral daylight, so a table look's own colour is the colour you see. */
  const val DAYLIGHT_KELVIN: Float = 6_500.0f

  /**
   * Down, and from over the player's shoulder — the direction a lamp is in
   * when somebody rolls dice on a table in front of them. The direction the
   * light *travels*.
   */
  val KEY_DIRECTION: Vector3 = Vector3(-0.4, -0.3, -1.0)

  /** And back the other way, across the tray, to lift the shadowed faces. */
  val FILL_DIRECTION: Vector3 = Vector3(0.6, 0.5, -0.7)

  /**
   * How bright the gradient room is: about a seventh of the key light.
   *
   * Enough that a wall facing away from both lamps reads as a wall rather
   * than as a hole, and low enough that the key still casts the shadow that
   * puts a die on the table. Tuned against the Pixel 10a, which is the only
   * place it can be judged (`docs/TODO.md`, Step 5.6).
   *
   * It is the *average* brightness, not the brightness in any one
   * direction: [gradientIntensity] divides it by the room's own average.
   */
  const val AMBIENT_LUX: Double = 12_000.0

  /** This app's up, in the tray, in the physics and in the renderer. */
  val UP: Vector3 = Vector3(0.0, 0.0, 1.0)

  /**
   * Where a white floor facing up lands, as a fraction of the brightest the
   * screen can show.
   *
   * Not one, because the tone mapper is linear and stops dead at one, and
   * every surface carries a little sheen on top of its colour — the felt's
   * is about one per cent of white, which on `#1f5e3a`'s red channel is a
   * third of the colour. The brightest matte surface is not the floor but a
   * face tilted towards the key and the studio's window ([faceLevel]: 1.13
   * of white here). A pure white face there goes past white; the built-in
   * set's bone resin, a felt, a table do not. It also keeps the exposure
   * within a fifth of Filament's default, the one the tray had before any
   * of this, so going from one tone mapper to the other changes the picture
   * by the mapper and not by a jump in exposure.
   */
  const val WHITE_LEVEL: Double = 0.95

  /**
   * The intensity Filament is given for the gradient room ([RoomLight]):
   * [AMBIENT_LUX] divided by the room's average, so that the gradient changed
   * where the light came from and not how much of it there was.
   */
  fun gradientIntensity(): Double = AMBIENT_LUX / RoomLight.averageBrightness()

  /**
   * What a surface facing straight up receives from the room, in the same
   * units Filament shades in: the gradient's sky, at [gradientIntensity].
   *
   * This is the number the studio is calibrated to, so changing the room
   * changes what the felt *reflects* and where its light comes from, but not
   * how bright the felt is. The fill is not in it: it is added on top, the
   * same for either room ([FillLight.upward]).
   */
  fun ambientUpward(): Double = gradientIntensity() * RoomLight.upwardBrightness()

  /**
   * The intensity the studio is given: whatever makes its upward irradiance
   * [ambientUpward]. Up in the tray is up in the panorama whatever the yaw
   * ([StudioLight.rotation]), so the turn does not enter into it.
   */
  fun studioIntensity(harmonics: FloatArray = StudioLight.IRRADIANCE): Double {
    val upward = RoomLight.luminance(StudioLight.evaluate(harmonics, PANORAMA_UP))
    require(upward > 0.0) { "a room that sends no light down cannot be calibrated" }
    return ambientUpward() / upward
  }

  /** The turn that puts the studio's window behind the key light ([StudioLight.yawToward]). */
  fun studioRotation(): FloatArray = StudioLight.rotation(StudioLight.yawToward(-KEY_DIRECTION))

  /**
   * The luminance of a white, matte floor facing up under the key and the
   * room, in candela per square metre: the key's lux times how squarely it
   * falls, over π, plus [ambientUpward] and [FillLight.upward] — which Filament's
   * harmonics already carry divided by π.
   */
  fun whiteFloorLuminance(): Double = falling(KEY_LUX, KEY_DIRECTION, UP) / PI + ambientUpward() + FillLight.upward()

  /**
   * The camera's exposure: [WHITE_LEVEL] over [whiteFloorLuminance], so that
   * floor lands at [WHITE_LEVEL].
   *
   * This is the factor Filament multiplies a luminance by, about 2.1 × 10⁻⁵ —
   * **not** a number to hand to `Camera.setExposure(float)`. That overload
   * means something else by its argument: it keeps f/1 and 1.2 s and sets the
   * ISO to `100 / exposure`, which Filament then clamps to 204,800, and the
   * camera came out at an exposure of 2,048 — twenty-six stops over, every lit
   * pixel white whether or not a tone mapper ran. So the camera is given a
   * real aperture, shutter and [sensitivity] instead, and [exposureOf] is
   * Filament's own formula for what those come to, which is what a device
   * test reads back off the camera.
   */
  fun exposure(): Double = WHITE_LEVEL / whiteFloorLuminance()

  /**
   * How bright a white, matte face of a die facing [normal] (in the tray)
   * comes out, as a fraction of the brightest the screen shows: the key as
   * squarely as it falls on the face, and the studio with its fill, read
   * through the same turn Filament reads it through. Shadows aside — this is
   * the face in the open.
   *
   * What the brightest and darkest faces come to is a property of the lamps,
   * the room and the exposure together, and the test that holds them is what
   * keeps a shadowed face readable (`TrayLightingTest`).
   */
  fun faceLevel(normal: Vector3): Double {
    val n = normal.normalised()
    val room = RoomLight.luminance(StudioLight.evaluate(FillLight.inStudio(), StudioLight.turned(studioRotation(), n)))
    return exposure() * (falling(KEY_LUX, KEY_DIRECTION, n) / PI + studioIntensity() * room)
  }

  /**
   * [rgb] scaled to a luminance of one, so a lamp given [KEY_LUX] delivers
   * [KEY_LUX] whatever Filament's colour-temperature conversion normalises
   * its colours to.
   */
  fun unitLuminance(rgb: FloatArray): FloatArray {
    val luminance = RoomLight.luminance(Colour(rgb[0].toDouble(), rgb[1].toDouble(), rgb[2].toDouble(), 1.0))
    require(luminance > 0.0) { "a lamp with no luminance has no colour to keep" }
    return FloatArray(CHANNELS) { (rgb[it] / luminance).toFloat() }
  }

  /** The aperture the camera is set to: Filament's default f/16, kept so only one number moves. */
  const val APERTURE: Float = 16.0f

  /** And the shutter, Filament's default 1/125 s. */
  const val SHUTTER_SECONDS: Float = 1.0f / 125.0f

  /** The lowest ISO Filament accepts; anything below is raised to it. */
  const val MIN_SENSITIVITY: Double = 10.0

  /** And the highest; anything above is lowered to it. */
  const val MAX_SENSITIVITY: Double = 204_800.0

  /**
   * The ISO that, at [APERTURE] and [SHUTTER_SECONDS], makes [exposure]: about
   * 80, a little under the default 100.
   */
  fun sensitivity(): Double = exposure() * EXPOSURE_SCALE * APERTURE * APERTURE / SHUTTER_SECONDS * ISO_BASE

  /**
   * What Filament makes of an aperture in f-stops, a shutter in seconds and an
   * ISO: `1 / (1.2 · N² / t · 100 / S)` (`Exposure::exposure`, the 1.2 being
   * the saturation-based sensor model's headroom). The ISO is clamped first,
   * as Filament clamps it.
   */
  fun exposureOf(
    aperture: Double,
    shutterSeconds: Double,
    sensitivity: Double,
  ): Double {
    val iso = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
    return 1.0 / (EXPOSURE_SCALE * aperture * aperture / shutterSeconds * ISO_BASE / iso)
  }

  /**
   * MSAA, at four samples.
   *
   * The edges that alias are geometry — a die's silhouette against the felt,
   * the rim against the floor — which is what multisampling is for and what
   * FXAA, Filament's default, only blurs after the fact. On a tiled GPU four
   * samples are resolved in tile memory, so the cost is the extra coverage
   * work and not a second full-screen pass; FXAA is switched off with it, so
   * the pass it cost is given back. Not temporal: TAA jitters every frame and
   * resolves over several, and a settled die that shimmers while it converges
   * is a die that looks like it twitched.
   */
  const val MSAA_SAMPLES: Int = 4

  /** Filament's sensor model: a sensor saturates 1.2 times above a meter's middle grey. */
  private const val EXPOSURE_SCALE = 1.2

  /** ISO is relative to ISO 100. */
  private const val ISO_BASE = 100.0

  /** Filament's harmonics are in the panorama's frame, whose up is `+y`. */
  private val PANORAMA_UP = Vector3(0.0, 1.0, 0.0)

  private const val CHANNELS = 3
}

/** The illuminance a lamp travelling along [direction] puts on a surface facing [normal]. */
private fun falling(
  lux: Double,
  direction: Vector3,
  normal: Vector3,
): Double = lux * max(0.0, -(direction.normalised() dot normal))
