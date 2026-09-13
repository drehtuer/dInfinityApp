package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.Renderer
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
    // Power-saving mode is not a second implementation, and this line is why:
    // it is the same roll the screen would have watched, stepped by nobody
    // (`docs/physics-and-rendering.md`, "Power-saving mode").
    return start(spec).use(LiveRoll::runToEnd)
  }

  /**
   * Opens the world, spawns the dice and hands back the roll for a caller to
   * step — the roll screen's way in (`docs/TODO.md`, Step 4.1).
   *
   * The caller owns it and must close it: a [LiveRoll] holds a physics world,
   * and a world that is never closed is native memory that is never freed.
   *
   * @param renderer what watches the throw. The default watches nothing, which
   *   is what [run] uses.
   */
  fun start(
    spec: ThrowSpec,
    renderer: Renderer = HeadlessRenderer(),
  ): LiveRoll {
    require(spec.dice.isNotEmpty()) { "a throw of no dice has nothing to simulate" }

    // There is no fallback and there deliberately is not one: the physics
    // result *is* the roll, so a bridge that will not open has to stop the roll
    // rather than quietly produce a number some other way
    // (`docs/architecture.md`, goal 1). Why it would not open is in logcat
    // under `dinfinity.jolt`.
    val world = worlds.open(spec) ?: error("the physics bridge would not open (libdinfinity_jolt)")

    // Everything from here to the `LiveRoll` is between an open world and a
    // caller who could close it, so a hull the shape catalogue refuses or a
    // spawn that will not fit has to take the world down with it. A native
    // world nobody holds is native memory nobody frees.
    return runCatching {
      val layout = SpawnLayout(spec.geometry, largestRadiusMm(spec), spec.seed)
      spec.dice.forEachIndexed { index, instance ->
        world.addDie(
          hull = ShapeGeometry.hullOf(instance.die, spec.dieScale),
          material = instance.die.material,
          placement = layout.placementOf(index, spec.dice.size),
        )
      }
      world.finish()
      LiveRoll(spec, world, RollLoop(spec, world, layout, ShakeDriver(spec.shake)), renderer)
    }.getOrElse { failure ->
      world.close()
      throw failure
    }
  }
}

/**
 * The grid is laid out for the biggest die in the throw, not for each die's
 * own size.
 *
 * A throw can mix a d4 and a d20 from different sets, and cells sized for the
 * d4 would put the d20 through its neighbour's cell wall before anything had
 * been thrown.
 *
 * Not private, because the golden suite has to lay out the same grid to record
 * what the engine was handed, and a second copy of this line is a second copy
 * that can drift.
 */
internal fun largestRadiusMm(spec: ThrowSpec): Double =
  spec.dice.maxOf {
    ShapeGeometry.boundingRadiusPerSize(it.die.shape) * it.die.material.sizeMm * spec.dieScale
  }

/** Opens the world one throw runs in. The seam the tests come in through. */
fun interface WorldFactory {
  /** A world for [spec], or null when there is no engine to open one with. */
  fun open(spec: ThrowSpec): PhysicsWorld?
}
