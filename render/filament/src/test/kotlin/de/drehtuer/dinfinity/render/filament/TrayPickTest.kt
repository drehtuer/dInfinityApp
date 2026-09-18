package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.DieAtRest
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

/**
 * Whether a finger lands on the die the player is looking at.
 *
 * The claim is a round trip: the camera says where a die is drawn, and this
 * says which die is at that point. So most of these tests project a die
 * through the same frustum [TrayCamera] framed it in and then ask for it
 * back — which is a far sharper question than "roughly the middle", and it is
 * the mistake this arithmetic is actually capable of making. A sign flipped
 * anywhere in it picks the die on the other side of the tray, and the player
 * watches a die they did not touch.
 */
class TrayPickTest {
  private val geometry = TableGeometry.referenceDevice()
  private val radiusMm = ClearSpace.radiusOf(StandardDice.d6, 1.0)

  @Test
  fun `the middle of the screen looks the way the camera is pointed`() {
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)

    val middle = TrayPick(shot, PORTRAIT).lookingAlong(HALF, HALF)

    assertTrue("the middle of the picture is not where the camera looks", middle.approximates(shot.forward, TOLERANCE))
  }

  @Test
  fun `every die on the table is picked where it is drawn`() {
    val dice = spread()
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)
    val pick = TrayPick(shot, PORTRAIT)

    dice.forEachIndexed { index, die ->
      val (across, down) = shot.screenFraction(die.at.position, PORTRAIT)
      assertEquals("the finger landed on the wrong die", index, pick.dieUnder(across, down, dice))
    }
  }

  @Test
  fun `a die is still picked where it is drawn after a pinch and a drag`() {
    // The view is the other half of the frustum, and a pick worked out against
    // the whole tray while the player is zoomed into a corner is a finger on a
    // die two hand-widths away.
    val view = TrayView(zoom = 2.5, panAlongMm = 40.0, panAcrossMm = -15.0).within(geometry)
    val dice = spread()
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT, view)
    val pick = TrayPick.through(geometry, PORTRAIT, view)

    val onScreen =
      dice.indices.filter { index ->
        val (across, down) = shot.screenFraction(dice[index].at.position, PORTRAIT)
        across in 0.0..1.0 && down in 0.0..1.0
      }

    assertTrue("nothing was in shot, so this test asserts nothing", onScreen.isNotEmpty())
    onScreen.forEach { index ->
      val (across, down) = shot.screenFraction(dice[index].at.position, PORTRAIT)
      assertEquals("a pinched tray picked the wrong die", index, pick.dieUnder(across, down, dice))
    }
  }

  @Test
  fun `a finger on the bare floor picks nothing`() {
    val dice = listOf(at(Vector3(0.0, 0.0, radiusMm)))
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)
    val pick = TrayPick(shot, PORTRAIT)
    val (across, down) = shot.screenFraction(Vector3(geometry.longSideMm / 2 - radiusMm, 0.0, 0.0), PORTRAIT)

    assertNull("the floor is not a die", pick.dieUnder(across, down, dice))
  }

  @Test
  fun `an empty tray has nothing to pick`() {
    val pick = TrayPick.through(geometry, PORTRAIT)

    assertNull("a tray with no dice in it offered one", pick.dieUnder(HALF, HALF, emptyList()))
  }

  @Test
  fun `the die the player can see is the one that is picked`() {
    // Two dice in a line from the camera. Only the near one is visible, so it
    // is the one a finger on that spot means — the far one is behind it.
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)
    val near = Vector3(-30.0, 0.0, radiusMm)
    val far = near + shot.forward * (4 * radiusMm)
    val dice = listOf(at(far), at(near))
    val (across, down) = shot.screenFraction(near, PORTRAIT)

    assertEquals("the finger reached through the near die", 1, TrayPick(shot, PORTRAIT).dieUnder(across, down, dice))
  }

  @Test
  fun `a die shrunk by the capacity rule is a smaller target`() {
    // Forty dice are thrown small, and a pick that used their full size would
    // hand back a die whose picture the finger is nowhere near.
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)
    val pick = TrayPick(shot, PORTRAIT)
    val die = at(Vector3(0.0, 0.0, radiusMm))
    val (across, down) = shot.screenFraction(Vector3(0.0, radiusMm * EDGE_SHARE, radiusMm), PORTRAIT)

    assertNotNull("a full-sized die was missed at its own edge", pick.dieUnder(across, down, listOf(die)))
    assertNull("a shrunken die was picked well outside itself", pick.dieUnder(across, down, listOf(die), SMALL))
  }

  @Test
  fun `right on the screen is across the tray towards minus y`() {
    // The same direction the two-finger drag moves the view in. There is one
    // camera, so there is one answer, and a disagreement here is a finger that
    // picks the die on the wrong side of the table.
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)

    val right = TrayPick(shot, PORTRAIT).lookingAlong(1.0, HALF)

    assertTrue("the right of the picture is not the right of the tray", right dot Vector3(0.0, -1.0, 0.0) > 0.0)
  }

  @Test
  fun `up the screen is up the long side of the tray`() {
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)

    val up = TrayPick(shot, PORTRAIT).lookingAlong(HALF, 0.0)

    assertTrue("the top of the picture is not the far end of the tray", up.x > shot.forward.x)
  }

  @Test
  fun `a viewport with no width is refused rather than divided by`() {
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)

    assertThrows(IllegalArgumentException::class.java) { TrayPick(shot, 0.0) }
    assertThrows(IllegalArgumentException::class.java) { TrayPick(shot, -1.0) }
  }

  @Test
  fun `a camera inside a die still picks it rather than reaching behind itself`() {
    // Not a view any pinch can reach, and answered anyway: the alternative is
    // a negative distance winning the comparison and a die being picked from
    // behind the player's head.
    val shot = TrayCamera.framingTheTray(geometry, PORTRAIT)
    val swallowing = DieAtRest(StandardDice.d6, "builtin", RestingPlace(shot.position, Quaternion.Identity))

    assertEquals(0, TrayPick(shot, PORTRAIT).dieUnder(HALF, HALF, listOf(swallowing), HUGE))
  }

  @Test
  fun `the pick and the picture are framed for the same screen`() {
    // `through` exists so that the frustum a finger is read against cannot
    // drift from the one the dice were drawn in.
    val dice = spread()
    val shot = TrayCamera.framingTheTray(geometry, LANDSCAPE)
    val (across, down) = shot.screenFraction(dice[0].at.position, LANDSCAPE)

    assertEquals(
      "a pick built from the tray disagreed with a pick built from the shot",
      TrayPick(shot, LANDSCAPE).dieUnder(across, down, dice),
      TrayPick.through(geometry, LANDSCAPE).dieUnder(across, down, dice),
    )
  }

  @Test
  fun `a pick on a table looked at straight down is read against that shot`() {
    // The lean is the player's (**Table view**), and a finger read against a
    // shot the screen is not taking picks the die next to the one it is on.
    val dice = spread()
    val flat = TrayCamera.framingTheTray(geometry, PORTRAIT, tiltDegrees = TrayCamera.NO_TILT_DEGREES)
    val pick = TrayPick.through(geometry, PORTRAIT, tiltDegrees = TrayCamera.NO_TILT_DEGREES)

    dice.forEachIndexed { index, die ->
      val (across, down) = flat.screenFraction(die.at.position, PORTRAIT)
      assertEquals("the finger landed on the wrong die", index, pick.dieUnder(across, down, dice))
    }
    // And it really is a different picture: the same finger on the leaning
    // shot's frustum is somewhere else on the table.
    val leaning = TrayCamera.framingTheTray(geometry, PORTRAIT)
    val corner = dice.last().at.position
    val (across, down) = flat.screenFraction(corner, PORTRAIT)
    assertTrue(
      "the two positions of the setting frame the same die identically",
      leaning.screenFraction(corner, PORTRAIT) != Pair(across, down),
    )
  }

  /** Dice at rest across the tray, no two of them in the same place. */
  private fun spread(): List<DieAtRest> =
    listOf(
      Vector3(0.0, 0.0, radiusMm),
      Vector3(geometry.longSideMm / 2 - radiusMm * 2, 0.0, radiusMm),
      Vector3(-geometry.longSideMm / 2 + radiusMm * 2, 0.0, radiusMm),
      Vector3(0.0, geometry.shortSideMm / 2 - radiusMm * 2, radiusMm),
      Vector3(0.0, -geometry.shortSideMm / 2 + radiusMm * 2, radiusMm),
    ).map(::at)

  private fun at(position: Vector3): DieAtRest =
    DieAtRest(StandardDice.d6, "builtin", RestingPlace(position, Quaternion.Identity))

  /** Where [point] is drawn, as fractions across and down the viewport. */
  private fun CameraShot.screenFraction(
    point: Vector3,
    aspectRatio: Double,
  ): Pair<Double, Double> {
    val upward = tan(Math.toRadians(verticalFieldOfViewDegrees / 2))
    val across = upward * aspectRatio
    val right = cross(forward, up)
    val offset = point - position
    val along = offset dot forward
    return ((offset dot right) / (across * along) + 1) / 2 to (1 - (offset dot up) / (upward * along)) / 2
  }

  private companion object {
    const val PORTRAIT = 1080.0 / 2400.0
    const val LANDSCAPE = 2400.0 / 1080.0
    const val HALF = 0.5
    const val TOLERANCE = 1e-9

    /** How far out along a die's own radius the edge test aims. */
    const val EDGE_SHARE = 0.8

    /** The capacity rule's floor, which is how small a die ever gets. */
    const val SMALL = 0.4

    /** Big enough that the camera is inside the die. */
    const val HUGE = 1e4
  }
}
