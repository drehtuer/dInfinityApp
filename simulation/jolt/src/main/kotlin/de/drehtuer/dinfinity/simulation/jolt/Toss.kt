package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.Placement
import de.drehtuer.dinfinity.simulation.api.Release
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How the hand tosses the dice when it lets go (`docs/physics-and-rendering.md`,
 * "The hand lets go"; `docs/architecture.md`, decision 103).
 *
 * Every die is thrown from wherever the shake left it: up, so it leaves the
 * felt and lands as a bounce rather than a slide, and along the hand's last
 * stroke ([Release]) — each a little off that line and a little faster or
 * slower, the way a handful leaves a hand — with a fresh spin. What it lands
 * on is the solver's.
 *
 * **One after another, not all at once** ([delayOf]). A handful leaves a hand
 * over a moment, the dice at the front first; tossed on one step, every die
 * hopped together and the owner saw the dice jump.
 *
 * Drawn from the throw's seed, per die, from a stream of its own
 * ([Seeds.RELEASE]), so the same record tosses the same way every time and a
 * throw recorded before there was a toss draws exactly the numbers it did.
 */
object Toss {
  /**
   * How many steps after the hand lets go die [index] leaves it: 0 to
   * [STAGGER_STEPS], from a stream of its own so it moves none of the die's
   * other numbers.
   */
  fun delayOf(
    seed: Long,
    index: Int,
  ): Int = Seeds.stream(seed, index, Seeds.RELEASE xor STAGGER_SALT).nextInt(0, STAGGER_STEPS + 1)

  /**
   * The velocities die [index] of [count] leaves the hand with, linear in
   * mm/s and angular in rad/s, for a die moving at [moving] when it goes.
   *
   * **Onward, not turned round.** A die already sliding is tossed the way it
   * is going, at the hand's speed or its own if that is faster; only a die
   * that is barely moving takes the stroke's direction. Tossed along the
   * stroke whatever they were doing, a heap sliding one way reversed on the
   * spot and the owner saw the dice jump.
   *
   * **And never into a wall it is against** ([offTheWalls]), for a die at
   * [position] in [tray]. A stroke that stops slams the heap into the wall it
   * was heading for, and the stroke's direction points straight at it: tossed
   * that way, the dice bounced off the end wall as one, which along the tray's
   * long side was the hop the owner still saw.
   */
  @Suppress("LongParameterList")
  fun of(
    release: Release,
    seed: Long,
    index: Int,
    count: Int,
    moving: Vector3 = Vector3.Zero,
    position: Vector3? = null,
    tray: TableGeometry? = null,
  ): Pair<Vector3, Vector3> {
    val random = Seeds.stream(seed, index, Seeds.RELEASE)
    val along = Vector3(moving.x, moving.y, 0.0)
    val direction = if (along.length >= ONWARD_MM_PER_SECOND) along.normalised() else release.direction ?: aimless(seed)
    val turned = turned(direction, random.nextDouble(-AIM_SPREAD_RADIANS, AIM_SPREAD_RADIANS))
    val heading = if (position != null && tray != null) offTheWalls(turned, position, tray) else turned
    val hand = release.speedMmPerSecond * taperFor(count)
    val speed = maxOf(hand, along.length) * random.nextDouble(1 - SPEED_SPREAD, 1 + SPEED_SPREAD)
    val lift = sqrt(2 * ShakeDriver.GRAVITY_MM_PER_SECOND2 * random.nextDouble(LEAST_LIFT_MM, MOST_LIFT_MM))
    val spin =
      randomDirection(random) *
        random.nextDouble(SpawnLayout.SPAWN_SPIN_RADIANS_PER_SECOND / 2, SpawnLayout.SPAWN_SPIN_RADIANS_PER_SECOND)
    return Pair(heading * speed + Vector3(0.0, 0.0, lift), spin)
  }

  /**
   * Tosses die [index] of [count] in [world] from where [state] says it is,
   * the way [release] went (decision 103).
   *
   * A die lying still under a hand that is still shaking is a roll caught at
   * the top of a swing, not a die at rest, so every die is tossed. Its pose is
   * kept exactly — the toss changes how it moves and nothing about where it is.
   */
  @Suppress("LongParameterList")
  fun one(
    world: PhysicsWorld,
    index: Int,
    count: Int,
    state: DieState,
    moving: Vector3,
    release: Release,
    seed: Long,
    tray: TableGeometry? = null,
  ) {
    val (linear, angular) = of(release, seed, index, count, moving, state.position, tray)
    world.respawn(
      index,
      Placement(
        position = state.position,
        rotation = state.orientation,
        linearVelocity = linear,
        angularVelocity = angular,
      ),
    )
  }

  /**
   * The dice of one throw leaving the hand, one after another: each when its
   * [delayOf] has passed since the hand let go.
   *
   * A hand that shakes hard again before its last dice have left it has them
   * back: they are shaken, and tossed when it lets go again.
   */
  class Handful(
    private val world: PhysicsWorld,
    private val count: Int,
    private val seed: Long,
    private val tray: TableGeometry? = null,
  ) {
    private var release: Release? = null
    private var letGoAt: Int = 0
    private val toToss: MutableSet<Int> = mutableSetOf()

    /** The hand let go at [step], the way [release] says. */
    fun letGo(
      release: Release,
      step: Int,
    ) {
      this.release = release
      letGoAt = step
      toToss.clear()
      toToss.addAll(0 until count)
    }

    /** Tosses, at [step], the dice whose turn has come, from where [states] say they are. */
    fun step(
      step: Int,
      states: List<DieState>,
      moving: List<Vector3>,
      handOn: Boolean,
    ) {
      val letting = release ?: return
      if (handOn) {
        toToss.clear()
        return
      }
      val due = toToss.filter { delayOf(seed, it) <= step - letGoAt }
      due.forEach { index ->
        one(world, index, count, states[index], moving.getOrElse(index) { Vector3.Zero }, letting, seed, tray)
      }
      toToss.removeAll(due.toSet())
    }
  }

  /**
   * How hard a die may be tossed when it is one of [count]: the whole of it
   * for a handful, and less as the tray fills — a full tray has no floor for a
   * throw to cross, and twenty dice thrown one way pile against the far wall.
   * The same rule the grid throw follows ([SpawnLayout]), and as much a first
   * guess here.
   */
  fun taperFor(count: Int): Double {
    val full = (count.toDouble() / TableCapacity.MAX_DICE).coerceIn(0.0, 1.0)
    return 1.0 - (1.0 - SpawnLayout.CROWDED_SHARE) * full
  }

  /**
   * [heading] turned back from any wall of [tray] a die at [position] is
   * within [WALL_ZONE_MM] of and heading into: the part of it pointing at
   * that wall is reversed, the rest kept. The tray's long side runs along
   * `x` and its short side along `y` (`docs/physics-and-rendering.md`,
   * "Coordinates").
   */
  fun offTheWalls(
    heading: Vector3,
    position: Vector3,
    tray: TableGeometry,
  ): Vector3 {
    val x = if (intoWall(position.x, heading.x, tray.longSideMm / 2 - WALL_ZONE_MM)) -heading.x else heading.x
    val y = if (intoWall(position.y, heading.y, tray.shortSideMm / 2 - WALL_ZONE_MM)) -heading.y else heading.y
    return Vector3(x, y, heading.z)
  }

  /** Whether something at [position] past [edge] from the middle is heading, at [heading], further out. */
  private fun intoWall(
    position: Double,
    heading: Double,
    edge: Double,
  ): Boolean = position * heading > 0 && abs(position) > edge

  /** The way a hand with no direction of its own throws: one for the whole throw, from the seed. */
  private fun aimless(seed: Long): Vector3 {
    val angle = Seeds.stream(seed, AIMLESS_INDEX, Seeds.RELEASE).nextDouble() * 2 * PI
    return Vector3(Exact.cos(angle), Exact.sin(angle), 0.0)
  }

  private fun turned(
    direction: Vector3,
    radians: Double,
  ): Vector3 {
    val cos = Exact.cos(radians)
    val sin = Exact.sin(radians)
    return Vector3(direction.x * cos - direction.y * sin, direction.x * sin + direction.y * cos, 0.0)
  }

  private fun randomDirection(random: Random): Vector3 {
    val z = random.nextDouble(-1.0, 1.0)
    val angle = random.nextDouble() * 2 * PI
    val ring = sqrt(1 - z * z)
    return Vector3(ring * Exact.cos(angle), ring * Exact.sin(angle), z)
  }

  /** How near a wall, centre to wall, a die counts as against it: about a die and a half. */
  const val WALL_ZONE_MM: Double = 30.0

  /** Slower than this along the floor, a die takes the stroke's direction rather than its own. */
  const val ONWARD_MM_PER_SECOND: Double = 100.0

  /** How far either side of the hand's direction a die may be thrown, in radians (about 17°). */
  const val AIM_SPREAD_RADIANS: Double = 0.3

  /** How much faster or slower than the hand a die may leave, as a share of it. */
  const val SPEED_SPREAD: Double = 0.15

  /**
   * How high a die is tossed, at the least and the most, in millimetres it
   * would rise to from where it was: enough to leave the felt and land as a
   * bounce, and well under the tray's 200 mm lid. First guesses, to be set
   * from the owner's recordings (`docs/TODO.md`, Step 5.8).
   */
  const val LEAST_LIFT_MM: Double = 15.0

  /**
   * And the most. Lowered from 40–90 mm when the owner saw the dice jump as
   * they left the hand: most of a toss goes along the floor, not up.
   */
  const val MOST_LIFT_MM: Double = 60.0

  /** Over how many steps a handful leaves the hand: a tenth of a second. */
  const val STAGGER_STEPS: Int = 12

  /** Mixed into the release stream, so the stagger is a stream of its own. */
  private const val STAGGER_SALT: Long = 0x53_54_41_47

  /** The stream the shared direction of an aimless throw is drawn from: no die's own. */
  private const val AIMLESS_INDEX: Int = -1
}
