package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlaces
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
class TrayRenderer(
  /**
   * How far the camera leans over the table — the player's **Table view**
   * setting (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
   *
   * Held here rather than in [FilamentDiceRenderer] because this is the thing
   * that outlives a surface: a rotation builds a new drawing renderer, and it
   * has to be built with the same answer or the phone would come back from a
   * turn looking at the table from somewhere else.
   *
   * Read when the roll screen opens and never watched, like power saving and
   * the rest (`docs/architecture.md`, decision 16).
   */
  private val tableView: TableView = TableView.Angled,
) : Renderer {
  private var drawing: FilamentDiceRenderer? = null
  private var canvas: Stage? = null
  private var scene: Scene? = null
  private var latest: RenderFrame? = null
  private var settled = false
  private var view: TrayView = TrayView.Whole

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
    canvas = stage
    drawing = stage?.let { FilamentDiceRenderer(it, tableView) }
    val renderer = drawing ?: return
    val showing = scene ?: return

    val spec = showing.spec
    if (spec == null) {
      // A table with nothing on it: there is no frame to replay, and the one
      // draw it is worth is the caller's to ask for.
      renderer.table(showing.geometry, showing.look, view)
      return
    }

    renderer.begin(spec, showing.geometry, showing.look)
    // Where the player was looking is part of the picture, and a rotation is
    // not a reason to put them back at the whole tray. `begin` framed the whole
    // table, so this is only worth saying when they had moved off it.
    if (view != TrayView.Whole) renderer.look(view)
    latest?.let { frame ->
      // A roll that had already finished is put back finished, not re-run: the
      // camera belongs on the dice, where it was.
      if (settled) renderer.settled(frame) else renderer.show(frame)
    }
  }

  /**
   * The player is looking somewhere else, or closer.
   *
   * Remembered like everything else here, so a surface arriving afterwards is
   * aimed where the player left the camera rather than back at the whole tray.
   */
  fun look(view: TrayView) {
    this.view = view
    drawing?.look(view)
  }

  /**
   * There is a table, and nothing has been thrown onto it yet.
   *
   * Told once when the screen opens, and again whenever the look changes. It
   * is remembered like a throw is, so a surface that arrives later — or
   * arrives again after a rotation — gets the table rather than nothing.
   */
  fun table(
    geometry: TableGeometry,
    look: TableLook,
  ) {
    scene = Scene(spec = null, geometry = geometry, look = look)
    latest = null
    settled = false
    drawing?.table(geometry, look, view)
  }

  /**
   * Draws the current scene again, and says whether a frame actually landed.
   *
   * For the pictures that do not move. False when there is nowhere to draw, so
   * a caller that is asking until one lands stops asking when the surface goes.
   */
  fun redraw(): Boolean = canvas?.draw() ?: false

  override fun begin(
    spec: ThrowSpec,
    geometry: TableGeometry,
    look: TableLook,
  ) {
    scene = Scene(spec, geometry, look)
    latest = null
    settled = false
    // A throw is watched from the whole table. The dice can land anywhere in
    // it, and a camera left closed in on one corner would hide most of what
    // was just rolled (`docs/physics-and-rendering.md`).
    view = TrayView.Whole
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

  /**
   * The roll is over and its dice are gone.
   *
   * What is left is the table they were thrown onto, not nothing: taking the
   * dice away is not the same as taking the table away, and a player who puts
   * a result away is still sitting in front of one (`docs/TODO.md`, Step 4.1).
   * A renderer that was never told about a table has nothing to fall back to,
   * and goes back to drawing nothing.
   */
  override fun end() {
    latest = null
    settled = false
    val table = scene?.copy(spec = null)
    scene = table
    if (table == null) {
      drawing?.end()
      return
    }
    drawing?.table(table.geometry, table.look, view)
  }

  /**
   * Draws the dice that are waiting to be thrown, at rest on the table.
   *
   * The same two calls a finished roll ends on — build the bodies, then show
   * them standing still — because that is exactly what this is: dice on a
   * table, not moving. What it is *not* is a roll, so nothing is stepped and
   * no face is read.
   */
  fun waiting(spec: ThrowSpec) {
    val showing = scene ?: return
    if (spec.dice.isEmpty()) {
      table(showing.geometry, showing.look)
      return
    }
    val standing = RenderFrame.still(restingTransforms(spec))
    scene = showing.copy(spec = spec)
    // Remembered, not just drawn. A surface comes and goes — the lock screen,
    // a rotation — and the board has to come back with it, exactly as a
    // finished roll does. Kept as settled, because that is what it is: dice on
    // a table, not moving (`docs/physics-and-rendering.md`).
    latest = standing
    settled = true
    drawing?.let { renderer ->
      renderer.begin(spec, showing.geometry, showing.look)
      renderer.settled(standing)
    }
  }

  /** Where each waiting die is drawn: laid out so none of them overlaps. */
  private fun restingTransforms(spec: ThrowSpec): List<BodyTransform> =
    RestingPlaces
      .of(
        geometry = spec.geometry,
        radiiMm = spec.dice.map { ClearSpace.radiusOf(it.die, spec.dieScale) },
      ).mapIndexed { index, place ->
        BodyTransform(index = index, position = place, orientation = Quaternion.Identity)
      }

  /**
   * What a new stage has to be told to catch up with the old one.
   *
   * A null [spec] is a table with nothing on it, which is a scene like any
   * other: it has to be rebuilt on a new surface exactly as a throw does.
   */

  private data class Scene(
    val spec: ThrowSpec?,
    val geometry: TableGeometry,
    val look: TableLook,
  )
}
