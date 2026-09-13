package de.drehtuer.dinfinity.render.headless

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * The renderer power-saving mode uses: it draws nothing, and creating it costs
 * nothing (`docs/physics-and-rendering.md`, "Power-saving mode").
 *
 * "No Filament engine is created at all" is a claim about a whole dependency,
 * so it is kept here, in a module with no Android in it and nothing to create.
 * A headless mode built out of the real renderer with the drawing switched off
 * would still hold a GPU context, still allocate its buffers, and would
 * quietly stop being free the first time somebody added a resource in the
 * wrong place.
 *
 * It counts what it was shown, because that is exactly what the tests of the
 * layer above want to know — that a roll ran, that it ended, and that nothing
 * asked for a frame after it settled — and because a counter is the only thing
 * a renderer can honestly offer that is not a picture.
 */
class HeadlessRenderer : Renderer {
  /** How many frames were offered. In power-saving mode, usually none. */
  var framesShown: Int = 0
    private set

  /** Whether a throw is in progress. */
  var running: Boolean = false
    private set

  /** The last frame handed over, settled or not. */
  var lastFrame: RenderFrame? = null
    private set

  /** Whether the roll it was shown has settled. */
  var finished: Boolean = false
    private set

  override fun begin(
    spec: ThrowSpec,
    geometry: TableGeometry,
    look: TableLook,
  ) {
    running = true
    finished = false
    framesShown = 0
    lastFrame = null
  }

  override fun show(frame: RenderFrame) {
    framesShown++
    lastFrame = frame
  }

  override fun settled(frame: RenderFrame) {
    lastFrame = frame
    finished = true
  }

  override fun end() {
    running = false
  }
}
