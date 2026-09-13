package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry

/**
 * What happens on the tray, frame by frame: which stage is being drawn to,
 * which roll is being watched, and how much time each frame is worth.
 *
 * Everything here is a decision and none of it is Android, so it is tested on
 * a JVM. [TrayDriver] is the thread and the surface it runs on, and is the
 * only part that needs a device (`docs/architecture.md`, decision 40).
 *
 * Two rules are worth naming because they are easy to get subtly wrong and
 * impossible to notice afterwards:
 *
 * - **A surface coming or going never touches the roll.** A new stage is given
 *   the scene it missed by [TrayRenderer]; the simulation is not asked for
 *   anything and certainly not restarted.
 * - **The first frame of a roll is worth no time at all.** There is no frame
 *   before it to measure against, and measuring from zero would hand the clock
 *   however long the device has been awake — spending the whole catch-up
 *   budget on frame one and starting the roll a fifth of a second in.
 *
 * Not thread-safe: one thread owns a roll, and [TrayDriver] is the thread that
 * does (`docs/architecture.md`, "Threading").
 */
class TrayLoop : AutoCloseable {
  private val renderer = TrayRenderer()

  private var stage: Stage? = null
  private var roll: WatchedRoll? = null
  private var settling: ((SimulationOutcome) -> Unit)? = null
  private var lastFrameNanos: Long? = null
  private var owed = false

  /**
   * True while there is something to draw and somewhere to draw it — which is
   * exactly when it is worth asking for another frame.
   *
   * Two things count as something to draw: a roll, which wants every frame it
   * can get, and a still picture that has not landed yet. The second is why
   * this is not simply "is there a roll": an empty table is worth one frame,
   * but Filament may decline the one it is offered, so the asking has to go on
   * until a frame actually lands.
   */
  val wantsFrames: Boolean get() = stage != null && (roll != null || owed)

  /** True while a roll is in progress, watched or not. */
  val rolling: Boolean get() = roll != null

  /**
   * Draw onto this from now on. Any stage already here is closed first.
   *
   * Called again with a new stage when the view is resized or the phone is
   * turned: Filament fixes its swap chain and viewport when a stage is made,
   * so a new size means a new stage. The roll does not notice.
   */
  fun stage(stage: Stage) {
    surfaceLost()
    this.stage = stage
    renderer.stage(stage)
    // A new surface has never been drawn to. Whatever is being shown — a table
    // with nothing on it, or a roll that has already come to rest — owes it a
    // frame, because neither of those will produce one on its own.
    owed = true
  }

  /**
   * There is a table, and nothing has been thrown onto it yet.
   *
   * Told when the screen opens, before any roll, so that what a player sees on
   * arrival is a table waiting rather than a black rectangle
   * (`docs/TODO.md`, Step 4.1). Remembered, so a surface that arrives
   * afterwards gets it too.
   */
  fun table(
    geometry: TableGeometry,
    look: TableLook,
  ) {
    renderer.table(geometry, look)
    owed = true
  }

  /**
   * The player is looking somewhere else, or closer.
   *
   * Only the camera moves, and nothing about the roll does. Owed a frame like
   * any other still picture: between throws nothing else would produce one, so
   * a pinch would otherwise not appear until something else happened to draw.
   */
  fun look(view: TrayView) {
    renderer.look(view)
    owed = true
  }

  /**
   * The surface is gone. The roll, if there is one, carries on unwatched.
   *
   * Also how a surface is let go of on the way to a new one, and on the way
   * out: there is only one way to stop drawing to a surface, and this is it.
   */
  fun surfaceLost() {
    renderer.stage(null)
    stage?.close()
    stage = null
  }

  /**
   * Throws the dice.
   *
   * [start] is handed the renderer to watch with and returns the roll it
   * opened, so the physics world is created on whichever thread is going to
   * step it. A roll already in progress is ended first — a second throw
   * replaces the first rather than landing on top of it.
   *
   * [onSettled] is called once, on this thread, with what the dice came to —
   * and only for a roll that actually finished. A roll abandoned because the
   * player left the screen reports nothing, because nothing landed.
   */
  fun roll(
    start: (Renderer) -> WatchedRoll,
    onSettled: (SimulationOutcome) -> Unit = {},
  ) {
    endRoll()
    roll = start(renderer)
    settling = onSettled
    lastFrameNanos = null
  }

  /** One more moment of the shake, if there is a roll for it to drive. */
  fun shake(sample: ShakeSample) {
    roll?.shake(sample)
  }

  /**
   * Takes whatever is on the tray off it.
   *
   * The table stays. What is cleared is the throw, and what is left is the
   * empty table it was thrown onto — which has to be drawn, because nothing
   * else is going to.
   */
  fun clear() {
    endRoll()
    renderer.end()
    owed = true
  }

  /**
   * One displayed frame, at [nanos] on the frame clock's own timeline.
   *
   * Returns whether another frame is worth asking for. A roll that has
   * finished is left on screen exactly as it finished: the dice have stopped
   * and nothing may touch them, so there is nothing left to draw
   * (`.claude/CLAUDE.md`).
   */
  fun frame(nanos: Long): Boolean {
    val live = roll
    if (live == null) {
      // Nothing is moving, but something may not have been drawn yet: the
      // table before the first throw, or a landed roll on a surface that has
      // just arrived. One frame settles it — and only a frame that actually
      // landed does, because a still picture has nothing coming after it to
      // cover for a skip.
      if (owed && renderer.redraw()) owed = false
      return wantsFrames
    }

    val previous = lastFrameNanos
    lastFrameNanos = nanos

    // A frame clock that jumped backwards — a different clock source, or a
    // counter that wrapped — is worth no time rather than a negative amount,
    // which the frame clock would refuse outright.
    val elapsed = if (previous == null) 0.0 else ((nanos - previous).coerceAtLeast(0)) / NANOS_PER_SECOND
    live.advance(elapsed)

    // A roll draws every frame of its own accord, so nothing is owed while one
    // is running.
    owed = false

    if (!live.running) {
      // Read before closing: a roll that has been given up holds nothing.
      val reached = live.outcome
      val report = settling
      endRoll()
      // The last frame of a roll is the picture that stays on screen, and the
      // one frame with nothing after it to cover for a skip. Owed until it
      // lands, like any other still picture.
      owed = true
      reached?.let { report?.invoke(it) }
    }
    return wantsFrames
  }

  override fun close() {
    endRoll()
    // The picture ends here rather than with the roll: a landed roll stays on
    // screen for as long as there is a screen, and only giving the tray up
    // takes it away.
    renderer.end()
    surfaceLost()
  }

  private fun endRoll() {
    roll?.close()
    roll = null
    settling = null
    lastFrameNanos = null
  }

  private companion object {
    const val NANOS_PER_SECOND = 1_000_000_000.0
  }
}
