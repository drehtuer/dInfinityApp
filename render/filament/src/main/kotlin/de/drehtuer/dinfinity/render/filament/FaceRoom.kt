package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.ShapeAtlas
import kotlin.math.abs
import kotlin.math.hypot

/**
 * How big a label can be on a die's face, and where on that face it goes.
 *
 * All of it is arithmetic over the mesh's own texture coordinates — the same
 * mapping the renderer samples the atlas with — so a number is judged against
 * the face the player will actually see rather than against a second account
 * of the same solid (`docs/architecture.md`, decision 45). What is printed is
 * [DieNumbers]'s to decide; this only says how much room there is for it.
 */
object FaceRoom {
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
   *
   * It is measured off the mesh's own texture coordinates, the same mapping
   * the renderer samples the atlas with, so the box is judged against the face
   * the player will actually see rather than against a second account of the
   * same solid (`docs/architecture.md`, decision 45).
   */
  fun on(
    surface: MeshFace,
    grid: ShapeAtlas.Grid,
    cell: Pair<Int, Int>,
    aspect: Double,
    share: Double,
  ): Room {
    val edges = edgesOf(cornersOf(surface, grid, cell), aspect)
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

  /** The face's corners in this cell's own coordinates, which run `0..1`. */
  fun cornersOf(
    surface: MeshFace,
    grid: ShapeAtlas.Grid,
    cell: Pair<Int, Int>,
  ): List<Pair<Double, Double>> {
    val (column, row) = cell
    return surface.uvs.map { it.u * grid.columns - column to it.v * grid.rows - row }
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
