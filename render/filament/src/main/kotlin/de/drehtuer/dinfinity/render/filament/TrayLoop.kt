package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll

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
  private var lastFrameNanos: Long? = null

  /**
   * True while there is a roll to advance and somewhere to draw it — which is
   * exactly when it is worth asking for another frame.
   */
  val wantsFrames: Boolean get() = roll != null && stage != null

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
    closeStage()
    this.stage = stage
    renderer.stage(stage)
  }

  /** The surface is gone. The roll, if there is one, carries on unwatched. */
  fun surfaceLost() {
    closeStage()
  }

  /**
   * Throws the dice.
   *
   * [start] is handed the renderer to watch with and returns the roll it
   * opened, so the physics world is created on whichever thread is going to
   * step it. A roll already in progress is ended first — a second throw
   * replaces the first rather than landing on top of it.
   */
  fun roll(start: (Renderer) -> WatchedRoll) {
    endRoll()
    roll = start(renderer)
    lastFrameNanos = null
  }

  /** Takes whatever is on the tray off it. */
  fun clear() {
    endRoll()
    renderer.end()
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
    val live = roll ?: return false
    val previous = lastFrameNanos
    lastFrameNanos = nanos

    // A frame clock that jumped backwards — a different clock source, or a
    // counter that wrapped — is worth no time rather than a negative amount,
    // which the frame clock would refuse outright.
    val elapsed = if (previous == null) 0.0 else ((nanos - previous).coerceAtLeast(0)) / NANOS_PER_SECOND
    live.advance(elapsed)

    if (!live.running) endRoll()
    return wantsFrames
  }

  override fun close() {
    endRoll()
    closeStage()
  }

  private fun endRoll() {
    roll?.close()
    roll = null
    lastFrameNanos = null
  }

  private fun closeStage() {
    renderer.stage(null)
    stage?.close()
    stage = null
  }

  private companion object {
    const val NANOS_PER_SECOND = 1_000_000_000.0
  }
}
