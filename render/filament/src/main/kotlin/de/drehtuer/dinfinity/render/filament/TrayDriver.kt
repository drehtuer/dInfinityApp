package de.drehtuer.dinfinity.render.filament

import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Surface
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import java.util.concurrent.CountDownLatch

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
  private val stages: ((Surface, Int, Int) -> Stage)? = null,
) : Tray {
  private val loop = TrayLoop()
  private val thread = HandlerThread(THREAD_NAME).apply { start() }
  private val handler = Handler(thread.looper)

  private var ticking = false

  /**
   * The engine and the compiled material, kept across every surface this
   * driver ever draws to.
   *
   * Made on first use rather than here, because it has to be made on the roll
   * thread — a graphics context belongs to the thread that made it, and this
   * constructor runs on whichever thread built the screen. Null for a driver
   * given its own way of making stages, which is what a test does.
   */
  private var filament: FilamentEngine? = null

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
    handler.post {
      loop.stage(stageFor(surface, width, height))
      schedule()
    }
  }

  /** The surface is being taken away. Blocks until the stage is closed. */
  override fun surfaceLost() = onTheRollThread(loop::surfaceLost)

  /**
   * Throws the dice. [start] runs on the roll thread and is handed the
   * renderer to watch with, so the world it opens is stepped where it is made.
   *
   * [onSettled] arrives on the roll thread too, once, with what the dice came
   * to. Whoever wants it on the main thread posts it there.
   */
  override fun roll(
    start: (Renderer) -> WatchedRoll,
    onSettled: (SimulationOutcome) -> Unit,
  ) {
    handler.post {
      loop.roll(start, onSettled)
      schedule()
    }
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
    handler.post { loop.shake(sample) }
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
    handler.post {
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
    handler.post {
      loop.look(view)
      schedule()
    }
  }

  /** Takes whatever is on the tray off it. */
  override fun clear() {
    handler.post {
      loop.clear()
      schedule()
    }
  }

  /** Stops the thread. The driver cannot be used again. */
  override fun close() {
    onTheRollThread {
      // The stage first, then what it was borrowing: a swap chain outliving
      // its engine is a crash rather than a leak.
      loop.close()
      filament?.close()
      filament = null
    }
    thread.quitSafely()
  }

  private fun onTheRollThread(work: () -> Unit) {
    val done = CountDownLatch(1)
    handler.post {
      try {
        work()
      } finally {
        done.countDown()
      }
    }
    done.await()
  }

  private fun schedule() {
    if (ticking || !loop.wantsFrames) return
    ticking = true
    Choreographer.getInstance().postFrameCallback(tick)
  }

  /**
   * A stage for this surface, sharing the engine with every stage before it.
   *
   * The engine and the compiled material are made once, on this thread, and
   * kept: compiling the material happens on the device for the driver that is
   * actually there, and doing it again for every rotation is what used to
   * leave the tray black for a moment (`docs/TODO.md`, Step 4.1).
   */
  private fun stageFor(
    surface: Surface,
    width: Int,
    height: Int,
  ): Stage {
    stages?.let { return it(surface, width, height) }
    val shared = filament ?: FilamentEngine().also { filament = it }
    return shared.stage(surface, width, height)
  }

  private companion object {
    const val THREAD_NAME = "dinfinity-roll"
  }
}
