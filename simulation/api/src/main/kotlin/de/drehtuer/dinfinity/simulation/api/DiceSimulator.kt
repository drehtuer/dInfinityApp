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
 * @param among the dice already at rest in this tray, for a throw an explosion
 *   or a reroll added. **None of them is in this throw's world**: their faces
 *   are read and they are finished, so there is no body for them to be shoved
 *   by. They are here for the two things outside the solver that still need
 *   them — the clear floor the new die is dropped onto ([ClearSpace]) and the
 *   picture it lands in.
 */
data class ThrowSpec(
  val dice: List<DieInstance>,
  val geometry: TableGeometry,
  val table: TableLook,
  val seed: Long,
  val dieScale: Double = 1.0,
  val shake: List<ShakeSample> = emptyList(),
  val among: List<DieAtRest> = emptyList(),
) {
  init {
    require(dieScale in TableCapacity.MIN_SCALE..1.0) {
      "a die is thrown between ${TableCapacity.MIN_SCALE} and full size, not $dieScale"
    }
    require(dice.size <= TableCapacity.MAX_DICE) { "${dice.size} dice is past the engine's cap" }
    // A round of added dice may be several — three sixes in `8d6!` earn three
    // throws, and a player throws them together — so this is no longer one die
    // at a time. What made it one was that two dice asked [ClearSpace] the same
    // question and were dropped onto the same patch of floor; the spawn now
    // stands each die of a round where the last one went before placing the
    // next (`SpawnLayout`), so they make room for each other.
    require(among.isEmpty() || dice.isNotEmpty()) {
      "a throw into a tray that already holds ${among.size} dice is a throw of at least one die"
    }
  }

  /**
   * How much room the biggest die in this throw needs, at this throw's scale.
   *
   * The grid is laid out for it rather than for each die's own size: a throw
   * can mix a d4 and a d20 from different sets, and cells sized for the d4
   * would put the d20 through its neighbour's cell wall before anything had
   * been thrown.
   */
  val largestDieRadiusMm: Double get() = dice.maxOf { ClearSpace.radiusOf(it.die, dieScale) }
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
) {
  /**
   * True when this moment names a step a roll could actually take.
   *
   * A hand goes on shaking for as long as it likes and the record of it does
   * not: the simulation takes fixed 1/120 s steps and is force-settled at
   * [SettleRule.HARD_CAP_STEPS], so a sample naming a later step has no step
   * to drive and never will. Keeping it would grow the record of a
   * thirty-second shake without bound and without adding anything a replay
   * could use.
   */
  val drivesAStep: Boolean get() = stepIndex in 0 until MAX_RECORDED

  companion object {
    /**
     * How many moments a throw's record can hold, at most.
     *
     * Not a number picked to feel safe: it is the twelve-second cap at the
     * simulation's own 120 Hz, which is every step a roll can possibly take,
     * and a step holds one sample. So the bound is "every moment that could
     * have shaped this throw, and nothing else" — 1,440 samples, about 50 kB,
     * for a roll that cannot last longer than twelve seconds however long the
     * hand does (`docs/physics-and-rendering.md`, "Shake input").
     */
    const val MAX_RECORDED: Int = SettleRule.HARD_CAP_STEPS
  }
}

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
 * @param stackedAtRest dice that came to rest standing on another die. The
 *   first failure the stacking ladder exists to prevent, and the target is
 *   zero (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked
 *   dice"). Counted at the end rather than judged during the roll, because a
 *   die standing on another *while it is still moving* is an ordinary moment
 *   of a throw and only the last one is a result.
 * @param deepestDiePenetrationMm how far one die was ever inside another,
 *   at any step of the roll. The solver resolves overlaps rather than
 *   forbidding them, so this is never exactly zero; what matters is that it
 *   stays small enough that nobody watching sees two solids share a corner
 *   (`docs/TODO.md`, Step 5.4). It comes from the engine's own contact
 *   manifolds, which is the only place it exists — nothing upstream can work
 *   it out from positions.
 * @param restingAt where each die stopped, keyed like [faces]. A roll whose
 *   formula explodes or rerolls is not over when its dice stop: the throw that
 *   comes next has to be aimed at the floor this one left clear, and drawn
 *   among the dice it left standing there
 *   (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll adds").
 * @param medianTurnsAfterLanding how far the middle die turned after it first
 *   touched the table, in whole turns. Settle time cannot tell a die that
 *   tumbled from one that landed flat and slid, and only one of those reads as
 *   a die being thrown, so this is the figure a throw is judged honest by
 *   ([Tumble]). A reading, never an input.
 */
data class SimulationOutcome(
  val faces: Map<Int, Int>,
  val steps: Int = 0,
  val corrections: Int = 0,
  val rethrows: Int = 0,
  val forcedSettles: Int = 0,
  val postRestCorrections: Int = 0,
  val stackedAtRest: Int = 0,
  val deepestDiePenetrationMm: Double = 0.0,
  val restingAt: Map<Int, RestingPlace> = emptyMap(),
  val medianTurnsAfterLanding: Double = 0.0,
) {
  /** How many dice were in the throw. */
  val diceCount: Int get() = faces.size

  /** True when the roll finished inside the cap with nothing forced. */
  val clean: Boolean get() = forcedSettles == 0 && postRestCorrections == 0

  init {
    require(steps <= SettleRule.HARD_CAP_STEPS) { "a roll cannot run past the ${SettleRule.HARD_CAP_SECONDS}s cap" }
    require(stackedAtRest <= faces.size) { "$stackedAtRest of ${faces.size} dice cannot be stacked" }
    require(deepestDiePenetrationMm >= 0.0) { "an overlap of $deepestDiePenetrationMm mm is not a depth" }
    require(medianTurnsAfterLanding >= 0.0) { "$medianTurnsAfterLanding turns is not an amount of turning" }
  }
}
