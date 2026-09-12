package de.drehtuer.dinfinity.render.headless

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.Quaternion
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
 * @param bodies one entry per die, in throw order.
 * @param interpolation how far this frame sits between the previous
 *   simulation state and this one, `0` to `1`.
 */
data class RenderFrame(
  val bodies: List<BodyTransform>,
  val interpolation: Double = 1.0,
)

/** One die's place and orientation, in the tray's millimetres. */
data class BodyTransform(
  val index: Int,
  val position: Vector3,
  val orientation: Quaternion,
)
