package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The phone's axes against the tray's.
 *
 * The bug this exists to stop is invisible in code and obvious in the hand: a
 * sensor vector used in the frame it arrives in moves the dice a quarter turn
 * away from the way the hand went. So the tests are written the way a player
 * would describe it — *shake it towards the top of the screen and the dice go
 * up the tray* — rather than as a matrix nobody can check by eye.
 */
class PhoneAxesTest {
  @Test
  fun `held upright, a push towards the top of the screen runs up the tray`() {
    // Android's +y is up the screen; the tray's long side is +x.
    val upTheScreen = Vector3(0.0, PUSH, 0.0)

    val tray = PhoneAxes.toTray(upTheScreen, rotationDegrees = 0)

    assertEquals("a push up the screen did not run up the tray", PUSH, tray.x, EXACT)
    assertEquals(0.0, tray.y, EXACT)
    assertEquals(0.0, tray.z, EXACT)
  }

  @Test
  fun `held upright, a push to the right of the screen runs across the tray`() {
    // The tray's +y points across the screen to the left, so a push right is
    // negative — and, either way, it must not come out along the long side.
    val toTheRight = Vector3(PUSH, 0.0, 0.0)

    val tray = PhoneAxes.toTray(toTheRight, rotationDegrees = 0)

    assertEquals("a sideways push was applied along the length of the tray", 0.0, tray.x, EXACT)
    assertEquals(-PUSH, tray.y, EXACT)
  }

  @Test
  fun `out of the glass is up out of the table, however the phone is held`() {
    // The one axis the two frames already agree on.
    ROTATIONS.forEach { rotation ->
      val outOfTheGlass = Vector3(0.0, 0.0, PUSH)

      val tray = PhoneAxes.toTray(outOfTheGlass, rotation)

      assertEquals("at $rotation degrees", PUSH, tray.z, EXACT)
      assertEquals(0.0, tray.x, EXACT)
      assertEquals(0.0, tray.y, EXACT)
    }
  }

  @Test
  fun `turning the phone turns which way is up the tray`() {
    // The tray is the screen however the screen is held, so "up the tray" is a
    // different device axis in each orientation. A fixed swap would be right in
    // exactly one of these four.
    val upTheScreenAt =
      mapOf(
        0 to Vector3(0.0, PUSH, 0.0),
        90 to Vector3(-PUSH, 0.0, 0.0),
        180 to Vector3(0.0, -PUSH, 0.0),
        270 to Vector3(PUSH, 0.0, 0.0),
      )

    upTheScreenAt.forEach { (rotation, deviceFrame) ->
      val tray = PhoneAxes.toTray(deviceFrame, rotation)

      assertEquals("up the screen at $rotation degrees is not up the tray", PUSH, tray.x, EXACT)
      assertEquals("and it leaked across the tray at $rotation degrees", 0.0, tray.y, EXACT)
    }
  }

  @Test
  fun `the map is a rotation, so a rate of turn survives it as a force does`() {
    // The gyroscope's samples go through the same function, and an angular
    // velocity only transforms like a vector under a *proper* rotation. If this
    // map flipped handedness, gravity would tilt the wrong way round.
    ROTATIONS.forEach { rotation ->
      val x = PhoneAxes.toTray(Vector3(1.0, 0.0, 0.0), rotation)
      val y = PhoneAxes.toTray(Vector3(0.0, 1.0, 0.0), rotation)
      val z = PhoneAxes.toTray(Vector3(0.0, 0.0, 1.0), rotation)

      val handedness = cross(x, y)
      assertEquals("at $rotation degrees the frame was mirrored", z.x, handedness.x, TOLERANCE)
      assertEquals(z.y, handedness.y, TOLERANCE)
      assertEquals(z.z, handedness.z, TOLERANCE)
    }
  }

  @Test
  fun `length is never changed, whatever the phone is doing`() {
    val awkward = Vector3(PUSH, -PUSH / 2, PUSH / 3)

    ROTATIONS.forEach { rotation ->
      assertEquals("at $rotation degrees", awkward.length, PhoneAxes.toTray(awkward, rotation).length, TOLERANCE)
    }
  }

  @Test
  fun `a rotation nobody expected is taken to the nearest quarter turn`() {
    // A display that reports something odd is not a reason to stop rolling
    // dice, and it is certainly not a reason to throw.
    val upTheScreen = Vector3(0.0, PUSH, 0.0)

    assertEquals(PhoneAxes.toTray(upTheScreen, 0), PhoneAxes.toTray(upTheScreen, 360))
    assertEquals(PhoneAxes.toTray(upTheScreen, 90), PhoneAxes.toTray(upTheScreen, 89))
    assertEquals(PhoneAxes.toTray(upTheScreen, 0), PhoneAxes.toTray(upTheScreen, -360))
    assertEquals(PhoneAxes.toTray(upTheScreen, 270), PhoneAxes.toTray(upTheScreen, -90))
  }

  private companion object {
    const val PUSH = 1_000.0
    const val EXACT = 0.0
    const val TOLERANCE = 1e-9
    val ROTATIONS = listOf(0, 90, 180, 270)
  }
}
