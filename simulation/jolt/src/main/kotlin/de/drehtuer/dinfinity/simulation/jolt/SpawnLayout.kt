package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.Quaternion
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
 */
class SpawnLayout(
  private val geometry: TableGeometry,
  private val dieRadiusMm: Double,
  private val seed: Long,
) {
  /** Where die [index] of [count] starts, and how hard it is thrown. */
  fun placementOf(
    index: Int,
    count: Int,
  ): Placement {
    require(index in 0 until count) { "die $index is not one of $count" }
    val random = randomFor(index, SPAWN_SALT)
    val grid = Grid.covering(count, geometry, dieRadiusMm)
    val cell = grid.cellOf(index)

    return Placement(
      position =
        Vector3(
          x = cell.centreX + jitter(random, cell.jitterX),
          y = cell.centreY + jitter(random, cell.jitterY),
          z = dropHeightMm(cell) + random.nextDouble(0.0, HEIGHT_JITTER_MM),
        ),
      rotation = randomRotation(random),
      // Down and sideways: the "drop from the hand" throw. The sideways part
      // is what makes a die that lands on another slide off it while it still
      // has speed to slide with.
      linearVelocity =
        Vector3(
          x = random.nextDouble(-THROW_LATERAL_MM_PER_SECOND, THROW_LATERAL_MM_PER_SECOND),
          y = random.nextDouble(-THROW_LATERAL_MM_PER_SECOND, THROW_LATERAL_MM_PER_SECOND),
          z = -random.nextDouble(THROW_DOWN_MIN_MM_PER_SECOND, THROW_DOWN_MAX_MM_PER_SECOND),
        ),
      angularVelocity = randomSpin(random, SPAWN_SPIN_RADIANS_PER_SECOND),
    )
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
    val random = randomFor(index, RETHROW_SALT + attempt)
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
   * depend on how many dice were drawn before it. The shift is the same one
   * [de.drehtuer.dinfinity.simulation.api.CorrectionLadder] uses, for the same
   * reason.
   */
  private fun randomFor(
    index: Int,
    salt: Long,
  ): Random = Random(seed xor (index.toLong() shl SEED_DIE_SHIFT) xor salt)

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
    /** How far above the tray floor the lowest band of dice is let go. */
    const val DROP_HEIGHT_MM: Double = 40.0

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

    /** How much sideways a die carries out of the hand. */
    const val THROW_LATERAL_MM_PER_SECOND: Double = 250.0

    /** Enough spin that the starting orientation tells you nothing. */
    const val SPAWN_SPIN_RADIANS_PER_SECOND: Double = 30.0

    /** And a floor under it, so "random" never comes out as "barely turning". */
    const val SPIN_FLOOR_SHARE: Double = 0.5

    /** A re-thrown die is dropped from here, where the player can see it. */
    const val RETHROW_HEIGHT_MM: Double = 25.0

    /** Dropped, not hurled. */
    const val RETHROW_DOWN_MM_PER_SECOND: Double = 200.0

    /** Still enough spin to be a throw rather than a placement. */
    const val RETHROW_SPIN_RADIANS_PER_SECOND: Double = 18.0

    private const val SEED_DIE_SHIFT = 32

    // "SPAWN" and "RETHRO" in ASCII: any two distinct numbers would do, and
    // ones that read as words are ones nobody later mistakes for a tuning
    // constant.
    private const val SPAWN_SALT: Long = 0x53_50_41_57_4E
    private const val RETHROW_SALT: Long = 0x52_45_54_48_52_4F
    private const val HALF = 0.5
  }
}
