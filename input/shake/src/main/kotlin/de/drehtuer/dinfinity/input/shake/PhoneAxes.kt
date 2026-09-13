package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * The phone's axes turned into the tray's
 * (`docs/physics-and-rendering.md`, "Coordinates" and "Shake input").
 *
 * Two right-handed frames that are **not** the same one, which is the whole
 * reason this file exists. Android reports every sensor vector in the device's
 * natural frame: `+x` across the screen to the right, `+y` up it, `+z` out of
 * the glass towards the player. The tray's long side is the screen's long side
 * and runs along `+x`, its short side along `+y`, and `+z` is up out of the
 * table. So the tray's `+x` is the screen's *up* and its `+y` is across — the
 * two frames are a quarter turn apart before the phone is turned at all.
 *
 * Used straight, a sideways shake loads the dice along the length of the tray
 * and a shake along the phone loads them across it, and the dice move in a
 * direction that has nothing to do with the hand. That is what it did.
 *
 * **The display rotation is part of the sum, not a detail.** The app is not
 * orientation-locked, and the tray is the screen however the screen is held, so
 * "up the screen" is a different device axis in each orientation. A fixed swap
 * would be right in exactly one of the four.
 */
object PhoneAxes {
  /**
   * [deviceFrame] as the tray sees it, for a display turned [rotationDegrees]
   * from the phone's natural orientation.
   *
   * Works for any vector quantity the sensors report — an acceleration, or the
   * gyroscope's rate of turn. The map is a rotation about the screen's normal
   * and nothing else: it is its own handedness, so a rate of turn comes through
   * it the same way a force does, and `+z` is untouched because both frames
   * already agree that out of the glass is up out of the table.
   *
   * @param rotationDegrees 0, 90, 180 or 270 — `Display.getRotation()` in
   *   degrees. Anything else is taken to the nearest quarter turn rather than
   *   refused: a rotation nobody expected is a reason to keep rolling dice.
   */
  fun toTray(
    deviceFrame: Vector3,
    rotationDegrees: Int,
  ): Vector3 {
    // How far up the screen and how far across it this vector points, in the
    // device's own components — which is what changes when the phone is turned.
    val (up, across) =
      when (quarterTurns(rotationDegrees)) {
        0 -> deviceFrame.y to deviceFrame.x
        1 -> -deviceFrame.x to deviceFrame.y
        2 -> -deviceFrame.y to -deviceFrame.x
        else -> deviceFrame.x to -deviceFrame.y
      }

    // The tray's `+y` points across the screen to the *left*: with `+x` up the
    // screen and `+z` out of it, right-handedness leaves it nowhere else.
    return Vector3(up, -across, deviceFrame.z)
  }

  private fun quarterTurns(rotationDegrees: Int): Int {
    val turns = Math.floorDiv(rotationDegrees + HALF_QUARTER, QUARTER)
    return Math.floorMod(turns, QUARTERS_IN_A_TURN)
  }

  private const val QUARTER = 90
  private const val HALF_QUARTER = 45
  private const val QUARTERS_IN_A_TURN = 4
}
