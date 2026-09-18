package de.drehtuer.dinfinity.core.glyphs

import kotlin.math.abs
import kotlin.math.hypot

/**
 * How big a label can be on a die's face, and where on that face it goes.
 *
 * A face is a convex polygon in the coordinates of its own atlas cell — `0..1`
 * on both axes, `(0, 0)` at the top left — and everything here is arithmetic
 * over those corners. Who the corners belong to is the caller's business: the
 * tray measures them off the mesh's own texture coordinates
 * (`render/filament`'s `FaceRoom`) and the face designer takes them from the
 * outline it masks the canvas into (`designer`'s `FaceShapes`).
 *
 * **It is one object rather than one each because the two have to agree.** A
 * die drawn from the designer's "fill all faces with numbers" and the same die
 * printed by the tray should not disagree about where a `6` sits, and two
 * implementations of the same solve are how they would come to
 * (`docs/architecture.md`, decision 45; `docs/face-designer.md`, "The stamp").
 */
object LabelRoom {
  /**
   * How much of the room a face has, a number takes up.
   *
   * *Of the room the face actually has*, not of its cell. A cell is the circle
   * drawn round a face, and how much of one a face fills depends entirely on
   * what polygon it is — a dodecahedron's pentagon fills most of it, a d20's
   * triangle half of it, and a d18's kite a quarter. A number sized against
   * the cell therefore comes out right on a d6 and crowding the edges on a
   * d20, which is what it did on the Pixel 10a.
   *
   * So the size is solved rather than chosen: the largest box of this label's
   * own proportions that fits inside this face, times this fraction. What is
   * left to judge is the fraction — how much smaller than the room a numeral
   * should be — and that needs a phone (`docs/TODO.md`, Step 5.6).
   */
  const val FACE_SHARE: Double = 0.78

  /**
   * How tall a d4's numbers are, as a fraction of the cell.
   *
   * Of the cell rather than solved against the edges the way [FACE_SHARE] is,
   * because these do not sit in the middle of the face: three of them share
   * one triangle, each near its own corner, and what bounds them is each other
   * rather than the edges.
   */
  const val CORNER_HEIGHT: Double = 0.20

  /**
   * How far out from the middle of the cell a corner-read number sits, as a
   * fraction of the way to the corner it belongs to.
   *
   * Not all the way: a number printed *at* a corner runs off the edge of the
   * triangle, and a real d4 prints them just inside it.
   */
  const val CORNER_REACH: Double = 0.62

  /**
   * Where a label goes in the middle of a face with these [corners].
   *
   * Null when there is nothing to print or nowhere to print it — a face with
   * no room for a box of this shape, which no catalogue solid is, and a
   * string the font cannot draw, which a set's own `💀` is (see [FaceLabel]).
   *
   * **The share is applied twice**: once to the box the centre is solved
   * clear of, and once again to what is printed inside it, so a numeral ends
   * up `FACE_SHARE` squared of the room its face has. That is what the tray
   * has always done and what the size on the Pixel 10a was judged against, so
   * it is kept rather than quietly corrected here; whether to apply it once
   * and re-judge the fraction is open (`docs/TODO.md`, "Open questions").
   *
   * **A [marked] number is measured with its mark on.** `6.` is wider than
   * `6`, so the room it is given is solved for the whole of what is printed —
   * a `6` sized as though it were bare and then given a dot would be a `6`
   * whose dot hangs over the edge of its face.
   */
  fun centred(
    corners: List<Pair<Double, Double>>,
    text: String,
    face: Typeface = BuiltinFont.face,
    marked: Boolean = false,
    share: Double = FACE_SHARE,
  ): Placement? {
    if (text.isEmpty()) return null
    val room = on(corners, Typesetter.inkWidth(Typesetter.printed(text, marked), 1.0, face), share)
    val height = share * room.height
    if (height <= 0) return null
    return Placement(
      centreX = room.centreX,
      centreY = room.centreY,
      height = height,
      marked = marked,
    )
  }

  /**
   * Where a label goes at one [corner] of a face, each turned to face its own
   * corner — what a d4 does, because its numbers belong to corners rather
   * than to faces (`docs/dice-sets.md`, "The d4").
   *
   * [corner] is the corner itself, in the cell's coordinates; how far inside
   * it the number sits is [CORNER_REACH] and is decided here, so that the
   * guide under a designer's canvas and the number a stamp puts down land in
   * the same place.
   */
  fun cornered(
    corners: List<Pair<Double, Double>>,
    corner: Pair<Double, Double>,
    text: String,
    face: Typeface = BuiltinFont.face,
    marked: Boolean = false,
  ): Placement? {
    if (text.isEmpty()) return null
    val (outX, outY) = inside(corner)
    // Held to what the polygon has at that point. A number placed at a corner
    // is nearer two edges than anything in the middle is, and a d4 whose
    // numbers ran over its own edges would be the one die in the set that
    // could not be read.
    val height =
      minOf(
        CORNER_HEIGHT,
        heightAt(corners, Typesetter.inkWidth(Typesetter.printed(text, marked), 1.0, face), outX, outY),
      )
    if (height <= 0) return null
    return Placement(
      centreX = outX,
      centreY = outY,
      height = height,
      // Up, for this number, is the way its own corner lies. StrictMath
      // rather than the platform's, which is `simulation/api`'s `Exact` for
      // the same reason: the same answer on every phone.
      turns = StrictMath.atan2(-(outX - HALF), -(outY - HALF)) / FULL_TURN,
      marked = marked,
    )
  }

  /**
   * [corner] pulled in towards the middle of the cell by [CORNER_REACH].
   *
   * Where a corner-read number's middle sits, and where the designer's guide
   * for it is drawn — one answer, so that tracing the guide and stamping the
   * number cannot come out in two places.
   */
  fun inside(corner: Pair<Double, Double>): Pair<Double, Double> =
    HALF + (corner.first - HALF) * CORNER_REACH to HALF + (corner.second - HALF) * CORNER_REACH

  /**
   * The biggest a label of this shape can be on this face, and where on it.
   *
   * The face is a convex polygon and the label is a box `aspect` wide for
   * every one tall. A box fits inside a convex polygon exactly when it stays
   * on the inner side of every edge, and how far a box of a given size reaches
   * towards an edge is a number — so "the biggest box that fits, anywhere on
   * the face" is three unknowns, two for where it sits and one for how big it
   * is, under one straight-line condition per edge. The best answer to that
   * always has three of those conditions met exactly, so every three are
   * solved and the biggest that satisfies the rest wins. Solved rather than
   * guessed at, like the camera's standing distance
   * (`docs/physics-and-rendering.md`).
   *
   * **Letting it move is not a nicety.** A d10's and a d18's faces are kites,
   * and a d18's are long enough that the middle of the cell is not inside the
   * kite at all — so a box pinned there does not fit anywhere, at any size.
   * A d20's triangle is the same problem in miniature: there is more room
   * slightly below the middle than at it.
   */
  fun on(
    corners: List<Pair<Double, Double>>,
    aspect: Double,
    share: Double,
  ): Room {
    val edges = edgesOf(corners, aspect)
    val height = share * (biggest(edges)?.height ?: return NOWHERE)
    // Where, having settled how big. The same solve again with the label's own
    // size taken out of every edge's allowance, which asks for the point that
    // is as far from all of them as it can be.
    val room = biggest(edges.map { it.clearing(height) }) ?: return NOWHERE
    return Room(height = height, centreX = room.centreX, centreY = room.centreY)
  }

  /**
   * The tallest box these conditions allow, or null when they allow none.
   *
   * The best answer is not always one point. A `1` on a d6 is as big anywhere
   * across the square, so the answers that tie make a stretch of the face and
   * every solve lands on one end of it — which prints the `1` hard against an
   * edge. Averaging them puts it back in the middle, which is where the tie
   * says it may as well be.
   */
  private fun biggest(edges: List<Edge>): Room? {
    val meetings =
      buildList {
        for (one in edges.indices) {
          for (other in one + 1 until edges.size) {
            for (third in other + 1 until edges.size) {
              meeting(edges[one], edges[other], edges[third])
                ?.takeIf { corner -> edges.all { it.allows(corner) } }
                ?.let(::add)
            }
          }
        }
      }
    val tallest = meetings.maxOfOrNull { it.height } ?: return null
    val tied = meetings.filter { it.height >= tallest - TIE }
    return Room(
      height = tallest,
      centreX = tied.sumOf { it.centreX } / tied.size,
      centreY = tied.sumOf { it.centreY } / tied.size,
    )
  }

  /**
   * One condition per edge.
   *
   * The normals are turned outwards by the ring's own winding rather than by
   * which side the middle of the cell is on, because for a long enough kite
   * the middle of the cell is not inside the face at all (see [on]).
   */
  private fun edgesOf(
    corners: List<Pair<Double, Double>>,
    aspect: Double,
  ): List<Edge> {
    // For a ring wound the way the shoelace calls positive, `(dy, -dx)` points
    // out of the polygon; for one wound the other way it points into it.
    val outward = if (turning(corners) > 0) 1.0 else -1.0
    val halfWidth = maxOf(aspect, THINNEST) / 2
    return corners.indices.map { corner ->
      val (fromX, fromY) = corners[corner]
      val (toX, toY) = corners[(corner + 1) % corners.size]
      val length = hypot(toY - fromY, fromX - toX)
      val normalX = outward * (toY - fromY) / length
      val normalY = outward * (fromX - toX) / length
      Edge(
        normalX = normalX,
        normalY = normalY,
        distance = normalX * fromX + normalY * fromY,
        reach = halfWidth * abs(normalX) + HALF * abs(normalY),
      )
    }
  }

  /** Twice the signed area of a polygon, which is what says which way it is wound. */
  private fun turning(corners: List<Pair<Double, Double>>): Double =
    corners.indices.sumOf { corner ->
      val (fromX, fromY) = corners[corner]
      val (toX, toY) = corners[(corner + 1) % corners.size]
      fromX * toY - toX * fromY
    }

  /** The box that meets all three edges exactly, or null when they do not meet in one. */
  private fun meeting(
    one: Edge,
    other: Edge,
    third: Edge,
  ): Room? {
    val determinant =
      one.normalX * (other.normalY * third.reach - third.normalY * other.reach) -
        other.normalX * (one.normalY * third.reach - third.normalY * one.reach) +
        third.normalX * (one.normalY * other.reach - other.normalY * one.reach)
    if (abs(determinant) < PARALLEL) return null
    val height =
      (
        one.normalX * (other.normalY * third.distance - third.normalY * other.distance) -
          other.normalX * (one.normalY * third.distance - third.normalY * one.distance) +
          third.normalX * (one.normalY * other.distance - other.normalY * one.distance)
      ) / determinant
    if (height <= 0.0) return null
    val centreX =
      (
        one.distance * (other.normalY * third.reach - third.normalY * other.reach) -
          other.distance * (one.normalY * third.reach - third.normalY * one.reach) +
          third.distance * (one.normalY * other.reach - other.normalY * one.reach)
      ) / determinant
    val centreY =
      (
        one.normalX * (other.distance * third.reach - third.distance * other.reach) -
          other.normalX * (one.distance * third.reach - third.distance * one.reach) +
          third.normalX * (one.distance * other.reach - other.distance * one.reach)
      ) / determinant
    return Room(height = height, centreX = centreX, centreY = centreY)
  }

  /** The tallest a box of this shape can be with its middle held at this point. */
  fun heightAt(
    corners: List<Pair<Double, Double>>,
    aspect: Double,
    centreX: Double,
    centreY: Double,
  ): Double =
    edgesOf(corners, aspect).minOf {
      (it.distance - it.normalX * centreX - it.normalY * centreY) / it.reach
    }

  /** What a face with no room on it comes to, which no catalogue solid is. */
  private val NOWHERE = Room(height = 0.0, centreX = 0.5, centreY = 0.5)

  /** A label narrower than this is still given this much room, so a `1` is not made huge. */
  private const val THINNEST = 0.42

  /** Three conditions closer to parallel than this never meet anywhere worth having. */
  private const val PARALLEL = 1e-9

  /** Two answers this close to the same size are the same size, and are averaged. */
  private const val TIE = 1e-9

  private const val HALF = 0.5

  private const val FULL_TURN = 2 * Math.PI
}

/**
 * How big a label may be on a face, and where on that face it goes.
 *
 * @param height the label's figure height, as a fraction of the cell.
 * @param centreX where its middle sits across the cell, `0.5` being the middle.
 * @param centreY the same, down the cell.
 */
data class Room(
  val height: Double,
  val centreX: Double,
  val centreY: Double,
)

/**
 * One edge of a face, as the condition it puts on a label: a box of this shape
 * centred at `(x, y)` and `height` tall stays on the inner side of this edge
 * when `normal · (x, y) + height * reach` is no more than `distance`.
 */
private data class Edge(
  val normalX: Double,
  val normalY: Double,
  val distance: Double,
  val reach: Double,
) {
  /** Whether [room] stays on the inner side of this edge. */
  fun allows(room: Room): Boolean =
    normalX * room.centreX + normalY * room.centreY + room.height * reach <= distance + SLACK

  /**
   * The same edge with a label of this height already accounted for, so that
   * what is solved for next is how much room is left over rather than how big
   * the label may be.
   */
  fun clearing(height: Double): Edge = copy(distance = distance - height * reach, reach = 1.0)

  private companion object {
    /** A box meeting an edge exactly must not be refused by that same edge's own arithmetic. */
    const val SLACK = 1e-9
  }
}
