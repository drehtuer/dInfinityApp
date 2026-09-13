package de.drehtuer.dinfinity.render.headless

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * A view of a roll (`docs/physics-and-rendering.md`).
 *
 * Rendering is a **passive observer** of the simulation, and this interface is
 * that sentence made enforceable: nothing here returns anything the
 * simulation could act on. A renderer is shown a scene and then shown where
 * the bodies are; it cannot move one, cannot ask for another step, and cannot
 * change what a die lands on. Turning it off therefore cannot change how a
 * roll is produced, which is what makes power-saving mode honest rather than a
 * second implementation to keep in step (`docs/architecture.md`, goal 1).
 *
 * There are two implementations and they are not peers: Filament draws, and
 * [HeadlessRenderer] does not. Power-saving mode creates no graphics engine at
 * all — not a hidden surface, not an off-screen target — it uses the one that
 * does nothing.
 */
interface Renderer {
  /**
   * Prepares to show a throw: the tray, its look, and one body per die.
   *
   * Called once, before the first step.
   */
  fun begin(
    spec: ThrowSpec,
    geometry: TableGeometry,
    look: TableLook,
  )

  /**
   * Where every body is, after the simulation has advanced.
   *
   * Called with the latest two simulation states rather than one, because the
   * physics runs at a fixed 120 Hz and a display does not: a renderer
   * interpolates between them at its own rate, so 120 Hz physics looks smooth
   * whatever the panel does.
   */
  fun show(frame: RenderFrame)

  /** The roll is over and the dice are where they will stay. */
  fun settled(frame: RenderFrame)

  /** Tears down whatever was built in [begin]. Safe to call without one. */
  fun end()
}

/**
 * Where the dice are at one moment, as the simulation reported it.
 *
 * It carries the last *two* simulation states rather than one because the
 * physics runs at a fixed 120 Hz and a display does not. A renderer is told
 * where the dice were, where they are, and how far between the two this
 * moment falls, and draws them there — so the same simulation looks smooth on
 * a 60 Hz panel and on a 120 Hz one without the simulation knowing either
 * exists (`docs/physics-and-rendering.md`).
 *
 * @param previous the state before the most recent step.
 * @param current the state after it, one entry per die in throw order.
 * @param interpolation how far this frame sits between the two, `0` to `1`.
 */
data class RenderFrame(
  val previous: List<BodyTransform>,
  val current: List<BodyTransform>,
  val interpolation: Double = 1.0,
) {
  init {
    require(previous.size == current.size) {
      "a frame has the same dice before and after a step, not ${previous.size} and ${current.size}"
    }
    require(interpolation in 0.0..1.0) { "$interpolation is not a moment between two steps" }
  }

  /**
   * Where each die is at this moment: the two states blended.
   *
   * Every renderer blends the same way or two of them would disagree about
   * the same roll, so the arithmetic is here rather than in each of them.
   * Positions move in a straight line and turns take the short way round
   * ([Quaternion.slerp]).
   */
  fun blended(): List<BodyTransform> =
    previous.zip(current) { before, after ->
      after.copy(
        position = before.position + (after.position - before.position) * interpolation,
        orientation = before.orientation.slerp(after.orientation, interpolation),
      )
    }

  companion object {
    /** Dice that are not moving: the same state at both ends. */
    fun still(bodies: List<BodyTransform>): RenderFrame = RenderFrame(bodies, bodies)
  }
}

/** One die's place and orientation, in the tray's millimetres. */
data class BodyTransform(
  val index: Int,
  val position: Vector3,
  val orientation: Quaternion,
)

/**
 * A roll in progress, as the thing drawing it sees one.
 *
 * The simulation knows what a roll is; a tray on a screen only needs to know
 * that time has passed and that the roll is or is not over. This is that much
 * of it, and no more — which is what lets `render/filament` drive a roll
 * without depending on a physics engine, and lets a test drive one without
 * having one (`docs/architecture.md`, decision 48).
 *
 * Note what is still missing: there is nothing here that reaches a die, asks
 * for a re-throw or decides a face. Drawing a roll cannot change it, at this
 * level for the same reason as at every other one.
 */
interface WatchedRoll : AutoCloseable {
  /** True until the last die has come to rest. */
  val running: Boolean

  /**
   * What the throw came to, or null while it is still going — and null for
   * good for a roll that was abandoned before it finished.
   *
   * The only thing that comes back out. A watcher may read what the dice did;
   * it still has no way to change it.
   */
  val outcome: SimulationOutcome?

  /**
   * Moves the roll on by however much [elapsedSeconds] is worth and hands back
   * where the dice are. The renderer watching has already been shown the same
   * frame.
   */
  fun advance(elapsedSeconds: Double): RenderFrame
}

/**
 * How a roll is opened for something to watch.
 *
 * The one line between a screen and a physics engine. A screen knows it wants
 * these dice thrown and wants to watch them land; which engine does it, and
 * whether there is an engine at all, is the app's business
 * (`docs/architecture.md`, decision 40).
 */
fun interface Rolls {
  /**
   * Opens [spec] as a roll in progress, with [watcher] shown every frame.
   *
   * Called on whichever thread is going to step it, because a roll belongs to
   * one thread and so does the world underneath it.
   */
  fun start(
    spec: ThrowSpec,
    watcher: Renderer,
  ): WatchedRoll
}
