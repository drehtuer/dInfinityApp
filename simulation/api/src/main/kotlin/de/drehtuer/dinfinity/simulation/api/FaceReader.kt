package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die

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
 * Every die is read this way, the d4 included: it is a long die that rests on
 * one of its four long faces, and one that came to rest on an end has no face
 * within fifteen degrees of up and is cocked like any other (decision 109).
 */
object FaceReader {
  /** How far from upright a face may be and still count as the one on top. */
  const val COCKED_DEGREES: Double = 15.0

  /** The dot product that corresponds to [COCKED_DEGREES]. */
  val UPRIGHT_THRESHOLD: Double = Exact.cos(Math.toRadians(COCKED_DEGREES))

  /**
   * Which position of [die] is up when it is turned by [orientation], or
   * [Reading.Cocked] when none of them is up enough to read.
   */
  fun read(
    die: Die,
    orientation: Quaternion,
  ): Reading {
    val directions = ShapeGeometry.directionsOf(die.shape)
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
    return if (bestAlignment >= UPRIGHT_THRESHOLD) Reading.Face(best) else Reading.Cocked(best, bestAlignment)
  }
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
   * @param nearestIndex the position that came closest to being up. It is
   *   **not** the result — reading it would fabricate a number nobody rolled —
   *   but the anomaly log and the debug overlay both want to know which face
   *   nearly won, and it is the only thing the safety valve at the 12-second
   *   cap has left to report (`docs/physics-and-rendering.md`).
   * @param bestAlignment how close that position came, for the same log.
   */
  data class Cocked(
    val nearestIndex: Int,
    val bestAlignment: Double,
  ) : Reading
}
