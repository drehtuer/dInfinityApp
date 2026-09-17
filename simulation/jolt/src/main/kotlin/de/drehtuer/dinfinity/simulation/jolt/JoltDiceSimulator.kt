package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.simulation.api.DiceSimulator
import de.drehtuer.dinfinity.simulation.api.SettleRule
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
    // (`docs/physics-and-rendering.md`, "Power-saving mode"). Nothing is
    // listening: a headless run has nowhere to play an impact.
    return start(spec, listening = false).use { live ->
      // A headless run has no screen to walk away from, so it gives up rather
      // than hanging — and fails rather than answering, because a roll whose
      // dice never stopped has no faces to report (`LiveRoll.runToEnd`).
      live.runToEnd() ?: error(
        "the dice had not settled after ${SettleRule.HARD_CAP_SECONDS} s, so there is no roll to report",
      )
    }
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
   * @param listening whether the roll writes down where the dice hit
   *   something. Off when neither haptics nor sound is on, which is the one
   *   thing those two settings save: nothing is measured rather than measured
   *   and then muted. It changes nothing about the throw, which
   *   `ImpactRecorderTest` asserts on the same seed both ways
   *   (`docs/physics-and-rendering.md`, "Impacts").
   */
  fun start(
    spec: ThrowSpec,
    renderer: Renderer = HeadlessRenderer(),
    listening: Boolean = true,
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
      val layout =
        SpawnLayout(
          geometry = spec.geometry,
          dieRadiusMm = largestRadiusMm(spec),
          seed = spec.seed,
          // The dice already down are told to the *layout* and to nothing else.
          // No body is created for them, so this throw has nothing it could
          // shove; what they decide is where the new die is not dropped
          // (`docs/physics-and-rendering.md`).
          among = spec.among.map { it.at.position },
        )
      spec.dice.forEachIndexed { index, instance ->
        world.addDie(
          hull = ShapeGeometry.hullOf(instance.die, spec.dieScale),
          material = instance.die.material,
          placement = layout.placementOf(index, spec.dice.size),
        )
      }
      world.finish()
      val heard =
        if (listening) {
          ImpactRecorder(spec.dice.map { it.die.material.sizeMm * spec.dieScale })
        } else {
          ImpactRecorder.deaf(spec.dice.size)
        }
      LiveRoll(spec, world, RollLoop(spec, world, layout, ShakeDriver(spec.shake), heard), renderer)
    }.getOrElse { failure ->
      world.close()
      throw failure
    }
  }
}

/**
 * The grid is laid out for the biggest die in the throw, not for each die's
 * own size ([ThrowSpec.largestDieRadiusMm]).
 *
 * Not private, because the golden suite has to lay out the same grid to record
 * what the engine was handed, and a second copy of this line is a second copy
 * that can drift.
 */
internal fun largestRadiusMm(spec: ThrowSpec): Double = spec.largestDieRadiusMm

/** Opens the world one throw runs in. The seam the tests come in through. */
fun interface WorldFactory {
  /** A world for [spec], or null when there is no engine to open one with. */
  fun open(spec: ThrowSpec): PhysicsWorld?
}
