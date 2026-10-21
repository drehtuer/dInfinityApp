package de.drehtuer.dinfinity.render.filament

import com.twelvemonkeys.imageio.plugins.hdr.HDRImageReadParam
import com.twelvemonkeys.imageio.plugins.hdr.tonemap.NullToneMapper
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The photographed room, as arithmetic.
 *
 * Whether a die looks lacquered needs a phone. Whether the irradiance written
 * into [StudioLight] is the irradiance of the file that ships, whether up in
 * the tray is up in the panorama, and whether the window ended up behind the
 * key light do not — and each of those is a mistake that looks, on a screen,
 * like a perfectly plausible picture of the wrong room.
 */
class StudioLightTest {
  @Test
  fun `the panorama ships, and it is a Radiance file`() {
    val bytes = StudioLight.bytes()
    assertNotNull("no panorama at ${StudioLight.RESOURCE}", bytes)
    assertTrue(String(bytes!!.copyOf(RADIANCE.length), Charsets.US_ASCII) == RADIANCE)
  }

  @Test
  fun `the irradiance written down is the irradiance of the file that ships`() {
    // Worked out again from the shipped bytes, by the same projection, so a
    // panorama swapped without its numbers fails here instead of lighting the
    // felt with a room that is not there.
    val (pixels, width, height) = decoded()
    val worked = StudioLight.irradianceOf(pixels, width, height)
    val scale = StudioLight.IRRADIANCE[0]
    StudioLight.IRRADIANCE.forEachIndexed { index, written ->
      assertEquals("coefficient ${index / 3}, channel ${index % 3}", written, worked[index], scale * FILE_TOLERANCE)
    }
  }

  @Test
  fun `a room of one brightness lights every surface with exactly that brightness`() {
    val harmonics = StudioLight.irradianceOf(uniform(RADIANCE_LEVEL), WIDTH, HEIGHT)
    listOf(PANORAMA_UP, PANORAMA_DOWN, Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0)).forEach { facing ->
      assertEquals(RADIANCE_LEVEL, StudioLight.evaluate(harmonics, facing).green, PROJECTION_TOLERANCE)
    }
  }

  @Test
  fun `a slope comes out at two thirds of itself, which is what a cosine does to one`() {
    // Radiance 1 + y: brighter overhead. A matte surface facing up integrates
    // it to 1 + 2/3, facing down to 1 − 2/3 (Ramamoorthi and Hanrahan).
    val harmonics = StudioLight.irradianceOf(sloped(), WIDTH, HEIGHT)
    assertEquals(1.0 + 2.0 / 3, StudioLight.evaluate(harmonics, PANORAMA_UP).red, PROJECTION_TOLERANCE)
    assertEquals(1.0 - 2.0 / 3, StudioLight.evaluate(harmonics, PANORAMA_DOWN).red, PROJECTION_TOLERANCE)
    assertEquals(1.0, StudioLight.evaluate(harmonics, Vector3(1.0, 0.0, 0.0)).red, PROJECTION_TOLERANCE)
  }

  @Test
  fun `the shader never sees light below nought`() {
    val dark = FloatArray(StudioLight.IRRADIANCE.size)
    dark[0] = -1f
    assertEquals(0.0, StudioLight.evaluate(dark, PANORAMA_UP).red, 0.0)
  }

  @Test
  fun `the top row looks straight up and the middle column along z`() {
    val top = StudioLight.direction(WIDTH / 2, 0, WIDTH, HEIGHT)
    assertTrue("the top row looks along $top", top.y > TOP_ROW)
    val middle = StudioLight.direction(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT)
    assertEquals(1.0, middle.z, DIRECTION_TOLERANCE)
    // A quarter of the way in looks along +x and three quarters along −x:
    // longitude runs towards −x, the panorama seen from inside.
    assertEquals(1.0, StudioLight.direction(WIDTH / 4, HEIGHT / 2, WIDTH, HEIGHT).x, DIRECTION_TOLERANCE)
    assertEquals(-1.0, StudioLight.direction(WIDTH * 3 / 4, HEIGHT / 2, WIDTH, HEIGHT).x, DIRECTION_TOLERANCE)
  }

  @Test
  fun `the studio is brighter overhead than underfoot`() {
    val up = RoomLight.luminance(StudioLight.evaluate(StudioLight.IRRADIANCE, PANORAMA_UP))
    val down = RoomLight.luminance(StudioLight.evaluate(StudioLight.IRRADIANCE, PANORAMA_DOWN))
    assertTrue("up $up, down $down", up > 2 * down)
  }

  @Test
  fun `up in the tray is up in the panorama, whichever way the room is turned`() {
    listOf(0.0, 1.0, PI, -2.5).forEach { yaw ->
      val turned = StudioLight.turned(StudioLight.rotation(yaw), TrayLighting.UP)
      assertEquals(0.0, turned.x, DIRECTION_TOLERANCE)
      assertEquals(1.0, turned.y, DIRECTION_TOLERANCE)
      assertEquals(0.0, turned.z, DIRECTION_TOLERANCE)
    }
  }

  @Test
  fun `the turn is a rotation, not a reflection`() {
    // Filament requires a rigid-body turn of an indirect light; a reflection
    // would mirror the room and every die in it.
    val r = StudioLight.rotation(YAW)
    val determinant =
      r[0] * (r[4] * r[8] - r[7] * r[5]) -
        r[3] * (r[1] * r[8] - r[7] * r[2]) +
        r[6] * (r[1] * r[5] - r[4] * r[2])
    assertEquals(1.0, determinant.toDouble(), DIRECTION_TOLERANCE)
    val x = StudioLight.turned(r, Vector3(1.0, 0.0, 0.0))
    val y = StudioLight.turned(r, Vector3(0.0, 1.0, 0.0))
    assertEquals(0.0, x dot y, DIRECTION_TOLERANCE)
    assertEquals(1.0, sqrt(x dot x), DIRECTION_TOLERANCE)
  }

  @Test
  fun `the window is turned to stand behind the key light`() {
    val from = -TrayLighting.KEY_DIRECTION
    val turned = StudioLight.turned(StudioLight.rotation(StudioLight.yawToward(from)), Vector3(from.x, from.y, 0.0))
    val window = StudioLight.brightest()
    assertEquals(
      "the key light comes from where the room is brightest",
      atan2(window.z, window.x),
      atan2(turned.z, turned.x),
      DIRECTION_TOLERANCE,
    )
  }

  @Test
  fun `the brightest side of the studio is its window, high and to one side`() {
    val window = StudioLight.brightest()
    assertEquals(1.0, sqrt(window dot window), DIRECTION_TOLERANCE)
    assertTrue("the window is not above the horizon: $window", window.y > 0.0)
  }

  @Test
  fun `a room of one brightness folds into a cube of that brightness, six faces of it`() {
    val faces = StudioCube.faces(Radiance.Image(WIDTH, HEIGHT, uniform(RADIANCE_LEVEL)), FOLD_SIZE)
    assertEquals(RoomLight.FACES * FOLD_SIZE * FOLD_SIZE * 3, faces.size)
    faces.forEach { assertEquals(RADIANCE_LEVEL.toFloat(), it, 1e-6f) }
  }

  @Test
  fun `a bright spot in the panorama lands in the cube where the irradiance thinks it is`() {
    // The reflections and the irradiance have to agree on where the window
    // is. The spot is put at a known pixel; the cube texel it lands in must
    // look the way `direction` says that pixel looks, which is the frame the
    // irradiance was projected in.
    listOf(SPOT_X to SPOT_Y, WIDTH / 2 to HEIGHT / 2, WIDTH - 3 to HEIGHT / 3).forEach { (x, y) ->
      val pixels = FloatArray(WIDTH * HEIGHT * 3)
      repeat(3) { pixels[(y * WIDTH + x) * 3 + it] = 1f }
      val faces = StudioCube.faces(Radiance.Image(WIDTH, HEIGHT, pixels), SPOT_SIZE)
      val brightest = (0 until faces.size / 3).maxBy { faces[it * 3] }
      val perFace = SPOT_SIZE * SPOT_SIZE
      val face = brightest / perFace
      val within = brightest % perFace
      val landed = RoomLight.direction(face, within % SPOT_SIZE, within / SPOT_SIZE, SPOT_SIZE)
      val meant = StudioLight.direction(x, y, WIDTH, HEIGHT)
      assertTrue("pixel ($x, $y) looks along $meant and landed at $landed", (landed dot meant) > SAME_WAY)
    }
  }

  @Test
  fun `the folded cube has the irradiance written down, the same way round`() {
    // The whole fold, end to end: the shipped file, decoded by the app's own
    // reader and folded into the cube the GPU samples, read back into a
    // panorama by an independent cube lookup and projected again. A fold that
    // mirrored the room would flip every coefficient that changes sign with x.
    val panorama = requireNotNull(Radiance.decode(requireNotNull(StudioLight.bytes())))
    val faces = StudioCube.faces(panorama, CHECK_SIZE)
    val unfolded = FloatArray(WIDTH * HEIGHT * 3)
    for (y in 0 until HEIGHT) {
      for (x in 0 until WIDTH) {
        val texel = texelAt(StudioLight.direction(x, y, WIDTH, HEIGHT), CHECK_SIZE)
        repeat(3) { unfolded[(y * WIDTH + x) * 3 + it] = faces[texel * 3 + it] }
      }
    }
    val worked = StudioLight.irradianceOf(unfolded, WIDTH, HEIGHT)
    val scale = StudioLight.IRRADIANCE[0]
    StudioLight.IRRADIANCE.forEachIndexed { index, written ->
      assertEquals("coefficient ${index / 3}, channel ${index % 3}", written, worked[index], scale * FOLD_TOLERANCE)
    }
  }

  @Test
  fun `sampling across the seam at the back blends the first and last columns`() {
    val pixels = FloatArray(WIDTH * HEIGHT * 3)
    val row = HEIGHT / 2
    pixels[(row * WIDTH) * 3] = 1f
    pixels[(row * WIDTH + WIDTH - 1) * 3] = 1f
    // Straight back is longitude ±π: half way between the last column's
    // middle and the first's.
    val back = StudioLight.direction(0, row, WIDTH, HEIGHT) + StudioLight.direction(WIDTH - 1, row, WIDTH, HEIGHT)
    val out = FloatArray(3)
    StudioCube.sample(Radiance.Image(WIDTH, HEIGHT, pixels), back.normalised(), out, 0)
    assertEquals(1f, out[0], 1e-3f)
  }

  @Test
  fun `a window too bright for the cube's format is held just under what it can store`() {
    val image = Radiance.Image(WIDTH, HEIGHT, FloatArray(WIDTH * HEIGHT * 3) { Float.MAX_VALUE })
    val out = FloatArray(3)
    StudioCube.sample(image, PANORAMA_UP, out, 0)
    assertEquals(StudioCube.BRIGHTEST, out[0], 0f)
  }

  @Test
  fun `the poles are looked up, not run off`() {
    val image = Radiance.Image(WIDTH, HEIGHT, uniform(RADIANCE_LEVEL))
    val out = FloatArray(3)
    listOf(PANORAMA_UP, PANORAMA_DOWN).forEach {
      StudioCube.sample(image, it, out, 0)
      assertEquals(RADIANCE_LEVEL.toFloat(), out[1], 1e-6f)
    }
  }

  @Test
  fun `the cube is a quarter of the panorama's width and goes down to one pixel`() {
    val panorama = requireNotNull(Radiance.decode(requireNotNull(StudioLight.bytes())))
    assertEquals(panorama.width / 4, StudioCube.FACE_SIZE)
    assertEquals(1 shl (StudioCube.LEVELS - 1), StudioCube.FACE_SIZE)
  }

  /**
   * Which texel of a [size]-pixel cube, numbered as [StudioCube.faces] lays
   * them out, the GPU reads looking along [d]: the OpenGL cube-map rule,
   * written out independently of [RoomLight.direction].
   */
  private fun texelAt(
    d: Vector3,
    size: Int,
  ): Int {
    val ax = abs(d.x)
    val ay = abs(d.y)
    val az = abs(d.z)
    // Face, then the s and t coordinates and the major axis, per the spec's table.
    val (face, s, t) =
      when {
        ax >= ay && ax >= az -> if (d.x > 0) Triple(0, -d.z / ax, -d.y / ax) else Triple(1, d.z / ax, -d.y / ax)
        ay >= az -> if (d.y > 0) Triple(2, d.x / ay, d.z / ay) else Triple(3, d.x / ay, -d.z / ay)
        else -> if (d.z > 0) Triple(4, d.x / az, -d.y / az) else Triple(5, -d.x / az, -d.y / az)
      }
    val x = ((s + 1) / 2 * size).toInt().coerceIn(0, size - 1)
    val y = ((t + 1) / 2 * size).toInt().coerceIn(0, size - 1)
    return face * size * size + y * size + x
  }

  /** The shipped panorama as linear floats, untouched by any tone mapping. */
  private fun decoded(): Triple<FloatArray, Int, Int> {
    val reader = ImageIO.getImageReadersByFormatName("hdr").next()
    ImageIO.createImageInputStream(ByteArrayInputStream(StudioLight.bytes())).use { input ->
      reader.input = input
      val parameters = (reader.defaultReadParam as HDRImageReadParam).apply { toneMapper = NullToneMapper() }
      val raster = reader.read(0, parameters).raster
      val pixels = FloatArray(raster.width * raster.height * 3)
      raster.getPixels(0, 0, raster.width, raster.height, pixels)
      reader.dispose()
      return Triple(pixels, raster.width, raster.height)
    }
  }

  private fun uniform(level: Double) = FloatArray(WIDTH * HEIGHT * 3) { level.toFloat() }

  private fun sloped(): FloatArray {
    val pixels = FloatArray(WIDTH * HEIGHT * 3)
    for (y in 0 until HEIGHT) {
      for (x in 0 until WIDTH) {
        val up = StudioLight.direction(x, y, WIDTH, HEIGHT).y
        repeat(3) { pixels[(y * WIDTH + x) * 3 + it] = (1.0 + up).toFloat() }
      }
    }
    return pixels
  }

  private companion object {
    const val RADIANCE = "#?RADIANCE"
    const val WIDTH = 256
    const val HEIGHT = 128
    const val RADIANCE_LEVEL = 0.75
    const val YAW = 0.7

    /** A 256 × 128 grid integrates a smooth room to well within this. */
    const val PROJECTION_TOLERANCE = 2e-3

    /** Decoders disagree about the half-step of a Radiance mantissa. */
    const val FILE_TOLERANCE = 0.01f
    const val DIRECTION_TOLERANCE = 1e-2
    const val TOP_ROW = 0.99

    /** A small cube for the folding tests, and a fine enough one to integrate. */
    const val FOLD_SIZE = 32
    const val CHECK_SIZE = 64
    const val SPOT_X = 40
    const val SPOT_Y = 30

    /** Fine enough that every panorama pixel has a texel inside it. */
    const val SPOT_SIZE = 128

    /** Within a couple of degrees of each other. */
    const val SAME_WAY = 0.995

    /** Resampled twice, through a 64-pixel cube: a few per cent of band zero. */
    const val FOLD_TOLERANCE = 0.03f

    val PANORAMA_UP = Vector3(0.0, 1.0, 0.0)
    val PANORAMA_DOWN = Vector3(0.0, -1.0, 0.0)
  }
}
