package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.RollPlan
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Whether a roll fits on the table, and how small its dice have to be to
 * (`docs/tables.md`, "Capacity rule").
 *
 * This runs **before a single physics body is created**. That is the whole
 * point: hundreds of convex bodies packed into a small box tunnel, jitter and
 * explode, and the result would not be a roll of the dice, it would be a bug.
 * The outcome graph already answers "what does 500d6 look like"; the table
 * answers "what did *these* dice do", and that only means something when they
 * had room to do it.
 *
 * The rule is: dice may collectively cover at most [FLOOR_SHARE] of the floor
 * with their bounding circles, and may shrink to [MIN_SCALE] of their nominal
 * size to get there. Both are tunable constants and both are pinned by tests.
 */
object TableCapacity {
  /** How much of the floor the dice's bounding circles may cover between them. */
  const val FLOOR_SHARE: Double = 0.30

  /** How small a die may be shrunk before the roll is refused instead. */
  const val MIN_SCALE: Double = 0.40

  /** Bodies the engine will take at all, whatever the scale. */
  const val MAX_DICE: Int = 100

  /** Whether [plan] fits on [table], and at what scale. */
  fun check(
    plan: RollPlan,
    table: TableGeometry,
  ): CapacityVerdict = check(plan.dice.map { it.die }, table, plan.capacityDiceCount)

  /**
   * The same, for a bare list of dice.
   *
   * @param diceCount how many dice the table has to make room for, which is
   *   not always [dice]`.size`: an exploding group could add as many again in
   *   a second throw into the same tray (`docs/dice-notation.md`).
   */
  fun check(
    dice: List<Die>,
    table: TableGeometry,
    diceCount: Int = dice.size,
  ): CapacityVerdict {
    if (dice.isEmpty()) return CapacityVerdict.Fits(scale = 1.0, diceCount = 0)
    val required = dice.sumOf(::footprintMm2) * diceCount / dice.size
    val scale = scaleFor(required, table)
    val largest = largestThatFits(dice, table)
    return when {
      diceCount > MAX_DICE -> CapacityVerdict.Refused(diceCount, minOf(largest, MAX_DICE))
      scale < MIN_SCALE -> CapacityVerdict.Refused(diceCount, largest)
      else -> CapacityVerdict.Fits(scale = scale, diceCount = diceCount)
    }
  }

  /** How much floor one die covers at scale 1: the circle its bounding sphere casts. */
  fun footprintMm2(die: Die): Double {
    val radius = die.material.boundingRadiusMm
    return PI * radius * radius
  }

  /** The scale at which [requiredMm2] of dice would cover exactly the allowed share. */
  private fun scaleFor(
    requiredMm2: Double,
    table: TableGeometry,
  ): Double {
    if (requiredMm2 <= 0.0) return 1.0
    return minOf(1.0, sqrt(FLOOR_SHARE * table.floorAreaMm2 / requiredMm2))
  }

  /**
   * The largest count of these dice that would still fit — the number the
   * refusal message quotes, because "500 dice don't fit" is no use without
   * "up to 80 do" (`docs/tables.md`).
   */
  private fun largestThatFits(
    dice: List<Die>,
    table: TableGeometry,
  ): Int {
    val perDie = dice.sumOf(::footprintMm2) / dice.size
    if (perDie <= 0.0) return MAX_DICE
    val room = FLOOR_SHARE * table.floorAreaMm2 / (MIN_SCALE * MIN_SCALE * perDie)
    return floor(room).toInt().coerceIn(0, MAX_DICE)
  }
}

/** What [TableCapacity] decided. */
sealed interface CapacityVerdict {
  /**
   * The roll happens, with every die spawned at [scale].
   *
   * Smaller dice that roll honestly beat big dice that jam
   * (`docs/physics-and-rendering.md`).
   */
  data class Fits(
    val scale: Double,
    val diceCount: Int,
  ) : CapacityVerdict

  /**
   * The roll does not happen, and no body was ever created.
   *
   * @param largestThatFits what the message offers instead. The screen also
   *   offers the outcome graph, which has no table to fit on.
   */
  data class Refused(
    val diceCount: Int,
    val largestThatFits: Int,
  ) : CapacityVerdict {
    /** "100 dice don't fit on the table; up to 80 do" */
    val reason: String
      get() = "$diceCount dice don't fit on the table; up to $largestThatFits do"
  }
}
