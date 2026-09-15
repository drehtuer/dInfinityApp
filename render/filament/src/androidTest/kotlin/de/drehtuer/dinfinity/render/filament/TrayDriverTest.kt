package de.drehtuer.dinfinity.render.filament

import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The tray on a real thread, drawing to a real surface (`docs/TODO.md`, Step
 * 3).
 *
 * Everything the tray *decides* is tested on a JVM, so what is left for a
 * device is the half a JVM cannot answer: does a background thread get vsync
 * callbacks at all, does Filament accept a `Surface` that belongs to something
 * else, and does the swap chain actually deliver a frame to it.
 *
 * The surface is an [ImageReader]'s rather than a `SurfaceView`'s, because a
 * `SurfaceView` needs a window and a window needs an activity, and none of
 * that would make this test say anything more: from Filament's side a surface
 * is a surface. What it does buy is the assertion at the end — a frame can be
 * taken off the other end, which is as close as an automated test gets to "it
 * appeared".
 */
@RunWith(AndroidJUnit4::class)
class TrayDriverTest {
  private val geometry = TableGeometry.referenceDevice()
  private val look = TableLook(id = "plain", name = "Plain")

  @Test
  fun aRollIsDrawnFrameByFrameOntoARealSurface() {
    val reader = surfaceReader()
    val roll = FakeRoll(FRAMES)
    val arrived = CountDownLatch(1)
    val listening = HandlerThread("frames-arriving").apply { start() }
    reader.setOnImageAvailableListener({ arrived.countDown() }, Handler(listening.looper))
    val counted = mutableListOf<Counted>()
    val settled = CountDownLatch(1)

    try {
      TrayDriver { surface, width, height ->
        FilamentStage.ready()
        Counted(FilamentStage(width, height, surface)).also { counted += it }
      }.use { driver ->
        driver.surfaceAvailable(reader.surface, WIDTH, HEIGHT)
        driver.roll(roll.start()) { _, _ -> settled.countDown() }

        assertTrue(
          "the roll thread was never given a frame callback",
          roll.finished.await(PATIENCE_SECONDS, TimeUnit.SECONDS),
        )
        assertTrue(
          "the roll finished but what the dice came to was never reported",
          settled.await(PATIENCE_SECONDS, TimeUnit.SECONDS),
        )
        // Drawn one frame at a time off the display's own clock, which is the
        // thing that cannot be checked anywhere but here.
        assertTrue("a whole roll went past in one callback", roll.advanced.size > 1)
      }

      // Closing the driver waits for the roll thread, so the counts below are
      // finished being written.
      val drawn = counted.sumOf { it.drawn }
      val skipped = counted.sumOf { it.skipped }
      assertTrue("Filament skipped every one of $skipped frames", drawn > 0)

      assertTrue(
        "no frame reached the other end of the surface",
        arrived.await(PATIENCE_SECONDS, TimeUnit.SECONDS),
      )
      assertNotNull(reader.acquireLatestImage())
    } finally {
      reader.close()
      listening.quitSafely()
    }
  }

  @Test
  fun anEmptyTableReachesTheSurfaceWithNoRollAtAll() {
    // The screen before anything has been thrown. Nothing is moving, so no
    // roll is driving the frame clock — and a table that never reaches the
    // surface is the black rectangle this exists to stop
    // (`docs/TODO.md`, Step 4.1).
    val reader = surfaceReader()
    val arrived = CountDownLatch(1)
    val listening = HandlerThread("empty-table-arriving").apply { start() }
    reader.setOnImageAvailableListener({ arrived.countDown() }, Handler(listening.looper))

    try {
      TrayDriver().use { driver ->
        driver.surfaceAvailable(reader.surface, WIDTH, HEIGHT)
        driver.table(geometry, look)

        assertTrue(
          "an empty table never reached the other end of the surface",
          arrived.await(PATIENCE_SECONDS, TimeUnit.SECONDS),
        )
      }
      assertNotNull(reader.acquireLatestImage())
    } finally {
      reader.close()
      listening.quitSafely()
    }
  }

  @Test
  fun theEngineOutlivesOneVisitToTheScreen() {
    // The black tray on the way back from the menu. A driver per visit is
    // right — a driver owns a roll, and a roll the player walked away from
    // never landed — but an *engine* per visit means compiling the dice
    // material again, on the device, while they watch it happen.
    //
    // This is the same fix `FilamentEngine` already makes one level in, where
    // it keeps the engine across surfaces so a rotation does not rebuild it.
    // Keeping it across visits needs the thread kept too: Filament only takes
    // calls from the thread that made the engine.
    val listening = HandlerThread("two-visits").apply { start() }

    RollThread().use { host ->
      assertFalse("an engine was made before anything asked to draw", host.engineMade)

      drawATable(host, listening)
      assertTrue("the engine went out with the first visit", host.engineMade)

      // The second visit. If this one has to build its own engine, the player
      // is looking at black while it compiles the material again.
      drawATable(host, listening)
      assertTrue("the engine did not survive to the second visit", host.engineMade)
    }

    listening.quitSafely()
  }

  /** One visit to the screen: a driver, a surface, a table, and away again. */
  private fun drawATable(
    host: RollThread,
    listening: HandlerThread,
  ) {
    val reader = surfaceReader()
    val arrived = CountDownLatch(1)
    reader.setOnImageAvailableListener({
      it.acquireLatestImage()?.close()
      arrived.countDown()
    }, Handler(listening.looper))

    try {
      TrayDriver(shared = host).use { driver ->
        driver.surfaceAvailable(reader.surface, WIDTH, HEIGHT)
        driver.table(geometry, look)

        assertTrue(
          "the table never reached the other end of the surface",
          arrived.await(PATIENCE_SECONDS, TimeUnit.SECONDS),
        )
      }
    } finally {
      reader.close()
    }
  }

  @Test
  fun lookingCloserRedrawsWithNoRollToDriveTheClock() {
    // A pinch between throws. Nothing is moving and no roll is asking for
    // frames, so without the still-picture path the new camera would not
    // appear until something else happened to draw (`docs/TODO.md`, Step 4.1).
    val reader = surfaceReader()
    val listening = HandlerThread("looking-around").apply { start() }
    // Acquired as they arrive: the reader holds a fixed number of buffers, and
    // one nobody takes is one the next frame cannot be written into.
    var tabled = CountDownLatch(1)
    reader.setOnImageAvailableListener({
      it.acquireLatestImage()?.close()
      tabled.countDown()
    }, Handler(listening.looper))

    try {
      TrayDriver().use { driver ->
        driver.surfaceAvailable(reader.surface, WIDTH, HEIGHT)
        driver.table(geometry, look)
        assertTrue(
          "the empty table never reached the surface",
          tabled.await(PATIENCE_SECONDS, TimeUnit.SECONDS),
        )

        // Only now, so the pinch cannot be folded into the table's own frame:
        // two asks before one vsync are one draw, which is right and would
        // have made this test pass without proving anything.
        val pinched = CountDownLatch(1)
        tabled = pinched
        driver.look(TrayView(zoom = 2.0))

        assertTrue(
          "a pinch with no roll running never reached the surface",
          pinched.await(PATIENCE_SECONDS, TimeUnit.SECONDS),
        )
      }
    } finally {
      reader.close()
      listening.quitSafely()
    }
  }

  @Test
  fun aSurfaceTakenAwayMidRollLeavesNothingHoldingIt() {
    // The thing that crashes if it is got wrong: a `Surface` may not be
    // touched after the callback that withdrew it returns, so `surfaceLost`
    // has to block until the engine has let go.
    val reader = surfaceReader()
    val roll = FakeRoll(FRAMES * FRAMES)

    try {
      TrayDriver().use { driver ->
        driver.surfaceAvailable(reader.surface, WIDTH, HEIGHT)
        driver.roll(roll.start()) { _, _ -> }
        driver.surfaceLost()
      }
    } finally {
      reader.close()
    }
  }

  @Test
  fun aResizeReplacesTheStageWithoutTouchingTheRoll() {
    // Turning the phone. Filament fixes its viewport when a stage is made, so
    // this is a new engine and a rebuilt scene — and the roll must not notice.
    val first = surfaceReader()
    val second = surfaceReader(HEIGHT, WIDTH)
    val roll = FakeRoll(FRAMES)

    try {
      TrayDriver().use { driver ->
        driver.surfaceAvailable(first.surface, WIDTH, HEIGHT)
        driver.roll(roll.start()) { _, _ -> }
        driver.surfaceAvailable(second.surface, HEIGHT, WIDTH)

        assertTrue(
          "the roll stopped when the surface was replaced",
          roll.finished.await(PATIENCE_SECONDS, TimeUnit.SECONDS),
        )
      }
    } finally {
      first.close()
      second.close()
    }
  }

  private fun surfaceReader(
    width: Int = WIDTH,
    height: Int = HEIGHT,
  ): ImageReader =
    ImageReader.newInstance(
      width,
      height,
      PixelFormat.RGBA_8888,
      BUFFERS,
      HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
    )

  /** A stage that counts what Filament actually did with each frame. */
  private class Counted(
    private val real: Stage,
  ) : Stage by real {
    var drawn = 0
      private set

    var skipped = 0
      private set

    override fun draw(): Boolean = real.draw().also { if (it) drawn++ else skipped++ }

    override fun close() = real.close()
  }

  /**
   * A roll that finishes after a fixed number of frames. No physics here — but
   * it shows the renderer every frame, exactly as a real roll does, because
   * that is what makes anything draw at all.
   */
  private inner class FakeRoll(
    private val frames: Int,
  ) : WatchedRoll {
    val advanced = mutableListOf<Double>()
    val finished = CountDownLatch(1)

    private var watcher: Renderer? = null

    override val running: Boolean get() = advanced.size < frames

    override val outcome: SimulationOutcome?
      get() = if (running) null else SimulationOutcome(faces = mapOf(0 to 0))

    override val drivenBy: List<ShakeSample> = emptyList()

    override val impacts: List<Impact> = emptyList()

    override fun advance(elapsedSeconds: Double): RenderFrame {
      advanced += elapsedSeconds
      val frame = frame()
      if (running) watcher?.show(frame) else watcher?.settled(frame)
      if (!running) finished.countDown()
      return frame
    }

    override fun shake(sample: ShakeSample) = Unit

    override fun close() {
      watcher?.end()
      finished.countDown()
    }

    fun start(): (Renderer) -> WatchedRoll =
      { renderer ->
        watcher = renderer
        renderer.begin(spec(), geometry, look)
        this
      }
  }

  private fun spec(): ThrowSpec =
    ThrowSpec(
      dice =
        listOf(StandardDice.d20, StandardDice.d6).mapIndexed { index, die ->
          DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
        },
      geometry = geometry,
      table = look,
      seed = 5L,
    )

  private fun frame(): RenderFrame =
    RenderFrame.still(
      List(2) { index ->
        BodyTransform(
          index = index,
          position = Vector3(index * 30.0 - 15.0, 0.0, 12.0),
          orientation = Quaternion.Identity,
        )
      },
    )

  private companion object {
    const val WIDTH = 320
    const val HEIGHT = 640
    const val BUFFERS = 3
    const val FRAMES = 6
    const val PATIENCE_SECONDS = 20L
  }
}
