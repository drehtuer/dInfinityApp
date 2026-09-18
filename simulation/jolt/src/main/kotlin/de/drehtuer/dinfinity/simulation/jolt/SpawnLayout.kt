package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Where the dice start, which is rung 1 of the correction ladder and the rung
 * that does the real work (`docs/physics-and-rendering.md`, "Avoiding stacked
 * and cocked dice").
 *
 * Dice that never land on each other never have to be nudged apart, so this is
 * where stacking is prevented rather than corrected. Two things do it: the
 * dice are spread over a grid of cells so no two start above the same spot,
 * and the cells are staggered in height so the ones that do drift together
 * arrive at different moments rather than in a heap.
 *
 * Everything is drawn from the roll's own seed, per die, so a roll replays to
 * itself and so adding a die to a throw does not silently re-roll the ones
 * before it.
 *
 * @param geometry the tray the dice have to land in.
 * @param dieRadiusMm the bounding radius of a die at the throw's scale, which
 *   is how much room each one needs.
 * @param seed the roll's seed.
 * @param among where the dice already at rest in this tray are, for the throw
 *   an explosion or a reroll adds. Empty for every other throw, which is what
 *   the grid above is for.
 */
@Suppress("TooManyFunctions")
class SpawnLayout(
  private val geometry: TableGeometry,
  private val dieRadiusMm: Double,
  private val seed: Long,
  private val among: List<Vector3> = emptyList(),
) {
  /** Where die [index] of [count] starts, and how hard it is thrown. */
  fun placementOf(
    index: Int,
    count: Int,
  ): Placement {
    require(index in 0 until count) { "die $index is not one of $count" }
    if (among.isNotEmpty()) return addedPlacement(index)
    val random = randomFor(index, Seeds.SPAWN)
    val grid = Grid.covering(count, geometry, dieRadiusMm)
    val cell = grid.cellOf(index)
    val lateral = lateralFor(count)

    return Placement(
      position =
        Vector3(
          x = cell.centreX + jitter(random, cell.jitterX),
          y = cell.centreY + jitter(random, cell.jitterY),
          z = dropHeightMm(cell) + random.nextDouble(0.0, HEIGHT_JITTER_MM),
        ),
      rotation = randomRotation(random),
      // Down and sideways: the throw. The sideways part is what makes a die
      // that lands on another slide off it while it still has speed to slide
      // with, and it is what turns a landing into a tumble ([lateralFor]).
      linearVelocity =
        Vector3(
          x = random.nextDouble(-lateral, lateral),
          y = random.nextDouble(-lateral, lateral),
          z = -random.nextDouble(THROW_DOWN_MIN_MM_PER_SECOND, THROW_DOWN_MAX_MM_PER_SECOND),
        ),
      angularVelocity = randomSpin(random, SPAWN_SPIN_RADIANS_PER_SECOND),
    )
  }

  /**
   * How hard a die may be thrown sideways when it is one of [count].
   *
   * **A handful is thrown; a hundred is tipped in.** A hard sideways throw is
   * what makes a die tumble rather than land and stick, but it needs floor to
   * tumble across. At the engine's cap the dice already fill the tray, and
   * throwing each of a hundred of them at a metre a second piles them against
   * a wall — a hundred coins, the flattest shape there is, stacked 28 deep
   * when this was a flat constant.
   *
   * The measure is the count against [TableCapacity.MAX_DICE], not the room
   * in a die's cell. Cell slack sounds like the better measure and is not:
   * twenty d20 have barely a third of a radius of slack each and throw
   * perfectly well, so tapering on slack throttled the ordinary roll to under
   * half speed — 1.69 turns after landing back to 1.43 — while a hundred
   * coins, whose slack is a rounding error, stayed on the wrong side of it
   * either way. What distinguishes the two cases is how many dice are in the
   * tray, so that is what this reads.
   */
  private fun lateralFor(count: Int): Double {
    val full = (count.toDouble() / TableCapacity.MAX_DICE).coerceIn(0.0, 1.0)
    return THROW_LATERAL_MM_PER_SECOND * (1.0 - (1.0 - CROWDED_SHARE) * full)
  }

  /** Where each die of an added round was put, filled in as they are asked for. */
  private val addedPoints = mutableListOf<Vector3>()

  /**
   * Where the die an explosion or a reroll added is dropped
   * (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll adds").
   *
   * Not the grid, because the grid deals the whole floor out to the throw and
   * this throw is one die arriving in a tray somebody has already rolled in.
   * It goes where [ClearSpace] says there is room — the point furthest from the
   * dice already down, and as central as that allows — and it is *dropped*
   * there rather than thrown across the tray, exactly as a re-thrown die is and
   * for the same reason: a die that travels is a die that arrives somewhere
   * nobody made room for. The dice already down are not in this throw's world
   * and cannot be moved by it; the clear space is so the *picture* is honest
   * too.
   *
   * A round may be several dice — three sixes in `8d6!` earn three throws — and
   * [addedPoint] is what stops them all landing on the same clear patch.
   */
  private fun addedPlacement(index: Int): Placement {
    val random = randomFor(index, Seeds.SPAWN)
    return Placement(
      position = addedPoint(index).copy(z = RETHROW_HEIGHT_MM + dieRadiusMm),
      rotation = randomRotation(random),
      linearVelocity = Vector3(0.0, 0.0, -RETHROW_DOWN_MM_PER_SECOND),
      angularVelocity = randomSpin(random, RETHROW_SPIN_RADIANS_PER_SECOND),
    )
  }

  /**
   * Where the [index]th die of an added round goes.
   *
   * **Each die makes room for the ones after it.** A round can be several dice
   * — three sixes in `8d6!` earn three throws and a player throws them
   * together — and asking [ClearSpace] the same question three times gives the
   * same answer three times, which would drop all three on one patch of floor.
   * So a die that has been placed counts as taken for the next one.
   *
   * Computed in order and kept, so asking for the third first still fills the
   * first two the same way: the answer for a die must not depend on which
   * order the caller happened to ask.
   */
  private fun addedPoint(index: Int): Vector3 {
    while (addedPoints.size <= index) {
      addedPoints +=
        requireNotNull(ClearSpace.clearestPoint(geometry, dieRadiusMm, among + addedPoints)) {
          "there is nowhere left in the tray to drop a die of ${dieRadiusMm * 2} mm"
        }
    }
    return addedPoints[index]
  }

  /**
   * Where die [index] restarts when it has to be thrown again — rung 3.
   *
   * Lower and gentler than the first throw, because that is what a player
   * does: a cocked die is picked up and dropped back on the table, not hurled
   * at it. [attempt] is mixed into the seed so a die that comes up cocked
   * twice is not thrown the same way twice.
   */
  fun rethrowPlacement(
    index: Int,
    attempt: Int,
  ): Placement {
    val random = randomFor(index, Seeds.RETHROW + attempt)
    val halfLong = geometry.longSideMm / 2 - marginMm()
    val halfShort = geometry.shortSideMm / 2 - marginMm()
    return Placement(
      position =
        Vector3(
          x = random.nextDouble(-halfLong, halfLong),
          y = random.nextDouble(-halfShort, halfShort),
          z = RETHROW_HEIGHT_MM + dieRadiusMm,
        ),
      rotation = randomRotation(random),
      linearVelocity = Vector3(0.0, 0.0, -RETHROW_DOWN_MM_PER_SECOND),
      angularVelocity = randomSpin(random, RETHROW_SPIN_RADIANS_PER_SECOND),
    )
  }

  /**
   * A nudge of up to [amount] either way.
   *
   * At the capacity limit a cell is narrower than the die that goes in it and
   * [amount] is zero: the dice then sit on the grid exactly, which is the
   * tightest packing the tray has and the reason the height bands exist.
   *
   * It draws its number and *then* scales it, rather than skipping the draw
   * when there is no room to move. Skipping would shift every later draw for
   * that die, so the same die in the same seed would be thrown differently for
   * no reason but how many dice were beside it.
   */
  private fun jitter(
    random: Random,
    amount: Double,
  ): Double = random.nextDouble(-1.0, 1.0) * amount

  /** How far a die's centre must stay from a wall to start inside the tray. */
  private fun marginMm(): Double = dieRadiusMm + WALL_CLEARANCE_MM

  private fun dropHeightMm(cell: Cell): Double =
    DROP_HEIGHT_MM + dieRadiusMm + cell.layer * (2 * dieRadiusMm + LAYER_GAP_MM)

  /**
   * A stream of its own per die and per purpose, so one die's numbers never
   * depend on how many dice were drawn before it.
   *
   * Through [Seeds], which is where every random number in a roll comes from
   * and which stirs the seed before it becomes a generator — two rolls whose
   * seeds differ by one are otherwise not two independent throws.
   */
  private fun randomFor(
    index: Int,
    salt: Long,
  ): Random = Seeds.stream(seed, index, salt)

  private fun randomRotation(random: Random): Quaternion {
    // Shoemake's method: three uniform numbers to a quaternion that is uniform
    // over all orientations. Picking three Euler angles instead would bunch
    // the dice around the poles, and a throw that favours some orientations is
    // a throw that favours some faces.
    val u1 = random.nextDouble()
    val u2 = random.nextDouble() * 2 * PI
    val u3 = random.nextDouble() * 2 * PI
    val root = sqrt(1 - u1)
    val rootComplement = sqrt(u1)
    return Quaternion(
      w = rootComplement * Exact.cos(u3),
      x = root * Exact.sin(u2),
      y = root * Exact.cos(u2),
      z = rootComplement * Exact.sin(u3),
    )
  }

  /**
   * A spin in a direction drawn evenly over the sphere, at a magnitude that is
   * never small.
   *
   * Three independent components would have been easier and wrong twice over:
   * the direction would bunch towards the corners of a cube, and the magnitude
   * would sometimes come out near zero — a die let go with no spin lands from
   * the orientation it started in, which is not a roll
   * (`docs/physics-and-rendering.md`, "Starting a roll").
   */
  private fun randomSpin(
    random: Random,
    maximum: Double,
  ): Vector3 = randomDirection(random) * random.nextDouble(maximum * SPIN_FLOOR_SHARE, maximum)

  private fun randomDirection(random: Random): Vector3 {
    val z = random.nextDouble(-1.0, 1.0)
    val angle = random.nextDouble() * 2 * PI
    val ring = sqrt(1 - z * z)
    return Vector3(ring * Exact.cos(angle), ring * Exact.sin(angle), z)
  }

  /** One die's share of the floor, and which height band it starts in. */
  internal data class Cell(
    val centreX: Double,
    val centreY: Double,
    val jitterX: Double,
    val jitterY: Double,
    val layer: Int,
  )

  /**
   * The grid the dice are dealt onto: as many roughly square cells as the tray
   * can be cut into for this many dice.
   */
  internal data class Grid(
    val columns: Int,
    val rows: Int,
    val cellWidth: Double,
    val cellHeight: Double,
    val originX: Double,
    val originY: Double,
    val radiusMm: Double,
  ) {
    fun cellOf(index: Int): Cell {
      val column = index % columns
      val row = index / columns
      return Cell(
        centreX = originX + (column + HALF) * cellWidth,
        centreY = originY + (row + HALF) * cellHeight,
        jitterX = max(0.0, cellWidth / 2 - radiusMm),
        jitterY = max(0.0, cellHeight / 2 - radiusMm),
        // Two cells that touch are never in the same height band, so a die
        // that drifts into its neighbour's column arrives after its neighbour
        // has already bounced away rather than on top of it.
        layer = (column + row) % HEIGHT_BANDS,
      )
    }

    companion object {
      fun covering(
        count: Int,
        geometry: TableGeometry,
        radiusMm: Double,
      ): Grid {
        val margin = radiusMm + WALL_CLEARANCE_MM
        val usableLong = max(2 * radiusMm, geometry.longSideMm - 2 * margin)
        val usableShort = max(2 * radiusMm, geometry.shortSideMm - 2 * margin)
        // Cells as square as the tray allows: the aspect ratio decides how the
        // count is split between the two axes, so a long thin tray gets a long
        // thin grid rather than a square one with empty ends.
        val columns =
          ceil(sqrt(count * usableLong / usableShort)).toInt().coerceIn(1, count)
        val rows = ceil(count.toDouble() / columns).toInt().coerceAtLeast(1)
        return Grid(
          columns = columns,
          rows = rows,
          cellWidth = usableLong / columns,
          cellHeight = usableShort / rows,
          originX = -usableLong / 2,
          originY = -usableShort / 2,
          radiusMm = radiusMm,
        )
      }
    }
  }

  companion object {
    /**
     * How far above the tray floor the lowest band of dice is let go.
     *
     * Raised from 40 mm, which gave a die about 90 ms of air — less than half
     * a turn at the spin it was thrown with, so it arrived barely rotated and
     * the felt took the rest. Measured on the Pixel 10a as part of the change
     * that took the middle die from 0.89 turns after landing to 1.69
     * (`Tumble`, `docs/physics-and-rendering.md`).
     */
    const val DROP_HEIGHT_MM: Double = 60.0

    /**
     * And how much higher each band above it is — enough that two dice in
     * neighbouring bands cannot overlap even after [HEIGHT_JITTER_MM] has
     * pushed one up and the other down.
     */
    const val LAYER_GAP_MM: Double = 12.0

    /** How many height bands the grid cycles through. */
    const val HEIGHT_BANDS: Int = 3

    /** A little more randomness in the drop, so bands are not perfectly level. */
    const val HEIGHT_JITTER_MM: Double = 8.0

    /** How far a die's surface starts from a wall. */
    const val WALL_CLEARANCE_MM: Double = 2.0

    /** The slowest a die is thrown downwards. */
    const val THROW_DOWN_MIN_MM_PER_SECOND: Double = 300.0

    /** And the fastest. */
    const val THROW_DOWN_MAX_MM_PER_SECOND: Double = 700.0

    /**
     * How much sideways a die carries out of the hand.
     *
     * **This is the constant that decides whether dice roll.** At the 250 mm/s
     * it used to be, a die travelled about 34 mm before it landed — it came
     * down roughly where it was let go, with nothing to convert into
     * tumbling, which is what "they get stuck on the table" was. It is a
     * throw across the felt now, so a die reaches a wall and comes off it.
     */
    const val THROW_LATERAL_MM_PER_SECOND: Double = 1100.0

    /**
     * Enough spin that the starting orientation tells you nothing.
     *
     * Well under the body's own 200 rad/s cap, so this is a choice rather
     * than a ceiling being met. Raised with [THROW_LATERAL_MM_PER_SECOND]: on
     * its own more spin is mostly spent in the air, and it is the pair that
     * moves the figure that matters.
     */
    const val SPAWN_SPIN_RADIANS_PER_SECOND: Double = 75.0

    /** And a floor under it, so "random" never comes out as "barely turning". */
    const val SPIN_FLOOR_SHARE: Double = 0.5

    /**
     * What share of the sideways throw a die gets when the tray is as full as
     * the capacity rule allows ([lateralFor]).
     */
    const val CROWDED_SHARE: Double = 0.25

    /** A re-thrown die is dropped from here, where the player can see it. */
    const val RETHROW_HEIGHT_MM: Double = 25.0

    /** Dropped, not hurled. */
    const val RETHROW_DOWN_MM_PER_SECOND: Double = 200.0

    /** Still enough spin to be a throw rather than a placement. */
    const val RETHROW_SPIN_RADIANS_PER_SECOND: Double = 18.0

    private const val HALF = 0.5
  }
}
