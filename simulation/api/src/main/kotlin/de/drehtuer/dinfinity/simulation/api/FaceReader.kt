package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.FaceRead
import kotlin.math.cos

/**
 * Reads the number off a die that has come to rest
 * (`docs/physics-and-rendering.md`, "Settling and reading the result").
 *
 * For each readable position, take the dot product of its outward direction
 * with up. The largest wins — *if* it is at least `cos(15°)`. Otherwise the
 * die is leaning on something and there is no face to read: it is cocked, and
 * the only honest answer is to throw it again where the player can see
 * (rung 3 of the ladder).
 *
 * A d4 is read from the vertex pointing up rather than a face, because a
 * tetrahedron resting on a face has no face pointing up. The check is the same
 * one, against the vertex direction instead of the face normal.
 */
object FaceReader {
  /** How far from upright a face may be and still count as the one on top. */
  const val COCKED_DEGREES: Double = 15.0

  /** The dot product that corresponds to [COCKED_DEGREES]. */
  val UPRIGHT_THRESHOLD: Double = cos(Math.toRadians(COCKED_DEGREES))

  /**
   * Which position of [die] is up when it is turned by [orientation], or
   * [Reading.Cocked] when none of them is up enough to read.
   */
  fun read(
    die: Die,
    orientation: Quaternion,
  ): Reading {
    val directions = ShapeGeometry.directionsOf(die.shape)
    // A vertex-up die is read from the corner at the top; a face-up one from
    // the face normal. Both are already in the shape's own direction list.
    require(directions.size == die.faces.size) {
      "${die.shape.id} has ${directions.size} positions but '${die.id}' has ${die.faces.size} faces"
    }
    var best = -1
    var bestAlignment = -Double.MAX_VALUE
    directions.forEachIndexed { index, direction ->
      val alignment = orientation.rotate(direction) dot Vector3.Up
      if (alignment > bestAlignment) {
        bestAlignment = alignment
        best = index
      }
    }
    return if (bestAlignment >= UPRIGHT_THRESHOLD) Reading.Face(best) else Reading.Cocked(bestAlignment)
  }

  /** True when a die read this way takes its value from a vertex, not a face. */
  fun readsFromVertex(die: Die): Boolean = die.read == FaceRead.VertexUp
}

/** What a settled die said. */
sealed interface Reading {
  /** The index of the position that came up, which the die's faces turn into a value. */
  data class Face(
    val index: Int,
  ) : Reading

  /**
   * Nothing is up enough to read: the die is on an edge, on a corner, or
   * resting on another die.
   *
   * It is **not** nudged, tilted or snapped to the nearest face — that would
   * fabricate a result nobody rolled. It is thrown again, visibly
   * (`docs/physics-and-rendering.md`, rung 4: "Never").
   *
   * @param bestAlignment how close the nearest position came, for the anomaly log.
   */
  data class Cocked(
    val bestAlignment: Double,
  ) : Reading
}
