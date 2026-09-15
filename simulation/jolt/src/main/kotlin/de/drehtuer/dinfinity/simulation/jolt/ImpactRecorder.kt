package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.ImpactRule

/**
 * Watches a roll go past and writes down where the dice hit something
 * (`docs/physics-and-rendering.md`, "Impacts").
 *
 * It is the whole of the impact detection, it is plain Kotlin over the states
 * [PhysicsWorld] already reports, and it decides nothing about the roll — which
 * is decision 40 applied to one more question that looks like physics and is
 * not (`docs/architecture.md`). A test hands it two steps of a die and asserts
 * what it heard; no engine, no phone, no microphone.
 *
 * **It cannot change the roll, by construction.** It is handed states and holds
 * numbers of its own; there is no reference to a world in it and no method on it
 * that a loop would call for an answer. `ImpactRecorderTest` asserts the outcome
 * of a seed is the same whether one of these is listening or [deaf].
 *
 * Cheap on purpose: per die per step it is one subtraction, one absolute value
 * and two comparisons. At 120 Hz with a hundred bodies that is about a hundred
 * and forty thousand of each over a whole twelve-second roll, which is nothing
 * beside one step of the solver.
 *
 * @param dieSizesMm how wide each die is at the scale it was thrown, in throw
 *   order. Kept rather than looked up, so an impact carries everything a player
 *   of it needs.
 * @param listening false when nothing is going to play them, which is what both
 *   feedback settings being off means. Then this costs one branch per step and
 *   the record stays empty — a setting that saves something real rather than
 *   one that mutes what was measured anyway.
 */
class ImpactRecorder(
  private val dieSizesMm: List<Double>,
  private val listening: Boolean = true,
) {
  private val previousSpeed = DoubleArray(dieSizesMm.size) { UNKNOWN }
  private val quietUntilStep = IntArray(dieSizesMm.size)
  private val heard = ArrayList<Impact>()

  /** Everything this roll has hit so far, in step order. */
  fun recorded(): List<Impact> = heard

  /**
   * One step has been taken: here is where every die is now, and how hard down
   * was pulling while it was taken.
   *
   * Called once per step with the states the loop has just read, so the states
   * are read once and looked at twice rather than read twice.
   *
   * @param gravityMmPerSecond2 the magnitude of the gravity the step was taken
   *   under — plain gravity, or gravity plus whatever the hand was doing
   *   ([ShakeDriver]). It is what the change in a die's speed is forgiven
   *   against ([ImpactRule.unexplained]).
   */
  fun step(
    stepIndex: Int,
    states: List<DieState>,
    gravityMmPerSecond2: Double,
  ) {
    if (!listening) return
    states.forEachIndexed { index, state ->
      val speed = state.motion.speedMmPerSecond
      val before = previousSpeed[index]
      previousSpeed[index] = speed
      // The first step a die is seen on has nothing to compare against, and a
      // die that has just been thrown again has a speed that came from an
      // invisible hand rather than from a contact.
      if (before == UNKNOWN || stepIndex < quietUntilStep[index]) return@forEachIndexed

      val change = ImpactRule.unexplained(before, speed, gravityMmPerSecond2)
      if (!ImpactRule.isImpact(change)) return@forEachIndexed

      quietUntilStep[index] = stepIndex + ImpactRule.QUIET_STEPS
      if (heard.size >= Impact.MAX_RECORDED) return@forEachIndexed
      heard +=
        Impact(
          stepIndex = stepIndex,
          dieIndex = index,
          struck = ImpactRule.struckBy(state.touchingFloor, state.touchingWall, state.supportedByDie),
          speedChangeMmPerSecond = change,
          dieSizeMm = dieSizesMm[index],
        )
    }
  }

  /**
   * Die [index] has been picked up and thrown again (rung 3).
   *
   * Its speed between one step and the next is then the re-throw rather than a
   * contact, and reporting that would be the app playing a sound for its own
   * invisible hand. The die is simply unknown again, and the landing that
   * follows is heard like any other.
   */
  fun rethrown(index: Int) {
    if (!listening) return
    previousSpeed[index] = UNKNOWN
  }

  companion object {
    /**
     * A die with no previous step to compare against.
     *
     * Negative, which a speed never is, so it cannot be confused with one a
     * world reported. `NaN` would do the same job and would silently swallow
     * every comparison written against it later.
     */
    private const val UNKNOWN: Double = -1.0

    /** Records nothing, for a roll nothing is going to play. */
    fun deaf(dieCount: Int): ImpactRecorder = ImpactRecorder(List(dieCount) { 1.0 }, listening = false)
  }
}
