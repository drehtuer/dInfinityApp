package de.drehtuer.dinfinity.feature.roll

import android.view.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import de.drehtuer.dinfinity.core.model.TableLook
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
import de.drehtuer.dinfinity.simulation.api.DeveloperLog
import de.drehtuer.dinfinity.simulation.api.DeveloperNotes
import de.drehtuer.dinfinity.simulation.api.DiceSimulator
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the developer toggle does to the roll screen, and what it does not
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * The two halves that matter are that with the toggle off nothing about the
 * screen changes at all, and that with it on the throw the log keeps is the
 * one that replays the roll — `FinishedThrow.thrown` rather than a spec
 * reconstructed afterwards.
 */
@RunWith(RobolectricTestRunner::class)
class DeveloperToolsTest {
  @get:Rule
  val compose = createComposeRule()

  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")
  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  @Test
  fun `with the toggle off there is no overlay and no relay`() {
    val presenter = presenter()

    assertFalse(presenter.showsDebug)
    // And the value the screen would draw is the empty one, so a caller that
    // drew it anyway would draw an empty tray rather than crash.
    assertEquals(RollDiagnostics.NONE, presenter.diagnostics)
  }

  @Test
  fun `with the toggle on the screen draws the overlay over the tray`() {
    val presenter = presenter(relay = DebugRelay(toTheScreen = { it() }))

    compose.setContent { RollScreen(presenter = presenter) }

    compose.onNodeWithTag(DebugTestTags.OVERLAY).assertIsDisplayed()
  }

  @Test
  fun `with the toggle off the screen draws no overlay at all`() {
    val presenter = presenter()

    compose.setContent { RollScreen(presenter = presenter) }

    assertEquals(0, compose.onAllNodesWithTag(DebugTestTags.OVERLAY).fetchSemanticsNodes().size)
  }

  @Test
  fun `a throw that landed is remembered as the spec that replays it`() {
    // Not a spec rebuilt from the plan afterwards: the one the roll actually
    // ran, with the shake that drove it written back into it
    // (`FinishedThrow.thrown`).
    val notes = DeveloperNotes()
    val presenter = presenter(developer = notes)

    presenter.type("2d6")
    presenter.roll()

    val thrown = requireNotNull(notes.lastThrow)
    assertEquals(2, thrown.dice.size)
    assertEquals(table, thrown.table)
    assertEquals(geometry, thrown.geometry)
    assertEquals(SEED, thrown.seed)
    assertEquals(mapOf(0 to 5, 1 to 5), notes.lastOutcome?.faces)
  }

  @Test
  fun `a clean throw leaves the anomaly log empty, which is every throw`() {
    val notes = DeveloperNotes()
    val presenter = presenter(developer = notes)

    presenter.type("2d6")
    presenter.roll()

    assertTrue("a clean roll was logged as an anomaly", notes.anomalies.isEmpty())
  }

  @Test
  fun `a throw the simulation had to finish for is written down with its seed`() {
    val notes = DeveloperNotes()
    val presenter =
      presenter(
        developer = notes,
        outcome = SimulationOutcome(faces = mapOf(0 to 5, 1 to 5), forcedSettles = 1),
      )

    presenter.type("2d6")
    presenter.roll()

    val anomaly = notes.anomalies.single()
    assertEquals(SEED, anomaly.seed)
    assertEquals(1, anomaly.forcedSettles)
    assertFalse(anomaly.invisibleHand)
  }

  @Test
  fun `nothing is remembered when the toggle is off`() {
    // The presenter is handed `DeveloperLog.NONE`, which keeps nothing — so
    // there is no seed anywhere in the app for a screen to find.
    val presenter = presenter(developer = DeveloperLog.NONE)

    presenter.type("2d6")
    presenter.roll()

    assertNull(DeveloperLog.NONE.lastThrow)
    assertTrue(DeveloperLog.NONE.anomalies.isEmpty())
  }

  @Test
  fun `the overlay sees the roll the tray was watching`() {
    val relay = DebugRelay(toTheScreen = { it() })
    val presenter = presenter(relay = relay)

    relay.saw(RollDiagnostics(steps = 11))

    assertTrue(presenter.showsDebug)
    assertEquals(11, presenter.diagnostics.steps)
  }

  private fun presenter(
    relay: DebugRelay? = null,
    developer: DeveloperLog = DeveloperLog.NONE,
    outcome: SimulationOutcome = SimulationOutcome(faces = mapOf(0 to 5, 1 to 5)),
  ): RollPresenter =
    RollPresenter(
      machine =
        RollMachine(
          catalog = catalog,
          geometry = geometry,
          look = { table },
          simulator =
            object : DiceSimulator {
              override fun run(spec: ThrowSpec) = SimulationOutcome(faces = spec.dice.indices.associateWith { 0 })
            },
          outside = Outside(seeds = { SEED }, clock = { AT }),
        ),
      driver = DirectTray(),
      rolls = LandingRolls(outcome),
      toTheScreen = { it() },
      debug = relay,
      developer = developer,
    )

  /** A roll that lands on its first step with the outcome the test chose. */
  private class LandingRolls(
    private val outcome: SimulationOutcome,
  ) : Rolls {
    override fun start(
      spec: ThrowSpec,
      watcher: Renderer,
    ): WatchedRoll =
      object : WatchedRoll {
        private var landed = false

        override val running: Boolean get() = !landed

        override val outcome: SimulationOutcome? get() = if (landed) this@LandingRolls.outcome else null

        override val drivenBy: List<ShakeSample> get() = emptyList()

        override val impacts: List<Impact> = emptyList()

        override fun advance(elapsedSeconds: Double): RenderFrame {
          landed = true
          return RenderFrame.still(
            spec.dice.indices.map { BodyTransform(it, Vector3(0.0, 0.0, 8.0), Quaternion.Identity) },
          )
        }

        override fun shake(sample: ShakeSample) = Unit

        override fun close() = Unit
      }
  }

  /** A tray that throws the dice where it stands, as `RollPresenterTest`'s does. */
  private class DirectTray : Tray {
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
      var frames = 0
      while (roll.running && frames++ < MOST_FRAMES) roll.advance(SettleRule.TIMESTEP_SECONDS)
      roll.outcome?.let { onSettled(it, roll.drivenBy) }
      roll.close()
    }

    override fun shake(sample: ShakeSample) = Unit

    override fun table(
      geometry: TableGeometry,
      look: TableLook,
    ) = Unit

    override fun look(view: TrayView) = Unit

    override fun clear() = Unit

    override fun close() = Unit

    private companion object {
      const val MOST_FRAMES = 8
    }
  }

  private companion object {
    const val SEED = 99L
    const val AT = 1_234L
  }
}
