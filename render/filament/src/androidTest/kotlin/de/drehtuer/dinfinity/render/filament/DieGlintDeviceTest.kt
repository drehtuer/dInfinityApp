package de.drehtuer.dinfinity.render.filament

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A die's face turning through the key light's mirror angle, read back off
 * the GPU (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
 *
 * The owner saw dice "almost blink" while they rolled. The key light is a
 * lamp with no size, the camera looks straight down, and a flat face has one
 * normal; under lacquer as polished as it was (a roughness of 0.12) about half
 * a face drew pure white over a few degrees of tilt around the mirror angle,
 * and a tumbling die turns through a few degrees in one frame. So a face went
 * from its own colour to white and back between two frames.
 *
 * This holds a d6 under a camera straight above it and turns its top face,
 * half a degree at a time, from flat to well past the angle at which it
 * mirrors the key light, and asks of every frame that no more than
 * [MOST_BURNT] of the face is blown out. A highlight may brighten the face;
 * it may not turn it white. The dark die is the other case because a
 * highlight shows most there, and because nothing of a dark body is white on
 * its own: whatever blows out on it is the highlight alone. (A pure white
 * body is not a case. Facing the key more squarely than the floor does, it is
 * brighter than the white the exposure is set to, glint or none.)
 */
@RunWith(AndroidJUnit4::class)
class DieGlintDeviceTest {
  private val geometry = TableGeometry.referenceDevice()

  /** A dark floor; it is never in frame, but the light it bounces is. */
  private val look = TableLook(id = "dark", name = "Dark", floorColorArgb = DARK, wallColorArgb = DARK)

  private lateinit var filament: FilamentEngine

  @Before
  fun ready() {
    FilamentStage.ready()
    filament = FilamentEngine()
  }

  @After
  fun done() {
    filament.close()
  }

  @Test
  fun aD6FaceTurningThroughTheKeyLightsMirrorAngleDoesNotBlowOut() {
    assertNoTiltBlowsOut(StandardDice.d6)
  }

  @Test
  fun aDarkD6FaceTurningThroughTheKeyLightsMirrorAngleDoesNotBlowOut() {
    val d6 = StandardDice.d6
    assertNoTiltBlowsOut(d6.copy(material = d6.material.copy(colorArgb = DARK)))
  }

  /*
   * A metal die mirrors the key with its own colour, about twenty times what
   * a lacquered plastic one sends back, so it blows out at roughnesses a
   * plastic die's lacquer never would. Measured on the Pixel 10a, bare: an
   * ivory face burnt over two thirds at 0.5 and nothing at 0.6, steel's grey
   * two thirds at 0.4 and nothing at 0.5 (decision 106). Both at the
   * designer's Metal, whose roughness is [METAL_ROUGHNESS].
   */
  @Test
  fun aMetalD6FaceTurningThroughTheKeyLightsMirrorAngleDoesNotBlowOut() {
    val d6 = StandardDice.d6
    assertNoTiltBlowsOut(d6.copy(material = d6.material.copy(metallic = 1.0, roughness = METAL_ROUGHNESS)))
  }

  @Test
  fun aSteelD6FaceTurningThroughTheKeyLightsMirrorAngleDoesNotBlowOut() {
    val d6 = StandardDice.d6
    assertNoTiltBlowsOut(
      d6.copy(material = d6.material.copy(metallic = 1.0, roughness = METAL_ROUGHNESS, colorArgb = STEEL)),
    )
  }

  @Test
  fun theSweepRunsThroughTheKeyLightsMirrorAngle() {
    // A sweep that stopped short of the mirror angle would pass whatever the
    // lacquer did, so the angle is worked out from the light that ships.
    val mirror = mirrorTiltDegrees()
    assertTrue(
      "the key light mirrors at %.1f°, outside the sweep".format(mirror),
      mirror < STEPS * STEP_DEGREES - MARGIN,
    )
  }

  private fun assertNoTiltBlowsOut(die: Die) {
    val tilts = (0..STEPS).map { it * STEP_DEGREES }
    val faces = tilts.map { faceAt(die, it) }
    assumeFalse("this backend does not read a post-processed frame back", faces.any { it.uniform })
    val readings = faces.map { Reading.of(it) }
    tilts.zip(readings).forEach { (tilt, reading) ->
      Log.i(TAG, "tilt %4.1f°: burnt %5.1f%%, mean %5.1f".format(tilt, reading.burnt * PERCENT, reading.mean))
    }
    val worst = readings.indices.maxBy { readings[it].burnt }
    assertTrue(
      "%.0f%% of the face blew out at %.1f°".format(readings[worst].burnt * PERCENT, tilts[worst]),
      readings[worst].burnt <= MOST_BURNT,
    )
  }

  /**
   * The tilt, from flat, at which a face mirrors the key light into a camera
   * straight above it: half the angle between straight up and the light.
   */
  private fun mirrorTiltDegrees(): Double {
    val towardLight = (-TrayLighting.KEY_DIRECTION).normalised()
    val half = (towardLight + Vector3.Up).normalised()
    return Math.toDegrees(atan2(sqrt(half.x * half.x + half.y * half.y), half.z))
  }

  /** The middle of [die]'s top face, tilted by [degrees] toward the key light, seen from straight above. */
  private fun faceAt(
    die: Die,
    degrees: Double,
  ): Snapshot =
    FilamentStage(SIZE, SIZE, shared = filament).use { stage ->
      val solid = SolidFaces.of(die.shape).first()
      val radius = die.material.boundingRadiusMm
      val flat = Quaternion.carrying(fromOut = solid.normal, fromUp = solid.up, toOut = Vector3.Up, toUp = X)
      // Toward the light, seen from above: the axis that tilts a face's
      // normal along the ground toward where the lamp stands.
      val ground = Vector3(-TrayLighting.KEY_DIRECTION.x, -TrayLighting.KEY_DIRECTION.y, 0.0).normalised()
      val axis = Vector3(-ground.y, ground.x, 0.0)
      val turn = Quaternion.about(axis, Math.toRadians(degrees)) * flat
      val position = Vector3(0.0, 0.0, HEIGHT_MM)
      val target = position + turn.rotate(solid.centre * radius)
      val reachMm = INSIDE * inscribedOf(solid.corners, solid.centre) * radius
      val distance = reachMm / tan(Math.toRadians(FIELD_OF_VIEW_DEGREES / 2))
      val renderer = FilamentDiceRenderer(stage)
      renderer.begin(
        ThrowSpec(
          dice = listOf(DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)),
          geometry = geometry,
          table = look,
          seed = 1L,
        ),
        geometry,
        look,
      )
      renderer.show(RenderFrame.still(listOf(BodyTransform(index = 0, position = position, orientation = turn))))
      stage.aim(CameraShot(target + Vector3.Up * distance, target, X, FIELD_OF_VIEW_DEGREES))
      checkNotNull(stage.capture()) { "no frame came back off the GPU" }
    }

  /** The distance from a face's [centre] to the nearest of its edges, on the unit solid. */
  private fun inscribedOf(
    corners: List<Vector3>,
    centre: Vector3,
  ): Double =
    corners.indices.minOf { at ->
      val from = corners[at]
      val edge = (corners[(at + 1) % corners.size] - from).normalised()
      val offset = centre - from
      (offset - edge * (offset dot edge)).length
    }

  /** How much of a frame is blown out, and how bright it is on average, out of 255. */
  private data class Reading(
    val burnt: Double,
    val mean: Double,
  ) {
    companion object {
      fun of(frame: Snapshot): Reading {
        var burnt = 0
        var sum = 0.0
        val count = frame.width * frame.height
        for (pixel in 0 until count) {
          val at = pixel * Snapshot.CHANNELS
          val channels = (0 until RGB).map { frame.pixels[at + it].toInt() and BYTE }
          if (channels.all { it >= BLOWN_OUT }) burnt++
          sum += channels.average()
        }
        return Reading(burnt / count.toDouble(), sum / count)
      }
    }
  }

  private companion object {
    const val TAG = "DieGlintDeviceTest"

    /** Small: the whole frame is the middle of one face. */
    const val SIZE = 64

    const val FIELD_OF_VIEW_DEGREES = 10.0

    /** High above the floor, so nothing but the die is in frame. */
    const val HEIGHT_MM = 100.0

    /** Inside the inscribed square, so no edge and no bevel is in frame. */
    const val INSIDE = 0.6

    /** From flat to well past the mirror angle (about 13°). */
    const val STEPS = 60
    const val STEP_DEGREES = 0.5

    /** A channel this bright is blown out. */
    const val BLOWN_OUT = 250

    /** The most of the face that may be blown out at any tilt: a speck, not a face. */
    const val MOST_BURNT = 0.01

    /** How far past the mirror angle the sweep has to run, in degrees. */
    const val MARGIN = 10.0

    const val DARK = 0xFF202020.toInt()

    /** The designer's Metal (`MaterialPreset.Metal`) and the steel sample set's roughness. */
    const val METAL_ROUGHNESS = 0.6

    /** The steel sample set's body (`sample-sets/steel`). */
    const val STEEL = 0xFF9EA2A6.toInt()
    const val BYTE = 0xFF
    const val RGB = 3
    const val PERCENT = 100.0

    /** The camera's up, and the face's texture-up. */
    val X = Vector3(1.0, 0.0, 0.0)
  }
}
