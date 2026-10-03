package de.drehtuer.dinfinity.simulation.api

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * What goes into a board's drop: which dice carry over from the board on
 * screen, and where and when the new ones are let go
 * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * Tapping a d6 in the picker drops a die onto the table from above, and it
 * comes down under real physics among the dice already there ([BoardSettler]).
 * Every die is let go over the same spot ([DROP_SPOT]), and several added at
 * once are let go one after another ([DROP_INTERVAL_SECONDS]). This is the
 * arithmetic either side of that: it builds the [BoardRequest] and says where
 * a die may start, and nothing else — no body, no world and no step — so it is
 * tested on a JVM.
 *
 * **The board is not a roll.** Nothing here or downstream of it reads a face;
 * the next shake throws every die from its own spawn, and the numbers the
 * board draws come through [Seeds.waiting], a purpose no throw uses, so a
 * board built between two throws cannot move a number in either of them.
 */
object BoardDrops {
  /**
   * The one point over which every added die is let go: the middle of the
   * tray. Only its `x` and `y` count; the height is [DROP_HEIGHT_MM].
   *
   * **One spot, so the eye can follow.** Dice dropped over random spots came
   * down all over the table, and a player adding five could not tell which
   * five were new. Let go one after another over the same point
   * ([DROP_INTERVAL_SECONDS]), they arrive as a stream the eye stays on, and
   * spread out by knocking into each other and into the dice already down —
   * which is physics, and what dropping dice onto a heap does
   * (`docs/architecture.md`, decision 67).
   *
   * The middle rather than the end nearest the dice pull-down: the pull-down
   * lies over the top of the table while it is open, which is exactly when
   * the player is tapping dice in, so a die let go up there would fall behind
   * it. The middle is the point the camera always frames, and the one with as
   * much room to scatter into on every side.
   */
  val DROP_SPOT: Vector3 = Vector3.Zero

  /**
   * How far from [DROP_SPOT] a die may be let go, in any direction across the
   * table: a millimetre and a half.
   *
   * Enough that two dice let go over the same point do not come down with
   * their centres stacked to the micron — a die balanced dead on top of
   * another is the landing a solver is slowest to resolve — and far too
   * little for the eye to see the spot move.
   */
  const val SPOT_JITTER_MM: Double = 1.5

  /**
   * How far above the felt, beyond its own radius, a die is let go.
   *
   * Higher than the 25 mm an added die is dropped from
   * (`SpawnLayout.RETHROW_HEIGHT_MM`), and deliberately. That drop happens
   * inside a roll, among dice whose faces the player is reading, and its job
   * is to be unobtrusive; this one *is* the thing the player asked to see. At
   * 25 mm the whole fall is over in an eighth of a second, which on a screen
   * is a die appearing with extra steps.
   *
   * Every die is let go from this one height. The dice of a handful come down
   * one after another now ([DROP_INTERVAL_SECONDS]), so nothing has to
   * stagger their landings, and a stream that leaves from one point is the
   * easiest thing to follow.
   */
  const val DROP_HEIGHT_MM: Double = 60.0

  /**
   * How long after one added die the next is let go, when several are added
   * at once — a saved roll put on the table, a formula typed, `40d6`.
   *
   * A tenth of a second: long enough that each die is seen leaving the spot
   * on its own (a die let go from [DROP_HEIGHT_MM] is most of the way down by
   * then, and out of the next one's way), short enough that forty of them are
   * a four-second patter rather than a wait. A judgement number for the owner
   * (`docs/TODO.md`, Step 5.6).
   */
  const val DROP_INTERVAL_SECONDS: Double = 0.1

  /** [DROP_INTERVAL_SECONDS] in whole simulation steps, which is what a drop is scheduled in. */
  val DROP_INTERVAL_STEPS: Int = (DROP_INTERVAL_SECONDS / SettleRule.TIMESTEP_SECONDS).roundToInt()

  /**
   * The longest a stream of added dice takes to let go, first die to last:
   * four seconds, which is forty-one dice at [DROP_INTERVAL_SECONDS].
   *
   * A bigger handful — the capacity rule allows a hundred — is let go closer
   * together instead, so that it fits ([intervalFor]). Ten seconds of dice
   * leaving one spot is a wait rather than a patter, and the whole drop is
   * worked out before the first die of it is shown, so a longer stream is
   * also a longer pause before anything moves.
   */
  const val LONGEST_STREAM_SECONDS: Double = 4.0

  /**
   * How many steps apart [count] dice added at once are let go:
   * [DROP_INTERVAL_STEPS], or as much less as it takes for the stream to last
   * no longer than [LONGEST_STREAM_SECONDS] — but never two in one step.
   */
  fun intervalFor(count: Int): Int {
    if (count <= 1) return DROP_INTERVAL_STEPS
    val longest = (LONGEST_STREAM_SECONDS / SettleRule.TIMESTEP_SECONDS).roundToInt()
    return (longest / (count - 1)).coerceIn(1, DROP_INTERVAL_STEPS)
  }

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
   * @param present which dice of [was] are actually on the table on screen.
   *   A die still waiting its turn to be let go ([BoardBody.dropStep]) is
   *   not: there is nothing on screen to carry over, so the next board drops
   *   it again, over the same spot.
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
   * moving. Every other die of [spec] is new and is let go over [DROP_SPOT]
   * ([release]), in index order and [DROP_INTERVAL_STEPS] apart: the first at
   * once, the next a tenth of a second later, and so on — closer together
   * only for a handful too big to let go in [LONGEST_STREAM_SECONDS] at that
   * pace ([intervalFor]). **Every die is let
   * go**: a die the player added must appear, and the throw that follows
   * counts it. Exactly where it starts is settled at the moment it is let go,
   * against the dice in play by then ([letGo]).
   *
   * @param number this board's number within the visit, which is what its
   *   drops are seeded by ([Seeds.waiting]). Not the spec's seed: every board
   *   is built from a spec seeded nought, so a die taken off and put back would
   *   otherwise tumble the same way every time.
   */
  fun request(
    number: Int,
    spec: ThrowSpec,
    kept: Map<Int, Placement>,
  ): BoardRequest {
    val interval = intervalFor(spec.dice.indices.count { it !in kept })
    var dropped = 0
    val bodies =
      spec.dice.mapIndexed { index, instance ->
        val carried = kept[index]
        if (carried != null) {
          BoardBody(index, instance.die, spec.dieScale, carried)
        } else {
          val placement =
            release(
              geometry = spec.geometry,
              radiusMm = ClearSpace.radiusOf(instance.die, spec.dieScale),
              random = Seeds.waiting(number, index),
            )
          BoardBody(index, instance.die, spec.dieScale, placement, dropStep = dropped++ * interval)
        }
      }
    return BoardRequest(number = number, geometry = spec.geometry, table = spec.table, bodies = bodies)
  }

  /**
   * How a new die of [radiusMm] is meant to be let go over [geometry]: over
   * [DROP_SPOT], within [SPOT_JITTER_MM] of it, [DROP_HEIGHT_MM] above the
   * felt beyond its own radius and always under the lid.
   *
   * Turned any way at all, drifting sideways and spinning: a die dropped
   * straight down without a spin would land on whatever face it was let go
   * on, which looks like a die being *put* down — and the drift is what sends
   * each die of a stream its own way off the spot.
   *
   * *Meant*, because whether that spot is clear is only known when the die is
   * let go: the dice before it may still be falling through it ([letGo]).
   */
  fun release(
    geometry: TableGeometry,
    radiusMm: Double,
    random: Random,
  ): Placement {
    val ceiling = geometry.ceilingHeightMm - radiusMm - ClearSpace.CLEARANCE_MM
    val off = SPOT_JITTER_MM * sqrt(random.nextDouble())
    val around = random.nextDouble() * 2 * PI
    val height = (radiusMm + DROP_HEIGHT_MM).coerceAtMost(ceiling)
    val spot = Vector3(DROP_SPOT.x + off * Exact.cos(around), DROP_SPOT.y + off * Exact.sin(around), height)
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

  /**
   * Where a die of [radiusMm] meant to start at [meant] actually starts, given
   * the dice in play at the moment it is let go: at [inPlay], none bigger than
   * [largestRadiusMm].
   *
   * **A die never starts inside another**, measured in three dimensions: the
   * die let go before it may still be in the air under the spot, and one may
   * have come to rest there. So it is lifted over whatever is in the way
   * ([CrowdedFloor.stackedHeight]) — never skipped, because a die the player
   * added must appear. Only when that would put it through the lid does it go
   * somewhere else: over the least crowded point of the tray, lifted the same
   * way ([CrowdedFloor.spot]).
   *
   * Only where it starts changes. It is turned, drifting and spinning as it
   * was meant to be.
   */
  fun letGo(
    geometry: TableGeometry,
    meant: Placement,
    radiusMm: Double,
    largestRadiusMm: Double,
    inPlay: List<Vector3>,
  ): Placement {
    val ceiling = geometry.ceilingHeightMm - radiusMm - ClearSpace.CLEARANCE_MM
    val apart = radiusMm + maxOf(radiusMm, largestRadiusMm) + ClearSpace.CLEARANCE_MM
    val from = meant.position
    val lifted = CrowdedFloor.stackedHeight(from, from.z, apart, inPlay)
    val spot =
      if (lifted <= ceiling) {
        from.copy(z = lifted)
      } else {
        CrowdedFloor.spot(geometry, radiusMm, inPlay, from.z, ceiling, apart)
      }
    return meant.copy(position = spot)
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
