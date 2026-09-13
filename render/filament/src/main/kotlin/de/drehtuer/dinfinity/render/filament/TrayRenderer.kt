package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * The renderer a tray hands out, which outlives the surface it draws on.
 *
 * A `SurfaceView`'s surface comes and goes for reasons that have nothing to do
 * with the roll: the phone is turned, the view is resized, the app is
 * backgrounded and brought back. Filament's swap chain and viewport are fixed
 * when a [FilamentStage] is made, so each of those means a new stage — and a
 * roll that was being drawn on the old one is still going.
 *
 * **A roll is never restarted to get a picture back.** Restarting the
 * simulation would be a different roll wearing the same seed's name, and the
 * player would watch the dice they were already watching begin again. So this
 * remembers what it was told — the throw, the tray, the last frame — and
 * *replays* it onto the new stage: the scene is rebuilt, the dice are put back
 * where the simulation says they are, and the roll carries on having noticed
 * nothing.
 *
 * With no stage at all it is a renderer that draws nothing, which is the right
 * thing to be while the app is in the background: the roll goes on, the
 * frames are dropped on the floor, and the dice are where they should be the
 * moment there is somewhere to put them.
 *
 * Every line of this is a decision about when to draw what, and none of it is
 * a GPU — so it sits on the near side of [Stage] and is tested on a JVM
 * (`docs/architecture.md`, decision 47).
 */
class TrayRenderer : Renderer {
  private var drawing: FilamentDiceRenderer? = null
  private var scene: Scene? = null
  private var latest: RenderFrame? = null
  private var settled = false

  /** True while there is somewhere to draw. */
  val drawable: Boolean get() = drawing != null

  /**
   * Draws onto [stage] from now on, or onto nothing when it is null.
   *
   * Whatever was being shown is put back: the scene is rebuilt on the new
   * stage and the dice are placed where the last frame left them. The stage
   * that was here before is *not* closed — whoever made it owns it, and it is
   * usually being torn down by the surface callback that called this.
   */
  fun stage(stage: Stage?) {
    drawing = stage?.let(::FilamentDiceRenderer)
    val renderer = drawing ?: return
    val showing = scene ?: return

    renderer.begin(showing.spec, showing.geometry, showing.look)
    latest?.let { frame ->
      // A roll that had already finished is put back finished, not re-run: the
      // camera belongs on the dice, where it was.
      if (settled) renderer.settled(frame) else renderer.show(frame)
    }
  }

  override fun begin(
    spec: ThrowSpec,
    geometry: TableGeometry,
    look: TableLook,
  ) {
    scene = Scene(spec, geometry, look)
    latest = null
    settled = false
    drawing?.begin(spec, geometry, look)
  }

  override fun show(frame: RenderFrame) {
    latest = frame
    drawing?.show(frame)
  }

  override fun settled(frame: RenderFrame) {
    latest = frame
    settled = true
    drawing?.settled(frame)
  }

  override fun end() {
    scene = null
    latest = null
    settled = false
    drawing?.end()
  }

  /** What a new stage has to be told to catch up with the old one. */
  private data class Scene(
    val spec: ThrowSpec,
    val geometry: TableGeometry,
    val look: TableLook,
  )
}
