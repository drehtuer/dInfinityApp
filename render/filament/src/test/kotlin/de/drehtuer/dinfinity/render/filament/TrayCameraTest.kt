package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.tan

/**
 * Whether the camera can actually see what it is pointed at.
 *
 * Framing is arithmetic about a frustum, and getting it wrong shows up as dice
 * half out of shot — which is both the most likely mistake here and one a test
 * can state far more precisely than a person squinting at a phone. So the
 * checks are not "the camera is roughly here": they project the thing being
 * framed and assert it lands inside the picture.
 */
class TrayCameraTest {
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `the whole tray is in shot at every shape of phone`() {
    // The camera never moves off this now — a roll is watched on the table it
    // happened on, start to finish — so "the tray fits" is the only framing
    // claim there is, and every die is inside it by construction.
    ASPECTS.forEach { aspect ->
      val shot = TrayCamera.framingTheTray(geometry, aspect)
      trayCorners().forEach { corner ->
        assertTrue("the tray is clipped at $aspect", shot.holds(corner, aspect))
      }
    }
  }

  @Test
  fun `a die against the far wall is still wholly in shot`() {
    // The corner a player is most likely to be squinting at, and the one a
    // camera framed a hair too tight would cut in half.
    val corner =
      Vector3(
        geometry.longSideMm / 2 - RADIUS_MM,
        geometry.shortSideMm / 2 - RADIUS_MM,
        RADIUS_MM,
      )

    ASPECTS.forEach { aspect ->
      val shot = TrayCamera.framingTheTray(geometry, aspect)
      cornersAround(corner).forEach { point ->
        assertTrue("a die in the far corner is clipped at $aspect", shot.holds(point, aspect))
      }
    }
  }

  @Test
  fun `the camera leans, so a roll is watched rather than diagrammed`() {
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)
    val down = Vector3(0.0, 0.0, -1.0)

    val tilt = Math.toDegrees(acos(shot.forward dot down))

    assertEquals("straight down is a diagram, not a dice tray", TrayCamera.TILT_DEGREES, tilt, TOLERANCE)
    assertTrue("the camera is below the rim it is looking over", shot.position.z > geometry.wallHeightMm)
    assertTrue("the camera should stand off the near end of the tray", shot.position.x < 0.0)
  }

  @Test
  fun `up the screen is up, and square to the way the camera looks`() {
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)

    assertEquals("a camera whose up is not square to its view is a shear", 0.0, shot.up dot shot.forward, TOLERANCE)
    assertEquals("up the screen is up the long side of the tray", 1.0, shot.up.length, TOLERANCE)
    assertTrue("the tray's long side runs up the screen", shot.up.x > 0.0)
  }

  @Test
  fun `a narrower viewport puts the camera further back`() {
    val wide = TrayCamera.framingTheTray(geometry, 1.0)
    val narrow = TrayCamera.framingTheTray(geometry, 0.3)

    assertTrue("a narrow screen has to see the same tray from further away", narrow.distanceMm > wide.distanceMm)
  }

  @Test
  fun `a viewport with no width is refused rather than divided by`() {
    assertThrows(IllegalArgumentException::class.java) { TrayCamera.framingTheTray(geometry, 0.0) }
    assertThrows(IllegalArgumentException::class.java) { TrayCamera.framingTheTray(geometry, -1.0) }
  }

  @Test
  fun `the same scene is framed the same way every time`() {
    assertEquals(
      TrayCamera.framingTheTray(geometry, PORTRAIT),
      TrayCamera.framingTheTray(geometry, PORTRAIT),
    )
  }

  /** True when [point] projects inside the picture this shot takes. */
  private fun CameraShot.holds(
    point: Vector3,
    aspectRatio: Double,
  ): Boolean {
    val upward = tan(Math.toRadians(verticalFieldOfViewDegrees / 2))
    val across = upward * aspectRatio
    val right = cross(up, forward)
    val offset = point - position
    val along = offset dot forward
    if (along <= 0.0) return false
    return abs(offset dot right) <= across * along + TOLERANCE &&
      abs(offset dot up) <= upward * along + TOLERANCE
  }

  /** The eight corners of the tray, floor and rim. */
  private fun trayCorners(): List<Vector3> =
    listOf(-1.0, 1.0).flatMap { x ->
      listOf(-1.0, 1.0).flatMap { y ->
        listOf(0.0, geometry.wallHeightMm).map { z ->
          Vector3(x * geometry.longSideMm / 2, y * geometry.shortSideMm / 2, z)
        }
      }
    }

  /** The corners of the box around one die. */
  private fun cornersAround(middle: Vector3): List<Vector3> =
    listOf(-RADIUS_MM, RADIUS_MM).flatMap { x ->
      listOf(-RADIUS_MM, RADIUS_MM).flatMap { y ->
        listOf(-RADIUS_MM, RADIUS_MM).map { z -> middle + Vector3(x, y, z) }
      }
    }

  private companion object {
    const val TOLERANCE = 1e-9
    const val RADIUS_MM = 14.0

    /** A Pixel 10a held upright, and two shapes of screen well either side of it. */
    const val PORTRAIT = 0.45
    val ASPECTS = listOf(PORTRAIT, 0.75, 1.0, 2.0)
  }
}
