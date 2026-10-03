package de.drehtuer.dinfinity.simulation.api

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * What goes into a board's drop: which dice carry over from the board on
 * screen, and where the new ones are let go from
 * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * Tapping a d6 in the picker drops a die onto the table from above, and it
 * comes down under real physics among the dice already there ([BoardSettler]).
 * This is the arithmetic either side of that: it builds the [BoardRequest] and
 * nothing else — no body, no world and no step — so it is tested on a JVM.
 *
 * **The board is not a roll.** Nothing here or downstream of it reads a face;
 * the next shake throws every die from its own spawn, and the numbers the
 * board draws come through [Seeds.waiting], a purpose no throw uses, so a
 * board built between two throws cannot move a number in either of them.
 */
object BoardDrops {
  /**
   * How far above the felt, beyond its own radius, a die is let go.
   *
   * Higher than the 25 mm an added die is dropped from
   * (`SpawnLayout.RETHROW_HEIGHT_MM`), and deliberately. That drop happens
   * inside a roll, among dice whose faces the player is reading, and its job
   * is to be unobtrusive; this one *is* the thing the player asked to see. At
   * 25 mm the whole fall is over in an eighth of a second, which on a screen
   * is a die appearing with extra steps.
   */
  const val DROP_HEIGHT_MM: Double = 60.0

  /**
   * And up to this much more, so a handful is a patter rather than a thud:
   * dice let go from slightly different heights reach the table at slightly
   * different moments.
   */
  const val HEIGHT_JITTER_MM: Double = 18.0

  /**
   * How many random spots are tried before the clearest one is taken instead.
   *
   * Random first, because a die put down at the one clearest point every time
   * is a board that is laid out rather than dropped on — the equal spacing the
   * player asked to see the back of. Eight misses means the board is getting
   * full, and then the clearest point is the honest answer ([ClearSpace]).
   */
  const val SPOT_DRAWS: Int = 8

  /** The least a die drifts sideways as it is let go, in mm/s. */
  const val LEAST_SLIDE_MM_PER_SECOND: Double = 40.0

  /** And the most: enough to skid on landing, not enough to reach a wall. */
  const val MOST_SLIDE_MM_PER_SECOND: Double = 150.0

  /** The most it is pushed down as it is let go, in mm/s — a toss, not a throw. */
  const val MOST_DOWNWARD_MM_PER_SECOND: Double = 150.0

  /** The least a die spins as it is let go, in rad/s: under this it lands flat. */
  const val LEAST_SPIN_RADIANS_PER_SECOND: Double = 9.0

  /** And the most, before it reads as a die spun rather than dropped. */
  const val MOST_SPIN_RADIANS_PER_SECOND: Double = 18.0

  /**
   * Which dice of a board of [now] were already on the board of [was]: the
   * index of each die of [now] that carries over, mapped to the index it had.
   *
   * Matched by what the die *is* rather than by where it sits in the formula,
   * so that taking a d6 out of `2d6 + 1d20` leaves the d20 where it was
   * instead of picking it up and dropping it again. Greedy and in order, which
   * for the two things that actually happen — a die appended, a die removed —
   * is the obvious answer and is stable.
   *
   * The keys are whatever the caller thinks makes two dice interchangeable.
   * The renderer uses the die and the size it is drawn at, because a board
   * whose dice have been shrunk to fit is a board whose dice have all changed
   * and is dropped afresh.
   *
   * @param present which dice of [was] are actually in the drop on screen.
   *   Every die of a board is let go ([release] always finds it somewhere),
   *   so this is all of them; it is asked rather than assumed so that a die
   *   the picture does not hold can never be carried over from nowhere.
   */
  fun <T> keeping(
    was: List<T>,
    now: List<T>,
    present: Set<Int> = was.indices.toSet(),
  ): Map<Int, Int> {
    val spent = BooleanArray(was.size)
    val kept = mutableMapOf<Int, Int>()
    now.forEachIndexed { index, wanted ->
      val found =
        was.indices.firstOrNull { !spent[it] && was[it] == wanted && it in present } ?: return@forEachIndexed
      spent[found] = true
      kept[index] = found
    }
    return kept
  }

  /**
   * The board for [spec], with the dice in [kept] already on it.
   *
   * The dice in [kept] start where they are on screen now, moving as they are
   * moving. Every other die of [spec] is new and is let go from above
   * ([release]), in index order, each clear of the dice before it. **Every
   * die is let go**: a die the player added must appear, and the throw that
   * follows counts it, so a crowded board drops it from higher rather than
   * leaving it out.
   *
   * @param number this board's number within the visit, which is what its
   *   drops are seeded by ([Seeds.waiting]). Not the spec's seed: every board
   *   is built from a spec seeded nought, so a die taken off and put back would
   *   otherwise land on the same spot in the same way every time.
   */
  fun request(
    number: Int,
    spec: ThrowSpec,
    kept: Map<Int, Placement>,
  ): BoardRequest {
    val taken = kept.values.mapTo(mutableListOf()) { it.position }
    val bodies =
      spec.dice.mapIndexed { index, instance ->
        val placement =
          kept[index] ?: release(
            geometry = spec.geometry,
            radiusMm = ClearSpace.radiusOf(instance.die, spec.dieScale),
            taken = taken,
            random = Seeds.waiting(number, index),
          ).also { taken += it.position }
        BoardBody(index, instance.die, spec.dieScale, placement)
      }
    return BoardRequest(number = number, geometry = spec.geometry, table = spec.table, bodies = bodies)
  }

  /**
   * Where and how a new die of [radiusMm] is let go over [geometry], clear of
   * the dice at [taken].
   *
   * A spot drawn at random inside the walls, the first of [SPOT_DRAWS] that
   * no die is standing on; failing that, the clearest point there is. Held
   * [DROP_HEIGHT_MM] and a little more above the felt.
   *
   * **And when the floor is full, it is let go anyway.** Random spots pack a
   * floor less tightly than a laid-out grid, so a board the capacity rule
   * accepts can run out of clear floor before it runs out of dice — and a die
   * the player added that never appeared would be a die the shake then throws
   * from nowhere. So it goes over the least crowded point there is, stacked
   * higher than every die it would otherwise start inside, measured in three
   * dimensions ([CrowdedFloor]); the physics settles it onto or among the
   * others. Turned any way at all,
   * drifting sideways and spinning: a die dropped straight down without a
   * spin would land on whatever face it was let go on, which looks like a die
   * being *put* down.
   */
  fun release(
    geometry: TableGeometry,
    radiusMm: Double,
    taken: List<Vector3>,
    random: Random,
  ): Placement {
    val ceiling = geometry.ceilingHeightMm - radiusMm - ClearSpace.CLEARANCE_MM
    val clear = spotFor(geometry, radiusMm, taken, random)
    val height = (radiusMm + DROP_HEIGHT_MM + random.nextDouble(0.0, HEIGHT_JITTER_MM)).coerceAtMost(ceiling)
    val spot = clear?.copy(z = height) ?: CrowdedFloor.spot(geometry, radiusMm, taken, height, ceiling)
    val rotation = anyTurn(random)
    val slide = random.nextDouble(LEAST_SLIDE_MM_PER_SECOND, MOST_SLIDE_MM_PER_SECOND)
    val heading = random.nextDouble() * 2 * PI
    val downward = random.nextDouble(0.0, MOST_DOWNWARD_MM_PER_SECOND)
    val spin = anyDirection(random) * random.nextDouble(LEAST_SPIN_RADIANS_PER_SECOND, MOST_SPIN_RADIANS_PER_SECOND)
    return Placement(
      position = spot,
      rotation = rotation,
      linearVelocity = Vector3(slide * Exact.cos(heading), slide * Exact.sin(heading), -downward),
      angularVelocity = spin,
    )
  }

  /** A spot on the floor for a die of [radiusMm], or null when there is none. */
  private fun spotFor(
    geometry: TableGeometry,
    radiusMm: Double,
    taken: List<Vector3>,
    random: Random,
  ): Vector3? {
    val margin = radiusMm + ClearSpace.CLEARANCE_MM
    val halfLong = geometry.longSideMm / 2 - margin
    val halfShort = geometry.shortSideMm / 2 - margin
    if (halfLong <= 0.0 || halfShort <= 0.0) return ClearSpace.clearestPoint(geometry, radiusMm, taken)
    val needed = 2 * radiusMm + ClearSpace.CLEARANCE_MM
    repeat(SPOT_DRAWS) {
      val x = random.nextDouble(-halfLong, halfLong)
      val y = random.nextDouble(-halfShort, halfShort)
      if (taken.all { other -> distanceAcross(x, y, other) >= needed }) return Vector3(x, y, 0.0)
    }
    return ClearSpace.clearestPoint(geometry, radiusMm, taken)
  }

  private fun distanceAcross(
    x: Double,
    y: Double,
    other: Vector3,
  ): Double {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
  }

  /**
   * A turn drawn evenly over every turn there is (Shoemake's method).
   *
   * A die let go in a turn that favours some faces lands on them more often,
   * and even on a board that is not a roll, the same face up on every die the
   * player adds would look like what it is.
   */
  internal fun anyTurn(random: Random): Quaternion {
    val first = random.nextDouble()
    val second = random.nextDouble() * 2 * PI
    val third = random.nextDouble() * 2 * PI
    val low = sqrt(1 - first)
    val high = sqrt(first)
    return Quaternion(
      w = high * Exact.cos(third),
      x = low * Exact.sin(second),
      y = low * Exact.cos(second),
      z = high * Exact.sin(third),
    )
  }

  /**
   * A direction drawn evenly over the sphere.
   *
   * Three independent components would bunch towards the corners of a cube,
   * and a spin that favours some axes is a tumble that looks the same every
   * time.
   */
  internal fun anyDirection(random: Random): Vector3 {
    val z = random.nextDouble(-1.0, 1.0)
    val angle = random.nextDouble() * 2 * PI
    val ring = sqrt(1 - z * z)
    return Vector3(ring * Exact.cos(angle), ring * Exact.sin(angle), z)
  }
}
