package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.max
import kotlin.math.sqrt

/**
 * How the die is turned on the stage, in degrees
 * (`docs/face-designer.md`, "The solid, not just the face").
 *
 * Two angles rather than a quaternion, because they are what a finger does:
 * [yaw] is the drag across, [pitch] the drag down. The pitch stops short of
 * straight up so that the die can never be looked at exactly edge-on, which is
 * the one view in which a polyhedron disappears.
 */
data class SolidTurn(
  val held: Quaternion = TILTED,
) {
  /**
   * Turned by a drag of [across] and [down], both in fractions of the stage.
   *
   * About the **reader's** axes rather than the die's: across swings it about
   * the upright of the screen and down tips it about the horizontal, wherever
   * the die happens to be pointing. That is what makes a drag do the same
   * thing twice — with the die's own axes, a die already turned a quarter
   * round answers a sideways drag by rolling.
   */
  fun dragged(
    across: Float,
    down: Float,
  ): SolidTurn = SolidTurn(swing(across * SWEEP) * tip(down * SWEEP) * held)

  /**
   * Turned by [seconds] of the spin the die does on its own.
   *
   * **About the upright of the screen, always, and from wherever the die has
   * been left.** The two halves of that are one line: composing on the left
   * means the axis is the reader's, and composing *onto* [held] means the spin
   * carries on from the orientation a drag put the die in rather than from
   * some pose of its own.
   *
   * It used to be a yaw angle applied before a lean, which is the same thing
   * as spinning about an axis carried by the die: lean the die towards you and
   * its spin axis leaned too, so a die tipped right over span like a coin on a
   * table rather than turning in the hand.
   */
  fun spun(seconds: Float): SolidTurn = SolidTurn(swing(seconds * SPIN) * held)

  /** [point] where this turn puts it. */
  fun turnedTo(point: Vector3): Vector3 = held.rotate(point)

  /** About the upright of the screen, which is the tray's `+z`. */
  private fun swing(degrees: Float): Quaternion = Quaternion.about(Vector3.Up, radiansOf(degrees))

  /** And about the horizontal of the screen, which is the tray's `+x`. */
  private fun tip(degrees: Float): Quaternion = Quaternion.about(ACROSS, radiansOf(degrees))

  companion object {
    /** The horizontal of the screen, which is the tray's `+x`. */
    private val ACROSS = Vector3(1.0, 0.0, 0.0)

    /** How far from level a die is looked at before anybody has touched it. */
    private const val LOOKED_DOWN_ON = 16.0

    /**
     * Where the die stands before anybody has touched it: looked at slightly
     * from above.
     */
    val TILTED: Quaternion = Quaternion.about(ACROSS, LOOKED_DOWN_ON * Math.PI / HALF_TURN)

    /** How far a drag across the whole stage turns the die. */
    private const val SWEEP = 176f

    /** Degrees a second of the turn the die makes on its own: once round in sixteen seconds. */
    private const val SPIN = 360f / 16

    private fun radiansOf(degrees: Float): Double = degrees * Math.PI / HALF_TURN

    private const val HALF_TURN = 180.0
  }
}

/**
 * A point on the stage, in fractions of it: `(0, 0)` is its top-left corner.
 *
 * Fractions rather than pixels for the reason a [Dot] is: what is decided here
 * is tested on a JVM and drawn on a phone, and neither of those two knows how
 * big the other's stage is.
 */
data class StagePoint(
  val x: Float,
  val y: Float,
)

/**
 * One closed shape on a face of the die, as it is seen.
 *
 * Rings rather than one path, and drawn under the even-odd rule, because that
 * is what a mark on a face already is: a stamped `0` is its outline and the
 * counter that leaves its hole open ([Rings]).
 */
data class StageShape(
  val rings: List<List<StagePoint>>,
  val colorArgb: Int,
)

/**
 * One face of the die, as it is seen.
 *
 * @param cell which face of the die this is, so the screen can say which of
 *   them is the one being drawn on.
 * @param outline the face's own polygon, projected.
 * @param marks what is drawn on it, furthest back first.
 * @param light how much of the light this face catches, `0..1`.
 * @param depth how far the middle of the face is from the eye. What the stage
 *   is sorted by, and the only thing here that is not a picture.
 */
data class StageFace(
  val cell: Int,
  val outline: List<StagePoint>,
  val marks: List<StageShape>,
  val light: Float,
  val depth: Double,
)

/**
 * The die as it is seen: its silhouette, and the faces turned towards the
 * viewer, furthest first.
 */
data class Stage(
  val silhouette: List<StagePoint>,
  val faces: List<StageFace>,
)

/**
 * The die in the hand: the real polyhedron, turned, projected and drawn flat
 * (`docs/face-designer.md`, "The solid, not just the face").
 *
 * **The arithmetic is here and the picture is not.** Where a corner lands,
 * which faces are facing away, what order the rest are drawn in and how much
 * light each catches are all decisions a JVM test can hold; "does this look
 * like a die" is not, and there is nothing left in the draw lambda but paths
 * and colours (`docs/architecture.md`, decision 55).
 *
 * The solid itself is `simulation/api`'s ([SolidFaces]) — the same corners the
 * solver collides and the same faces the renderer's mesh is poured from. This
 * file adds no geometry of its own.
 */
object SolidStage {
  /** [draft]'s die as it is seen when it is turned by [turn]. */
  fun of(
    draft: Draft,
    turn: SolidTurn,
  ): Stage {
    val shape = draft.die.shape
    val seen =
      SolidFaces.of(shape).mapNotNull { face ->
        faceOf(face, draft, turn)
      }
    return Stage(
      silhouette = hullOf(ShapeGeometry.verticesOf(shape).map { corner -> pointOf(turn.turnedTo(corner)) }),
      // Furthest first, which is the order paint goes on. A convex solid with
      // its back faces already dropped has nothing left that can overlap, so
      // this decides nothing about what is seen — it is what makes the stage
      // right for a solid whose faces could overlap, and it costs one sort of
      // twenty things.
      faces = seen.sortedByDescending(StageFace::depth),
    )
  }

  /**
   * [face] as it is seen, or null when it is facing away.
   *
   * The test is whether the eye is on the outward side of the face's own
   * plane, which for a convex solid is exactly "is this face one of the ones
   * you can see".
   */
  private fun faceOf(
    face: SolidFace,
    draft: Draft,
    turn: SolidTurn,
  ): StageFace? {
    val middle = turn.turnedTo(face.centre)
    val normal = turn.turnedTo(face.normal)
    if (((EYE - middle) dot normal) <= 0) return null
    val basis = FaceOnSolid.basisOf(face, draft.outline)
    return StageFace(
      cell = face.index,
      outline = face.corners.map { corner -> pointOf(turn.turnedTo(corner)) },
      marks = shapesOf(draft.face(face.index).marks, basis, turn),
      light = lightOn(normal),
      depth = (EYE - middle).length,
    )
  }

  /**
   * What of a face's drawing the stage can show.
   *
   * **Closed marks, and not the strokes of the pen.** A fill, a stamped
   * numeral and a face of pips are all closed rings, and a closed ring under a
   * projection is still a closed ring with its corners in the right places —
   * so what is drawn is the shape itself rather than an impression of it. A
   * stroke is not a shape but a *line of a width*, and a width on a tilted
   * face is wider one way than the other; drawing it as a line of one width
   * would be the picture telling a lie about the die. It is left out and said
   * out loud instead (`docs/face-designer.md`).
   */
  private fun shapesOf(
    marks: List<Mark>,
    basis: FaceBasis,
    turn: SolidTurn,
  ): List<StageShape> =
    marks.mapNotNull { mark ->
      val rings =
        when (mark) {
          is Fill -> listOf(mark.dots)
          is Rings -> mark.rings
          is Stroke -> null
        }
      rings?.let { closed ->
        StageShape(
          rings = closed.map { ring -> ring.map { dot -> pointOf(turn.turnedTo(basis.pointOf(dot))) } },
          colorArgb = mark.colorArgb,
        )
      }
    }

  /**
   * How much light a face pointing [normal] catches, `0..1`.
   *
   * One light, over the viewer's left shoulder, and a floor under it so that a
   * face turned away from it is still a face rather than a hole. What the die
   * is *made of* is the renderer's business and this is not a renderer
   * (`docs/physics-and-rendering.md`); all this has to do is say which face is
   * which at a glance.
   */
  private fun lightOn(normal: Vector3): Float = (AMBIENT + (1 - AMBIENT) * max(0.0, normal dot LAMP)).toFloat()

  /** Where [point] lands on the stage. */
  private fun pointOf(point: Vector3): StagePoint {
    val away = point.y + EYE_DISTANCE
    return StagePoint(
      x = (HALF + REACH * point.x / away).toFloat(),
      // Up on the die is up on the stage, and a stage counts down the screen.
      y = (HALF - REACH * point.z / away).toFloat(),
    )
  }

  /**
   * The outline of [points], anticlockwise.
   *
   * What the die's silhouette is: the projection of a convex solid is the
   * convex hull of the projections of its corners. The faces cover it for
   * every solid in the catalogue but one — a coin's rim belongs to no face at
   * all, and without this a d2 turned on its side would be a disc with nothing
   * behind it (`render/filament`'s `DieMesh`, "The band around the outside of
   * a coin").
   */
  fun hullOf(points: List<StagePoint>): List<StagePoint> {
    if (points.size < TRIANGLE) return points
    val sorted = points.sortedWith(compareBy(StagePoint::x, StagePoint::y))
    val below = sideOf(sorted)
    val above = sideOf(sorted.reversed())
    return below.dropLast(1) + above.dropLast(1)
  }

  /** One side of the hull, walked in the order [points] are given in. */
  private fun sideOf(points: List<StagePoint>): List<StagePoint> {
    val walk = mutableListOf<StagePoint>()
    points.forEach { point ->
      while (walk.size >= 2 && cornerOf(walk[walk.size - 2], walk.last(), point) <= 0f) {
        walk.removeAt(walk.lastIndex)
      }
      walk.add(point)
    }
    return walk
  }

  /** Which way the corner at [b] turns, going from [a] to [c]. */
  private fun cornerOf(
    a: StagePoint,
    b: StagePoint,
    c: StagePoint,
  ): Float = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

  /**
   * How far the eye is from the middle of the die, in die radii.
   *
   * Far enough that the near face is not stretched out of recognition, near
   * enough that a d20 reads as a solid rather than as a flat badge.
   */
  private const val EYE_DISTANCE = 4.0

  /** Where the eye is. The stage looks along `+y`, with `+z` up and `+x` across. */
  private val EYE = Vector3(0.0, -EYE_DISTANCE, 0.0)

  /**
   * How much of the stage the die may fill at its widest.
   *
   * Left at the die's widest rather than measured off the picture: a die whose
   * size changed as it turned would be a die breathing, and the widest a
   * corner on the unit sphere can ever project to is a number this can be
   * worked out from exactly.
   */
  private const val FILLS = 0.45

  /**
   * What a die radius projects to, so that the widest of them is [FILLS].
   *
   * `1 / sqrt(d² − 1)` is the largest offset any point of a unit sphere can
   * project to from a distance `d` — the point where the line of sight grazes
   * it — so this is the exact scale rather than one somebody tried until it
   * stopped clipping.
   */
  private val REACH = FILLS * sqrt(EYE_DISTANCE * EYE_DISTANCE - 1)

  /** Which way the light comes from: over the viewer's left shoulder, from above. */
  private val LAMP = Vector3(-0.35, -0.9, 0.45).normalised()

  /** How lit a face pointing straight away from the lamp still is. */
  private const val AMBIENT = 0.45

  private const val HALF = 0.5
  private const val TRIANGLE = 3
}
