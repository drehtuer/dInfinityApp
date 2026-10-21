package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How bright the tray is, as arithmetic.
 *
 * Whether the felt *looks* right needs a phone and a person. Whether swapping
 * the room for a photograph kept the felt as bright as it was, and whether the
 * exposure puts a white floor where the tone mapper keeps colours as they are,
 * are sums — and both fail on a screen as a table that is simply a bit too
 * dark, which nobody can point at.
 */
class TrayLightingTest {
  @Test
  fun `the gradient's intensity divides out its own average, as it always did`() {
    assertEquals(
      TrayLighting.AMBIENT_LUX / RoomLight.averageBrightness(),
      TrayLighting.gradientIntensity(),
      TOLERANCE,
    )
  }

  @Test
  fun `the felt receives from the room what it received from the gradient`() {
    val studio =
      TrayLighting.studioIntensity() *
        RoomLight.luminance(StudioLight.evaluate(StudioLight.IRRADIANCE, PANORAMA_UP))
    assertEquals(TrayLighting.ambientUpward(), studio, TrayLighting.ambientUpward() * RELATIVE)
  }

  @Test
  fun `the upward ambient is the gradient's sky at the gradient's intensity`() {
    assertEquals(
      TrayLighting.gradientIntensity() * RoomLight.luminance(RoomLight.SKY),
      TrayLighting.ambientUpward(),
      TOLERANCE,
    )
  }

  @Test
  fun `a room that sends no light down cannot be calibrated`() {
    assertThrows(IllegalArgumentException::class.java) {
      TrayLighting.studioIntensity(FloatArray(StudioLight.IRRADIANCE.size))
    }
  }

  @Test
  fun `a white floor is lit by the key as squarely as it falls, by the room and by the fill`() {
    // The key falls at 1 / |(-0.4, -0.3, -1)| of square and is lux over π as
    // luminance; the room and the fill come as Filament's harmonics carry them.
    val key = TrayLighting.KEY_LUX / sqrt(KEY_LENGTH_SQUARED)
    assertEquals(
      key / PI + TrayLighting.ambientUpward() + FillLight.upward(),
      TrayLighting.whiteFloorLuminance(),
      TOLERANCE,
    )
  }

  @Test
  fun `the fill on the floor is the three-band cosine, a few per cent under the exact one`() {
    // The fill falls at 0.7 / |(0.6, 0.5, -0.7)| of square.
    val exact = TrayLighting.FILL_LUX * FILL_DOWN / sqrt(FILL_LENGTH_SQUARED) / PI
    val ratio = FillLight.upward() / exact
    assertTrue("the fill lands at $ratio of a lamp's", ratio in THREE_BANDS_OF_A_COSINE)
  }

  @Test
  fun `the exposure puts that floor at the white level, under the linear mapper's ceiling`() {
    assertEquals(TrayLighting.WHITE_LEVEL, TrayLighting.whiteFloorLuminance() * TrayLighting.exposure(), TOLERANCE)
    assertEquals(TrayLighting.WHITE_LEVEL, TrayLighting.faceLevel(TrayLighting.UP), TOLERANCE)
    assertTrue(TrayLighting.WHITE_LEVEL < 1.0)
  }

  @Test
  fun `a clamped cosine in three bands, facing the lamp, side on and turned away`() {
    val lamp = Vector3(0.3, -0.5, 0.8).normalised()
    val fill = FillLight.harmonics(lamp, PI)
    // lux / π = 1: 1/4 + 1/2 + 5/16 facing it, 1/4 − 5/32 side on,
    // 1/4 − 1/2 + 5/16 turned away — and grey throughout.
    val side = Vector3(0.8, 0.0, -0.3).normalised()
    assertEquals(0.0, side dot lamp, TOLERANCE)
    assertEquals(FACING, StudioLight.evaluate(fill, lamp).red, FLOAT_TOLERANCE)
    assertEquals(SIDE_ON, StudioLight.evaluate(fill, side).green, FLOAT_TOLERANCE)
    assertEquals(TURNED_AWAY, StudioLight.evaluate(fill, -lamp).blue, FLOAT_TOLERANCE)
    // Everywhere else, within a tenth of the exact cosine.
    directions().forEach { n ->
      val exact = maxOf(0.0, n dot lamp)
      val drawn = StudioLight.evaluate(fill, n)
      assertEquals("facing $n", exact, drawn.red, COSINE_TOLERANCE)
      assertEquals(drawn.red, drawn.blue, FLOAT_TOLERANCE)
    }
  }

  @Test
  fun `the studio carries the fill turned with it, so the fill lights the tray from where it is`() {
    // Through Filament's turn, the studio with its fill is the studio without
    // it plus a lamp of FILL_LUX over the tray, from the fill's side.
    val rotation = TrayLighting.studioRotation()
    val intensity = TrayLighting.studioIntensity()
    val lamp = FillLight.harmonics(-TrayLighting.FILL_DIRECTION, TrayLighting.FILL_LUX)
    directions().forEach { n ->
      val seen = StudioLight.turned(rotation, n)
      val with = RoomLight.luminance(StudioLight.evaluate(FillLight.inStudio(), seen)) * intensity
      val room = StudioLight.evaluate(StudioLight.IRRADIANCE, seen)
      val without = RoomLight.luminance(room) * intensity
      val fill = RoomLight.luminance(StudioLight.evaluate(lamp, n))
      // Where neither is held at nought by the shader's clamp, they add.
      val open = room.red > 0.0 && room.green > 0.0 && room.blue > 0.0 && fill > 0.0
      if (open) assertEquals("facing $n", without + fill, with, with * RELATIVE_FLOAT)
    }
  }

  @Test
  fun `the gradient carries the same fill, in the tray's own frame`() {
    val harmonics = FillLight.inGradient()
    assertEquals(27, harmonics.size)
    val upward =
      RoomLight.luminance(StudioLight.evaluate(harmonics, TrayLighting.UP)) * TrayLighting.gradientIntensity()
    assertEquals(TrayLighting.ambientUpward() + FillLight.upward(), upward, upward * RELATIVE_FLOAT)
  }

  @Test
  fun `either room puts the same light on the felt`() {
    val studio =
      RoomLight.luminance(
        StudioLight.evaluate(
          FillLight.inStudio(),
          StudioLight.turned(TrayLighting.studioRotation(), TrayLighting.UP),
        ),
      ) * TrayLighting.studioIntensity()
    assertEquals(TrayLighting.ambientUpward() + FillLight.upward(), studio, studio * RELATIVE_FLOAT)
  }

  @Test
  fun `no face of the built-in die is brighter than the screen can show`() {
    // The brightest face is one tilted towards the key and the studio's
    // window together. A pure white one there goes a little past white; the
    // bone-coloured resin the built-in set is made of never gets there.
    val brightest = directions().maxOf(TrayLighting::faceLevel)
    assertTrue("the brightest face is at $brightest", brightest <= BRIGHTEST_FACE)
    val bone = Colour.of(BONE_ARGB)
    assertTrue("the built-in die's brightest face is at ${brightest * bone.red}", brightest * bone.red < 1.0)
  }

  @Test
  fun `every face a camera above the tray can see is lit well enough to read a number on`() {
    // Turned away from the key, in the open: the fill and the room are all
    // that light it, and a white face there must stay well off black.
    val darkest = directions().filter { it.z >= 0.0 }.minOf(TrayLighting::faceLevel)
    assertTrue("the darkest visible face is at $darkest", darkest >= DARKEST_FACE)
  }

  @Test
  fun `a lamp's colour is scaled to a luminance of one and keeps its hue`() {
    val scaled = TrayLighting.unitLuminance(floatArrayOf(2.0f, 1.0f, 0.5f))
    assertEquals(
      1.0,
      RoomLight.luminance(Colour(scaled[0].toDouble(), scaled[1].toDouble(), scaled[2].toDouble(), 1.0)),
      1e-6,
    )
    assertEquals(2.0f, scaled[0] / scaled[1], 1e-6f)
    assertEquals(0.5f, scaled[2] / scaled[1], 1e-6f)
    assertThrows(IllegalArgumentException::class.java) { TrayLighting.unitLuminance(FloatArray(3)) }
  }

  @Test
  fun `the exposure is within a fifth of the one the tray had, so nothing jumps`() {
    // Filament's default: f/16, 1/125 s, ISO 100.
    val before = 1.0 / (DEFAULT_SCALE * DEFAULT_APERTURE * DEFAULT_APERTURE * DEFAULT_SHUTTER_RECIPROCAL)
    val ratio = TrayLighting.exposure() / before
    assertTrue("the exposure moved by $ratio", ratio in WITHIN_A_FIFTH)
  }

  @Test
  fun `the aperture, shutter and ISO the camera is given come to that exposure`() {
    val given =
      TrayLighting.exposureOf(
        TrayLighting.APERTURE.toDouble(),
        TrayLighting.SHUTTER_SECONDS.toDouble(),
        TrayLighting.sensitivity().toFloat().toDouble(),
      )
    assertEquals(1.0, given / TrayLighting.exposure(), FLOAT_RELATIVE)
  }

  @Test
  fun `the ISO is one Filament keeps, not one it clamps`() {
    val iso = TrayLighting.sensitivity()
    assertTrue("ISO $iso", iso > TrayLighting.MIN_SENSITIVITY && iso < TrayLighting.MAX_SENSITIVITY)
    // About 80: f/16 and 1/125 s, a third of a stop under Filament's ISO 100.
    assertEquals(EXPECTED_ISO, iso, 1.0)
  }

  @Test
  fun `Filament's formula, worked by hand, at its default`() {
    // f/16, 1/125 s, ISO 100: 1 / (1.2 · 256 · 125) = 2.6e-5.
    assertEquals(
      1.0 / (DEFAULT_SCALE * DEFAULT_APERTURE * DEFAULT_APERTURE * DEFAULT_SHUTTER_RECIPROCAL),
      TrayLighting.exposureOf(DEFAULT_APERTURE, 1.0 / DEFAULT_SHUTTER_RECIPROCAL, DEFAULT_ISO),
      TOLERANCE,
    )
  }

  @Test
  fun `an ISO outside Filament's range is clamped, as Filament clamps it`() {
    val shutter = 1.0 / DEFAULT_SHUTTER_RECIPROCAL
    assertEquals(
      TrayLighting.exposureOf(DEFAULT_APERTURE, shutter, TrayLighting.MAX_SENSITIVITY),
      TrayLighting.exposureOf(DEFAULT_APERTURE, shutter, TrayLighting.MAX_SENSITIVITY * 10),
      0.0,
    )
    assertEquals(
      TrayLighting.exposureOf(DEFAULT_APERTURE, shutter, TrayLighting.MIN_SENSITIVITY),
      TrayLighting.exposureOf(DEFAULT_APERTURE, shutter, 1.0),
      0.0,
    )
  }

  @Test
  fun `handing the exposure to Filament's one-number setter would blow the frame out`() {
    // What `Camera.setExposure(float)` does with its argument: f/1, 1.2 s and
    // ISO 100 / e, clamped. This is the white tray the device saw, and why
    // the camera is given three numbers instead.
    val oneNumber = TrayLighting.exposureOf(1.0, ONE_NUMBER_SHUTTER, DEFAULT_ISO / TrayLighting.exposure())
    assertTrue("the one-number setter gives $oneNumber", oneNumber / TrayLighting.exposure() > BLOWN_OUT)
  }

  @Test
  fun `four samples`() {
    assertEquals(4, TrayLighting.MSAA_SAMPLES)
  }

  /** Directions all round the sphere, every fifteen degrees. */
  private fun directions(): List<Vector3> =
    (0..ELEVATION_STEPS).flatMap { up ->
      val elevation = PI * up / ELEVATION_STEPS - PI / 2
      (0 until AZIMUTH_STEPS).map { round ->
        val azimuth = 2 * PI * round / AZIMUTH_STEPS
        Vector3(cos(elevation) * cos(azimuth), cos(elevation) * sin(azimuth), sin(elevation))
      }
    }

  private companion object {
    const val TOLERANCE = 1e-6
    const val RELATIVE = 1e-9
    const val KEY_LENGTH_SQUARED = 0.16 + 0.09 + 1.0
    const val FILL_LENGTH_SQUARED = 0.36 + 0.25 + 0.49
    const val FILL_DOWN = 0.7
    const val DEFAULT_SCALE = 1.2
    const val DEFAULT_APERTURE = 16.0
    const val DEFAULT_SHUTTER_RECIPROCAL = 125.0
    const val DEFAULT_ISO = 100.0
    const val EXPECTED_ISO = 80.0
    const val ONE_NUMBER_SHUTTER = 1.2

    /** A float ISO is good to about seven digits. */
    const val FLOAT_RELATIVE = 1e-6

    /** Twenty stops: anything near this is a white frame, whatever the tone mapper. */
    const val BLOWN_OUT = 1e6
    const val FLOAT_TOLERANCE = 1e-6
    const val RELATIVE_FLOAT = 1e-5
    const val FACING = 1.0625
    const val SIDE_ON = 0.09375
    const val TURNED_AWAY = 0.0625
    const val COSINE_TOLERANCE = 0.11
    val THREE_BANDS_OF_A_COSINE = 0.9..1.0
    const val ELEVATION_STEPS = 12
    const val AZIMUTH_STEPS = 24

    /** A pure white face turned to the key and the window goes past white by no more than this. */
    const val BRIGHTEST_FACE = 1.15

    /** The built-in set's resin (`dicesets/builtin`). */
    const val BONE_ARGB = 0xFFE8DCC0.toInt()

    /** A white face at a tenth is about 89 of 255 on screen; black ink on it reads at ten to one. */
    const val DARKEST_FACE = 0.1
    val WITHIN_A_FIFTH = 0.8..1.2
    val PANORAMA_UP = Vector3(0.0, 1.0, 0.0)
  }
}
