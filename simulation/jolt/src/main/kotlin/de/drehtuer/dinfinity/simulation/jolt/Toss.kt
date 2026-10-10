package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.Placement
import de.drehtuer.dinfinity.simulation.api.Release
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
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
 * Drawn from the throw's seed, per die, from a stream of its own
 * ([Seeds.RELEASE]), so the same record tosses the same way every time and a
 * throw recorded before there was a toss draws exactly the numbers it did.
 */
object Toss {
  /** The velocities die [index] of [count] leaves the hand with, linear in mm/s and angular in rad/s. */
  fun of(
    release: Release,
    seed: Long,
    index: Int,
    count: Int,
  ): Pair<Vector3, Vector3> {
    val random = Seeds.stream(seed, index, Seeds.RELEASE)
    val direction = release.direction ?: aimless(seed)
    val heading = turned(direction, random.nextDouble(-AIM_SPREAD_RADIANS, AIM_SPREAD_RADIANS))
    val speed = release.speedMmPerSecond * taperFor(count) * random.nextDouble(1 - SPEED_SPREAD, 1 + SPEED_SPREAD)
    val lift = sqrt(2 * ShakeDriver.GRAVITY_MM_PER_SECOND2 * random.nextDouble(LEAST_LIFT_MM, MOST_LIFT_MM))
    val spin =
      randomDirection(random) *
        random.nextDouble(SpawnLayout.SPAWN_SPIN_RADIANS_PER_SECOND / 2, SpawnLayout.SPAWN_SPIN_RADIANS_PER_SECOND)
    return Pair(heading * speed + Vector3(0.0, 0.0, lift), spin)
  }

  /**
   * Tosses every die in [world] from where [states] say it is, the way
   * [release] went (decision 103).
   *
   * A die lying still under a hand that is still shaking is a roll caught at
   * the top of a swing, not a die at rest, so every die is tossed. Its pose is
   * kept exactly — the toss changes how it moves and nothing about where it is.
   */
  fun from(
    world: PhysicsWorld,
    states: List<DieState>,
    release: Release,
    seed: Long,
  ) {
    states.forEachIndexed { index, state ->
      val (linear, angular) = of(release, seed, index, states.size)
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
  const val LEAST_LIFT_MM: Double = 40.0

  /** And the most. */
  const val MOST_LIFT_MM: Double = 90.0

  /** The stream the shared direction of an aimless throw is drawn from: no die's own. */
  private const val AIMLESS_INDEX: Int = -1
}
