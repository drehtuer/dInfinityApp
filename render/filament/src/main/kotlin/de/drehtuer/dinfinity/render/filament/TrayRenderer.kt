package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.simulation.api.BoardDrops
import de.drehtuer.dinfinity.simulation.api.BoardRequest
import de.drehtuer.dinfinity.simulation.api.BoardTrack
import de.drehtuer.dinfinity.simulation.api.ClearSpace
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
 *
 * The class carries a function-count suppression for the reason [TrayLoop] and
 * [TrayDriver] do: most of these are one per thing that can happen to a
 * picture — a stage arrives, a table is named, a throw begins, a frame lands,
 * a roll ends, the board changes, its drop arrives, a die on it moves — and folding two
 * of them together would hide which is which rather than shorten anything.
 */
@Suppress("TooManyFunctions")
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
   * The dice waiting to be thrown, as a recorded drop, and how far into it the
   * board has played.
   *
   * A recording and a number: the world that made it was closed before it
   * arrived, and nothing goes on happening if nobody asks ([BoardTrack]).
   */
  private var track: BoardTrack = BoardTrack.EMPTY
  private var trackSeconds = 0.0

  /** What each die of the formula on show is, so the next board knows which are new. */
  private var standing: List<Pair<Die, Double>> = emptyList()

  /**
   * How many boards this visit has asked for, which is what each one's drops
   * are seeded by ([BoardDrops.request]).
   */
  private var boardsBuilt = 0

  /**
   * The board asked for and not yet arrived, if there is one.
   *
   * **Only the latest board is ever wanted.** A drop is worked out off this
   * thread, and the player can tap again before it is back; the next request
   * is built against what is on screen and replaces this, and a drop that
   * comes back for a board that is no longer pending is dropped. A throw, a
   * new table or a cleared tray forgets it altogether, so a late drop never
   * paints over a roll ([settled]).
   */
  private var pending: Pending? = null

  /** And where the board on screen had got to when it was asked for. */
  private var requestedAt = 0.0

  /** True while a board's drop is still playing. */
  val falling: Boolean get() = !track.ended(trackSeconds)

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
   * Asks for the dice waiting to be thrown to be put on the table, dropping in
   * the ones that were not there a moment ago
   * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
   *
   * Returns the board to let fall, or null when there is none to ask for —
   * no table yet, or no dice, which is the empty table again. Nothing on
   * screen changes until the drop comes back ([settled]): the request is
   * built from **what is on screen now**, each die already on the board
   * starting where it is drawn and moving as it is moving, so the drop picks
   * up from exactly the picture the player is looking at.
   *
   * What it is *not* is a roll. No face is read, ever, and the shake that
   * follows throws every one of these dice from a spawn of its own
   * ([BoardDrops]).
   */
  fun waiting(spec: ThrowSpec): BoardRequest? {
    val showing = scene ?: return null
    if (spec.dice.isEmpty()) {
      table(showing.geometry, showing.look)
      return null
    }
    val wanted = spec.dice.map { it.die to ClearSpace.radiusOf(it.die, spec.dieScale) }
    val recorded = track.indices.withIndex().associate { (die, index) -> index to die }
    val kept =
      BoardDrops
        .keeping(standing, wanted, present = recorded.keys)
        .mapValues { (_, was) -> track.placementAt(recorded.getValue(was), trackSeconds) }
    boardsBuilt++
    val request = BoardDrops.request(boardsBuilt, spec, kept)
    pending = Pending(boardsBuilt, spec, wanted)
    requestedAt = trackSeconds
    return request
  }

  /**
   * The drop for board [number] has come back. Returns whether it is the one
   * being waited for, and so is now on screen.
   *
   * It starts as far in as the board on screen has moved since it was asked
   * for: the dice carried over were snapshotted at that moment, so playing the
   * new drop from its beginning would put them back to where they were a few
   * frames ago.
   */
  fun settled(
    number: Int,
    track: BoardTrack,
  ): Boolean {
    val waited = pending?.takeIf { it.number == number } ?: return false
    val showing = scene ?: return false
    val catchUp = (trackSeconds - requestedAt).coerceAtLeast(0.0)
    pending = null
    this.track = track
    trackSeconds = catchUp
    standing = waited.dice
    scene = showing.copy(spec = waited.spec)
    // The scene has just been rebuilt, so this board has to be drawn even when
    // nothing on it is moving — a die taken off the board moves none of the
    // others and would otherwise leave the old picture up.
    settled = false
    drawing?.begin(waited.spec, showing.geometry, showing.look)
    draw()
    return true
  }

  /**
   * Moves the drop on by [elapsedSeconds] and draws where the dice have got to.
   *
   * Called once per displayed frame while [falling] is true and no more
   * ([TrayLoop.frame]). Nothing here is a simulation clock: the whole drop was
   * recorded before it was shown, so a frame that arrives late finds the dice
   * exactly where a frame that arrived on time would have.
   */
  fun fall(elapsedSeconds: Double) {
    if (track.dice == 0) return
    trackSeconds += elapsedSeconds
    draw()
  }

  /**
   * Shows the board as it is at this moment, still or moving.
   *
   * Drawn between the two recorded steps either side of the moment, the way a
   * roll is ([RenderFrame.blended]). A board that has come to rest is shown as
   * *settled* and only once: it is dice on a table, not moving, and a surface
   * that arrives afterwards has to be given it back that way rather than
   * mid-drop (`docs/physics-and-rendering.md`).
   */
  private fun draw() {
    val step = track.stepAt(trackSeconds)
    val moving = falling
    val frame =
      if (moving) {
        RenderFrame(posesAt(step), posesAt(step + 1), track.fractionAt(trackSeconds))
      } else {
        RenderFrame.still(posesAt(track.steps - 1))
      }
    latest = frame
    if (!moving && settled) return
    settled = !moving
    drawing?.let { renderer -> if (moving) renderer.show(frame) else renderer.settled(frame) }
  }

  /** Every recorded die at [step] of the drop. */
  private fun posesAt(step: Int): List<BodyTransform> =
    track.indices.mapIndexed { die, index ->
      val pose = track.poseAt(die, step)
      BodyTransform(index = index, position = pose.position, orientation = pose.orientation)
    }

  /**
   * There is no board any more, so nothing is dropping onto one — and a drop
   * still being worked out is for a board that no longer exists.
   */
  private fun clearBoard() {
    track = BoardTrack.EMPTY
    standing = emptyList()
    trackSeconds = 0.0
    pending = null
    requestedAt = 0.0
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

  /** A board asked for: its number, the formula it is, and what each die of it is. */
  private class Pending(
    val number: Int,
    val spec: ThrowSpec,
    val dice: List<Pair<Die, Double>>,
  )
}
