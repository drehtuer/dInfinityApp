package de.drehtuer.dinfinity.render.filament

import android.os.Handler
import android.os.HandlerThread
import de.drehtuer.dinfinity.core.model.AtlasImage
import java.util.concurrent.CountDownLatch

/**
 * The thread a roll happens on, and the half of Filament that outlives a visit
 * to the screen.
 *
 * [FilamentEngine] already draws one line — what a *surface* owns dies with the
 * surface, and the engine and the material compiled on the device do not. This
 * draws the same line one level out: what a *visit* owns dies when the player
 * leaves, and these two do not.
 *
 * The difference matters because it is the same cost either way. Compiling the
 * material happens on the device, for the driver that is actually there
 * (`docs/architecture.md`, decision 46), and it costs long enough to watch.
 * Keeping the engine across surfaces is what stopped a rotation showing a black
 * tray; keeping it across visits is what stops leaving the roll screen and
 * coming back showing the same one, which is what it did.
 *
 * **The thread has to be kept as well as the engine, not instead of it.**
 * Filament insists every call comes from the thread that made the engine
 * (`docs/architecture.md`, decision 49), so an engine that outlived its thread
 * would be an engine nothing is allowed to touch. The two are kept together or
 * not at all, which is why they are one object.
 *
 * What is *not* kept is anything belonging to a roll: the physics world, the
 * scene and the stage all go when the visit does. A throw the player walked
 * away from never landed, and there is nothing to score
 * (`docs/physics-and-rendering.md`).
 *
 * One of these belongs to the application, not to a screen. [close] is for
 * tests and for symmetry; a process that is ending does not need it.
 *
 * @param artwork where a die's decoded atlas comes from ([AtlasKey]). It is
 *   handed on to the engine and to nothing else: the artwork of a package is
 *   kept for as long as the engine is, for the same reason the compiled
 *   material is (`docs/dice-sets.md`, "Textures").
 */
class RollThread(
  private val artwork: (String) -> AtlasImage? = { null },
) : AutoCloseable {
  private val thread = HandlerThread(THREAD_NAME).apply { start() }

  /** Where every call into the engine and the physics world is posted. */
  val handler: Handler = Handler(thread.looper)

  @Volatile
  private var filament: FilamentEngine? = null

  /**
   * Whether the engine has been made yet.
   *
   * Readable from any thread, and the one thing a test can ask to tell a kept
   * engine from a rebuilt one.
   */
  val engineMade: Boolean get() = filament != null

  /**
   * The engine, made on first use.
   *
   * **Call only on the roll thread.** A graphics context belongs to the thread
   * that made it, and every caller is already inside a [handler] post.
   */
  fun filament(): FilamentEngine = filament ?: FilamentEngine(artwork).also { filament = it }

  /**
   * Runs [work] on the roll thread and waits for it.
   *
   * For the two things that cannot be fire-and-forget: giving a `Surface` back,
   * which may not be touched after the callback that withdrew it has returned,
   * and closing, which has to finish before what it was using goes.
   */
  fun await(work: () -> Unit) {
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

  /** Gives up the engine and stops the thread. Nothing may use either again. */
  override fun close() {
    await {
      filament?.close()
      filament = null
    }
    thread.quitSafely()
  }

  private companion object {
    const val THREAD_NAME = "dinfinity-roll"
  }
}
