package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.DeveloperNotes
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The developer screen, drawn (`docs/physics-and-rendering.md`, "Debug
 * tooling").
 *
 * It is a tool rather than a screen a player uses, but it is still a screen:
 * its buttons are as pressable as any other, its state is readable, and it
 * survives something beside it changing.
 */
@RunWith(RobolectricTestRunner::class)
class DeveloperScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun `with nothing thrown it says so and both replays are dead`() {
    compose.setContent { DeveloperScreen(presenter = presenter(DeveloperNotes())) }

    compose.onNodeWithTag(DeveloperTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(DeveloperTestTags.NOTHING).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(DeveloperTestTags.REPLAY_LAST).performScrollTo().assertIsNotEnabled()
    compose.onNodeWithTag(DeveloperTestTags.REPLAY_SEED).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun `an empty anomaly log is worded as the expected state rather than as an absence`() {
    compose.setContent { DeveloperScreen(presenter = presenter(DeveloperNotes())) }

    compose
      .onNodeWithTag(DeveloperTestTags.NO_ANOMALIES)
      .performScrollTo()
      .assertTextContains("No anomalies. This is what a working build looks like.")
  }

  @Test
  fun `a throw that landed offers a replay, and the seed it was made under`() {
    val notes = DeveloperNotes()
    notes.landed(spec(seed = 7L), clean(), atEpochMs = 1L)

    compose.setContent { DeveloperScreen(presenter = presenter(notes)) }

    compose
      .onNodeWithTag(DeveloperTestTags.READY)
      .performScrollTo()
      .assertTextContains("Last throw · dice 2 · seed 7")
    compose.onNodeWithTag(DeveloperTestTags.REPLAY_LAST).performScrollTo().assertIsEnabled()
  }

  @Test
  fun `replaying the last roll shows the faces and says the seed reproduced it`() {
    val notes = DeveloperNotes()
    notes.landed(spec(seed = 7L), clean(), atEpochMs = 1L)
    compose.setContent { DeveloperScreen(presenter = presenter(notes)) }

    compose.onNodeWithTag(DeveloperTestTags.REPLAY_LAST).performScrollTo().performClick()

    compose
      .onNodeWithTag(DeveloperTestTags.RESULT)
      .performScrollTo()
      .assertTextContains("seed 7 · faces 3 4 · steps 120")
    compose.onNodeWithTag(DeveloperTestTags.VERDICT).performScrollTo().assertTextContains("Determinism holds", true)
  }

  @Test
  fun `the seed button stays dead until what is typed is a seed`() {
    val notes = DeveloperNotes()
    notes.landed(spec(seed = 7L), clean(), atEpochMs = 1L)
    compose.setContent { DeveloperScreen(presenter = presenter(notes)) }

    compose.onNodeWithTag(DeveloperTestTags.REPLAY_SEED).performScrollTo().assertIsNotEnabled()
    compose.onNodeWithTag(DeveloperTestTags.SEED).performScrollTo().performTextInput("12")

    compose.onNodeWithTag(DeveloperTestTags.REPLAY_SEED).performScrollTo().assertIsEnabled()
  }

  @Test
  fun `an anomaly is a line with its seed on it, and can be shared or cleared`() {
    val notes = DeveloperNotes()
    notes.landed(spec(seed = 7L), forced(), atEpochMs = 1L)
    val shared = mutableListOf<String>()
    compose.setContent { DeveloperScreen(presenter = presenter(notes), onShare = { shared += it }) }

    compose.onNodeWithTag(DeveloperTestTags.anomalyOf(7L)).performScrollTo().assertTextContains("seed=7", true)
    compose.onNodeWithTag(DeveloperTestTags.SHARE).performScrollTo().performClick()

    assertEquals(1, shared.size)
    // The seed is in it, because reproducing the roll is the point of the log.
    assertEquals(true, shared.single().contains("seed=7"))
  }

  @Test
  fun `clearing the log puts the screen back to the state a working build is in`() {
    val notes = DeveloperNotes()
    notes.landed(spec(seed = 7L), forced(), atEpochMs = 1L)
    compose.setContent { DeveloperScreen(presenter = presenter(notes)) }

    compose.onNodeWithTag(DeveloperTestTags.CLEAR).performScrollTo().performClick()

    compose.onNodeWithTag(DeveloperTestTags.NO_ANOMALIES).performScrollTo().assertIsDisplayed()
    assertEquals(0, compose.onAllNodesWithTag(DeveloperTestTags.anomalyOf(7L)).fetchSemanticsNodes().size)
  }

  @Test
  fun `every control on it is as pressable as a control anywhere`() {
    // Android's own minimum. A debugging tool is still something somebody has
    // to hit with a thumb.
    val notes = DeveloperNotes()
    notes.landed(spec(seed = 7L), forced(), atEpochMs = 1L)
    compose.setContent { DeveloperScreen(presenter = presenter(notes)) }

    listOf(
      DeveloperTestTags.REPLAY_LAST,
      DeveloperTestTags.REPLAY_SEED,
      DeveloperTestTags.SHARE,
      DeveloperTestTags.CLEAR,
    ).forEach { tag ->
      compose.onNodeWithTag(tag).performScrollTo().assertHeightIsAtLeast(48.dp)
    }
  }

  @Test
  fun `a recomposition around the screen that changes nothing leaves it saying the same thing`() {
    val notes = DeveloperNotes()
    notes.landed(spec(seed = 7L), clean(), atEpochMs = 1L)
    var tick by mutableStateOf(0)
    compose.setContent {
      Column {
        Text("tick $tick")
        DeveloperScreen(presenter = presenter(notes))
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose
      .onNodeWithTag(DeveloperTestTags.READY)
      .performScrollTo()
      .assertTextContains("Last throw · dice 2 · seed 7")
  }

  private fun presenter(log: DeveloperNotes): DeveloperPresenter =
    DeveloperPresenter(
      log = log,
      throwAgain = { clean() },
      // Unconfined so a click's replay has finished by the time the assertion
      // runs; the real one is a lifecycle scope on a background dispatcher.
      scope = CoroutineScope(Dispatchers.Unconfined),
    )

  private fun clean(): SimulationOutcome = SimulationOutcome(faces = mapOf(0 to 3, 1 to 4), steps = 120)

  private fun forced(): SimulationOutcome =
    SimulationOutcome(faces = mapOf(0 to 3, 1 to 4), steps = SettleRule.HARD_CAP_STEPS, forcedSettles = 1)

  private fun spec(seed: Long): ThrowSpec =
    ThrowSpec(
      dice =
        List(2) {
          DieInstance(index = it, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = StandardDice.d6)
        },
      geometry = geometry,
      table = table,
      seed = seed,
    )
}
