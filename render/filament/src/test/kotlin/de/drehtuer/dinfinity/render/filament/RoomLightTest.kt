package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The room, as arithmetic.
 *
 * Whether a tray looks well lit needs a phone. Whether the light comes from
 * above rather than from below does not, and that is the failure this file
 * exists for: a sign error in a spherical harmonic is invisible in code review
 * and looks, on a screen, like a perfectly plausible table lit from the floor.
 */
class RoomLightTest {
  @Test
  fun `looking straight up finds the sky, and straight down the ground`() {
    assertEquals(RoomLight.SKY.red, irradianceAt(UP).x, TOLERANCE)
    assertEquals(RoomLight.SKY.green, irradianceAt(UP).y, TOLERANCE)
    assertEquals(RoomLight.SKY.blue, irradianceAt(UP).z, TOLERANCE)
    assertEquals(RoomLight.GROUND.red, irradianceAt(DOWN).x, TOLERANCE)
    assertEquals(RoomLight.GROUND.green, irradianceAt(DOWN).y, TOLERANCE)
    assertEquals(RoomLight.GROUND.blue, irradianceAt(DOWN).z, TOLERANCE)
  }

  @Test
  fun `a wall sees half of each, which is what makes it a wall and not a hole`() {
    val sideways = irradianceAt(Vector3(1.0, 0.0, 0.0))
    assertEquals((RoomLight.SKY.red + RoomLight.GROUND.red) / 2, sideways.x, TOLERANCE)
    assertEquals((RoomLight.SKY.blue + RoomLight.GROUND.blue) / 2, sideways.z, TOLERANCE)
  }

  @Test
  fun `the room is not brighter to the north, or to the east`() {
    val north = irradianceAt(Vector3(0.0, 1.0, 0.0))
    val south = irradianceAt(Vector3(0.0, -1.0, 0.0))
    val east = irradianceAt(Vector3(1.0, 0.0, 0.0))
    assertEquals(north.x, south.x, TOLERANCE)
    assertEquals(north.x, east.x, TOLERANCE)
  }

  @Test
  fun `the sky is brighter than the ground, which is the whole point`() {
    assertTrue(irradianceAt(UP).y > irradianceAt(DOWN).y)
  }

  @Test
  fun `it fills exactly the bands it says it does`() {
    assertEquals(RoomLight.BANDS * RoomLight.BANDS * SH_STRIDE, RoomLight.irradiance().size)
  }

  @Test
  fun `the middle of each face looks along that face's own axis`() {
    val middle = RoomLight.SIZE / 2
    val axes =
      listOf(
        Vector3(1.0, 0.0, 0.0),
        Vector3(-1.0, 0.0, 0.0),
        Vector3(0.0, 1.0, 0.0),
        Vector3(0.0, -1.0, 0.0),
        Vector3(0.0, 0.0, 1.0),
        Vector3(0.0, 0.0, -1.0),
      )
    axes.forEachIndexed { face, axis ->
      val looked = RoomLight.direction(face, middle, middle)
      assertTrue(
        "face $face looks along $looked, not $axis",
        abs(looked.x - axis.x) < FACE_TOLERANCE &&
          abs(looked.y - axis.y) < FACE_TOLERANCE &&
          abs(looked.z - axis.z) < FACE_TOLERANCE,
      )
    }
  }

  @Test
  fun `every direction it hands back is a direction`() {
    for (face in 0 until RoomLight.FACES) {
      for (x in 0 until RoomLight.SIZE step STRIDE) {
        for (y in 0 until RoomLight.SIZE step STRIDE) {
          val looked = RoomLight.direction(face, x, y)
          val length = looked.x * looked.x + looked.y * looked.y + looked.z * looked.z
          assertEquals("face $face at $x,$y", 1.0, length, TOLERANCE)
        }
      }
    }
  }

  @Test
  fun `the face overhead is sky and the face underfoot is ground`() {
    assertTrue(greenOf(RoomLight.face(UP_FACE), 0) > greenOf(RoomLight.face(DOWN_FACE), 0))
    // The one overhead is nearly all sky: its dimmest pixel is still brighter
    // than the brightest pixel of the one underfoot.
    assertTrue(dimmest(RoomLight.face(UP_FACE)) > brightest(RoomLight.face(DOWN_FACE)))
  }

  @Test
  fun `a side face is bright where it looks up and dim where it looks down`() {
    // Not "bright at the top of the image": a cube face's own up is the
    // graphics convention's, which is `+y`, and this app's up is `+z`. The
    // colour is taken from the direction rather than from the row, so the
    // gradient across a side face runs *across* it — and the thing worth
    // asserting is the one that does not depend on knowing that.
    val wall = RoomLight.face(SIDE_FACE)
    val pixels = 0 until RoomLight.SIZE * RoomLight.SIZE
    val highest = pixels.maxBy { RoomLight.direction(SIDE_FACE, it % RoomLight.SIZE, it / RoomLight.SIZE).z }
    val lowest = pixels.minBy { RoomLight.direction(SIDE_FACE, it % RoomLight.SIZE, it / RoomLight.SIZE).z }
    assertTrue(
      "the wall is ${greenOf(wall, highest)} looking up and ${greenOf(wall, lowest)} looking down",
      greenOf(wall, highest) > greenOf(wall, lowest),
    )
  }

  @Test
  fun `a coarser level is the same room, flatter`() {
    // Nothing uploads these — the cubemap is one level ([RoomLight.LEVELS]) —
    // but the blur is what says a rough surface reflects an average rather
    // than a picture, and it is worth keeping true.
    val coarse = RoomLight.face(SIDE_FACE, level = COARSE)
    val first = coarse.take(PIXEL_BYTES)
    coarse.toList().chunked(PIXEL_BYTES).forEach { assertEquals(first, it) }
  }

  @Test
  fun `every level is the size it promises, down to one pixel`() {
    for (level in 0..COARSE) {
      assertEquals(RoomLight.faceBytes(level), RoomLight.face(SIDE_FACE, level = level).size)
    }
    assertEquals(PIXEL_BYTES, RoomLight.face(SIDE_FACE, level = COARSE).size)
  }

  @Test
  fun `the sizes are the ones Filament works out for itself`() {
    // Filament computes what it needs from the region it is given — width
    // times height times depth times four bytes — and refuses a buffer that
    // is smaller. These are those numbers, written out, so a level that comes
    // back the wrong size is a failure here rather than a precondition inside
    // a driver.
    assertEquals(32 * 32 * 4, RoomLight.faceBytes(0))
    assertEquals(16 * 16 * 4, RoomLight.faceBytes(1))
    assertEquals(8 * 8 * 4, RoomLight.faceBytes(2))
    assertEquals(6 * 32 * 32 * 4, RoomLight.level(0).size)
    assertEquals(6 * 16 * 16 * 4, RoomLight.level(1).size)
    assertEquals(6 * 8 * 8 * 4, RoomLight.level(2).size)
  }

  @Test
  fun `a whole level is the six faces, end to end, in order`() {
    val whole = RoomLight.level(level = 1)
    assertEquals(RoomLight.FACES * RoomLight.faceBytes(1), whole.size)
    val second = RoomLight.face(1, level = 1)
    val at = RoomLight.faceBytes(1)
    assertEquals(
      second.toList(),
      whole.copyOfRange(at, at + second.size).toList(),
    )
  }

  @Test
  fun `the room is dimmer than a flat white one, which is what the intensity divides by`() {
    val average = RoomLight.averageBrightness()
    assertTrue("a room of average $average", average > 0.0 && average < 1.0)
  }

  @Test
  fun `a room of one colour is that colour in every direction`() {
    val one = Colour(red = 0.5, green = 0.5, blue = 0.5, alpha = 1.0)
    val flat = RoomLight.irradiance(sky = one, ground = one)
    assertEquals(0.5f, flat[0])
    assertEquals(0.0f, flat[SKYWARD_COEFFICIENT])
  }

  /**
   * What Filament's shader does with these numbers, written out.
   *
   * `sh[0] + sh[1] * n.y + sh[2] * n.z + sh[3] * n.x`, three floats to a
   * coefficient. Copying the evaluation here is the only way a JVM test can
   * say which way up the room is, and it is a short enough sum that the copy
   * is worth less than the answer.
   */
  private fun irradianceAt(direction: Vector3): Vector3 {
    val sh = RoomLight.irradiance()

    fun channel(offset: Int): Double =
      sh[offset].toDouble() +
        sh[SH_STRIDE + offset] * direction.y +
        sh[2 * SH_STRIDE + offset] * direction.z +
        sh[3 * SH_STRIDE + offset] * direction.x
    return Vector3(channel(RED), channel(GREEN), channel(BLUE))
  }

  private fun greenOf(
    face: ByteArray,
    pixel: Int,
  ): Int = face[pixel * PIXEL_BYTES + GREEN].toInt() and BYTE

  private fun dimmest(face: ByteArray): Int = (0 until face.size / PIXEL_BYTES).minOf { greenOf(face, it) }

  private fun brightest(face: ByteArray): Int = (0 until face.size / PIXEL_BYTES).maxOf { greenOf(face, it) }

  private companion object {
    val UP = Vector3(0.0, 0.0, 1.0)
    val DOWN = Vector3(0.0, 0.0, -1.0)

    /** Three floats to a spherical-harmonic coefficient. */
    const val SH_STRIDE = 3

    /** Four bytes to a pixel of the cubemap. */
    const val PIXEL_BYTES = 4

    const val RED = 0
    const val GREEN = 1
    const val BLUE = 2

    /** Where the skyward coefficient starts: band one, the `z` term. */
    const val SKYWARD_COEFFICIENT = 6
    const val UP_FACE = 4
    const val DOWN_FACE = 5
    const val SIDE_FACE = 0
    const val STRIDE = 7

    /** 32 halved five times is one pixel, which is a room with no shape at all. */
    const val COARSE = 5
    const val BYTE = 0xFF

    /** These arrive as 32-bit floats, so this is what "the same number" means. */
    const val TOLERANCE = 1e-6
    const val FACE_TOLERANCE = 0.05
  }
}
