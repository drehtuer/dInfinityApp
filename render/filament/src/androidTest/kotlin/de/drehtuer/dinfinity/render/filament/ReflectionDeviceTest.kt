package de.drehtuer.dinfinity.render.filament

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.tan

/**
 * The dice in a glossy table, on a real GPU (`docs/physics-and-rendering.md`,
 * "The dice in a glossy table").
 *
 * Which looks reflect, how strongly and where the camera under the floor
 * stands are settled on a JVM (`ReflectionTest`). What only a device can say
 * is whether the picture of the dice is drawn at all, lands where the floor
 * looks for it, and changes the floor there — and whether a matte table, given
 * exactly the same scene, is left alone.
 *
 * Every question is asked of one patch of floor: where the reflection of a die
 * held above the table has to appear, on the side of it away from its shadow
 * (the key light throws that towards `−y`, the patch is on `+y`) and clear of
 * the die itself. The die is held up rather than set down so that the three —
 * the die, its shadow and its reflection — are apart on the screen and the
 * patch is honestly none but the last. A frame with the die and one without
 * it are compared there, pixel by pixel.
 */
@RunWith(AndroidJUnit4::class)
class ReflectionDeviceTest {
  private val geometry = TableGeometry.referenceDevice()

  @Before
  fun ready() {
    FilamentStage.ready()
  }

  @Test
  fun aPolishedTableShowsTheDieAndAMatteOneDoesNot() {
    // A mirror-polished metal floor, so that what is asked is whether the
    // picture is drawn and sampled in the right place, at a strength no
    // driver's rounding can hide: the whole room is in that floor, and the die
    // replaces a piece of it. Post-processing off, as the rest of the suite
    // reads frames, so the emulator answers too.
    val polished = MIRROR.copy(roughness = 0.0)
    val matte = MIRROR.copy(roughness = MATTE)
    assertTrue(Reflection.of(polished) != null && Reflection.of(matte) == null)

    val shown = changedShare(polished, postProcessing = false)
    val notShown = changedShare(matte, postProcessing = false)
    Log.i(TAG, "polished: ${percent(shown)} % of the patch changed with the die; matte: ${percent(notShown)} %")
    assertTrue("only ${percent(shown)} % of the floor in front of the die showed it", shown >= LEAST_CHANGED)
    assertTrue("${percent(notShown)} % of a matte floor changed with a die above it", notShown <= MOST_UNCHANGED)
  }

  @Test
  fun theDarkGlassShowsTheDieAndFeltOfItsColourDoesNot() {
    // The look that ships, seen as a player sees it: tone-mapped. A reflection
    // of a few per cent on a dark floor is a few levels in linear light and a
    // good many more after the tone curve, which is where it is looked at.
    val glass = requireNotNull(BuiltinDiceSet.set.table("dark-glass"))
    val felt = glass.copy(roughness = FELT, metallic = 0.0)
    assertEquals(2.0 / 3.0, Reflection.of(glass)!!.strength, 1e-9)

    val shown = changedShare(glass, postProcessing = true)
    val notShown = changedShare(felt, postProcessing = true)
    Log.i(TAG, "dark glass: ${percent(shown)} % of the patch changed with the die; felt: ${percent(notShown)} %")
    assertTrue("only ${percent(shown)} % of the glass in front of the die showed it", shown >= LEAST_CHANGED)
    assertTrue("${percent(notShown)} % of felt changed with a die above it", notShown <= MOST_UNCHANGED)
  }

  /**
   * The share of the patch that a die above [look] changes, between a frame
   * with it and the same frame without it.
   */
  private fun changedShare(
    look: TableLook,
    postProcessing: Boolean,
  ): Double {
    val shot = TrayCamera.framingTheTray(geometry, WIDTH.toDouble() / HEIGHT)
    val patch = patch(shot)
    assertTrue("the patch in front of the die is ${patch.size} pixels", patch.size >= LEAST_PATCH)
    assertTrue("the patch overlaps the die itself", patch.none { it in footprint(shot) })

    FilamentEngine().use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT, postProcessing = postProcessing).use { stage ->
        val renderer = FilamentDiceRenderer(stage)
        renderer.begin(spec(look), geometry, look)
        renderer.show(RenderFrame.still(listOf(BodyTransform(0, HELD, Quaternion.Identity))))
        val with = requireNotNull(stage.capture()) { "Filament skipped the frame with the die" }
        renderer.show(RenderFrame.still(emptyList()))
        val without = requireNotNull(stage.capture()) { "Filament skipped the frame without it" }
        assumeTrue(
          "this backend hands back a blank post-processed frame (FilamentStageTest.aDrawnFrameIsNotBlank)",
          !postProcessing || !blank(without.pixels),
        )
        val changed = patch.count { differs(with.pixels, without.pixels, it) }
        return changed.toDouble() / patch.size
      }
    }
  }

  /**
   * Where the reflection of the `+y` half of the die has to appear: the
   * pixels the die's mirror image under the floor projects to, kept to the
   * inside of the die so an edge's blur does not decide anything.
   */
  private fun patch(shot: CameraShot): Set<Int> =
    pixelsOf(
      shot,
      centre = Vector3(HELD.x, SHADOW_GAP + INNER_ACROSS / 2, -HELD.z),
      half = Vector3(INNER_ALONG / 2, INNER_ACROSS / 2, INNER_ALONG / 2),
    )

  /** Every pixel the die itself could cover, generously. */
  private fun footprint(shot: CameraShot): Set<Int> = pixelsOf(shot, centre = HELD, half = Vector3(OUTER, OUTER, OUTER))

  /** The pixels inside the screen box of a box [half] across each way around [centre]. */
  private fun pixelsOf(
    shot: CameraShot,
    centre: Vector3,
    half: Vector3,
  ): Set<Int> {
    val corners =
      listOf(-half.x, half.x).flatMap { dx ->
        listOf(-half.y, half.y).flatMap { dy ->
          listOf(-half.z, half.z).map { dz -> project(shot, centre + Vector3(dx, dy, dz)) }
        }
      }
    val left = corners.minOf { it.first }.toInt() + 1
    val right = corners.maxOf { it.first }.toInt() - 1
    val top = corners.minOf { it.second }.toInt() + 1
    val bottom = corners.maxOf { it.second }.toInt() - 1
    return (top..bottom).flatMap { row -> (left..right).map { column -> row * WIDTH + column } }.toSet()
  }

  /** Where [point] lands on the screen, in pixels from the top left — the way a frame is read back. */
  private fun project(
    shot: CameraShot,
    point: Vector3,
  ): Pair<Double, Double> {
    val forward = shot.forward
    val up = shot.up.normalised()
    val right =
      Vector3(
        forward.y * up.z - forward.z * up.y,
        forward.z * up.x - forward.x * up.z,
        forward.x * up.y - forward.y * up.x,
      )
    val offset = point - shot.position
    val depth = offset dot forward
    val reach = tan(Math.toRadians(shot.verticalFieldOfViewDegrees / 2)) * depth
    val across = (offset dot right) / (reach * WIDTH / HEIGHT)
    val upward = (offset dot up) / reach
    return (across + 1) / 2 * WIDTH to (1 - upward) / 2 * HEIGHT
  }

  /** Whether every pixel of [frame] is the first one. */
  private fun blank(frame: ByteArray): Boolean =
    frame.indices.all { frame[it] == frame[it % FilamentStage.PIXEL_BYTES] }

  private fun differs(
    a: ByteArray,
    b: ByteArray,
    pixel: Int,
  ): Boolean {
    val from = pixel * FilamentStage.PIXEL_BYTES
    return (from until from + RGB).any { abs((a[it].toInt() and BYTE) - (b[it].toInt() and BYTE)) >= LEVELS }
  }

  private fun spec(look: TableLook): ThrowSpec {
    val die = StandardDice.d6.copy(material = StandardDice.d6.material.copy(colorArgb = WHITE))
    return ThrowSpec(
      dice = listOf(DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)),
      geometry = geometry,
      table = look,
      seed = 1L,
    )
  }

  private fun percent(share: Double): Int = (share * PERCENT).toInt()

  private companion object {
    /** The Pixel 10a's shape, at a size a read-back is quick at. */
    const val WIDTH = 540
    const val HEIGHT = 1200

    /**
     * Up the far half of the tray, where the angled camera looks across the
     * floor rather than down onto it, and held above it so the die, its
     * shadow and its reflection stand apart.
     */
    val HELD = Vector3(60.0, 0.0, 30.0)

    /**
     * A d6 of 16 mm across its corners is a cube about 9 mm on a side: the
     * patch is kept inside it — 7 mm along the tray and up, 3 mm across, on
     * the `+y` half — and the die itself is cleared by 10 mm.
     */
    const val INNER_ALONG = 7.0
    const val INNER_ACROSS = 3.0
    const val OUTER = 10.0

    /**
     * How far onto the `+y` side the patch starts. The key light comes down
     * from `+x+y`, so the die's shadow falls on `−y` of it: held 30 mm up it
     * stops 3 mm short of the die's middle line, and this is a millimetre past
     * it on the other side.
     */
    const val SHADOW_GAP = 1.0

    /** A floor that reflects the whole room, so the die in it cannot hide. */
    val MIRROR =
      TableLook(
        id = "mirror",
        name = "Mirror",
        floorColorArgb = 0xFFE0E0E0.toInt(),
        wallColorArgb = 0xFF404040.toInt(),
        metallic = 1.0,
      )

    /** Well past the glossiest matte: nothing is reflected. */
    const val MATTE = 0.9
    const val FELT = 0.9

    const val WHITE = 0xFFF2F2F2.toInt()

    /** A patch of a few hundred pixels; fewer is a projection gone wrong. */
    const val LEAST_PATCH = 60

    /** Three levels: past the post pass's dithering, which moves a pixel by one. */
    const val LEVELS = 3

    /** A reflection that is there changes most of the patch. */
    const val LEAST_CHANGED = 0.3

    /** And a floor that reflects nothing changes next to none of it. */
    const val MOST_UNCHANGED = 0.02

    const val RGB = 3
    const val BYTE = 0xFF
    const val PERCENT = 100
    const val TAG = "dinfinity.reflection"
  }
}
