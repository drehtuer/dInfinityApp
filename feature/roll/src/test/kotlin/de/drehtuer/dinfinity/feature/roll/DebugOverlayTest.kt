package de.drehtuer.dinfinity.feature.roll

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import de.drehtuer.dinfinity.simulation.api.ContactPoint
import de.drehtuer.dinfinity.simulation.api.DebugWatch
import de.drehtuer.dinfinity.simulation.api.DieDiagnostic
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.Struck
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The debug overlay, drawn (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * It draws what a roll is doing and can do nothing to it, so what is worth
 * checking here is that the numbers reach the screen, that the line saying
 * something has gone wrong appears only when something has, and that a
 * snapshot arriving redraws it and nothing else.
 */
@RunWith(RobolectricTestRunner::class)
class DebugOverlayTest {
  @get:Rule
  val compose = createComposeRule()

  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `the overlay says how far the roll has got and how many dice have stopped`() {
    compose.setContent {
      DebugOverlay(
        diagnostics = RollDiagnostics(steps = 137, dice = listOf(die(0, still = SettleRule.REST_STEPS), die(1))),
        geometry = geometry,
      )
    }

    compose.onNodeWithTag(DebugTestTags.OVERLAY).assertIsDisplayed()
    compose.onNodeWithTag(DebugTestTags.COUNTERS, useUnmergedTree = true).assertTextContains("step 137 · at rest 1/2")
    compose.onNodeWithTag(DebugTestTags.PLAN, useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `the overlay counts the corrections, the re-throws and the contacts`() {
    compose.setContent {
      DebugOverlay(
        diagnostics =
          RollDiagnostics(
            dice = listOf(die(0)),
            corrections = 9,
            rethrows = 1,
            contacts = listOf(contact(), contact()),
          ),
        geometry = geometry,
      )
    }

    compose
      .onNodeWithTag(DebugTestTags.LADDER, useUnmergedTree = true)
      .assertTextContains("corr 9 · rethrow 1 · hits 2")
  }

  @Test
  fun `a clean roll has no line saying something went wrong`() {
    compose.setContent {
      DebugOverlay(diagnostics = RollDiagnostics(dice = listOf(die(0)), corrections = 40), geometry = geometry)
    }

    // Corrections and re-throws are ordinary. The anomaly row is for the two
    // things that are supposed to be impossible, and a row that is always
    // there is a row nobody reads (`docs/TODO.md`, Step 5.5).
    assertEquals(0, compose.onAllNodesWithTag(DebugTestTags.ANOMALY).fetchSemanticsNodes().size)
  }

  @Test
  fun `a forced settle puts the word BUG on the tray, because that is what it is`() {
    compose.setContent {
      DebugOverlay(
        diagnostics = RollDiagnostics(dice = listOf(die(0)), forcedSettles = 1, postRestCorrections = 2),
        geometry = geometry,
      )
    }

    compose
      .onNodeWithTag(DebugTestTags.ANOMALY, useUnmergedTree = true)
      .assertTextContains("BUG: forced 1 · post-rest 2")
  }

  @Test
  fun `the overlay is one thing for a screen reader rather than four rows of numbers`() {
    compose.setContent {
      DebugOverlay(diagnostics = RollDiagnostics(dice = listOf(die(0))), geometry = geometry)
    }

    compose.onNodeWithTag(DebugTestTags.OVERLAY).assertContentDescriptionEquals("Debug overlay")
  }

  @Test
  fun `a new snapshot is what the overlay shows next`() {
    // Sixty of these a second while a roll runs, each one a new value of the
    // same parameter. What must not happen is the overlay keeping the numbers
    // it drew first.
    var diagnostics by mutableStateOf(RollDiagnostics(steps = 1, dice = listOf(die(0))))
    compose.setContent { DebugOverlay(diagnostics = diagnostics, geometry = geometry) }

    compose.runOnIdle { diagnostics = RollDiagnostics(steps = 2, dice = listOf(die(0, still = SettleRule.REST_STEPS))) }

    compose.onNodeWithTag(DebugTestTags.COUNTERS, useUnmergedTree = true).assertTextContains("step 2 · at rest 1/1")
  }

  @Test
  fun `a recomposition around the overlay that changes nothing leaves it saying the same thing`() {
    var tick by mutableStateOf(0)
    compose.setContent {
      Column {
        Text("tick $tick")
        DebugOverlay(diagnostics = RollDiagnostics(steps = 9, dice = listOf(die(0))), geometry = geometry)
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(DebugTestTags.COUNTERS, useUnmergedTree = true).assertTextContains("step 9 · at rest 0/1")
  }

  @Test
  fun `a relay carries a snapshot to the screen and returns nothing`() {
    // The seam between the roll thread and Compose. It is a `DebugWatch`, so
    // what the roll gets back from being watched is `Unit`
    // (`docs/architecture.md`, decision 38).
    val relay = DebugRelay(toTheScreen = { it() })
    // Typed as the watcher, so what the roll hands it is all it ever gets and
    // `Unit` is all it can hand back.
    val watch: DebugWatch = relay
    assertEquals(RollDiagnostics.NONE, relay.latest)

    watch.saw(RollDiagnostics(steps = 5))

    assertTrue(watch.watching)
    assertEquals(5, relay.latest.steps)
  }

  @Test
  fun `a relay made the way the app makes one posts to the screen's own thread`() {
    // The default: the roll thread hands a snapshot over and the main looper
    // delivers it, which is the same crossing a finished throw makes
    // (`docs/architecture.md`, "Threading").
    val relay = DebugRelay()

    relay.saw(RollDiagnostics(steps = 3))

    assertEquals("a snapshot arrived before the looper ran", 0, relay.latest.steps)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(3, relay.latest.steps)
  }

  private fun die(
    index: Int,
    still: Int = 0,
  ): DieDiagnostic =
    DieDiagnostic(
      index = index,
      position = Vector3(0.0, 0.0, 8.0),
      acrossMm = 16.0,
      stillForSteps = still,
      atRest = still >= SettleRule.REST_STEPS,
    )

  private fun contact(): ContactPoint =
    ContactPoint(
      stepIndex = 0,
      dieIndex = 0,
      position = Vector3(0.0, 0.0, 8.0),
      struck = Struck.Die,
      strength = 0.5,
    )
}
