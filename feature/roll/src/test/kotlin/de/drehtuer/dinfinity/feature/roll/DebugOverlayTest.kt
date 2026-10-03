package de.drehtuer.dinfinity.feature.roll

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionContains
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
import de.drehtuer.dinfinity.simulation.api.FrameMeter
import de.drehtuer.dinfinity.simulation.api.FrameRate
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.Struck
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun `the overlay counts the corrections, the dice waiting for a shake and the contacts`() {
    compose.setContent {
      DebugOverlay(
        diagnostics =
          RollDiagnostics(
            dice = listOf(die(0)),
            corrections = 9,
            waiting = 1,
            contacts = listOf(contact(), contact()),
          ),
        geometry = geometry,
      )
    }

    compose
      .onNodeWithTag(DebugTestTags.LADDER, useUnmergedTree = true)
      .assertTextContains("corr 9 · waiting 1 · hits 2")
  }

  @Test
  fun `the overlay says the frame rate and the p99 frame time`() {
    compose.setContent {
      DebugOverlay(
        diagnostics = RollDiagnostics.NONE,
        geometry = geometry,
        frameRate = FrameRate(framesPerSecond = 59.6, p99Millis = 33.34, frames = 120),
      )
    }

    compose.onNodeWithTag(DebugTestTags.FRAMES, useUnmergedTree = true).assertTextContains("60 fps · p99 33.3 ms")
  }

  @Test
  fun `before two frames in a row the frame-rate line says so rather than nought`() {
    compose.setContent { DebugOverlay(diagnostics = RollDiagnostics.NONE, geometry = geometry) }

    compose.onNodeWithTag(DebugTestTags.FRAMES, useUnmergedTree = true).assertTextContains("fps — no frames yet")
  }

  @Test
  fun `the overlay says the steps this roll and this visit have dropped`() {
    compose.setContent {
      DebugOverlay(
        diagnostics = RollDiagnostics(droppedSteps = 12),
        geometry = geometry,
        droppedThisVisit = 40,
      )
    }

    compose.onNodeWithTag(DebugTestTags.DROPPED, useUnmergedTree = true).assertTextContains("dropped 12 · visit 40")
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

    compose.onNodeWithTag(DebugTestTags.OVERLAY).assertContentDescriptionContains("Debug overlay")
  }

  /**
   * The plan tints a die's box by its state, and two of the three tints are
   * red on red. The counts are the whole of what the picture claims, so a
   * `Canvas` that would otherwise be silent says them
   * (`docs/architecture.md`, "Accessibility").
   */
  @Test
  fun `the plan says how many dice are in each state rather than only tinting them`() {
    compose.setContent {
      DebugOverlay(
        diagnostics =
          RollDiagnostics(
            dice =
              listOf(
                die(0, still = SettleRule.REST_STEPS),
                die(1),
                die(2, stacked = true),
              ),
          ),
        geometry = geometry,
      )
    }

    compose
      .onNodeWithTag(DebugTestTags.PLAN, useUnmergedTree = true)
      .assertContentDescriptionEquals("Tray plan: 3 dice, 1 at rest, 1 moving, 1 stacked")
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
  fun `a relay adds up the steps every roll of the visit dropped`() {
    val relay = DebugRelay(toTheScreen = { it() })

    relay.saw(RollDiagnostics(steps = 40, droppedSteps = 30))
    relay.saw(RollDiagnostics(steps = 0))
    relay.saw(RollDiagnostics(steps = 8, droppedSteps = 2))

    assertEquals(2, relay.latest.droppedSteps)
    assertEquals(32L, relay.droppedThisVisit)
  }

  @Test
  fun `a relay posts a frame rate once it has a window's share of frames, and not every frame`() {
    val posted = mutableListOf<() -> Unit>()
    val relay = DebugRelay(toTheScreen = { posted += it })

    repeat((DebugRelay.POST_EVERY - 1).toInt()) { relay.framed(SIXTIETH) }
    assertTrue("a reading crossed before it was due", posted.isEmpty())

    relay.framed(SIXTIETH)
    assertEquals(1, posted.size)
    assertNull("the screen's copy moved before the post ran", relay.frameRate)

    posted.single().invoke()
    val rate = requireNotNull(relay.frameRate)
    assertEquals(60.0, rate.framesPerSecond, 0.01)
    assertEquals(DebugRelay.POST_EVERY.toInt(), rate.frames)
    assertTrue(rate.frames <= FrameMeter.WINDOW)
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
    stacked: Boolean = false,
  ): DieDiagnostic =
    DieDiagnostic(
      index = index,
      position = Vector3(0.0, 0.0, 8.0),
      acrossMm = 16.0,
      stillForSteps = still,
      atRest = still >= SettleRule.REST_STEPS,
      supportedByDie = stacked,
    )

  private fun contact(): ContactPoint =
    ContactPoint(
      stepIndex = 0,
      dieIndex = 0,
      position = Vector3(0.0, 0.0, 8.0),
      struck = Struck.Die,
      strength = 0.5,
    )

  private companion object {
    const val SIXTIETH = 16_666_667L
  }
}
