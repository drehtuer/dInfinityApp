package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.DiceSimulator
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * [DiceSimulator] over Jolt: the implementation the app actually rolls with.
 *
 * It builds a world for the throw, spawns the dice where [SpawnLayout] says,
 * and hands the whole thing to [RollLoop], which is where every rule lives.
 * This class is the wiring and nothing else.
 *
 * The same instance serves normal and power-saving mode. There is no second
 * path: power-saving is this, stepped as fast as the processor allows with
 * nobody watching, which is the only way "the same seed gives the same result
 * with the renderer on and off" can be true rather than maintained
 * (`docs/architecture.md`, goal 1).
 *
 * @param worlds how a world is opened. The default is Jolt; a test hands in
 *   its own and gets the same loop over a world it controls.
 */
class JoltDiceSimulator(
  private val worlds: WorldFactory = JoltWorldFactory,
) : DiceSimulator {
  override fun run(spec: ThrowSpec): SimulationOutcome {
    if (spec.dice.isEmpty()) return SimulationOutcome(faces = emptyMap())

    // There is no fallback and there deliberately is not one: the physics
    // result *is* the roll, so a bridge that will not open has to stop the roll
    // rather than quietly produce a number some other way
    // (`docs/architecture.md`, goal 1). Why it would not open is in logcat
    // under `dinfinity.jolt`.
    val world = worlds.open(spec) ?: error("the physics bridge would not open (libdinfinity_jolt)")

    return world.use {
      val layout = SpawnLayout(spec.geometry, largestRadiusMm(spec), spec.seed)
      spec.dice.forEachIndexed { index, instance ->
        world.addDie(
          hull = ShapeGeometry.hullOf(instance.die, spec.dieScale),
          material = instance.die.material,
          placement = layout.placementOf(index, spec.dice.size),
        )
      }
      world.finish()
      RollLoop(spec, world, layout, ShakeDriver(spec.shake)).run()
    }
  }

  /**
   * The grid is laid out for the biggest die in the throw, not for each die's
   * own size.
   *
   * A throw can mix a d4 and a d20 from different sets, and cells sized for
   * the d4 would put the d20 through its neighbour's cell wall before anything
   * had been thrown.
   */
  private fun largestRadiusMm(spec: ThrowSpec): Double =
    spec.dice.maxOf {
      ShapeGeometry.boundingRadiusPerSize(it.die.shape) * it.die.material.sizeMm * spec.dieScale
    }
}

/** Opens the world one throw runs in. The seam the tests come in through. */
fun interface WorldFactory {
  /** A world for [spec], or null when there is no engine to open one with. */
  fun open(spec: ThrowSpec): PhysicsWorld?
}
