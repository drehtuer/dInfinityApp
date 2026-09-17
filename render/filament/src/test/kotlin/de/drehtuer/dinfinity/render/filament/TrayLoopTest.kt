package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableSound
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.DebugWatch
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Impacts
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.Struck
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the tray does frame by frame.
 *
 * None of this needs a device: a frame callback is a number of nanoseconds, a
 * surface is a stage that may or may not be there, and a roll is something
 * that can be advanced. What needs a device is the thread, the vsync and the
 * GPU, and that is [TrayDriver] — this is everything else
 * (`docs/architecture.md`, decision 40).
 */
class TrayLoopTest {
  private val geometry = TableGeometry.referenceDevice()
  private val look = TableLook(id = "plain", name = "Plain")

  @Test
  fun `the first frame of a roll is worth no time at all`() {
    // There is no frame before it to measure against. Measuring from zero
    // would hand the clock however long the device has been awake, spend the
    // whole catch-up budget on frame one, and start the roll already late.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.frame(SOME_LATE_UPTIME)

    assertEquals(listOf(0.0), roll.advanced)
  }

  @Test
  fun `a frame is worth the time since the frame before it`() {
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.frame(SOME_LATE_UPTIME)
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)
    loop.frame(SOME_LATE_UPTIME + 3 * SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals(3, roll.advanced.size)
    assertEquals(1.0 / 60.0, roll.advanced[1], EPSILON)
    assertEquals(2.0 / 60.0, roll.advanced[2], EPSILON)
  }

  @Test
  fun `a frame clock that jumped backwards is worth no time rather than negative time`() {
    // The frame clock would refuse a negative length of time outright, and
    // taking the app down because a counter wrapped is not a trade worth
    // making.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.frame(SOME_LATE_UPTIME)
    loop.frame(SOME_LATE_UPTIME - SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals(0.0, roll.advanced.last(), EPSILON)
  }

  @Test
  fun `a second throw replaces the first rather than landing on top of it`() {
    val loop = TrayLoop()
    val first = FakeRoll(steps = 100)
    val second = FakeRoll(steps = 100)
    loop.stage(FakeStage())

    loop.roll(first.start())
    loop.frame(SOME_LATE_UPTIME)
    loop.roll(second.start())
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)

    assertTrue("the roll being replaced was not closed", first.closed)
    assertEquals("the first roll was still being stepped", 1, first.advanced.size)
    assertEquals("the second roll's first frame was not its first", listOf(0.0), second.advanced)
  }

  @Test
  fun `a roll that has finished stops asking for frames`() {
    // The dice have stopped and nothing may touch them, so there is nothing
    // left to draw — and a frame callback that kept arriving would keep this
    // thread awake for as long as the screen was on.
    //
    // One frame after the last is not that callback: the roll's own final
    // frame is the one that stays on screen and the one with nothing after it
    // to cover for a skip, so it is asked for until it lands and then never
    // again.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 2)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    assertTrue(loop.frame(SOME_LATE_UPTIME))
    assertTrue("the last picture of the roll was never made sure of", loop.frame(SOME_LATE_UPTIME + 1))
    assertFalse("the loop asked for another frame after the dice stopped", loop.frame(SOME_LATE_UPTIME + 2))
    assertFalse(loop.rolling)
  }

  @Test
  fun `a table with nothing on it is drawn once, and then left alone`() {
    // What a player sees before they have thrown anything. It does not move,
    // so it is worth exactly one frame (`docs/TODO.md`, Step 4.1).
    val stage = FakeStage()
    val loop = TrayLoop()
    loop.stage(stage)

    loop.table(TableGeometry.referenceDevice(), TableLook(id = "plain", name = "Plain"))

    assertTrue("the empty table was never drawn", loop.wantsFrames)
    assertFalse("the table kept asking for frames", loop.frame(SOME_LATE_UPTIME))
    assertEquals(1, stage.frames)
    assertFalse(loop.rolling)
  }

  @Test
  fun `a still picture is asked for again until a frame actually lands`() {
    // Filament may decline the frame it is offered. A roll would simply draw
    // the next one; a table that is not moving has no next one, so the debt
    // stands until it is paid.
    val stage = FakeStage()
    stage.refuseFrames = true
    val loop = TrayLoop()
    loop.stage(stage)
    loop.table(TableGeometry.referenceDevice(), TableLook(id = "plain", name = "Plain"))

    assertTrue("a skipped frame settled the debt", loop.frame(SOME_LATE_UPTIME))
    assertTrue(loop.frame(SOME_LATE_UPTIME + 1))

    stage.refuseFrames = false
    assertFalse("the frame that landed did not settle the debt", loop.frame(SOME_LATE_UPTIME + 2))
  }

  @Test
  fun `a new surface is drawn to even when nothing is happening`() {
    // Turning the phone between throws. Nothing is moving, so nothing would
    // produce a frame on its own, and the new surface would stay black.
    val loop = TrayLoop()
    loop.table(TableGeometry.referenceDevice(), TableLook(id = "plain", name = "Plain"))

    val stage = FakeStage()
    loop.stage(stage)

    assertTrue(loop.wantsFrames)
    loop.frame(SOME_LATE_UPTIME)
    assertEquals("the surface that arrived was never painted", 1, stage.frames)
  }

  @Test
  fun `with nowhere to draw there is nothing to ask for`() {
    val loop = TrayLoop()
    loop.table(TableGeometry.referenceDevice(), TableLook(id = "plain", name = "Plain"))

    assertFalse("a tray with no surface asked for a frame", loop.wantsFrames)
    assertFalse(loop.frame(SOME_LATE_UPTIME))
  }

  @Test
  fun `what the dice came to is reported once, when they stop`() {
    val loop = TrayLoop()
    val reported = mutableListOf<SimulationOutcome>()
    loop.stage(FakeStage())
    loop.roll(FakeRoll(steps = 2).start()) { outcome, _ -> reported += outcome }

    loop.frame(SOME_LATE_UPTIME)
    assertTrue("a roll still in the air reported a result", reported.isEmpty())
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)
    repeat(SPARE_FRAMES) { loop.frame(SOME_LATE_UPTIME + 2 * SIXTIETH_OF_A_SECOND_NANOS) }

    assertEquals("the result arrived more than once", 1, reported.size)
  }

  @Test
  fun `the shake that drove the roll comes back with what the dice came to`() {
    // Read off the roll before it is closed, which is the only moment it can
    // be: a roll is given up the instant it is read, and the record of a throw
    // belongs to the roll that collected it
    // (`docs/physics-and-rendering.md`, "Shake input").
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 2)
    val hand = List(2) { ShakeSample(it, Vector3(5_000.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0)) }
    var drove: List<ShakeSample>? = null
    loop.stage(FakeStage())
    loop.roll(roll.start()) { _, shake -> drove = shake }

    hand.forEach(loop::shake)
    loop.frame(SOME_LATE_UPTIME)
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals(hand, drove)
    assertTrue("the roll was read but never given up", roll.closed)
  }

  @Test
  fun `a roll abandoned before it landed reports nothing`() {
    // The player left the screen. Nothing landed, so there is nothing to
    // score — and a half-finished roll must never become a total.
    val loop = TrayLoop()
    val reported = mutableListOf<SimulationOutcome>()
    loop.stage(FakeStage())
    loop.roll(FakeRoll(steps = 100).start()) { outcome, _ -> reported += outcome }
    loop.frame(SOME_LATE_UPTIME)

    loop.clear()
    loop.close()

    assertTrue("an abandoned roll produced a result", reported.isEmpty())
  }

  @Test
  fun `a frame with no roll to advance is not asked for again`() {
    val loop = TrayLoop()
    loop.stage(FakeStage())

    assertFalse(loop.frame(SOME_LATE_UPTIME))
  }

  @Test
  fun `losing the surface closes the stage and leaves the roll alone`() {
    // Backgrounding the app does not stop the dice. It stops the drawing.
    val loop = TrayLoop()
    val stage = FakeStage()
    val roll = FakeRoll(steps = 100)
    loop.stage(stage)
    loop.roll(roll.start())

    loop.surfaceLost()

    assertTrue("the stage was left holding a surface that has gone", stage.closed)
    assertFalse(roll.closed)
    assertTrue("the roll was ended with the surface", loop.rolling)
    // And it keeps asking for frames, because the frame callback is what steps
    // the simulation. A roll that stops being asked is a roll that stops — and
    // one stopped half way is never read and never over, which is a screen
    // stuck on "Rolling…" for good.
    assertTrue("the roll was left stranded with nobody to step it", loop.wantsFrames)
  }

  @Test
  fun `a roll that loses its surface still finishes`() {
    // The whole of why the rule above matters. The app is backgrounded, the
    // screen blanks, the view is resized — and the throw has to run to its end
    // regardless, because nothing else is going to finish it.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 4)
    val reported = mutableListOf<SimulationOutcome>()
    loop.stage(FakeStage())
    loop.roll(roll.start()) { outcome, _ -> reported += outcome }

    loop.surfaceLost()
    var frames = 0
    var nanos = SOME_LATE_UPTIME
    while (loop.wantsFrames && frames++ < PATIENCE_FRAMES) {
      nanos += SIXTIETH_OF_A_SECOND_NANOS
      loop.frame(nanos)
    }

    assertFalse("the roll never finished with nobody watching", loop.rolling)
    assertEquals("a roll nobody watched reported nothing", 1, reported.size)
  }

  @Test
  fun `a roll goes on while there is nowhere to draw it`() {
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 100)
    loop.stage(FakeStage())
    loop.roll(roll.start())
    loop.surfaceLost()

    loop.frame(SOME_LATE_UPTIME)
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals("a roll nobody is watching stopped being stepped", 2, roll.advanced.size)
  }

  @Test
  fun `a roll that has landed is still on screen when the surface comes back`() {
    // The screensaver. The surface goes, the surface returns, and the dice are
    // where they stopped — without the simulation being asked for anything,
    // because a roll that has ended cannot be asked.
    val loop = TrayLoop()
    loop.stage(FakeStage())
    loop.roll(FakeRoll(steps = 1).start())
    loop.frame(SOME_LATE_UPTIME)
    assertFalse("the roll did not land", loop.rolling)

    loop.surfaceLost()
    val returned = FakeStage()
    loop.stage(returned)

    assertTrue("the tray came back empty after a roll had landed", returned.added.isNotEmpty())
    assertTrue("the dice came back but were put nowhere", returned.placed.isNotEmpty())
    assertTrue("the returned tray was left dark", returned.lit)
  }

  @Test
  fun `giving up the tray takes the picture with it`() {
    val loop = TrayLoop()
    loop.stage(FakeStage())
    loop.roll(FakeRoll(steps = 1).start())
    loop.frame(SOME_LATE_UPTIME)

    loop.close()

    val afterwards = FakeStage()
    TrayLoop().stage(afterwards)
    assertTrue("a closed loop left a scene behind", afterwards.added.isEmpty())
  }

  @Test
  fun `a new stage closes the one it replaces`() {
    // Turning the phone. Filament fixes its viewport when a stage is made, so
    // this happens on every resize, and a stage left behind is an engine left
    // behind.
    val loop = TrayLoop()
    val first = FakeStage()
    loop.stage(first)

    loop.stage(FakeStage(width = 640, height = 320))

    assertTrue("the stage that was replaced was left open", first.closed)
  }

  @Test
  fun `clearing the tray ends the roll`() {
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 100)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.clear()

    assertTrue(roll.closed)
    assertFalse(loop.rolling)
  }

  @Test
  fun `closing the loop gives up the roll and the stage`() {
    val loop = TrayLoop()
    val stage = FakeStage()
    val roll = FakeRoll(steps = 100)
    loop.stage(stage)
    loop.roll(roll.start())

    loop.close()

    assertTrue("a physics world was left open", roll.closed)
    assertTrue("an engine was left open", stage.closed)
  }

  @Test
  fun `each frame's impacts are handed on as they happen`() {
    val heard = FakeImpacts()
    val loop = TrayLoop(heard)
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    roll.hits += impact(step = 1)
    loop.frame(SOME_LATE_UPTIME)
    roll.hits += impact(step = 2, die = 1)
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals(listOf(1, 1), heard.played.map { it.first.size })
    assertTrue("a watched tray spread its impacts over time", heard.played.all { it.second == 0.0 })
  }

  @Test
  fun `an impact is played once rather than once per frame after it`() {
    val heard = FakeImpacts()
    val loop = TrayLoop(heard)
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    roll.hits += impact(step = 1)
    repeat(THREE_FRAMES) { frame -> loop.frame(SOME_LATE_UPTIME + frame * SIXTIETH_OF_A_SECOND_NANOS) }

    assertEquals(1, heard.played.size)
  }

  @Test
  fun `a second throw starts its impacts again from the beginning`() {
    val heard = FakeImpacts()
    val loop = TrayLoop(heard)
    val first = FakeRoll(steps = 2)
    loop.stage(FakeStage())
    loop.roll(first.start())
    first.hits += impact(step = 1)
    loop.frame(SOME_LATE_UPTIME)

    val second = FakeRoll(steps = 2)
    loop.roll(second.start())
    second.hits += impact(step = 1)
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals(listOf(1, 1), heard.played.map { it.first.size })
  }

  @Test
  fun `a roll with nothing to play plays nothing`() {
    val heard = FakeImpacts()
    val loop = TrayLoop(heard)
    val roll = FakeRoll(steps = 3)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    repeat(THREE_FRAMES) { frame -> loop.frame(SOME_LATE_UPTIME + frame * SIXTIETH_OF_A_SECOND_NANOS) }

    assertTrue(heard.played.isEmpty())
  }

  @Test
  fun `the table says which sounds these impacts will be`() {
    val heard = FakeImpacts()

    TrayLoop(heard).table(geometry, look.copy(sound = TableSound.Glass))

    assertEquals(listOf(TableSound.Glass), heard.tables)
  }

  private fun impact(
    step: Int,
    die: Int = 0,
  ): Impact =
    Impact(
      stepIndex = step,
      dieIndex = die,
      struck = Struck.Floor,
      speedChangeMmPerSecond = 600.0,
      dieSizeMm = 16.0,
    )

  /** Something that plays impacts and only remembers being asked to. */
  private class FakeImpacts : Impacts {
    val tables = mutableListOf<TableSound>()
    val played = mutableListOf<Pair<List<Impact>, Double>>()

    override fun on(sound: TableSound) {
      tables += sound
    }

    override fun play(
      impacts: List<Impact>,
      overSeconds: Double,
    ) {
      played += impacts to overSeconds
    }
  }

  /**
   * A roll that finishes after a fixed number of frames and remembers what it
   * was handed. No physics: what this class decides is *when* a roll is
   * advanced and by how much, which is the same question whatever is
   * underneath.
   */
  @Test
  fun `a tray nobody is debugging never asks the roll for a snapshot`() {
    // The whole reason the developer toggle costs nothing when it is off:
    // `DebugWatch.NONE` says it is not watching, and the loop asks that before
    // it asks the roll for anything.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.frame(SOME_LATE_UPTIME)
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals(0, roll.snapshotsAsked)
  }

  @Test
  fun `a tray being debugged is shown the roll on every frame`() {
    val seen = mutableListOf<RollDiagnostics>()
    val loop = TrayLoop(debug = DebugWatch { seen += it })
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.frame(SOME_LATE_UPTIME)
    loop.frame(SOME_LATE_UPTIME + SIXTIETH_OF_A_SECOND_NANOS)

    assertEquals(2, seen.size)
    assertEquals(listOf(1, 2), seen.map(RollDiagnostics::steps))
  }

  @Test
  fun `the last frame of a roll is watched too, so the numbers it stopped on stay`() {
    // The overlay is read after the dice have landed as much as during: what a
    // roll came to is exactly what somebody debugging wants to look at.
    val seen = mutableListOf<RollDiagnostics>()
    val loop = TrayLoop(debug = DebugWatch { seen += it })
    val roll = FakeRoll(steps = 1)
    loop.stage(FakeStage())
    loop.roll(roll.start())

    loop.frame(SOME_LATE_UPTIME)

    assertFalse("the roll should have finished on its only step", roll.running)
    assertEquals(1, seen.size)
  }

  @Test
  fun `a tray with nothing on it asks for no snapshot at all`() {
    // No roll, nothing to describe. A watcher that was handed an empty
    // snapshot every idle frame would be a watcher recomposing for nothing.
    val seen = mutableListOf<RollDiagnostics>()
    val loop = TrayLoop(debug = DebugWatch { seen += it })
    loop.stage(FakeStage())
    loop.table(geometry, look)

    loop.frame(SOME_LATE_UPTIME)

    assertTrue(seen.isEmpty())
  }

  private inner class FakeRoll(
    private val steps: Int,
  ) : WatchedRoll {
    val advanced = mutableListOf<Double>()
    val shaken = mutableListOf<ShakeSample>()
    var closed = false
      private set

    private var watcher: Renderer? = null

    override val running: Boolean get() = advanced.size < steps

    override val outcome: SimulationOutcome?
      get() = if (running) null else SimulationOutcome(faces = mapOf(0 to 0))

    override val drivenBy: List<ShakeSample> get() = shaken.toList()

    /**
     * One more die read per step, the way a real roll counts them off and takes
     * them away as it goes.
     */
    override val countedSoFar: Map<Int, Int>
      get() = (0 until minOf(advanced.size, steps)).associateWith { 0 }

    /** Impacts a test pushes in, as a real roll would accumulate them. */
    val hits = mutableListOf<Impact>()

    override val impacts: List<Impact> get() = hits

    /**
     * How often a snapshot was asked for.
     *
     * Counted rather than returned blindly, because the promise the loop makes
     * is that a tray nobody is debugging never asks — building one means
     * walking every die (`docs/physics-and-rendering.md`, "Debug tooling").
     */
    var snapshotsAsked = 0
      private set

    override val diagnostics: RollDiagnostics
      get() {
        snapshotsAsked++
        return RollDiagnostics(steps = advanced.size)
      }

    override fun advance(elapsedSeconds: Double): RenderFrame {
      advanced += elapsedSeconds
      val frame = frame()
      // Shown to the renderer exactly as a real roll shows it. A fake that
      // skipped this would let a loop that never drew anything pass.
      if (running) watcher?.show(frame) else watcher?.settled(frame)
      return frame
    }

    override fun shake(sample: ShakeSample) {
      shaken += sample
    }

    override fun close() {
      closed = true
    }

    /** The roll as the loop asks for it: begun on whichever renderer it has. */
    fun start(): (Renderer) -> WatchedRoll =
      { renderer ->
        watcher = renderer
        renderer.begin(spec(), geometry, look)
        this
      }
  }

  @Test
  fun `dice put on the board are owed a frame like any other still picture`() {
    // Nothing else here would produce one, so without it the dice would not
    // appear until something else happened to draw.
    val loop = TrayLoop()
    val stage = FakeStage()
    loop.stage(stage)
    loop.table(geometry, look)
    val drawn = stage.frames

    loop.waiting(spec())
    loop.frame(SOME_LATE_UPTIME)

    assertTrue("the board was never drawn", stage.frames > drawn)
  }

  @Test
  fun `a roll in the air keeps the board`() {
    // The board is what a player arranges *between* throws. A formula edited
    // while the dice are still moving is for the throw after this one.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 10)
    loop.stage(FakeStage())
    loop.table(geometry, look)
    loop.roll(roll.start())

    loop.waiting(spec())

    assertTrue("the board replaced a roll that was still going", loop.rolling)
  }

  @Test
  fun `the dice counted so far are passed on as they are read`() {
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 4)
    val read = mutableListOf<Map<Int, Int>>()
    loop.stage(FakeStage())
    loop.roll(roll.start(), onCounted = { read += it })

    repeat(3) { loop.frame(SOME_LATE_UPTIME + it) }

    assertTrue("nothing was ever reported as counted", read.isNotEmpty())
  }

  @Test
  fun `the same reading is not reported twice`() {
    // This runs every frame, and a roll whose dice are all still in the air
    // would otherwise post the same empty map at the screen's thread a hundred
    // and twenty times a second.
    val loop = TrayLoop()
    val roll = FakeRoll(steps = 20)
    val read = mutableListOf<Map<Int, Int>>()
    loop.stage(FakeStage())
    loop.roll(roll.start(), onCounted = { read += it })

    repeat(5) { loop.frame(SOME_LATE_UPTIME + it) }

    assertEquals("the same reading was posted more than once", read.size, read.distinct().size)
  }

  private fun spec(): ThrowSpec =
    ThrowSpec(
      dice =
        List(DICE) { index ->
          DieInstance(
            index = index,
            groupId = 0,
            setId = "builtin",
            requestedSetId = "builtin",
            die = StandardDice.d6,
          )
        },
      geometry = geometry,
      table = look,
      seed = 3L,
    )

  private fun frame(): RenderFrame =
    RenderFrame.still(
      List(DICE) { index ->
        BodyTransform(index = index, position = Vector3(index * 20.0, 0.0, 8.0), orientation = Quaternion.Identity)
      },
    )

  private companion object {
    const val DICE = 2
    const val EPSILON = 1e-9

    /** A phone that has been awake for a day, which is what a frame clock counts from. */
    const val SOME_LATE_UPTIME = 86_400_000_000_000L
    const val SIXTIETH_OF_A_SECOND_NANOS = 16_666_667L
    const val SPARE_FRAMES = 3
    const val THREE_FRAMES = 3

    /** Enough frames for a short roll, and a bound so a stranded one fails rather than hangs. */
    const val PATIENCE_FRAMES = 50
  }
}
