package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import kotlin.math.abs
import kotlin.math.tan

/**
 * Where the camera stands (`docs/physics-and-rendering.md`, "Rendering").
 *
 * How far it leans is the player's: **Table view** in Settings is straight
 * down or [TILT_DEGREES] off it, and the shot is the same arithmetic either
 * way. The argument for leaning is that straight down is a diagram and the
 * whole point of rolling real dice is that you can see them tumble; the
 * argument against it is a phone, where a leaning shot spends a large share of
 * the frame on the rim. Neither wins, which is why it is a setting and why
 * straight down is what a new install gets ([TableView]).
 *
 * The frame is solved rather than fixed, so a lean of nothing needs no special
 * case: the camera's own up rotates with it, stays square to the way it looks
 * at every angle, and at 0° stands directly over the middle of the tray.
 *
 * While a roll is running it frames the whole tray,
 * because a die can be anywhere in it; once the dice have settled it frames
 * *them* and eases in, because by then the only thing worth looking at is what
 * they came to.
 *
 * The tray's long side is the screen's *height*: a portrait phone gets a
 * portrait tray (`docs/tables.md`), and the long side is `+x`, so `+x` is up
 * the screen and `+y` is across it.
 *
 * Nothing here touches a GPU. Framing is arithmetic about a frustum, and
 * getting it wrong shows up as dice half out of shot — which a test can say
 * far more precisely than a person squinting at a phone.
 */
object TrayCamera {
  /**
   * How far the camera leans away from straight down on [TableView.Angled] —
   * and what every caller that does not care gets, because it is the shot this
   * file took before the lean was a setting.
   */
  const val TILT_DEGREES: Double = 22.0

  /** [TableView.StraightDown]: no lean at all, and every die square to the screen. */
  const val NO_TILT_DEGREES: Double = 0.0

  /** How much of the scene the camera takes in, top to bottom. */
  const val FIELD_OF_VIEW_DEGREES: Double = 40.0

  /** A little air around whatever is being framed, so nothing touches the edge. */
  const val MARGIN: Double = 1.06

  /**
   * The shot that holds the tray, or as much of it as [view] asks for.
   *
   * The default holds all of it, which is where every roll is watched from: a
   * camera that closes in while the dice are moving takes the table away, and
   * a player cannot then tell four dice from two. Looking closer is the
   * player's to do, and [view] is them doing it
   * (`docs/physics-and-rendering.md`).
   *
   * @param tiltDegrees how far to lean away from straight down — the Table
   *   view setting, through [tiltDegreesOf]. [TILT_DEGREES] when nobody says,
   *   which is the shot this took before the lean was a setting; the *setting*
   *   defaults the other way, to [TableView.StraightDown].
   */
  fun framingTheTray(
    geometry: TableGeometry,
    aspectRatio: Double,
    view: TrayView = TrayView.Whole,
    tiltDegrees: Double = TILT_DEGREES,
  ): CameraShot {
    val held = view.within(geometry)
    val middle = Vector3(held.panAlongMm, held.panAcrossMm, 0.0)
    return shotOn(
      target = middle + Vector3(0.0, 0.0, geometry.wallHeightMm / 2),
      framed =
        box(
          middle = middle,
          reach = Vector3(geometry.longSideMm / 2 / held.zoom, geometry.shortSideMm / 2 / held.zoom, 0.0),
          height = geometry.wallHeightMm,
        ),
      aspectRatio = aspectRatio,
      tiltDegrees = tiltDegrees,
    )
  }

  /** How far [view] leans, in degrees away from straight down. */
  fun tiltDegreesOf(view: TableView): Double =
    when (view) {
      TableView.StraightDown -> NO_TILT_DEGREES
      TableView.Angled -> TILT_DEGREES
    }

  /**
   * A camera aimed at [target], pulled back until every one of [framed] is
   * inside the frustum.
   *
   * The distance is solved rather than guessed. For a point `q`, with `a`, `b`
   * and `e` its distances along the camera's forward, right and up axes from
   * the target, being inside the frustum at distance `d` means
   * `|b| ≤ tan(h)·(a + d)` across and `|e| ≤ tan(v)·(a + d)` up. Both are
   * linear in `d`, so each point names the nearest the camera may stand and
   * the answer is the furthest of those.
   */
  private fun shotOn(
    target: Vector3,
    framed: List<Vector3>,
    aspectRatio: Double,
    tiltDegrees: Double,
  ): CameraShot {
    require(aspectRatio > 0.0) { "a viewport $aspectRatio wide for its height is not a viewport" }
    val tilt = radians(tiltDegrees)
    // Down the screen and forwards: the camera stands off the near end of the
    // tray, which is the bottom of the screen, and looks back along it.
    val forward = Vector3(Exact.sin(tilt), 0.0, -Exact.cos(tilt))
    val up = Vector3(Exact.cos(tilt), 0.0, Exact.sin(tilt))
    // `cross(forward, up)`, in that order: the other way round is screen-*left*
    // and was written here for a long time, harmless only because its one use
    // is inside an `abs()`. It is right here so that nobody hunting a
    // handedness bug stops on this line (`docs/TODO.md`, Step 4.9).
    val right = cross(forward, up)
    val upward = tan(radians(FIELD_OF_VIEW_DEGREES / 2))
    val across = upward * aspectRatio

    val distance =
      framed.maxOf { point ->
        val offset = point - target
        val along = offset dot forward
        maxOf(
          abs(offset dot right) / across - along,
          abs(offset dot up) / upward - along,
        )
      } * MARGIN

    return CameraShot(
      position = target - forward * distance,
      target = target,
      up = up,
      verticalFieldOfViewDegrees = FIELD_OF_VIEW_DEGREES,
    )
  }

  /** The eight corners of the box from [lowest] to [highest]. */
  private fun corners(
    lowest: Vector3,
    highest: Vector3,
  ): List<Vector3> =
    listOf(lowest.x, highest.x).flatMap { x ->
      listOf(lowest.y, highest.y).flatMap { y ->
        listOf(lowest.z, highest.z).map { z -> Vector3(x, y, z) }
      }
    }

  /** The box around [middle] reaching [reach] sideways and [height] up. */
  private fun box(
    middle: Vector3,
    reach: Vector3,
    height: Double,
  ): List<Vector3> = corners(middle - reach, middle + reach + Vector3(0.0, 0.0, height))

  private fun radians(degrees: Double): Double = degrees * Math.PI / HALF_TURN_DEGREES

  private const val HALF_TURN_DEGREES = 180.0
}

/**
 * One position of the camera.
 *
 * @param position where it stands, in the tray's millimetres.
 * @param target what it looks at.
 * @param up which way is up the screen, perpendicular to the view direction.
 * @param verticalFieldOfViewDegrees how much it takes in, top to bottom.
 */
data class CameraShot(
  val position: Vector3,
  val target: Vector3,
  val up: Vector3,
  val verticalFieldOfViewDegrees: Double,
) {
  /** Which way the camera is looking, one unit long. */
  val forward: Vector3 get() = (target - position).normalised()

  /** How far the camera stands from what it is looking at. */
  val distanceMm: Double get() = (target - position).length
}
