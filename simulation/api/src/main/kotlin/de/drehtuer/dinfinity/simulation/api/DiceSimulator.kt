package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook

/**
 * Rolls dice. The one thing everything above the physics talks to.
 *
 * It is deliberately narrow and engine-agnostic: `docs/architecture.md` says
 * the physics engine is the choice most likely to change, and the point of
 * this interface is that swapping it is contained to one module. Filament, the
 * roll screen and power-saving mode all sit on this and none of them knows
 * what is underneath.
 *
 * **The result of running this is the roll.** There is no other path to a
 * number, in any mode (`docs/architecture.md`, goal 1). Rendering is a passive
 * observer of the same simulation; turning it off changes nothing about how a
 * roll is produced, which is what makes power-saving mode honest rather than a
 * second implementation that has to be kept in step.
 */
interface DiceSimulator {
  /**
   * Runs [spec] to completion and reports what the dice did.
   *
   * The same spec must produce the same outcome every time, on every device
   * and every ABI. That is not a nicety: it is what the golden determinism
   * suite asserts, what makes power-saving provably the same roll, and what
   * makes a bug report reproducible (`docs/physics-and-rendering.md`).
   */
  fun run(spec: ThrowSpec): SimulationOutcome
}

/**
 * Everything a throw needs, and nothing that could make it come out
 * differently twice.
 *
 * @param dice the dice to spawn, in throw order; their indices are what the
 *   outcome reports against.
 * @param dieScale how far down the capacity rule shrank them
 *   (`docs/tables.md`). Every die is spawned at the same scale.
 * @param seed the roll's own seed. Recorded with the result and never shown:
 *   determinism is for tests and bug reports, not a feature
 *   (`docs/architecture.md`, decision 13).
 * @param table the tray's physics. Power-saving ignores the *look* of the
 *   selected table but still uses its friction and restitution, so the roll is
 *   identical to what normal mode would have produced.
 * @param shake the recorded and quantised motion of the phone, or empty for a
 *   tap-to-roll throw (`docs/physics-and-rendering.md`, "Shake input").
 */
data class ThrowSpec(
  val dice: List<DieInstance>,
  val geometry: TableGeometry,
  val table: TableLook,
  val seed: Long,
  val dieScale: Double = 1.0,
  val shake: List<ShakeSample> = emptyList(),
) {
  init {
    require(dieScale in TableCapacity.MIN_SCALE..1.0) {
      "a die is thrown between ${TableCapacity.MIN_SCALE} and full size, not $dieScale"
    }
    require(dice.size <= TableCapacity.MAX_DICE) { "${dice.size} dice is past the engine's cap" }
  }
}

/**
 * One quantised moment of the phone's motion, as the sensors reported it.
 *
 * Quantised on purpose: raw sensor doubles would make a roll unreproducible
 * from its record, and a roll that cannot be replayed cannot be debugged
 * (`docs/physics-and-rendering.md`).
 *
 * @param stepIndex which simulation step this sample belongs to, so the same
 *   record always feeds the same steps.
 * @param accelerationMmPerSecond2 the phone's acceleration with gravity taken
 *   out, applied to the *tray* rather than to the dice — which is what makes a
 *   shake feel like a cupped hand rather than like random impulses.
 * @param gravity which way down is, after the gyroscope has turned it.
 */
data class ShakeSample(
  val stepIndex: Int,
  val accelerationMmPerSecond2: Vector3,
  val gravity: Vector3,
)

/**
 * What a throw came to.
 *
 * @param faces the index of the face each die came to rest on, keyed by its
 *   position in the throw. This is the roll.
 * @param steps how many fixed steps it took, which is simulated time and so is
 *   the same on every device.
 * @param corrections dice that needed a nudge while they were still moving.
 * @param rethrows dice that came to rest cocked or stacked and were thrown
 *   again, visibly. Honest, and counted: a number that climbs is a physics
 *   bug.
 * @param forcedSettles dice the simulation had to finish for rather than
 *   letting them finish: still moving when the 12-second cap fired, or still
 *   cocked after their last re-throw. Either way the ladder has already failed
 *   and [clean] is false; Step 5 asserts this stays zero.
 * @param postRestCorrections dice touched **after** they had come to rest.
 *   This must always be zero. It is reported rather than assumed so that the
 *   device harness can assert it, and one occurrence is a bug, not a statistic
 *   (`docs/TODO.md`, Step 5.5).
 */
data class SimulationOutcome(
  val faces: Map<Int, Int>,
  val steps: Int = 0,
  val corrections: Int = 0,
  val rethrows: Int = 0,
  val forcedSettles: Int = 0,
  val postRestCorrections: Int = 0,
) {
  /** How many dice were in the throw. */
  val diceCount: Int get() = faces.size

  /** True when the roll finished inside the cap with nothing forced. */
  val clean: Boolean get() = forcedSettles == 0 && postRestCorrections == 0

  init {
    require(steps <= SettleRule.HARD_CAP_STEPS) { "a roll cannot run past the ${SettleRule.HARD_CAP_SECONDS}s cap" }
  }
}
