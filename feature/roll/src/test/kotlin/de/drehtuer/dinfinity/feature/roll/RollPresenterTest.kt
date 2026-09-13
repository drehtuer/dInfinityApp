package de.drehtuer.dinfinity.feature.roll

import android.os.Looper
import android.view.Surface
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.render.filament.Tray
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

  private fun RollState.Settled.rounding(): Rounding = result.rounding

  private fun machine() =
    RollMachine(
      catalog = catalog,
      geometry = geometry,
      table = table,
      simulator =
        object : DiceSimulator {
          override fun run(spec: ThrowSpec) = SimulationOutcome(faces = spec.dice.indices.associateWith { 0 })
        },
      seeds = { 1L },
      clock = { 0L },
    )

  private fun presenter(rolls: RecordingRolls) =
    RollPresenter(
      machine = machine(),
      driver = DirectTray(),
      rolls = rolls,
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
  private class DirectTray : Tray {
    val shaken = mutableListOf<ShakeSample>()

    override fun surfaceAvailable(
      surface: Surface,
      width: Int,
      height: Int,
    ) = Unit

    override fun surfaceLost() = Unit

    override fun roll(
      start: (Renderer) -> WatchedRoll,
      onSettled: (SimulationOutcome) -> Unit,
    ) {
      val live = start(HeadlessRenderer())
      // Capped, because a fake roll that never lands is a test case here and
      // an unbounded loop is not a useful way to fail it.
      var frames = 0
      while (live.running && frames++ < MOST_FRAMES) live.advance(SettleRule.TIMESTEP_SECONDS)
      live.outcome?.let(onSettled)
      live.close()
    }

    override fun shake(sample: ShakeSample) {
      shaken += sample
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

        override val running: Boolean get() = !landed

        override val outcome: SimulationOutcome? get() = if (landed) SimulationOutcome(faces = faces) else null

        override fun advance(elapsedSeconds: Double): RenderFrame {
          landed = landImmediately
          return RenderFrame.still(
            spec.dice.indices.map { BodyTransform(it, Vector3(0.0, 0.0, 8.0), Quaternion.Identity) },
          )
        }

        override fun shake(sample: ShakeSample) = Unit

        override fun close() = Unit
      }
    }
  }
}
