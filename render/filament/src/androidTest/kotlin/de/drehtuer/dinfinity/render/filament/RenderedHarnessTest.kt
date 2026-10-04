package de.drehtuer.dinfinity.render.filament

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.BoardSettler
import de.drehtuer.dinfinity.simulation.api.BoardTrack
import de.drehtuer.dinfinity.simulation.api.Passes
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.harness.DeviceFacts
import de.drehtuer.dinfinity.simulation.harness.HarnessPlan
import de.drehtuer.dinfinity.simulation.harness.HarnessRequest
import de.drehtuer.dinfinity.simulation.harness.RenderedFrames
import de.drehtuer.dinfinity.simulation.harness.RenderedReport
import de.drehtuer.dinfinity.simulation.harness.RunLength
import de.drehtuer.dinfinity.simulation.jolt.JoltDiceSimulator
import de.drehtuer.dinfinity.simulation.jolt.LiveRoll
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The rendered harness: twenty d20s thrown through the shipping path onto a
 * real surface, every frame timed (`docs/TODO.md`, Step 5.7;
 * `docs/build-setup.md`, "The physics harness").
 *
 * The headless harness in `simulation/jolt` can time only the simulation half
 * of a frame, because it has no surface and no Filament. This is the other
 * half, and it is the app's own code from the frame callback down: a
 * [TrayDriver] on a [RollThread] with the engine the roll screen uses, its
 * `TrayLoop` pacing each frame by `RollPace` exactly as the screen does, a
 * [LiveRoll] from [JoltDiceSimulator.start], and `FilamentDiceRenderer` drawing
 * each frame through a [FilamentStage]. The only things added are two thin
 * wrappers that hold a stopwatch — one round the roll's `advance`, one round
 * the stage's `draw` — and an [ImageReader] standing in for the `SurfaceView`
 * (`TrayDriverTest` says why that is the same thing to Filament).
 *
 * What is timed, and what is not, is `RenderedFrames`'s to say and is said
 * there; the arithmetic and the scoring are plain Kotlin in
 * `:simulation:harness` (`docs/architecture.md`, decisions 53 and 80).
 *
 * ### Running it
 *
 * The scored run does nothing without `harness.rolls` or `harness.soak`, like
 * `HarnessTest`, and declines with an assumption that `DeviceTestCounts` reads
 * as a skip. `tools/harness.sh --rendered` is the way in. The one-roll check
 * beside it always runs, so the ordinary device suite still proves the path
 * draws and times frames, without scoring them.
 */
@RunWith(AndroidJUnit4::class)
class RenderedHarnessTest {
  @Before
  fun ready() {
    FilamentStage.ready()
  }

  @Test
  fun oneRollIsDrawnAndTimedOnARealSurface() {
    // One throw and no bar: this is the suite checking that the harness can
    // run at all, on whatever GPU the device has — the emulator's included,
    // which is nowhere near the budget and is not asked to be.
    val request =
      HarnessRequest(
        label = "check",
        shape = HarnessRequest.DEFAULT_SHAPE,
        diceCount = HarnessRequest.DEFAULT_DICE,
        length = RunLength.Rolls(1),
        seed = HarnessRequest.DEFAULT_SEED,
      )
    val run = draw(request, request.plan())
    val summary = requireNotNull(run.frames.summary()) { "not one frame of the roll was drawn" }

    assertTrue("a whole roll went past in ${summary.frames.frames} frames", summary.frames.frames > 1)
    assertTrue("the roll never finished", run.rolls == 1)
  }

  @Test
  fun aRunOfDrawnRollsMeetsTheFrameBudget() {
    val arguments = InstrumentationRegistry.getArguments()
    val asked = HarnessRequest.from { arguments.getString(it) }
    assumeTrue(
      "no run was asked for; pass -e ${HarnessRequest.ROLLS} <n> (tools/harness.sh --rendered)",
      asked != null,
    )
    val request = requireNotNull(asked)
    val run = draw(request, request.plan(table = lookOf(request.table)))

    val report =
      RenderedReport(
        label = RenderedReport.labelOf(request.label),
        shape = request.shape,
        diceCount = request.diceCount,
        rolls = run.rolls,
        width = run.width,
        height = run.height,
        device =
          DeviceFacts.of(
            model = Build.MODEL,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            androidApi = Build.VERSION.SDK_INT,
            hardware = Build.HARDWARE,
          ),
        summary = run.frames.summary(),
      )
    val text = report.text()
    write(report.label, text)
    println(text)

    assertTrue(text, report.scorecard.passed)
  }

  /**
   * The built-in look [id] names, or the plain physics-only look when it
   * names none (`tools/harness.sh --table`). A glossy look draws the dice a
   * second time, for the reflection, so which table a run is on is part of
   * what it measures (`docs/physics-and-rendering.md`, "The dice in a glossy
   * table").
   */
  private fun lookOf(id: String?): TableLook =
    id?.let {
      requireNotNull(BuiltinDiceSet.set.table(it)) {
        "the built-in package has no table called \"$it\"; it has " +
          BuiltinDiceSet.set.tables.joinToString { table -> table.id }
      }
    } ?: HarnessRequest.PLAIN

  /** What a run drew: how many throws, on how big a surface, and their frames. */
  private class Run(
    val rolls: Int,
    val width: Int,
    val height: Int,
    val frames: RenderedFrames,
  )

  /**
   * Every roll [request] asks for, thrown onto a surface the size of the
   * screen, one after the other.
   *
   * A throw that leaves dice unread is followed at once by the throw of those
   * dice, as `HarnessTest` does it ([Passes.scripted]): the screen would wait
   * for a shake, and there is no hand here to give one.
   */
  private fun draw(
    request: HarnessRequest,
    plan: HarnessPlan,
  ): Run {
    val (width, height) = screenSize()
    val frames = RenderedFrames()
    val simulator = JoltDiceSimulator()
    val reader =
      ImageReader.newInstance(
        width,
        height,
        PixelFormat.RGBA_8888,
        BUFFERS,
        HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
      )
    // Every frame is taken off the other end as it arrives. A buffer nobody
    // takes is one Filament cannot draw the next frame into, and the run would
    // be timing a queue rather than a phone.
    val consumer = HandlerThread("rendered-harness-frames").apply { start() }
    reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(consumer.looper))

    var rolls = 0
    try {
      RollThread().use { host ->
        TrayDriver(shared = host, boards = NO_DROP) { surface, w, h ->
          TimedStage(host.filament().stage(surface, w, h), frames)
        }.use { driver ->
          driver.surfaceAvailable(reader.surface, width, height)
          driver.table(plan.geometry, plan.table)
          val started = System.nanoTime()
          do {
            Passes.scripted(plan.specFor(rolls)) { pass -> throwOnce(driver, simulator, pass, frames) }
            rolls++
          } while (request.length.keepGoing(rolls, (System.nanoTime() - started) / NANOS_PER_SECOND))
        }
      }
    } finally {
      reader.close()
      consumer.quitSafely()
    }
    // Closing the driver waited for the roll thread, so the frames are
    // finished being written by the time they are read.
    return Run(rolls, width, height, frames)
  }

  /** One pass, thrown through the tray and waited for. Null when it gave up. */
  private fun throwOnce(
    driver: TrayDriver,
    simulator: JoltDiceSimulator,
    pass: ThrowSpec,
    frames: RenderedFrames,
  ): SimulationOutcome? {
    val over = CountDownLatch(1)
    var landed: SimulationOutcome? = null
    driver.roll(
      // Listening, because the roll screen listens whenever haptics or sound
      // is on, and writing the impacts down is part of a frame's simulation.
      start = { renderer -> TimedRoll(simulator.start(pass, renderer, listening = true), frames) },
      onCounted = {},
      onStalled = { over.countDown() },
      onSettled = { outcome, _ ->
        landed = outcome
        over.countDown()
      },
    )
    assertTrue("a pass was still going after $PATIENCE_SECONDS s", over.await(PATIENCE_SECONDS, TimeUnit.SECONDS))
    return landed
  }

  /**
   * The screen's size in pixels, upright.
   *
   * The whole display rather than the tray's share of it, which is a little
   * smaller: the GPU's work grows with the pixels it fills, so a surface the
   * size of the screen is the most the tray could ever ask of it, and a run
   * that meets the budget there meets it on the screen.
   */
  private fun screenSize(): Pair<Int, Int> {
    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    val display =
      requireNotNull(context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)) {
        "this device has no default display to size a surface by"
      }
    val metrics = context.createDisplayContext(display).resources.displayMetrics
    return minOf(metrics.widthPixels, metrics.heightPixels) to maxOf(metrics.widthPixels, metrics.heightPixels)
  }

  private fun write(
    label: String,
    text: String,
  ) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory =
      requireNotNull(context.getExternalFilesDir(null)) {
        "this device has no external files directory, so a run has nowhere to leave its numbers"
      }
    File(directory, "harness-$label.txt").writeText(text + "\n")
  }

  /**
   * The roll the screen would watch, with a stopwatch round each frame.
   *
   * `advance` is the whole of a frame's work on the roll thread: the steps the
   * frame earns, then the renderer placing the dice and drawing them. Timing
   * it from outside changes nothing about what it does.
   */
  private class TimedRoll(
    private val live: LiveRoll,
    private val frames: RenderedFrames,
  ) : WatchedRoll by live {
    override fun advance(elapsedSeconds: Double): RenderFrame {
      val begin = System.nanoTime()
      val frame = live.advance(elapsedSeconds)
      frames.framed(beginNanos = begin, workNanos = System.nanoTime() - begin)
      return frame
    }

    override fun close() {
      frames.passEnded(live.droppedSteps)
      live.close()
    }
  }

  /** The roll screen's stage, reading Filament's frame history after each draw. */
  private class TimedStage(
    private val real: FilamentStage,
    private val frames: RenderedFrames,
  ) : Stage by real {
    override fun draw(): Boolean {
      val drawn = real.draw()
      if (drawn) real.gpuFrames(frames::gpu) else frames.skippedDraw()
      return drawn
    }

    override fun close() = real.close()
  }

  private companion object {
    /** The tray here is never asked to drop a die onto the board. */
    val NO_DROP: BoardSettler = BoardSettler { BoardTrack.EMPTY }

    const val BUFFERS = 3

    /** A pass at the watched pace runs to its twelve-second cap in about fifteen. */
    const val PATIENCE_SECONDS = 60L
    const val NANOS_PER_SECOND = 1_000_000_000.0
  }
}
