package de.drehtuer.dinfinity.feature.roll

import android.os.Looper
import android.view.Surface
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.SavedRollSource
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.DiceSimulator
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The screen's state, and the one thing about it that is not obvious: a roll
 * happens on another thread and has to come back.
 *
 * Robolectric because a presenter reaches the main looper to bring a result
 * home. Everything else it does is [RollMachine], which is tested without one.
 */
@RunWith(RobolectricTestRunner::class)
class RollPresenterTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")
  private val oak = TableLook(id = "oak", name = "Oak")
  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  @Test
  fun `a throw goes to the tray and the result comes back to the screen`() {
    val rolls = RecordingRolls(faces = mapOf(0 to 5, 1 to 5, 2 to 5))
    val presenter = presenter(rolls)

    presenter.type("3d6")
    presenter.roll()

    assertEquals("the throw never reached the tray", 1, rolls.started.size)
    assertEquals(
      3,
      rolls.started
        .single()
        .dice.size,
    )
    val settled = presenter.state as RollState.Settled
    assertEquals("three sixes", 18L, settled.result.total)
  }

  @Test
  fun `the screen says it is rolling while the dice are in the air`() {
    val rolls = RecordingRolls(faces = mapOf(0 to 0), landImmediately = false)
    val presenter = presenter(rolls)

    presenter.type("1d20")
    presenter.roll()

    assertTrue("the screen did not say the dice were in the air", presenter.state is RollState.Rolling)
  }

  @Test
  fun `a formula the table cannot hold never reaches the tray`() {
    val rolls = RecordingRolls(faces = emptyMap())
    val presenter = presenter(rolls)

    presenter.type("500d6")
    presenter.roll()

    assertTrue(presenter.state is RollState.TooMany)
    assertEquals("a refused roll opened a physics world", 0, rolls.started.size)
  }

  @Test
  fun `a shake with nothing typed throws nothing`() {
    val rolls = RecordingRolls(faces = emptyMap())
    val presenter = presenter(rolls)

    presenter.roll(shake = emptyList())

    assertEquals(RollState.Empty, presenter.state)
    assertEquals(0, rolls.started.size)
  }

  @Test
  fun `the field keeps what was typed`() {
    val presenter = presenter(RecordingRolls(faces = emptyMap()))

    presenter.type("2d20kh1")

    assertEquals("2d20kh1", presenter.text)
  }

  @Test
  fun `rolling again puts the total away and keeps the formula`() {
    val rolls = RecordingRolls(faces = mapOf(0 to 0))
    val presenter = presenter(rolls)
    presenter.type("1d20")
    presenter.roll()

    presenter.clear()

    assertTrue(presenter.state is RollState.Ready)
    assertEquals("1d20", presenter.text)
  }

  @Test
  fun `a shake throws again without the total having to be put away first`() {
    // A shake cannot press "Roll again" first. Before this, shaking after a
    // roll did nothing at all, which is what it looked like on the phone.
    val rolls = RecordingRolls(faces = mapOf(0 to 0))
    val presenter = presenter(rolls)
    presenter.type("1d20")
    presenter.roll()
    assertTrue(presenter.state is RollState.Settled)

    presenter.roll()

    assertEquals("a shake after a roll threw nothing", 2, rolls.started.size)
    assertTrue(presenter.state is RollState.Settled)
  }

  @Test
  fun `rounding again redraws the total without touching the dice`() {
    val rolls = RecordingRolls(faces = mapOf(0 to 6))
    val presenter = presenter(rolls)
    presenter.type("1d20 / 3")
    presenter.roll()

    presenter.round(Rounding.Up)

    val settled = presenter.state as RollState.Settled
    assertEquals("the dice were thrown again to change the rounding", 1, rolls.started.size)
    assertEquals(Rounding.Up, settled.rounding())
    assertEquals(3L, settled.result.total)
  }

  @Test
  fun `by default the result comes home on the thread the screen is read on`() {
    // The roll happens on the roll thread; Compose reads its state on the main
    // one. The default hand-off is a post to the main looper, and a result that
    // skipped it would be a race nobody sees until a phone is slow enough
    // (`docs/architecture.md`, "Threading").
    val rolls = RecordingRolls(faces = mapOf(0 to 0))
    val presenter =
      RollPresenter(
        machine = machine(),
        driver = DirectTray(),
        rolls = rolls,
      )

    presenter.type("1d20")
    presenter.roll()

    assertTrue("a result arrived before the main thread ran it", presenter.state is RollState.Rolling)
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue("the result never reached the screen", presenter.state is RollState.Settled)
  }

  @Test
  fun `the tray is told about the table before anything is thrown`() {
    // Otherwise the screen opens on a black rectangle and stays that way until
    // the first roll (`docs/TODO.md`, Step 4.1).
    val tray = DirectTray()

    RollPresenter(
      machine = machine(),
      driver = tray,
      rolls = RecordingRolls(faces = emptyMap()),
      toTheScreen = { it() },
    )

    assertEquals(listOf(geometry to table), tray.tabled)
  }

  @Test
  fun `tapping a saved roll that pins a table puts that table under the dice`() {
    // The tray is built when the screen opens, so a pinned table has to reach
    // it afterwards or the dice land on one table and are drawn on another
    // (`docs/tables.md`, "Selecting a table").
    val tray = DirectTray()
    val presenter = presenterOn(tray)

    presenter.typeSaved("8d6", SavedRollSource("fireball", "thorin", TablePin("brass", "oak")))

    assertEquals(listOf(geometry to table, geometry to oak), tray.tabled)
  }

  @Test
  fun `typing over it puts the app's table back`() {
    val tray = DirectTray()
    val presenter = presenterOn(tray)
    presenter.typeSaved("8d6", SavedRollSource("fireball", "thorin", TablePin("brass", "oak")))

    presenter.type("8d6 + 1")

    assertEquals(listOf(geometry to table, geometry to oak, geometry to table), tray.tabled)
  }

  @Test
  fun `a keystroke that changes nothing about the table does not rebuild the tray`() {
    // A scene is rebuilt when the tray is told about a table, so telling it on
    // every keystroke would rebuild one on every keystroke.
    val tray = DirectTray()
    val presenter = presenterOn(tray)

    presenter.type("1d20")
    presenter.type("1d20 + 1")
    presenter.type("2d20 + 1")

    assertEquals("the tray was rebuilt for a table that never changed", 1, tray.tabled.size)
  }

  private fun presenterOn(tray: DirectTray) =
    RollPresenter(
      machine = machine(),
      driver = tray,
      rolls = RecordingRolls(faces = emptyMap()),
      toTheScreen = { it() },
    )

  private fun RollState.Settled.rounding(): Rounding = result.rounding

  private fun machine() =
    RollMachine(
      catalog = catalog,
      geometry = geometry,
      look = { pin -> if (pin == TablePin("brass", "oak")) oak else table },
      simulator =
        object : DiceSimulator {
          override fun run(spec: ThrowSpec) = SimulationOutcome(faces = spec.dice.indices.associateWith { 0 })
        },
      outside = Outside(seeds = { 1L }, clock = { 0L }),
    )

  @Test
  fun `a throw that lands is handed over to be written down`() {
    // Nothing recorded a roll before this: the statistics tables existed and
    // were never written to (`docs/statistics.md`).
    val written = mutableListOf<FinishedThrow>()
    val presenter = presenter(RecordingRolls(faces = mapOf(0 to 5, 1 to 5)), { written += it })

    presenter.type("2d6")
    presenter.roll()

    assertEquals(1, written.size)
    assertEquals(12L, written.single().result.total)
    // The plan goes with it, because the result knows which face came up and
    // only the plan knows which die it was.
    assertEquals(
      2,
      written
        .single()
        .plan.dice.size,
    )
  }

  @Test
  fun `the seed goes with it, for a bug report that needs the throw back`() {
    val written = mutableListOf<FinishedThrow>()
    val rolls = RecordingRolls(faces = mapOf(0 to 5))
    val presenter = presenter(rolls, { written += it })

    presenter.type("1d6")
    presenter.roll()

    assertEquals(rolls.started.single().seed, written.single().seed)
  }

  @Test
  fun `re-rounding the same throw does not write it down again`() {
    // The dice do not move, so it is the same roll. A history with one row per
    // rounding somebody tried would be a history of the buttons pressed.
    val written = mutableListOf<FinishedThrow>()
    val presenter = presenter(RecordingRolls(faces = mapOf(0 to 5)), { written += it })
    presenter.type("1d6 / 2")
    presenter.roll()

    presenter.round(Rounding.Up)
    presenter.round(Rounding.Nearest)

    assertEquals(1, written.size)
  }

  @Test
  fun `throwing again writes a second roll down`() {
    val written = mutableListOf<FinishedThrow>()
    val presenter = presenter(RecordingRolls(faces = mapOf(0 to 5)), { written += it })
    presenter.type("1d6")

    presenter.roll()
    presenter.roll()

    assertEquals(2, written.size)
  }

  @Test
  fun `the shake that threw the dice goes with the throw, ready to replay it`() {
    // The throw went out with an empty shake — the dice are spawned when the
    // shake is confirmed — and the sensors reported into it while it ran. What
    // is handed over has to be the two joined back together, because only that
    // rolls these dice again (`docs/physics-and-rendering.md`, "Shake input").
    val written = mutableListOf<FinishedThrow>()
    val rolls = RecordingRolls(faces = mapOf(0 to 5))
    val hand = hand(3)
    lateinit var presenter: RollPresenter
    presenter = presenter(rolls, { written += it }, DirectTray { hand.forEach(presenter::shaking) })

    presenter.type("1d6")
    presenter.roll()

    assertTrue(
      "the throw went out already knowing its shake",
      rolls.started
        .single()
        .shake
        .isEmpty(),
    )
    assertEquals(hand, written.single().thrown.shake)
    assertEquals(rolls.started.single().copy(shake = hand), written.single().thrown)
  }

  @Test
  fun `a tapped throw is written down with no shake at all`() {
    val written = mutableListOf<FinishedThrow>()
    val presenter = presenter(RecordingRolls(faces = mapOf(0 to 5)), { written += it })

    presenter.type("1d6")
    presenter.roll()

    assertTrue(
      written
        .single()
        .thrown.shake
        .isEmpty(),
    )
  }

  @Test
  fun `a roll that never lands is never written down, and nor is its shake`() {
    // The player left the screen with the dice in the air. Nothing landed, so
    // there is nothing to score and nothing to record — and the samples that
    // reached the roll go with the roll.
    val written = mutableListOf<FinishedThrow>()
    val rolls = RecordingRolls(faces = mapOf(0 to 5), landImmediately = false)
    val hand = hand(3)
    lateinit var presenter: RollPresenter
    presenter = presenter(rolls, { written += it }, DirectTray { hand.forEach(presenter::shaking) })

    presenter.type("1d6")
    presenter.roll()

    assertTrue("a roll that never landed was written down", written.isEmpty())
  }

  /** A hand moving sideways for [moments] simulation steps. */
  private fun hand(moments: Int): List<ShakeSample> =
    List(moments) { step ->
      ShakeSample(
        stepIndex = step,
        accelerationMmPerSecond2 = Vector3(5_000.0, 0.0, 0.0),
        gravity = Vector3(0.0, 0.0, -1.0),
      )
    }

  private fun presenter(
    rolls: RecordingRolls,
    recorder: ThrowRecorder = ThrowRecorder.NONE,
    tray: Tray = DirectTray(),
  ) = RollPresenter(
    machine = machine(),
    driver = tray,
    rolls = rolls,
    recorder = recorder,
    // Straight through, so the test sees what the screen would see without
    // having to pump a looper for it.
    toTheScreen = { it() },
  )

  /**
   * A tray that throws the dice where it stands.
   *
   * The real one hands the roll to its own thread and the result back from it.
   * What is under test here is *which* throw goes out and *what* comes back, so
   * the thread would only make the answer arrive later
   * (`docs/architecture.md`, decision 40).
   */
  private class DirectTray(
    /**
     * What the hand does while the dice are in the air.
     *
     * Called with the roll open and not yet stepped, which is where a shake
     * actually arrives: the dice are spawned when the shake is confirmed and
     * the sensors go on reporting into a throw that is already running
     * (`docs/physics-and-rendering.md`, "Shake input").
     */
    private val whileRolling: () -> Unit = {},
  ) : Tray {
    val shaken = mutableListOf<ShakeSample>()

    private var live: WatchedRoll? = null

    /** Every table this tray has been told about, in order. */
    val tabled = mutableListOf<Pair<TableGeometry, TableLook>>()

    /** Every view the player has asked for, in order. */
    val looked = mutableListOf<TrayView>()

    override fun surfaceAvailable(
      surface: Surface,
      width: Int,
      height: Int,
    ) = Unit

    override fun surfaceLost() = Unit

    override fun roll(
      start: (Renderer) -> WatchedRoll,
      onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
    ) {
      val roll = start(HeadlessRenderer())
      live = roll
      whileRolling()
      // Capped, because a fake roll that never lands is a test case here and
      // an unbounded loop is not a useful way to fail it.
      var frames = 0
      while (roll.running && frames++ < MOST_FRAMES) roll.advance(SettleRule.TIMESTEP_SECONDS)
      roll.outcome?.let { onSettled(it, roll.drivenBy) }
      live = null
      roll.close()
    }

    override fun shake(sample: ShakeSample) {
      shaken += sample
      live?.shake(sample)
    }

    override fun table(
      geometry: TableGeometry,
      look: TableLook,
    ) {
      tabled += geometry to look
    }

    override fun look(view: TrayView) {
      looked += view
    }

    override fun clear() = Unit

    override fun close() = Unit

    private companion object {
      const val MOST_FRAMES = 64
    }
  }

  /** Records the throws it is asked to open, and lands them on cue. */
  private class RecordingRolls(
    private val faces: Map<Int, Int>,
    private val landImmediately: Boolean = true,
  ) : Rolls {
    val started = mutableListOf<ThrowSpec>()

    override fun start(
      spec: ThrowSpec,
      watcher: Renderer,
    ): WatchedRoll {
      started += spec
      return object : WatchedRoll {
        private var landed = false
        private val drove = mutableListOf<ShakeSample>()

        override val running: Boolean get() = !landed

        override val outcome: SimulationOutcome? get() = if (landed) SimulationOutcome(faces = faces) else null

        override val drivenBy: List<ShakeSample> get() = drove.toList()

        override fun advance(elapsedSeconds: Double): RenderFrame {
          landed = landImmediately
          return RenderFrame.still(
            spec.dice.indices.map { BodyTransform(it, Vector3(0.0, 0.0, 8.0), Quaternion.Identity) },
          )
        }

        override fun shake(sample: ShakeSample) {
          drove += sample
        }

        override fun close() = Unit
      }
    }
  }
}
