package de.drehtuer.dinfinity.simulation.api

import kotlin.random.Random

/**
 * A [DiceSimulator] that does not simulate anything.
 *
 * It lives in the **test** source set and will never move out of it. The app's
 * first goal is that the physics result *is* the roll, with no RNG shortcut
 * deciding a die's value outside the simulation in any mode
 * (`docs/architecture.md`, goal 1; `.claude/CLAUDE.md`). A class that picks
 * faces out of a `Random` is precisely that shortcut, so the only safe place
 * for it is somewhere the app cannot link against.
 *
 * What it is for: everything above the physics — the capacity check, the
 * evaluator, the breakdown, the outcome graph's agreement with a roll — can be
 * tested to completion before `simulation/jolt` exists, and goes on being
 * testable without an emulator afterwards.
 *
 * It is seeded from the throw, so the same [ThrowSpec] gives the same faces:
 * the layers above are entitled to assume determinism and this has to keep
 * that promise even while being a fake.
 */
class FakeDiceSimulator(
  private val steps: Int = DEFAULT_STEPS,
  private val cocked: Set<Int> = emptySet(),
) : DiceSimulator {
  override fun run(spec: ThrowSpec): SimulationOutcome {
    val random = Random(spec.seed)
    val faces =
      spec.dice.associate { instance ->
        instance.index to random.nextInt(instance.die.faces.size)
      }
    return SimulationOutcome(
      faces = faces,
      steps = steps,
      rethrows = cocked.count { it in faces.keys },
    )
  }

  private companion object {
    /** About a second and a half of simulated time, which is a typical roll. */
    const val DEFAULT_STEPS = 180
  }
}
