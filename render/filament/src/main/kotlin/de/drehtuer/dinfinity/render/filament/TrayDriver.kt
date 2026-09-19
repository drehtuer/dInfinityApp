package de.drehtuer.dinfinity.render.filament

import android.view.Choreographer
import android.view.Surface
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.DebugWatch
import de.drehtuer.dinfinity.simulation.api.Impacts
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * The thread a roll happens on, and the surface it is drawn to
 * (`docs/architecture.md`, "Threading").
 *
 * One thread owns the physics world, the Filament engine and the frame
 * callback that turns them — one fewer than the design first described, and
 * deliberately. Two would have had to exchange the last two simulation states
 * on every step, and the only thing the drawing side can do with them is draw
 * them: a lock-free double-buffer handing eighty transforms across a boundary
 * neither side wants, in exchange for overlapping a copy with a draw
 * (`docs/architecture.md`, decision 49).
 *
 * What it is **not** is the main thread. Eighty convex bodies at 120 Hz is
 * real work and it does not belong where the UI is drawn.
 *
 * Everything public may be called from any thread and runs on that one. The
 * exception is [surfaceLost], which blocks until the stage is gone, because a
 * `Surface` may not be touched after the callback that withdrew it has
 * returned.
 *
 * What to draw is not decided here — that is [TrayLoop] and [TrayRenderer], on
 * the near side of [Stage] where a JVM test can reach it. This file is a
 * thread, a surface and a vsync, which is why it is excluded from the coverage
 * figure and not from anything else (`.claude/CLAUDE.md`).
 *
 * The class carries a function-count suppression, like [FilamentStage] and for
 * a related reason: eight of its methods are [Tray], one per thing a screen can
 * say to a tray, and the other three are the thread, the vsync and the stage.
 * Folding any of them together would hide the one thing this file exists to
 * make obvious — every public method posts to the roll thread, and nothing
 * reaches the loop from anywhere else.
 */
@Suppress("TooManyFunctions")
class TrayDriver(
  shared: RollThread? = null,
  impacts: Impacts = Impacts.NONE,
  /**
   * What watches the roll's diagnostics, and draws nothing itself — the debug
   * overlay behind the developer toggle
   * (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * Given here rather than switched on later, for the reason power saving,
   * the haptics and the sound are all read when the screen opens: an overlay
   * appearing under a roll in progress is not a setting taking effect
   * (`docs/architecture.md`, decision 16).
   */
  debug: DebugWatch = DebugWatch.NONE,
  /**
   * How far the camera leans over the table — the player's **Table view**
   * setting (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
   *
   * Given here for the reason the overlay is: this driver is one visit to the
   * roll screen, so reading the setting when it is built is exactly what
   * "takes effect the next time the screen opens" means
   * (`docs/architecture.md`, decision 16).
   */
  tableView: TableView = TableView.Angled,
  // Last, so that a trailing lambda still means this one. A driver is built
  // with a stage factory in exactly one place — the device suite — and it is
  // written as a trailing lambda there; putting anything after it makes that
  // lambda quietly bind to the wrong parameter, which is what happened.
  private val stages: ((Surface, Int, Int) -> Stage)? = null,
) : Tray {
  private val loop = TrayLoop(impacts, debug, tableView)

  /**
   * The thread and engine this driver made for itself, if it was not given
   * one — and therefore the only one it is allowed to close.
   */
  private val own: RollThread? = if (shared == null) RollThread() else null

  private val host: RollThread = shared ?: requireNotNull(own)
  private val handler get() = host.handler

  private var ticking = false

  /**
   * Set before anything is torn down, and checked inside everything posted.
   *
   * A driver used to own its thread, so closing it stopped the thread and with
   * it anything still queued. The thread now outlives the visit, so a message
   * posted a moment before the player left would otherwise run against a closed
   * loop — on a thread the *next* visit is already using.
   */
  @Volatile
  private var closed = false

  private val tick =
    Choreographer.FrameCallback { nanos ->
      ticking = false
      if (loop.frame(nanos)) schedule()
    }

  /**
   * There is somewhere to draw, this big. Called again with a new size when
   * the view is resized or the phone is turned.
   */
  override fun surfaceAvailable(
    surface: Surface,
    width: Int,
    height: Int,
  ) {
    post {
      loop.stage(stageFor(surface, width, height))
      schedule()
    }
  }

  /** The surface is being taken away. Blocks until the stage is closed. */
  override fun surfaceLost() = host.await { if (!closed) loop.surfaceLost() }

  /**
   * Throws the dice. [start] runs on the roll thread and is handed the
   * renderer to watch with, so the world it opens is stepped where it is made.
   *
   * [onSettled] arrives on the roll thread too, once, with what the dice came
   * to and the shake that drove them. Whoever wants it on the main thread posts
   * it there.
   */
  override fun roll(
    start: (Renderer) -> WatchedRoll,
    onCounted: (Map<Int, Int>) -> Unit,
    onStalled: (List<Int>) -> Unit,
    onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
  ) {
    post {
      loop.roll(start, onCounted, onStalled, onSettled)
      schedule()
    }
  }

  /**
   * Puts the dice that are waiting to be thrown on the table.
   *
   * Posted like everything else, and then [schedule]d: a die the player just
   * added is falling onto the board, and it needs the frame callback running
   * for the fifth of a second that takes.
   */
  override fun waiting(spec: ThrowSpec) =
    post {
      loop.waiting(spec)
      schedule()
    }

  /**
   * One more moment of the shake, handed to the roll on its own thread.
   *
   * Posted rather than applied where it arrives: the sensors are read on the
   * main thread and the roll belongs to this one, and a shake written into a
   * world that is mid-step is a race with a physics engine on the other end
   * of it.
   */
  override fun shake(sample: ShakeSample) {
    post { loop.shake(sample) }
  }

  /**
   * There is a table, and nothing has been thrown onto it yet.
   *
   * Posted like everything else: the scene belongs to the roll thread, and the
   * screen says this from the main one.
   */
  override fun table(
    geometry: TableGeometry,
    look: TableLook,
  ) {
    post {
      loop.table(geometry, look)
      schedule()
    }
  }

  /**
   * The player is looking somewhere else, or closer.
   *
   * Posted like everything else: gestures arrive on the main thread and the
   * camera belongs to this one.
   */
  override fun look(view: TrayView) {
    post {
      loop.look(view)
      schedule()
    }
  }

  /** Takes whatever is on the tray off it. */
  override fun clear() {
    post {
      loop.clear()
      schedule()
    }
  }

  /**
   * Gives up this visit's roll. The driver cannot be used again.
   *
   * The stage and the physics world go; the engine and the thread stay, unless
   * this driver made them itself. A swap chain outliving its engine is a crash
   * rather than a leak, so the order matters and the engine is never closed
   * before the stage that borrowed it.
   */
  override fun close() {
    if (closed) return
    closed = true
    host.await {
      // The frame callback is removed by hand now that the thread survives:
      // a tick left posted would step a loop that has been closed.
      Choreographer.getInstance().removeFrameCallback(tick)
      loop.close()
    }
    own?.close()
  }

  /**
   * Runs [work] on the roll thread, unless this visit is over.
   *
   * The guard is both sides of the post: nothing is queued after [close], and
   * anything already queued when it happened does nothing when it arrives.
   */
  private fun post(work: () -> Unit) {
    if (closed) return
    handler.post { if (!closed) work() }
  }

  private fun schedule() {
    if (ticking || !loop.wantsFrames) return
    ticking = true
    Choreographer.getInstance().postFrameCallback(tick)
  }

  /**
   * A stage for this surface, sharing the engine with every stage before it —
   * including the ones from earlier visits to the screen.
   *
   * The engine and the compiled material are made once, on the roll thread,
   * and kept there: compiling the material happens on the device for the
   * driver that is actually there, and doing it again is what used to leave
   * the tray black for a moment — first on every rotation, then on every visit
   * ([RollThread]).
   */
  private fun stageFor(
    surface: Surface,
    width: Int,
    height: Int,
  ): Stage {
    stages?.let { return it(surface, width, height) }
    return host.filament().stage(surface, width, height)
  }
}
