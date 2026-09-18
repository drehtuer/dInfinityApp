package de.drehtuer.dinfinity.simulation.api

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How a die the player has just added drops onto the board and tumbles to a
 * stop (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * Tapping a d6 in the picker used to make a die *appear* on the table. It now
 * falls in from above and turns over as it comes down, because that is what
 * putting a die on a table looks like and because a player asked for it. It is
 * still not a roll.
 *
 * **It is an animation, and it cannot be anything else.** There is no body, no
 * world and no step here — only arithmetic over time — and the one fact that
 * settles the question is that **every fall ends at [Quaternion.Identity]**.
 * The orientation a die comes to rest in is decided before it is released and
 * is the same for every die and every seed, so there is no face to read even
 * if something wanted to: the tumble is the only part that varies, and by the
 * time the die is standing it is over. A die's value comes from the simulation
 * and from nowhere else (`.claude/CLAUDE.md`), and this cannot become a second
 * way to get one.
 *
 * **Nothing it does touches a die that has already come to rest.** A board is
 * rebuilt whenever the formula changes, and the dice that were already
 * standing keep the exact places they were standing in ([keeping]); only the
 * dice that are new to the board are given somewhere to fall, and they are
 * given floor the standing ones are not using ([RestingPlaces.of]'s `among`).
 *
 * **And there is one board, not one per tap.** Tapping out `8d6` builds eight
 * boards in a row, each of which is a list of [Drop]s and a clock — no thread,
 * no world, nothing to leave running. A die that was still in the air when the
 * next tap came keeps falling, because its release is carried over onto the
 * new board's clock rather than restarted ([Drop.shiftedBy]).
 */
object FallingIn {
  /**
   * How far above its resting place a die is let go.
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
   * And a little more for some of them, so a handful is a patter rather than
   * a thud.
   *
   * The same trick the spawn's height bands play (`SpawnLayout`): dice let go
   * from slightly different heights reach the table at slightly different
   * moments. It is height rather than a delay because a die held in the air
   * waiting for its turn would be drawn hanging there — every die on the board
   * is drawn from the first frame.
   */
  const val HEIGHT_JITTER_MM: Double = 18.0

  /**
   * Earth's, to the same figure the shake driver uses
   * (`ShakeDriver.GRAVITY_MM_PER_SECOND2`, pinned equal by `SpawnLayoutTest`).
   *
   * A drop that fell at some other rate than the throw that follows it would
   * be two tables in one tray.
   */
  const val GRAVITY_MM_PER_SECOND2: Double = 9_806.65

  /** How much of its speed a die keeps when it hits the felt. */
  const val BOUNCE: Double = 0.35

  /** And how many bounces are worth drawing before it is simply down. */
  const val BOUNCES: Int = 3

  /** The least a die turns on the way down. Under one turn reads as a wobble. */
  const val LEAST_TURNS: Double = 0.75

  /** And the most, before it reads as a die being spun rather than dropped. */
  const val MOST_TURNS: Double = 1.6

  /**
   * One die on its way to a place on the board, or standing in it.
   *
   * Everything about the fall is fixed when the drop is made, so asking where
   * the die is at some moment is arithmetic and not a step: the same clock
   * asked twice gives the same answer, and a board that is drawn at 60 Hz and
   * one drawn at 120 Hz are the same fall.
   *
   * @param index which die of the board this is.
   * @param restingAt where it ends up — [RestingPlaces]' answer, and the place
   *   it would simply have been stood in before there was a fall at all.
   * @param turningAbout the axis it turns about, drawn evenly over the sphere.
   * @param throughRadians how far it turns between release and rest. It turns
   *   *back to* [Quaternion.Identity], so this is where it starts rather than
   *   where it ends.
   * @param heldAtMm how far above [restingAt] it is let go.
   * @param releasedAt when it was let go, on the board's clock. Zero for a die
   *   the board was just given, and negative for one carried over from the
   *   board before — a die that was mid-air when the formula changed goes on
   *   falling rather than starting again.
   */
  data class Drop(
    val index: Int,
    val restingAt: Vector3,
    val turningAbout: Vector3,
    val throughRadians: Double,
    val heldAtMm: Double,
    val releasedAt: Double,
  ) {
    /** How long this die spends in the air, release to rest. */
    val fallSeconds: Double get() = fallTime(heldAtMm)

    /** When it stops, on the board's clock. */
    val restsAt: Double get() = releasedAt + fallSeconds

    /** Whether it is still on its way down at [seconds] on the board's clock. */
    fun moving(seconds: Double): Boolean = seconds < restsAt

    /**
     * Where the die's centre is at [seconds] on the board's clock.
     *
     * A die that is no longer [moving] is at [restingAt] exactly, rather than
     * at whatever the last fraction of a millimetre of arithmetic came to: a
     * die on the table is on the table.
     */
    fun positionAt(seconds: Double): Vector3 {
      if (!moving(seconds)) return restingAt
      return restingAt.copy(z = restingAt.z + heightAt((seconds - releasedAt).coerceAtLeast(0.0), heldAtMm))
    }

    /**
     * How the die is turned at [seconds] on the board's clock.
     *
     * It unwinds: the die is released already turned [throughRadians] about
     * its axis and rotates back to nothing, easing off as it comes down. So
     * the last frame of every fall is [Quaternion.Identity], by construction
     * rather than by a tolerance — which is the whole of why this cannot
     * decide a face.
     */
    fun orientationAt(seconds: Double): Quaternion {
      if (!moving(seconds)) return Quaternion.Identity
      val left = 1.0 - eased(((seconds - releasedAt) / fallSeconds).coerceIn(0.0, 1.0))
      if (left <= 0.0) return Quaternion.Identity
      return Quaternion.about(turningAbout, throughRadians * left)
    }

    /**
     * The same drop on a clock that has been wound back to [seconds].
     *
     * What carries a die from one board to the next. A die that has already
     * landed lands again immediately; a die still in the air is exactly as far
     * through its fall as it was a moment ago.
     */
    fun shiftedBy(seconds: Double): Drop = copy(releasedAt = releasedAt - seconds)
  }

  /**
   * The board for [radiiMm], with [keeping] already standing on it.
   *
   * The dice named in [keeping] are left exactly where they are. Every other
   * die is new: it is given the clearest floor left ([RestingPlaces.of]) and
   * dropped onto it, and it is drawn falling from the first frame.
   *
   * Returns as many dice as there is floor for, in index order — the same
   * answer, and the same shortfall, [RestingPlaces.of] gives on its own.
   *
   * @param seed the board's seed, which is **not** a roll's. It reaches
   *   [Random] only through [Seeds.WAITING], a purpose no throw uses, so a
   *   board built between two throws cannot move a number in either of them.
   */
  fun board(
    geometry: TableGeometry,
    radiiMm: List<Double>,
    seed: Long,
    keeping: Map<Int, Drop> = emptyMap(),
  ): List<Drop> {
    val fresh = radiiMm.indices.filterNot { keeping.containsKey(it) }
    val places =
      RestingPlaces.of(
        geometry = geometry,
        radiiMm = fresh.map { radiiMm[it] },
        among = keeping.values.map { it.restingAt },
      )
    val dropped =
      places.mapIndexed { place, at -> releasedOnto(fresh[place], at, seed) }
    return (keeping.values + dropped).sortedBy { it.index }
  }

  /**
   * Which of [drops] carry over from a board of [was] to a board of [now], on
   * a clock wound back to [since].
   *
   * Matched by what the die *is* rather than by where it sits in the formula,
   * so that taking a d6 out of `2d6 + 1d20` leaves the d20 standing where it
   * was instead of picking it up and dropping it again. Greedy and in order,
   * which for the two things that actually happen — a die appended, a die
   * removed — is the obvious answer and is stable.
   *
   * The keys are whatever the caller thinks makes two dice interchangeable.
   * The renderer uses the die and the size it is being drawn at, because a
   * board whose dice have been shrunk to fit is a board whose places have all
   * moved and cannot be kept.
   */
  fun <T> keeping(
    was: List<T>,
    now: List<T>,
    drops: List<Drop>,
    since: Double,
  ): Map<Int, Drop> {
    val standing = drops.associateBy { it.index }
    val spent = BooleanArray(was.size)
    val kept = mutableMapOf<Int, Drop>()
    now.forEachIndexed { index, wanted ->
      val found =
        was.indices.firstOrNull { !spent[it] && was[it] == wanted && standing.containsKey(it) }
          ?: return@forEachIndexed
      spent[found] = true
      kept[index] = standing.getValue(found).copy(index = index).shiftedBy(since)
    }
    return kept
  }

  /** Whether any die of [board] is still in the air at [seconds]. */
  fun stillFalling(
    board: List<Drop>,
    seconds: Double,
  ): Boolean = board.any { it.moving(seconds) }

  /** How long the whole of [board] takes to come to rest. */
  fun restsAt(board: List<Drop>): Double = board.maxOfOrNull { it.restsAt } ?: 0.0

  /**
   * How long a die let go [heightMm] above its place spends in the air.
   *
   * The free fall, and then a bounce at a time: each one leaves at [BOUNCE] of
   * the speed it arrived with, so it is up and down again in twice that over
   * gravity. Closed form rather than a loop over a timestep, because there is
   * no timestep here — nothing is being solved.
   */
  fun fallTime(heightMm: Double): Double {
    val arriving = sqrt(2 * GRAVITY_MM_PER_SECOND2 * heightMm)
    var total = arriving / GRAVITY_MM_PER_SECOND2
    var leaving = arriving * BOUNCE
    repeat(BOUNCES) {
      total += 2 * leaving / GRAVITY_MM_PER_SECOND2
      leaving *= BOUNCE
    }
    return total
  }

  /**
   * How far above its place a die let go [heightMm] up is, [seconds] after it
   * was released.
   *
   * Never below nought and never above [heightMm]: a die that has finished
   * bouncing is on the table, and one that has run past the end of its last
   * bounce is on the table too.
   */
  fun heightAt(
    seconds: Double,
    heightMm: Double,
  ): Double = heightAbove(seconds, heightMm).coerceIn(0.0, heightMm)

  /**
   * The same before it is clamped, which is the arithmetic on its own.
   *
   * The clamp above is not tidying. The sum of the bounces and the walk
   * through them are the same numbers added in a different order, so a die
   * asked for its height at the exact instant it stops can come out a hair's
   * breadth either side of the felt, and a die below the felt is a bug
   * whatever its size.
   */
  private fun heightAbove(
    seconds: Double,
    heightMm: Double,
  ): Double {
    val arriving = sqrt(2 * GRAVITY_MM_PER_SECOND2 * heightMm)
    var left = seconds
    val falling = arriving / GRAVITY_MM_PER_SECOND2
    if (left < falling) return heightMm - GRAVITY_MM_PER_SECOND2 * left * left / 2
    left -= falling
    var leaving = arriving * BOUNCE
    repeat(BOUNCES) {
      val airborne = 2 * leaving / GRAVITY_MM_PER_SECOND2
      if (left < airborne) return leaving * left - GRAVITY_MM_PER_SECOND2 * left * left / 2
      left -= airborne
      leaving *= BOUNCE
    }
    return 0.0
  }

  /**
   * The die's turn as a share of the whole, [through] of the way down.
   *
   * Eased out: most of the turning happens while the die is falling and it
   * slows as it settles, which is what a die does and what stops the last
   * frames looking like a model snapping to an axis.
   */
  fun eased(through: Double): Double = 1.0 - (1.0 - through) * (1.0 - through)

  /** One new die, dropped onto [at] with its own tumble. */
  private fun releasedOnto(
    index: Int,
    at: Vector3,
    seed: Long,
  ): Drop {
    val random = Seeds.stream(seed, index, Seeds.WAITING)
    return Drop(
      index = index,
      restingAt = at,
      turningAbout = anyDirection(random),
      throughRadians = random.nextDouble(LEAST_TURNS, MOST_TURNS) * 2 * PI,
      heldAtMm = DROP_HEIGHT_MM + random.nextDouble(0.0, HEIGHT_JITTER_MM),
      releasedAt = 0.0,
    )
  }

  /**
   * A direction drawn evenly over the sphere.
   *
   * Three independent components would bunch towards the corners of a cube,
   * and a tumble that favours some axes is a tumble that looks the same every
   * time (`SpawnLayout.randomSpin`, which says the same thing about a throw).
   */
  private fun anyDirection(random: Random): Vector3 {
    val z = random.nextDouble(-1.0, 1.0)
    val angle = random.nextDouble() * 2 * PI
    val ring = sqrt(1 - z * z)
    return Vector3(ring * Exact.cos(angle), ring * Exact.sin(angle), z)
  }
}
