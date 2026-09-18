package de.drehtuer.dinfinity.simulation.api

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min

/**
 * How far a die turns once it is on the table, in whole turns.
 *
 * A roll is only honest if the dice *roll*. Settle time does not say whether
 * they did: a die that lands flat and slides to a halt takes just as long as
 * one that tumbles corner over corner, and the first reads as a number being
 * placed on the felt rather than thrown onto it. This counts the second kind.
 *
 * **It measures after the first touch, not from the throw.** The spin a die is
 * given in the air is a constant somebody chose, so counting it would be
 * marking our own homework; what nobody can set directly is how much of that
 * survives the landing. A die that arrives, grips and stops turns a few tenths
 * of a turn as it topples onto a face. A die that tumbles turns whole ones.
 *
 * **It is a reading and never an input**, the same promise [RollDiagnostics]
 * makes: nothing here reaches the solver, so the same seed comes to the same
 * faces whether or not anybody is counting turns
 * (`docs/architecture.md`, decision 38).
 *
 * A die thrown again starts again — its first throw's turns are not a fact
 * about the throw the player ends up reading.
 */
class Tumble(
  private val dieCount: Int,
) {
  private val turnedRadians = DoubleArray(dieCount)
  private val lastSeen = arrayOfNulls<Quaternion>(dieCount)
  private val hasTouched = BooleanArray(dieCount)
  private val frozen = BooleanArray(dieCount)

  /**
   * Takes one step's reading of one die.
   *
   * [touching] is what makes the difference between the two kinds of roll, so
   * it is the caller's business to say what counts as touching — the floor, a
   * wall, or another die. Once a die has touched anything it keeps counting:
   * a die that bounces back into the air is still a die that is rolling.
   */
  fun step(
    index: Int,
    orientation: Quaternion,
    touching: Boolean,
  ) {
    if (index !in 0 until dieCount || frozen[index]) return
    if (touching) hasTouched[index] = true
    val previous = lastSeen[index]
    lastSeen[index] = orientation
    if (!hasTouched[index] || previous == null) return
    turnedRadians[index] += angleBetween(previous, orientation)
  }

  /**
   * Stops counting this die: it has been read and taken off the table.
   *
   * Without this a counted die would go on contributing zeroes for as long as
   * the rest of the roll lasted, which would say the throw tumbled less the
   * longer its slowest die took.
   */
  fun settled(index: Int) {
    if (index in 0 until dieCount) frozen[index] = true
  }

  /** Starts this die again, because it is being thrown again. */
  fun rethrown(index: Int) {
    if (index !in 0 until dieCount) return
    turnedRadians[index] = 0.0
    lastSeen[index] = null
    hasTouched[index] = false
    frozen[index] = false
  }

  /** How far one die turned after it first touched anything, in whole turns. */
  fun turnsOf(index: Int): Double = if (index in 0 until dieCount) turnedRadians[index] / FULL_TURN else 0.0

  /**
   * The middle die's turns, which is what a target is set against.
   *
   * The median rather than the mean, because one die that skitters the length
   * of the tray should not be able to say a throw tumbled when the other
   * nineteen dropped dead.
   */
  val medianTurns: Double
    get() {
      if (dieCount == 0) return 0.0
      val sorted = (0 until dieCount).map(::turnsOf).sorted()
      val middle = sorted.size / 2
      return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

  companion object {
    private const val FULL_TURN: Double = 2.0 * Math.PI

    /**
     * The angle between two orientations, in radians, the short way round.
     *
     * A rotation has two quaternions, `q` and `-q`, so the absolute value is
     * what keeps a die that the solver happened to write down the other way
     * up from registering as half a turn it never made.
     */
    fun angleBetween(
      from: Quaternion,
      to: Quaternion,
    ): Double {
      val together = abs(from.normalised() dot to.normalised())
      return 2.0 * acos(min(1.0, together))
    }
  }
}
