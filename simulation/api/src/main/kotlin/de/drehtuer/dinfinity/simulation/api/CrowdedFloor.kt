package de.drehtuer.dinfinity.simulation.api

import kotlin.math.sqrt

/**
 * Where a die dropped onto the board goes when no floor is clear
 * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * [BoardDrops] tries random spots first, and random spots pack a floor less
 * tightly than a grid — so a board the capacity rule accepts can run out of
 * clear floor before it runs out of dice. A die the player added must still
 * appear, because the shake that follows counts it. This finds it a place: the
 * least crowded point of the tray, lifted clear in three dimensions of every
 * die it would otherwise start inside, for the physics to settle onto or among
 * the others.
 */
internal object CrowdedFloor {
  /**
   * Where a die of [radiusMm] goes when no floor is clear: over the least
   * crowded point of the tray, at [from] or as much higher as it takes not to
   * start inside any die at [taken].
   *
   * Points are tried from the least crowded down, and the first that fits
   * under [ceiling] is the answer. A tray so full that none does is past
   * anything the capacity rule accepts; the lowest of them, held under the
   * lid, is the least wrong place then.
   */
  fun spot(
    geometry: TableGeometry,
    radiusMm: Double,
    taken: List<Vector3>,
    from: Double,
    ceiling: Double,
  ): Vector3 {
    val apart = 2 * radiusMm + ClearSpace.CLEARANCE_MM
    val tried =
      floorPoints(geometry, radiusMm)
        .map { point -> point to nearestAcross(point, taken) }
        .sortedWith(compareByDescending<Pair<Vector3, Double>> { it.second }.thenBy { fromTheMiddle(it.first) })
        .map { (point, _) -> point.copy(z = stackedHeight(point, from, apart, taken)) }
    return tried.firstOrNull { it.z <= ceiling } ?: tried.minBy { it.z }.let { it.copy(z = minOf(it.z, ceiling)) }
  }

  /**
   * How high over [point] a die must start to be [apart] from every die at
   * [taken], starting from [from].
   *
   * Each clash lifts it to [apart] and a little more above that die. A clash
   * means the die is less than [apart] above it, so every lift is upwards,
   * and once lifted clear of a die it stays clear of it: each die clashes at
   * most once, and it ends after no more lifts than there are dice. The
   * little more is what makes that true in floating point — lifted to exactly
   * `z + apart`, a die straight overhead can measure a hair under [apart]
   * from the one below and be lifted to the same height for ever.
   */
  fun stackedHeight(
    point: Vector3,
    from: Double,
    apart: Double,
    taken: List<Vector3>,
  ): Double {
    var height = from
    repeat(taken.size + 1) {
      val clash = taken.firstOrNull { (Vector3(point.x, point.y, height) - it).length < apart } ?: return height
      height = clash.z + apart + LIFT_SLACK_MM
    }
    return height
  }

  /** How much more than [stackedHeight]'s `apart` a lift clears a die by. */
  const val LIFT_SLACK_MM: Double = 0.01

  /** A grid over the floor a die of [radiusMm] can stand on, the middle included. */
  private fun floorPoints(
    geometry: TableGeometry,
    radiusMm: Double,
  ): List<Vector3> {
    val margin = radiusMm + ClearSpace.CLEARANCE_MM
    val halfLong = (geometry.longSideMm / 2 - margin).coerceAtLeast(0.0)
    val halfShort = (geometry.shortSideMm / 2 - margin).coerceAtLeast(0.0)
    val along = axisPoints(halfLong)
    val across = axisPoints(halfShort)
    return along.flatMap { x -> across.map { y -> Vector3(x, y, 0.0) } }
  }

  private fun axisPoints(half: Double): List<Double> {
    if (half == 0.0) return listOf(0.0)
    val count = ClearSpace.MOST_POINTS_TRIED
    return List(count) { -half + it * (2 * half / (count - 1)) }
  }

  /** How far from the middle of the tray [point] is, squared — only the order matters. */
  private fun fromTheMiddle(point: Vector3): Double = point.x * point.x + point.y * point.y

  /** How far, across the floor, [point] is from the nearest die at [taken]. */
  private fun nearestAcross(
    point: Vector3,
    taken: List<Vector3>,
  ): Double =
    taken.minOfOrNull {
      val dx = point.x - it.x
      val dy = point.y - it.y
      sqrt(dx * dx + dy * dy)
    } ?: Double.MAX_VALUE
}
