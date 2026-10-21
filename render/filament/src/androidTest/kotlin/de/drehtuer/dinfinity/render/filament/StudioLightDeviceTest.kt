package de.drehtuer.dinfinity.render.filament

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The photographed room on a real GPU (`docs/architecture.md`, decision 89).
 *
 * Everything about the studio that is arithmetic — the irradiance, which way
 * is up, how bright it is — is tested on a JVM (`StudioLightTest`,
 * `TrayLightingTest`). What is left is what only a driver can answer: does
 * Filament's own decoder read the shipped file, does its prefilter run here,
 * how long does that take on the roll thread's first draw, and does a table
 * come out the colour its package wrote once the tone mapper has had it.
 */
@RunWith(AndroidJUnit4::class)
class StudioLightDeviceTest {
  private val geometry = TableGeometry.referenceDevice()

  @Before
  fun ready() {
    FilamentStage.ready()
  }

  @Test
  fun theStudioIsDecodedAndPrefilteredOnThisDevice() {
    // The engine's default room is the shipped panorama. If Filament could not
    // read it the engine falls back to the gradient without a word, so this is
    // where that silence is broken.
    //
    // Twice, as the app does it: the first launch of a version decodes and
    // folds the panorama and keeps the cube in the code cache (`StudioCache`),
    // and every launch after reads it back. The budget is for the launch a
    // player has every day, and the second engine must have *read* — a cache
    // that quietly folded again would pass the clock on a fast day and not
    // the next.
    val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "studio-test")
    dir.deleteRecursively()
    try {
      val folding = timedRoom(StudioCache(dir))
      Log.i(TAG, "studio room decoded, folded and prefiltered in ${folding.first} ms")
      assertTrue("the shipped panorama was not used; the tray is lit by the gradient", folding.second.litByStudio)
      assertFalse("an empty cache handed back a cube", folding.second.studioFromDisk)
      assertTrue(
        "the first launch of a version took ${folding.first} ms to build the studio",
        folding.first < FIRST_LAUNCH_BUDGET_MS,
      )

      val reading = timedRoom(StudioCache(dir))
      Log.i(TAG, "studio room read back and prefiltered in ${reading.first} ms")
      assertTrue("the second launch folded the panorama again", reading.second.studioFromDisk)
      // It is paid on the roll thread's first draw of every cold start, which
      // is 0.8 s in all (`docs/physics-and-rendering.md`).
      assertTrue(
        "building the studio took ${reading.first} ms on the roll thread's first draw",
        reading.first < BUDGET_MS,
      )
    } finally {
      dir.deleteRecursively()
    }
  }

  /** How long a fresh engine kept in [cache] takes to build its room, and what it built. */
  private fun timedRoom(cache: StudioCache): Pair<Long, Built> =
    FilamentEngine(studio = cache).use { filament ->
      val started = System.nanoTime()
      filament.room
      filament.engine.flushAndWait()
      val took = (System.nanoTime() - started) / NANOS_PER_MS
      Pair(took, Built(filament.litByStudio, filament.studioFromDisk))
    }

  private class Built(
    val litByStudio: Boolean,
    val studioFromDisk: Boolean,
  )

  @Test
  fun theFeltBesideTheWallsIsTheFeltInTheMiddle() {
    // **No shadow, band or darkening along the inside of the tray.** The tray
    // casts no shadow (`FilamentDiceRenderer.addTray`), there is no ambient
    // occlusion (`FilamentStage.light`), and the shadow map is PCF because the
    // variance map PCSS draws from put the rim's shadow back on the felt as a
    // band 25 to 40 mm in from the walls. Any of the three coming back fails
    // here, by name and with a number, rather than in a gallery somebody has
    // to look at.
    //
    // The empty tray, straight down, with the real lamp and room, before and
    // after the post pass. The felt is measured in strips along all four
    // walls — 3 to 50 mm out from the foot of the wall, which covers the
    // contact band occlusion draws, a cast rim shadow (the rim is 60 mm up and
    // the key comes down at two in one, so its shadow lands about 30 mm out)
    // and anything between — clear of the rounded corners, and compared with
    // the felt in the middle of the floor.
    listOf(false, true).forEach { post ->
      FilamentEngine().use { filament ->
        filament.stage(surface = null, width = WIDTH, height = HEIGHT, postProcessing = post).use { stage ->
          FilamentDiceRenderer(stage, TableView.StraightDown).table(geometry, felt)
          val picture = requireNotNull(stage.capture()) { "Filament skipped the frame" }
          if (post && picture.uniform) {
            Log.i(
              TAG,
              "this backend does not read a post-processed frame back; the strips were measured before it only",
            )
          } else {
            assertTheFeltIsEven(picture, post)
          }
        }
      }
    }
  }

  private fun assertTheFeltIsEven(
    picture: Snapshot,
    post: Boolean,
  ) {
    val shot =
      TrayCamera.framingTheTray(
        geometry,
        WIDTH.toDouble() / HEIGHT,
        tiltDegrees = TrayCamera.tiltDegreesOf(TableView.StraightDown),
      )
    val pass = if (post) "after" else "before"
    val middle = luminanceAt(picture, shot, Vector3(0.0, 0.0, 0.0), post)
    assertTrue("the middle of the felt drew black $pass the post pass", middle > 0.0)
    val ratios =
      wallStrips().mapValues { (_, points) -> points.minOf { luminanceAt(picture, shot, it, post) / middle } }
    Log.i(
      TAG,
      "felt beside the walls / felt in the middle, $pass the post pass: " +
        ratios.entries.joinToString { (wall, ratio) -> "$wall %.3f".format(ratio) },
    )
    ratios.forEach { (wall, ratio) ->
      assertTrue(
        "the felt along the $wall wall is at $ratio of the middle $pass the post pass: something is shading it",
        ratio >= EVEN_FELT,
      )
    }
  }

  /** Points on the felt along each inner wall, [STRIP_MM] out from its foot, clear of the corners. */
  private fun wallStrips(): Map<String, List<Vector3>> {
    val halfLong = geometry.longSideMm / 2
    val halfShort = geometry.shortSideMm / 2
    val clear = geometry.cornerRadiusMm + CORNER_CLEARANCE_MM
    val alongShortWall = listOf(-halfShort + clear, 0.0, halfShort - clear)
    val alongLongWall = (-2..2).map { (halfLong - clear) * it / 2 }
    return mapOf(
      "far" to STRIP_MM.flatMap { d -> alongShortWall.map { Vector3(halfLong - d, it, 0.0) } },
      "near" to STRIP_MM.flatMap { d -> alongShortWall.map { Vector3(-halfLong + d, it, 0.0) } },
      "left" to STRIP_MM.flatMap { d -> alongLongWall.map { Vector3(it, halfShort - d, 0.0) } },
      "right" to STRIP_MM.flatMap { d -> alongLongWall.map { Vector3(it, -halfShort + d, 0.0) } },
    )
  }

  /**
   * The relative luminance of the felt at [point], as linear light: a frame
   * after the post pass is sRGB-encoded and is decoded first, a frame before
   * it is linear already.
   */
  private fun luminanceAt(
    picture: Snapshot,
    shot: CameraShot,
    point: Vector3,
    post: Boolean,
  ): Double {
    val (x, y) = requireNotNull(TrayCamera.pixelOf(shot, point, picture.width, picture.height))
    val rgb = averageAround(picture, x.toInt(), y.toInt(), STRIP_REACH)
    val linear = rgb.map { level -> if (post) decoded(level / BYTE_LEVELS) else level / BYTE_LEVELS }
    return RED_WEIGHT * linear[0] + GREEN_WEIGHT * linear[1] + BLUE_WEIGHT * linear[2]
  }

  /** The sRGB transfer function undone. */
  private fun decoded(encoded: Double): Double =
    if (encoded <= SRGB_KNEE) {
      encoded / SRGB_SLOPE
    } else {
      Math.pow((encoded + SRGB_OFFSET) / (1 + SRGB_OFFSET), SRGB_EXPONENT)
    }

  @Test
  fun noPanoramaIsTheGradientRoom() {
    FilamentEngine(environment = { null }).use { filament ->
      filament.room
      assertFalse(filament.litByStudio)
    }
  }

  @Test
  fun aPanoramaThatIsNotOneIsTheGradientRoom() {
    // A broken file is the gradient, not a crash on the first draw.
    FilamentEngine(environment = { "not a panorama".toByteArray() }).use { filament ->
      filament.room
      assertFalse(filament.litByStudio)
    }
  }

  @Test
  fun aDieInTheStudioIsDrawnDifferentlyFromOneInTheGradient() {
    // Post-processing off, for the reason `FilamentStageTest.aDrawnFrameIsNotBlank`
    // gives: this asks whether the room reaches the frame at all, on both tiers.
    val studio =
      FilamentEngine().use {
        val picture = drawn(it)
        assumeTrue("this driver lit the tray with the gradient (see the test above)", it.litByStudio)
        picture
      }
    val gradient = FilamentEngine(environment = { null }).use { drawn(it) }
    assertFalse("the studio drew exactly what the gradient did", studio.contentEquals(gradient))
  }

  @Test
  fun theCameraKeepsTheExposureItWasGiven() {
    // Read back off the camera, after whatever Filament clamped: an exposure
    // handed over in the wrong units comes back thousands of times too big
    // and every frame is white, on both tiers (`TrayLighting.exposure`).
    FilamentEngine().use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
        val ratio = stage.exposure() / TrayLighting.exposure()
        assertTrue("the camera is exposed at $ratio times what was asked for", abs(ratio - 1.0) < EXPOSURE_TOLERANCE)
      }
    }
  }

  @Test
  fun theFeltIsGreenAndNotBlownOutWithoutThePostPass() {
    // The tier a backend that cannot read a post-processed frame still has:
    // no tone mapper, so what comes back is the light itself. A felt facing up
    // under the calibrated room is its base colour, far under white and
    // green above all; a blown-out exposure is white here too.
    FilamentEngine().use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT, postProcessing = false).use { stage ->
        FilamentDiceRenderer(stage, TableView.StraightDown).table(geometry, felt)
        val picture = requireNotNull(stage.capture()) { "Filament skipped the frame" }
        val middle = averageAround(picture, WIDTH / 2, HEIGHT / 2)
        val drew = middle.joinToString()
        Log.i(TAG, "felt #1f5e3a drew as $drew without the post pass")
        assertTrue("the felt drew as $drew: blown out", middle.all { it < NOT_BLOWN_OUT })
        assertTrue("the felt drew as $drew: not green", middle[1] > middle[0] && middle[1] > middle[2])
        assertTrue("the felt drew as $drew: black", middle[1] > 0)
      }
    }
  }

  @Test
  fun theFeltIsTheColourItsSetWrote() {
    // The calibration end to end: the studio's intensity, the exposure and PBR
    // Neutral, on the real driver, with post-processing on because the tone
    // mapper is the post pass. A backend that cannot read a post-processed
    // frame back hands over one colour, and that is not a measurement.
    FilamentEngine().use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
        FilamentDiceRenderer(stage, TableView.StraightDown).table(geometry, felt)
        val picture = requireNotNull(stage.capture()) { "Filament skipped the frame" }
        assumeFalse("this backend does not read a post-processed frame back", picture.uniform)
        val middle = averageAround(picture, WIDTH / 2, HEIGHT / 2)
        val wrote = intArrayOf(FELT_RED, FELT_GREEN, FELT_BLUE)
        Log.i(TAG, "felt #1f5e3a drew as ${middle.joinToString()}")
        wrote.indices.forEach { channel ->
          assertTrue(
            "the felt drew as ${middle.joinToString()} for #1f5e3a",
            abs(middle[channel] - wrote[channel]) <= FELT_TOLERANCE,
          )
        }
      }
    }
  }

  private val felt = TableLook(id = "felt-green", name = "Green felt", floorColorArgb = FELT_ARGB)

  /** One die over the felt, as bytes, before the post pass. */
  private fun drawn(filament: FilamentEngine): ByteArray =
    filament.stage(surface = null, width = WIDTH, height = HEIGHT, postProcessing = false).use { stage ->
      val renderer = FilamentDiceRenderer(stage)
      renderer.begin(
        ThrowSpec(
          dice =
            listOf(
              DieInstance(
                index = 0,
                groupId = 0,
                setId = "builtin",
                requestedSetId = "builtin",
                die = StandardDice.d20,
              ),
            ),
          geometry = geometry,
          table = felt,
          seed = 1L,
        ),
        geometry,
        felt,
      )
      renderer.show(
        RenderFrame.still(
          listOf(
            BodyTransform(index = 0, position = Vector3(0.0, 0.0, DIE_HEIGHT_MM), orientation = Quaternion.Identity),
          ),
        ),
      )
      val pixels = stage.pixelBuffer()
      assertTrue(stage.draw(pixels))
      ByteArray(pixels.capacity()).also {
        pixels.rewind()
        pixels.get(it)
      }
    }

  /** The mean red, green and blue of a small square of [picture] around ([x], [y]). */
  private fun averageAround(
    picture: Snapshot,
    x: Int,
    y: Int,
    reach: Int = REACH,
  ): IntArray {
    val sums = IntArray(Snapshot.CHANNELS)
    var count = 0
    for (row in y - reach..y + reach) {
      for (column in x - reach..x + reach) {
        val at = (row * picture.width + column) * Snapshot.CHANNELS
        repeat(Snapshot.CHANNELS) { sums[it] += picture.pixels[at + it].toInt() and BYTE }
        count++
      }
    }
    return IntArray(RGB) { sums[it] / count }
  }

  private companion object {
    const val TAG = "dinfinity.studio"
    const val WIDTH = 360
    const val HEIGHT = 720
    const val NANOS_PER_MS = 1_000_000L
    const val BUDGET_MS = 500L

    /**
     * The first launch of a version decodes and folds the panorama as well:
     * 558 ms on the Pixel 10a before the cache existed. Not the budget a
     * player meets every day — that is [BUDGET_MS], read back — but a fold
     * that doubled would still be found here.
     */
    const val FIRST_LAUNCH_BUDGET_MS = 1_000L
    const val DIE_HEIGHT_MM = 10.0
    const val FELT_ARGB = 0xFF1F5E3A.toInt()
    const val FELT_RED = 0x1F
    const val FELT_GREEN = 0x5E
    const val FELT_BLUE = 0x3A

    /**
     * Ten levels of eight bits a channel. The gradient room under ACES drew
     * this felt as (28, 102, 69) on the Pixel 10a — eleven too blue, from its
     * cool sky — which is the drift this test exists to keep out.
     */
    const val FELT_TOLERANCE = 10
    const val REACH = 4

    /**
     * The felt by the walls must be at least this much of the felt in the
     * middle. The felt's light does not depend on where it is: the key falls
     * on all of it alike and the room's irradiance is a function of the
     * normal only, so what is left is the felt's sheen changing with the view
     * angle, about a per cent, and a level of rounding on an eight-bit green
     * near 28, under four. A rim shadow takes the key away, which is half the
     * felt's light (about 0.5), and occlusion bands run 0.6 to 0.85.
     */
    const val EVEN_FELT = 0.9

    /** How far out from the foot of each wall the felt is measured, in millimetres. */
    val STRIP_MM = listOf(3.0, 6.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 50.0)

    /** How far clear of a corner's rounding, so the measurement is the wall's and not the corner's. */
    const val CORNER_CLEARANCE_MM = 10.0

    /** A five-pixel square: 3 mm from the wall is about seven pixels in. */
    const val STRIP_REACH = 2
    const val BYTE_LEVELS = 255.0
    const val RED_WEIGHT = 0.2126
    const val GREEN_WEIGHT = 0.7152
    const val BLUE_WEIGHT = 0.0722
    const val SRGB_KNEE = 0.04045
    const val SRGB_SLOPE = 12.92
    const val SRGB_OFFSET = 0.055
    const val SRGB_EXPONENT = 2.4

    /** Float rounding of the ISO, and nothing else. */
    const val EXPOSURE_TOLERANCE = 1e-3

    /**
     * Well under white: linear `#1f5e3a` lit to one is (4, 29, 11) in eight
     * bits, or (31, 94, 58) if the swap chain encodes sRGB.
     */
    const val NOT_BLOWN_OUT = 160
    const val RGB = 3
    const val BYTE = 0xFF
  }
}
