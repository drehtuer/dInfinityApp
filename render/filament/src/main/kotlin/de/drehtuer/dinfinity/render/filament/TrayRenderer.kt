package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.FallingIn
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

  /**
   * The dice waiting to be thrown, each on its way to a place or standing in
   * one, and how long the board has been running.
   *
   * A list and a number: no thread, no world, nothing that goes on happening
   * if nobody asks. Tapping out `8d6` replaces this eight times and leaves
   * nothing behind either time ([FallingIn]).
   */
  private var board: List<FallingIn.Drop> = emptyList()
  private var boardSeconds = 0.0

  /** What each die on the board is, so the next board knows which are new. */
  private var standing: List<Pair<Die, Double>> = emptyList()

  /** True while a die the player added is still on its way down. */
  val falling: Boolean get() = FallingIn.stillFalling(board, boardSeconds)

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
    clearBoard()
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
    // The dice that were waiting have been thrown, and a half-finished fall
    // belongs to a board that no longer exists.
    clearBoard()
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
    clearBoard()
    val table = scene?.copy(spec = null)
    scene = table
    if (table == null) {
      drawing?.end()
      return
    }
    drawing?.table(table.geometry, table.look, view)
  }

  /**
   * Puts the dice that are waiting to be thrown on the table, dropping in the
   * ones that were not there a moment ago
   * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
   *
   * The dice already standing stay exactly where they are standing — they are
   * handed to [FallingIn.board] as taken floor and no place is computed for
   * them again. The dice that are new fall in from above and tumble to a
   * stop, and where they stop is the place they would simply have been put
   * before there was a fall at all.
   *
   * What it is *not* is a roll. Nothing is stepped, no face is read, and the
   * orientation every one of these dice comes to rest in is fixed before it is
   * let go ([FallingIn]).
   */
  fun waiting(spec: ThrowSpec) {
    val showing = scene ?: return
    if (spec.dice.isEmpty()) {
      table(showing.geometry, showing.look)
      return
    }
    val radii = spec.dice.map { ClearSpace.radiusOf(it.die, spec.dieScale) }
    val wanted = spec.dice.mapIndexed { index, instance -> instance.die to radii[index] }
    board =
      FallingIn.board(
        geometry = spec.geometry,
        radiiMm = radii,
        seed = spec.seed,
        keeping = FallingIn.keeping(standing, wanted, board, boardSeconds),
      )
    standing = wanted
    boardSeconds = 0.0
    scene = showing.copy(spec = spec)
    // The scene has just been rebuilt, so this board has to be drawn even when
    // nothing on it is moving — a die taken off the board moves none of the
    // others and would otherwise leave the old picture up.
    settled = false
    drawing?.begin(spec, showing.geometry, showing.look)
    draw()
  }

  /**
   * Moves the fall on by [elapsedSeconds] and draws where the dice have got to.
   *
   * Called once per displayed frame while [falling] is true and no more, which
   * is a fifth of a second after a tap and nothing at all between taps
   * ([TrayLoop.frame]). Nothing here is a simulation clock: the whole fall is
   * decided when the board is built, so a frame that arrives late finds the
   * dice exactly where a frame that arrived on time would have.
   */
  fun fall(elapsedSeconds: Double) {
    if (board.isEmpty()) return
    boardSeconds += elapsedSeconds
    draw()
  }

  /**
   * Shows the board as it is at this moment, still or moving.
   *
   * A board that has come to rest is shown as *settled* and only once: it is
   * dice on a table, not moving, and a surface that arrives afterwards has to
   * be given it back that way rather than mid-fall
   * (`docs/physics-and-rendering.md`).
   */
  private fun draw() {
    val frame = RenderFrame.still(boardTransforms())
    latest = frame
    val moving = falling
    if (!moving && settled) return
    settled = !moving
    drawing?.let { renderer -> if (moving) renderer.show(frame) else renderer.settled(frame) }
  }

  /** Where each waiting die is at this moment on the board's clock. */
  private fun boardTransforms(): List<BodyTransform> =
    board.map { drop ->
      BodyTransform(
        index = drop.index,
        position = drop.positionAt(boardSeconds),
        orientation = drop.orientationAt(boardSeconds),
      )
    }

  /** There is no board any more, so nothing is falling onto one. */
  private fun clearBoard() {
    board = emptyList()
    standing = emptyList()
    boardSeconds = 0.0
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
