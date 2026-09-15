package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Where a die added to a tray that already has dice in it is dropped
 * (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll adds").
 *
 * An explosion and a reroll each add a die to a roll that has already landed,
 * and the one rule that cannot bend is the app's oldest: **nothing touches a
 * die that has come to rest.** A die dropped onto the pile that then shoved it
 * would change a number the player has already read, which is the exact
 * failure this project exists not to have.
 *
 * So an added die is thrown into the clear floor the settled dice leave, and
 * this is what finds it: the point of the tray furthest from every die already
 * down, and as near the middle as that allows. There is no arithmetic here
 * about *whether* the dice touch — an added die is thrown in a world of its
 * own, where there is nothing to touch — but the picture has to be honest too,
 * and a die drawn sliding through a settled one is a picture claiming the
 * physics did something it did not.
 *
 * Everything is on the floor plane: dice at rest all sit on it, so how high a
 * die is dropped from is not this object's business and the point it hands back
 * has `z = 0`.
 */
object ClearSpace {
  /**
   * How much floor must be free around an added die, beyond the two dice's own
   * bounding circles.
   *
   * The same two millimetres a die is spawned clear of a wall by
   * (`SpawnLayout.WALL_CLEARANCE_MM`), for the same reason: something that
   * starts touching is something that starts overlapping as far as a solver is
   * concerned.
   */
  const val CLEARANCE_MM: Double = 2.0

  /**
   * How many points along each side are tried.
   *
   * A grid rather than a search, because the answer has to be the same on every
   * device and at every moment: a hill-climb with a starting guess is a hill
   * that a different guess comes down a different side of. Thirty-three points
   * along the long side of a 240 mm tray are under seven millimetres apart,
   * about the radius of the thing being placed.
   *
   * Odd, always, so that the middle of the tray is one of the points tried:
   * it is the answer for an empty tray and the tie-break for every other.
   */
  const val MOST_POINTS_TRIED: Int = 33

  /**
   * Where to drop a die of [dieRadiusMm] into [geometry] when [taken] is
   * already in it, or null when there is nowhere left to drop one.
   *
   * [taken] is where the dice already at rest are, in tray millimetres. Only
   * their `x` and `y` are read, because a die at rest is on the floor.
   */
  fun clearestPoint(
    geometry: TableGeometry,
    dieRadiusMm: Double,
    taken: List<Vector3>,
  ): Vector3? {
    require(dieRadiusMm > 0.0) { "a die $dieRadiusMm mm across has no room to need" }
    val margin = dieRadiusMm + CLEARANCE_MM
    val halfLong = geometry.longSideMm / 2 - margin
    val halfShort = geometry.shortSideMm / 2 - margin
    // A die wider than the tray it is being added to. Nothing fits, and the
    // arithmetic below would happily place it through a wall.
    if (halfLong < 0.0 || halfShort < 0.0) return null

    val asClearAsItGets = geometry.longSideMm + geometry.shortSideMm
    val needed = 2 * dieRadiusMm + CLEARANCE_MM
    var best: Candidate? = null
    val columns = pointsAcross(halfLong)
    val rows = pointsAcross(halfShort)
    repeat(columns) { column ->
      val x = coordinate(column, columns, halfLong)
      repeat(rows) { row ->
        val y = coordinate(row, rows, halfShort)
        val room = clearanceAt(x, y, dieRadiusMm, taken, asClearAsItGets)
        if (room >= needed) best = betterOf(best, Candidate(x, y, room), dieRadiusMm)
      }
    }
    return best?.let { Vector3(it.x, it.y, 0.0) }
  }

  /**
   * Whether the tray could take one more die at all.
   *
   * Asked *before* a die is thrown, because a chain of explosions has to stop
   * somewhere and "there is nowhere left to put it" is the honest place for it
   * to stop (`docs/dice-notation.md`, "Limits"). Two things can end it: the
   * engine's hard cap on bodies in a tray, and the floor running out. It is the
   * same code that finds the point, deliberately — a roll told there was room
   * and then unable to find any would be two rules disagreeing.
   */
  fun roomForAnother(
    geometry: TableGeometry,
    dieRadiusMm: Double,
    taken: List<Vector3>,
  ): Boolean = taken.size < TableCapacity.MAX_DICE && clearestPoint(geometry, dieRadiusMm, taken) != null

  /**
   * The room a die of [die]'s size, thrown at [dieScale], needs on the floor.
   *
   * One line, so the spawn, the drawing and the question "is there room for
   * another" cannot each have their own idea of how big a die is.
   */
  fun radiusOf(
    die: Die,
    dieScale: Double,
  ): Double = die.material.boundingRadiusMm * dieScale

  /**
   * How much clear floor a die at ([x], [y]) would have around it, never more
   * than [ceiling].
   *
   * Capped rather than infinite for an empty tray so that "as clear as each
   * other" below is plain arithmetic: infinity minus infinity is not a
   * difference of zero, it is not a number.
   */
  private fun clearanceAt(
    x: Double,
    y: Double,
    dieRadiusMm: Double,
    taken: List<Vector3>,
    ceiling: Double,
  ): Double {
    var nearest = ceiling
    taken.forEach { other ->
      val dx = x - other.x
      val dy = y - other.y
      // The gap between the two bounding circles, which is what has to be
      // positive for two dice not to overlap — not the gap between centres.
      val gap = sqrt(dx * dx + dy * dy) - 2 * dieRadiusMm
      if (gap < nearest) nearest = gap
    }
    return nearest
  }

  /**
   * The better of two candidates: the clearer one, and where they are as clear
   * as each other, the one nearer the middle of the tray.
   *
   * "As clear as each other" is within half a die. The clearest spot in a tray
   * with one die in the middle of it is a corner, and a corner is the part of
   * the floor a die has the least room to tumble in; a point a millimetre less
   * clear and well inside the tray is the better throw. With nothing down at
   * all every point ties, and what comes out is the middle of the tray.
   */
  private fun betterOf(
    best: Candidate?,
    candidate: Candidate,
    dieRadiusMm: Double,
  ): Candidate {
    if (best == null) return candidate
    if (abs(candidate.clearance - best.clearance) <= dieRadiusMm) {
      return if (candidate.fromTheMiddle < best.fromTheMiddle) candidate else best
    }
    return if (candidate.clearance > best.clearance) candidate else best
  }

  /**
   * How many points to try along a side reaching [half] either way.
   *
   * One when there is no room to move at all, so a tray that only just holds
   * the die still offers its middle.
   */
  private fun pointsAcross(half: Double): Int {
    if (half <= 0.0) return 1
    val tried = (2 * half).toInt().coerceIn(1, MOST_POINTS_TRIED)
    return if (tried % 2 == 0) tried + 1 else tried
  }

  private fun coordinate(
    index: Int,
    count: Int,
    half: Double,
  ): Double = if (count == 1) 0.0 else -half + index * (2 * half / (count - 1))

  /** One point tried, and how much room a die would have there. */
  private data class Candidate(
    val x: Double,
    val y: Double,
    val clearance: Double,
  ) {
    /** How far from the middle of the tray, squared — only the order matters. */
    val fromTheMiddle: Double get() = x * x + y * y
  }
}
