package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.DieAtRest
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Which die a finger is on (`docs/physics-and-rendering.md`, "Picking a die up
 * and throwing it again").
 *
 * The inverse of [TrayCamera]. That one turns a tray into a picture; this one
 * turns a point on the picture back into a place in the tray and asks which
 * die is standing there. It is the same frustum read the other way round — the
 * tilt, the field of view and the pinch the player has already applied — so it
 * lives beside the camera rather than in the gesture that calls it: a touch
 * point and a die's position are arithmetic, and arithmetic that runs inside a
 * pointer lambda is arithmetic no test ever sees
 * (`docs/architecture.md`, decision 62).
 *
 * Nothing here touches a die, a body or a GPU. It reads where the dice
 * *stopped*, which the simulation reported when it read their faces, and says
 * which of them a finger is over. Whether that die may then be thrown again is
 * a different question and a different answer ([`feature/roll`'s `PickUp`]).
 *
 * @param shot where the camera is standing, which is [TrayCamera]'s to say.
 * @param aspectRatio the viewport's width over its height, the same number the
 *   shot was framed for. A pick worked out against a different frustum from
 *   the one that drew the picture is a finger landing on the wrong die.
 */
class TrayPick(
  private val shot: CameraShot,
  private val aspectRatio: Double,
) {
  init {
    require(aspectRatio > 0.0) { "a viewport $aspectRatio wide for its height is not a viewport" }
  }

  /**
   * Which way the camera is looking through the point ([acrossFraction],
   * [downFraction]) of the viewport, one unit long.
   *
   * Fractions of the viewport rather than pixels, for the reason the pan takes
   * fractions: the tray is in millimetres, the screen is in neither, and a
   * gesture is the only place both are known. [acrossFraction] runs 0 at the
   * left edge to 1 at the right, [downFraction] 0 at the top to 1 at the
   * bottom — which is the way Compose counts a pointer and the opposite of the
   * way a frustum counts its height, so the second is turned over here rather
   * than at each call site.
   *
   * Right on the screen is the camera's `forward × up`. The tray's long side
   * is the screen's height and runs along `+x`, so screen-right is `-y` — the
   * same direction the two-finger drag moves the view in, and it is one
   * direction because there is one camera.
   */
  fun lookingAlong(
    acrossFraction: Double,
    downFraction: Double,
  ): Vector3 {
    val upward = tan(halfAngle(shot.verticalFieldOfViewDegrees))
    val across = upward * aspectRatio
    val right = cross(shot.forward, shot.up)
    return (
      shot.forward +
        right * ((2 * acrossFraction - 1) * across) +
        shot.up * ((1 - 2 * downFraction) * upward)
    ).normalised()
  }

  /**
   * Which of [dice] the finger at ([acrossFraction], [downFraction]) is on, as
   * a position in that list, or null for a touch on the bare floor.
   *
   * A die is taken as the ball around it — the bounding radius the capacity
   * rule threw it at, which is the same radius [ClearSpace] keeps a die clear
   * of and the same one the spawn grid deals the floor out by. A hull test
   * would be exact and would need every die's mesh, its orientation and a
   * convex sweep, to answer a question about a fingertip: the ball is inside a
   * die's own width everywhere and it is the size of the thing the player is
   * aiming at.
   *
   * Where the balls overlap — dice lying against each other, which is most of
   * a full tray — the **nearest to the camera** wins, because that is the die
   * the player can see. Two dice one behind the other are the picture's
   * ambiguity rather than this function's, and the answer to it is the pinch:
   * looking closer separates them on screen (`TrayView`).
   *
   * @param dieScale how far the capacity rule shrank the dice, which is the
   *   throw's and not a die's. A pick worked out at full size on a tray of
   *   forty shrunken dice would be forty balls twice the width they are drawn.
   */
  fun dieUnder(
    acrossFraction: Double,
    downFraction: Double,
    dice: List<DieAtRest>,
    dieScale: Double = 1.0,
  ): Int? {
    val direction = lookingAlong(acrossFraction, downFraction)
    var found: Int? = null
    var nearest = Double.MAX_VALUE
    dice.forEachIndexed { position, die ->
      val radiusMm = ClearSpace.radiusOf(die.die, dieScale)
      val reach = reaches(die.at.position, radiusMm, direction)
      if (reach != null && reach < nearest) {
        nearest = reach
        found = position
      }
    }
    return found
  }

  /**
   * How far the camera has to look along [direction] to reach the ball of
   * [radiusMm] around [centre], or null when the look misses it.
   *
   * The ordinary ray-against-sphere: the nearer of the two crossings when the
   * camera is outside the ball, and the far one when the camera is somehow
   * inside it, which is a zoom no [TrayView] allows and is answered anyway
   * rather than left to return a distance behind the player's head.
   */
  private fun reaches(
    centre: Vector3,
    radiusMm: Double,
    direction: Vector3,
  ): Double? {
    val fromCentre = shot.position - centre
    val along = fromCentre dot direction
    val outside = (fromCentre dot fromCentre) - radiusMm * radiusMm
    val discriminant = along * along - outside
    if (discriminant < 0.0) return null
    val root = sqrt(discriminant)
    val near = -along - root
    if (near >= 0.0) return near
    val far = -along + root
    return if (far >= 0.0) far else null
  }

  companion object {
    /**
     * The pick that matches the picture a tray of this shape is drawing at
     * [view].
     *
     * Built from [TrayCamera] rather than from a camera of its own, so the
     * frustum a finger is read against is by construction the frustum the dice
     * were drawn in. Two arrangements of the same numbers would be two
     * answers, and the one that disagreed would be the one nobody ran.
     */
    fun through(
      geometry: TableGeometry,
      aspectRatio: Double,
      view: TrayView = TrayView.Whole,
    ): TrayPick = TrayPick(TrayCamera.framingTheTray(geometry, aspectRatio, view), aspectRatio)

    private fun halfAngle(degrees: Double): Double = degrees / 2 * Math.PI / HALF_TURN_DEGREES

    private const val HALF_TURN_DEGREES = 180.0
  }
}
