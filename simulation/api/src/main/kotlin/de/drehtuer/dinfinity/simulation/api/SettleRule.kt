package de.drehtuer.dinfinity.simulation.api

/**
 * When a die has stopped, and when a roll has (`docs/physics-and-rendering.md`,
 * "Settling and reading the result").
 *
 * The thresholds are in simulated time, never wall time. That is what makes
 * power-saving mode honest: the simulation runs as fast as the processor
 * allows there and in lockstep with the frame clock here, and both have to
 * decide a die has stopped at exactly the same simulated moment or the same
 * seed would give two different rolls.
 */
object SettleRule {
  /** The fixed step the world is advanced by, always. */
  const val TIMESTEP_SECONDS: Double = 1.0 / 120.0

  /** Slower than this, in millimetres a second, counts as not moving. */
  const val REST_SPEED_MM_PER_SECOND: Double = 10.0

  /** And slower than this, in radians a second, counts as not turning. */
  const val REST_SPIN_RADIANS_PER_SECOND: Double = 0.05

  /** How long a die has to keep still before it is at rest rather than slow. */
  const val REST_DURATION_SECONDS: Double = 0.250

  /**
   * How long a roll may take before something watching it should give up.
   *
   * **It is not a settle rule any more.** It used to end a roll: every die
   * still moving was force-settled and read off whatever face it was nearest,
   * which is a fabricated answer to a roll that never finished. A roll now
   * runs until every die has been read and taken off the table, because that
   * is what makes the reading honest — and because a roll on screen is
   * interruptible, so it cannot hold anything up for ever.
   *
   * What is left of it is a **backstop for the runs nobody is watching**: the
   * headless harness throws thousands of rolls with no screen to walk away
   * from, and a roll that never settles there would hang the run rather than
   * fail it. Those give up at this and report that they did
   * (`docs/build-setup.md`, "The physics harness").
   */
  const val HARD_CAP_SECONDS: Double = 12.0

  /** Steps in one second of simulated time. */
  const val STEPS_PER_SECOND: Int = 120

  /** How many steps a die must be still for. */
  const val REST_STEPS: Int = 30

  /** How many steps the cap is. */
  const val HARD_CAP_STEPS: Int = 1_440

  /** True when a die this slow counts as having stopped moving. */
  fun isStill(motion: DieMotion): Boolean =
    motion.speedMmPerSecond < REST_SPEED_MM_PER_SECOND && motion.spinRadiansPerSecond < REST_SPIN_RADIANS_PER_SECOND

  /**
   * True while a die is slowing but not yet at rest — the only window in which
   * anything may touch it.
   *
   * The window opens at [WATCH_SPEED_MM_PER_SECOND], which is deliberately
   * well above the resting threshold: a bias applied here is of the order of
   * the energy the die still has, so it reads as the die finishing its tumble
   * rather than as a kick (`docs/physics-and-rendering.md`, rung 2).
   */
  fun isSettling(motion: DieMotion): Boolean = !isStill(motion) && motion.speedMmPerSecond < WATCH_SPEED_MM_PER_SECOND

  /** Where the watching window opens. */
  const val WATCH_SPEED_MM_PER_SECOND: Double = 120.0
}

/** How fast a die is going, which is all the settle rule needs to know about it. */
data class DieMotion(
  val speedMmPerSecond: Double,
  val spinRadiansPerSecond: Double,
) {
  companion object {
    /** A die that has stopped dead. */
    val Stopped: DieMotion = DieMotion(0.0, 0.0)
  }
}

/**
 * Counts how long each die has been still, in steps.
 *
 * Steps rather than seconds because a count of fixed steps is exact and a
 * running total of doubles is not, and two devices that disagree by one step
 * about when a die stopped would disagree about the roll.
 */
class RestTracker(
  private val diceCount: Int,
) {
  private val stillFor = IntArray(diceCount)
  private var steps = 0

  /** How many steps the roll has run for. */
  val stepsTaken: Int get() = steps

  /** Takes one step's worth of motion for every die, and says whether the roll is over. */
  fun step(motions: List<DieMotion>): Boolean {
    require(motions.size == diceCount) { "the roll has $diceCount dice, not ${motions.size}" }
    steps++
    motions.forEachIndexed { index, motion ->
      stillFor[index] = if (SettleRule.isStill(motion)) stillFor[index] + 1 else 0
    }
    return finished()
  }

  /** True when the die at [index] has been still long enough to be read. */
  fun isAtRest(index: Int): Boolean = stillFor[index] >= SettleRule.REST_STEPS

  /**
   * How many steps the die at [index] has been still for — its rest timer.
   *
   * Read by the debug overlay, which draws the timer filling
   * (`docs/physics-and-rendering.md`, "Debug tooling"). Reading it cannot
   * change it, which is the whole of why the overlay is allowed to exist.
   */
  fun stillSteps(index: Int): Int = stillFor[index]

  /**
   * Forgets that the die at [index] was ever still.
   *
   * A die that has been picked up and thrown again is not at rest, whatever it
   * was doing a moment ago (`docs/physics-and-rendering.md`, rung 3). Without
   * this the roll would count the re-thrown die as finished before its new
   * throw had taken a single step, and would stop with it in mid-air.
   */
  fun rethrown(index: Int) {
    stillFor[index] = 0
  }

  /**
   * True when every die is at rest.
   *
   * **And nothing else.** A roll used to finish when it ran out of time as
   * well, which meant a roll that had not settled still produced a result:
   * every die still moving was read off the face it happened to be nearest.
   * That is a made-up answer, and the one thing this app may not do is make a
   * number up (`.claude/CLAUDE.md`). A roll now ends when the dice have
   * stopped, and a roll nobody can finish is given up rather than answered.
   */
  fun finished(): Boolean = (0 until diceCount).all(::isAtRest)

  /** True when the roll has run longer than [SettleRule.HARD_CAP_SECONDS]. */
  fun outOfTime(): Boolean = steps >= SettleRule.HARD_CAP_STEPS

  /** The dice that are still moving. */
  fun stillMoving(): List<Int> = (0 until diceCount).filterNot(::isAtRest)
}
