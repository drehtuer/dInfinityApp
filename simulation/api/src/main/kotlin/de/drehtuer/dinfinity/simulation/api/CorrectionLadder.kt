package de.drehtuer.dinfinity.simulation.api

import kotlin.random.Random

/**
 * The rule that nothing touches a die which has come to rest, expressed as
 * something a physics bridge can ask rather than as a paragraph it might
 * remember (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked
 * dice").
 *
 * There are two ways to get stacked dice wrong and the second is worse than
 * the first. A die left resting on another has no face to read and nobody
 * believes it. A die *shoved by an invisible hand after everything has
 * stopped* destroys the whole point of the app: if the player can see the app
 * move a die, the result was not rolled, it was arranged.
 *
 * So the ladder is:
 *
 * 1. **Prevention** — spawn spread, dice-on-dice friction below
 *    dice-on-floor, enough throw energy, and the capacity rule keeping free
 *    floor. This is where the work goes; everything below only catches what
 *    slips through.
 * 2. **A bias while the die is still moving**, of the order of the energy it
 *    still has, so it reads as the die finishing its tumble. Seeded from the
 *    roll, so the roll stays reproducible.
 * 3. **A visible re-throw** of that one die, from a low height, while the
 *    others stay where they are. That is what a player does with a cocked die
 *    at a real table, and it is fair.
 * 4. **Never** an impulse on a resting die, a tray tilt to slide a settled
 *    pile, or a snap to the nearest face.
 *
 * [mayTouch] is rung 4 made mechanical. Every correction goes through it, and
 * a correction that reaches a die at rest is a bug the harness counts
 * (`docs/TODO.md`, Step 5: **zero** post-rest corrections).
 */
object CorrectionLadder {
  /** How much of a die's remaining speed a bias may be. Small: it is a nudge in the tumble. */
  const val BIAS_SHARE_OF_SPEED: Double = 0.15

  /** How many dice may need any correction at all before the tuning is wrong. */
  const val CORRECTION_BUDGET: Double = 0.005

  /** How many may need the last resort. */
  const val RETHROW_BUDGET: Double = 0.0005

  /**
   * Whether this die may be touched at all.
   *
   * True only while it is slowing and has not yet come to rest. This is the
   * single gate: a bridge that asks it cannot apply a post-rest correction
   * even by accident, and one that does not ask it is not using this module.
   */
  fun mayTouch(
    motion: DieMotion,
    atRest: Boolean,
  ): Boolean = !atRest && SettleRule.isSettling(motion)

  /**
   * The nudge for a die that is settling into trouble, derived from the roll's
   * own seed so the same roll always produces the same one.
   *
   * Its size is a share of the speed the die still has, so a die that has
   * nearly stopped gets nearly nothing — which is the difference between a
   * tumble finishing and a kick.
   */
  fun bias(
    seed: Long,
    dieIndex: Int,
    step: Int,
    motion: DieMotion,
  ): Vector3 {
    val random = Random(seed xor (dieIndex.toLong() shl SEED_DIE_SHIFT) xor step.toLong())
    val size = motion.speedMmPerSecond * BIAS_SHARE_OF_SPEED
    val direction =
      Vector3(
        random.nextDouble(-1.0, 1.0),
        random.nextDouble(-1.0, 1.0),
        random.nextDouble(0.0, 1.0),
      )
    return direction.normalised() * size
  }

  /** True when a roll needed more correcting than the tuning is supposed to allow. */
  fun withinBudget(outcome: SimulationOutcome): Boolean {
    if (outcome.diceCount == 0) return true
    val corrected = outcome.corrections.toDouble() / outcome.diceCount
    val rethrown = outcome.rethrows.toDouble() / outcome.diceCount
    return corrected <= CORRECTION_BUDGET && rethrown <= RETHROW_BUDGET
  }

  private const val SEED_DIE_SHIFT = 32
}
