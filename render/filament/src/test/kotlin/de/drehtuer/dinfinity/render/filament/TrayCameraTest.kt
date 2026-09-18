package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableView
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
  fun `straight down stands the camera over the middle of the tray`() {
    // The setting's default position: no lean at all, every die square to the
    // screen, and no wall in shot (`docs/physics-and-rendering.md`).
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT, tiltDegrees = STRAIGHT_DOWN)
    val down = Vector3(0.0, 0.0, -1.0)

    val lean = Math.toDegrees(acos(shot.forward dot down))

    assertEquals("a camera that is not leaning looks straight down", 0.0, lean, TOLERANCE)
    assertEquals("and stands over the middle of the table", 0.0, shot.position.x, TOLERANCE)
    assertEquals(0.0, shot.position.y, TOLERANCE)
    assertTrue("above the rim it is looking into", shot.position.z > geometry.wallHeightMm)
  }

  @Test
  fun `leaning stands the camera off the tray instead of over it`() {
    // The other position, and the difference between the two: the angled shot
    // is behind the near end of the table rather than above its middle, which
    // is what puts the top and left wall in the picture.
    val flat = TrayCamera.framingTheTray(geometry, PORTRAIT, tiltDegrees = STRAIGHT_DOWN)
    val angled = TrayCamera.framingTheTray(geometry, PORTRAIT, tiltDegrees = TrayCamera.TILT_DEGREES)

    assertTrue("the leaning camera should stand off the near end", angled.position.x < 0.0)
    assertTrue("and further off than the one that does not lean", angled.position.x < flat.position.x)
    assertEquals(
      "the lean is the setting's, to the degree",
      TrayCamera.TILT_DEGREES,
      Math.toDegrees(acos(angled.forward dot Vector3(0.0, 0.0, -1.0))),
      TOLERANCE,
    )
  }

  @Test
  fun `both positions hold the whole tray, with the same air around it`() {
    // What must not change with the setting: the table is framed either way,
    // at every shape of phone, and neither shot is looser than the other. The
    // fullest corner of the tray sits just inside the edge of the picture in
    // both, because the only air in either is MARGIN.
    ASPECTS.forEach { aspect ->
      val fills =
        TILTS.map { tilt ->
          val shot = TrayCamera.framingTheTray(geometry, aspect, tiltDegrees = tilt)
          trayCorners().forEach { corner ->
            assertTrue("the tray is clipped at $aspect leaning $tilt", shot.holds(corner, aspect))
          }
          trayCorners().maxOf { corner -> shot.fills(corner, aspect) }
        }
      fills.forEach { fill ->
        assertTrue("the tray fills $fill of the frame, which is the camera standing too far back", fill > TIGHT)
      }
      assertEquals("one position frames the tray more loosely than the other", fills.first(), fills.last(), SAME_AIR)
    }
  }

  @Test
  fun `each position of the setting is a number of degrees`() {
    assertEquals(0.0, TrayCamera.tiltDegreesOf(TableView.StraightDown), TOLERANCE)
    assertEquals(TrayCamera.TILT_DEGREES, TrayCamera.tiltDegreesOf(TableView.Angled), TOLERANCE)
    // The angled shot is what this file drew before the lean was a setting, so
    // a caller that says nothing still gets it.
    assertEquals(
      TrayCamera.framingTheTray(geometry, PORTRAIT, tiltDegrees = TrayCamera.tiltDegreesOf(TableView.Angled)),
      TrayCamera.framingTheTray(geometry, PORTRAIT),
    )
  }

  @Test
  fun `up the screen is still up with no lean at all`() {
    // The arithmetic that would fall over if the camera's up were the world's:
    // a camera looking along its own up vector has no frame at all. This one
    // rotates its up with the lean, so straight down is an ordinary case.
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT, tiltDegrees = STRAIGHT_DOWN)

    assertEquals("a camera whose up is not square to its view is a shear", 0.0, shot.up dot shot.forward, TOLERANCE)
    assertEquals(1.0, shot.up.length, TOLERANCE)
    assertTrue("the tray's long side runs up the screen", shot.up.x > 0.0)
    // Screen-right is `cross(forward, up)`, and with the tray's long side up
    // the screen and `+y` across it that comes out **−y** — which is the whole
    // point of the operands being this way round: `+y` is the *left* wall, and
    // the left wall is exactly what the angled shot is documented to show.
    assertEquals("screen-right is across the tray and nowhere else", 0.0, cross(shot.forward, shot.up).x, TOLERANCE)
    assertTrue("+y across the tray is screen-left, not screen-right", cross(shot.forward, shot.up).y < 0.0)
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

  @Test
  fun `looking closer stands the camera nearer and keeps it pointed at the table`() {
    val whole = TrayCamera.framingTheTray(geometry, PORTRAIT)
    val closer = TrayCamera.framingTheTray(geometry, PORTRAIT, TrayView(zoom = 2.0))

    assertTrue(
      "pinching in did not bring the camera any closer",
      closer.distanceMm < whole.distanceMm,
    )
    // Still the middle of the table: zooming alone does not move what is
    // being looked at, only how much of it is in shot.
    assertEquals(0.0, closer.target.x, TOLERANCE)
    assertEquals(0.0, closer.target.y, TOLERANCE)
  }

  @Test
  fun `panning moves what the camera looks at, and nothing else`() {
    val view = TrayView(zoom = 2.0, panAlongMm = 20.0, panAcrossMm = -10.0).within(geometry)
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT, view)

    assertEquals(view.panAlongMm, shot.target.x, TOLERANCE)
    assertEquals(view.panAcrossMm, shot.target.y, TOLERANCE)
  }

  @Test
  fun `what the player asked to see is in shot, at every zoom and every shape of phone`() {
    // The claim the whole feature rests on: whatever corner of the table the
    // player has pinched and dragged to, it is actually on screen.
    ASPECTS.forEach { aspect ->
      ZOOMS.forEach { zoom ->
        val view =
          TrayView(
            zoom = zoom,
            panAlongMm = geometry.longSideMm,
            panAcrossMm = geometry.shortSideMm,
          ).within(geometry)
        val shot = TrayCamera.framingTheTray(geometry, aspect, view)
        heldCorners(view).forEach { corner ->
          assertTrue("what was asked for is clipped at $aspect, $zoom×", shot.holds(corner, aspect))
        }
      }
    }
  }

  @Test
  fun `the default view is the whole tray`() {
    val default = TrayCamera.framingTheTray(geometry, PORTRAIT)
    val asked = TrayCamera.framingTheTray(geometry, PORTRAIT, TrayView.Whole)

    assertEquals(default.distanceMm, asked.distanceMm, TOLERANCE)
    assertEquals(default.target.x, asked.target.x, TOLERANCE)
  }

  /** The corners of the piece of table [view] asks to see. */
  private fun heldCorners(view: TrayView): List<Vector3> =
    listOf(-1.0, 1.0).flatMap { x ->
      listOf(-1.0, 1.0).flatMap { y ->
        listOf(0.0, geometry.wallHeightMm).map { z ->
          Vector3(
            view.panAlongMm + x * geometry.longSideMm / 2 / view.zoom,
            view.panAcrossMm + y * geometry.shortSideMm / 2 / view.zoom,
            z,
          )
        }
      }
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

  /**
   * How much of the frame [point] reaches: 1.0 is exactly on the edge of the
   * picture, and more than that is outside it.
   */
  private fun CameraShot.fills(
    point: Vector3,
    aspectRatio: Double,
  ): Double {
    val upward = tan(Math.toRadians(verticalFieldOfViewDegrees / 2))
    val across = upward * aspectRatio
    val right = cross(forward, up)
    val offset = point - position
    val along = offset dot forward
    return maxOf(abs(offset dot right) / (across * along), abs(offset dot up) / (upward * along))
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

    /** The two positions of the Table view setting, in degrees. */
    const val STRAIGHT_DOWN = 0.0
    val TILTS = listOf(STRAIGHT_DOWN, TrayCamera.TILT_DEGREES)

    /**
     * How full of tray the picture has to be, and how alike the two positions
     * have to be about it. A shot with MARGIN and nothing else fills about
     * 0.94 of its frame, so a camera that had quietly stood further back would
     * fall through the first of these and a lean that reframed would fall
     * through the second.
     */
    const val TIGHT = 0.9
    const val SAME_AIR = 0.02

    /** A Pixel 10a held upright, and two shapes of screen well either side of it. */
    const val PORTRAIT = 0.45
    val ASPECTS = listOf(PORTRAIT, 0.75, 1.0, 2.0)

    /** The whole table, halfway in, and as close as the player may get. */
    val ZOOMS = listOf(1.0, 2.0, TrayView.CLOSEST)
  }
}
