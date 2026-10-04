package de.drehtuer.dinfinity.feature.roll

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.render.filament.PowerSavingTray
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

/**
 * The screen over a throw that left dice nobody could read
 * (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice";
 * `docs/architecture.md`, decision 70).
 *
 * The roll used to throw them again by itself, and the player on the Pixel
 * 10a saw dice go back into the air with nobody's hand on them. Now the throw
 * stops, the screen says how many dice need another throw — on a plate, in an
 * accent prompt over the tray a screen reader announces (decision 84), and in
 * the tray's own description — and
 * the next shake throws those and only those. Every shake here comes through a
 * [TestHand], by the same calls the sensors make (decision 66).
 */
@RunWith(RobolectricTestRunner::class)
class ThrowAgainTest {
  @get:Rule
  val compose = createComposeRule()

  @get:Rule
  val shaking = ShakingHand()

  @Test
  fun `a die that landed where it cannot be read is asked about, not thrown`() {
    val rolls = PassingRolls(listOf(oneCocked, allRead))
    compose.setContent { RollScreen(presenter = rollPresenter(DirectTray(), rolls)) }

    typeFormula("3d6")
    shake()

    compose.onNodeWithTag(RollTestTags.THROW_AGAIN).assertExists()
    compose.onNodeWithText("Shake to throw it again.", substring = true).assertExists()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertDoesNotExist()
    assertEquals("the app threw the die again by itself", 1, rolls.started.size)
  }

  @Test
  fun `and says so in words a screen reader is told without asking`() {
    compose.setContent { RollScreen(presenter = rollPresenter(DirectTray(), PassingRolls(listOf(twoCocked)))) }

    typeFormula("3d6")
    shake()

    compose
      .onNodeWithTag(
        RollTestTags.SHAKE_PROMPT_TEXT,
        useUnmergedTree = true,
      ).assertTextEquals("Shake to re-throw 2 dice")
    compose
      .onNodeWithTag(RollTestTags.TRAY)
      .assertContentDescriptionEquals("Dice tray, 2 dice cannot be read, shake to throw them again")
  }

  @Test
  fun `the shake throws only the die nobody could read, and the total arrives`() {
    val rolls = PassingRolls(listOf(oneCocked, allRead))
    compose.setContent { RollScreen(presenter = rollPresenter(DirectTray(), rolls)) }
    typeFormula("3d6")
    shake()

    assertTrue("the waiting die would not go back in the air", shake())

    assertEquals(2, rolls.started.size)
    assertEquals(
      "the dice that were read went back in the air too",
      1,
      rolls.started
        .last()
        .dice.size,
    )
    compose.onNodeWithTag(RollTestTags.THROW_AGAIN).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.SHAKE_PROMPT).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
  }

  @Test
  fun `in power-saving mode the wait is the same wait, and the shake still answers it`() {
    // No frames and no tray to look at, so the plate and the prompt are the
    // whole of what the player is told — and the shake is the whole of what
    // they do (`docs/physics-and-rendering.md`, "Power-saving mode").
    val rolls = PassingRolls(listOf(oneCocked, allRead))
    val tray = PowerSavingTray(on = Executor { it.run() })
    compose.setContent { RollScreen(presenter = rollPresenter(tray, rolls)) }
    typeFormula("3d6")
    shake()

    compose.onNodeWithTag(RollTestTags.THROW_AGAIN).assertExists()
    compose
      .onNodeWithTag(
        RollTestTags.SHAKE_PROMPT_TEXT,
        useUnmergedTree = true,
      ).assertTextEquals("Shake to re-throw 1 die")

    assertTrue(shake())

    assertEquals(
      1,
      rolls.started
        .last()
        .dice.size,
    )
    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
  }

  @Test
  fun `a roll that needed a second shake is written down once, when every die is read`() {
    val written = mutableListOf<FinishedThrow>()
    val presenter = presenter(PassingRolls(listOf(twoCocked, oneCockedOfTwo, oneRead)), DirectTray()) { written += it }

    presenter.type("3d6")
    presenter.roll()
    assertTrue(presenter.state is RollState.ThrowAgain)
    presenter.roll()
    assertEquals(RollState.ThrowAgain(unread = 1, read = 2), presenter.state)
    assertTrue("a roll with a die still unread was written down", written.isEmpty())
    presenter.roll()

    assertEquals(1, written.size)
    assertEquals("the dice thrown again were not counted", 3, written.single().result.rethrows)
    assertTrue(presenter.state is RollState.Settled)
  }

  @Test
  fun `the range stays on the screen while the roll waits for a hand`() {
    val presenter = presenter(PassingRolls(listOf(oneCocked, allRead)), CountingThenLanding(mapOf(0 to 2, 1 to 3)))

    presenter.type("3d6")
    presenter.roll()

    assertTrue(presenter.state is RollState.ThrowAgain)
    assertNotNull("the range went away with the counting plate", presenter.progress)

    presenter.roll()

    assertNull("a finished roll kept a range it no longer has", presenter.progress)
  }

  @Test
  fun `typing a new formula over the wait drops the die it was holding`() {
    val rolls = PassingRolls(listOf(oneCocked, allRead))
    val presenter = presenter(rolls, DirectTray())
    presenter.type("3d6")
    presenter.roll()

    presenter.type("1d20")
    presenter.roll()

    assertEquals(
      "the shake threw the old formula's die",
      1,
      rolls.started
        .last()
        .dice.size,
    )
    assertEquals(
      "d20",
      rolls.started
        .last()
        .dice
        .single()
        .die.id,
    )
  }

  @Test
  fun `leaving the screen while a die waits gives the tray back and throws nothing`() {
    val rolls = PassingRolls(listOf(oneCocked, allRead))
    val tray = DirectTray()
    var open by mutableStateOf(true)
    compose.setContent { if (open) RollScreen(presenter = rollPresenter(tray, rolls)) }
    typeFormula("3d6")
    shake()

    open = false
    compose.waitForIdle()

    assertEquals("the tray was not given back", 1, tray.closes)
    assertFalse("a shake reached a screen that is not there", shake())
    assertEquals("a shake with no screen up threw the waiting die", 1, rolls.started.size)
  }

  private fun typeFormula(text: String) {
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(text)
  }

  private fun shake(): Boolean {
    val threw = compose.runOnUiThread { shaking.hand.shake() }
    compose.waitForIdle()
    return threw
  }

  private fun presenter(
    rolls: Rolls,
    tray: Tray,
    recorder: ThrowRecorder = ThrowRecorder.NONE,
  ): RollPresenter =
    RollPresenter(
      machine =
        RollMachine(
          catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
          geometry = TableGeometry.referenceDevice(),
          look = { TableLook(id = "plain", name = "Plain") },
          outside = Outside(seeds = { 1L }, clock = { 0L }),
        ),
      driver = tray,
      rolls = rolls,
      recorder = recorder,
      toTheScreen = { it() },
    )

  /** A tray that reports [read] counted and then lets the roll land. */
  private class CountingThenLanding(
    private val read: Map<Int, Int>,
  ) : DirectTray() {
    override fun roll(
      start: (Renderer) -> WatchedRoll,
      onCounted: (Map<Int, Int>) -> Unit,
      onStalled: (List<Int>) -> Unit,
      onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
    ) {
      onCounted(read)
      super.roll(start, onCounted, onStalled, onSettled)
    }
  }

  private companion object {
    /** Three dice: two read, the third cocked. */
    val oneCocked = SimulationOutcome(faces = mapOf(0 to 2, 1 to 3), unread = listOf(2))

    /** Three dice: one read, two left. */
    val twoCocked = SimulationOutcome(faces = mapOf(0 to 2), unread = listOf(1, 2))

    /** The two that were left: one read, one cocked again. */
    val oneCockedOfTwo = SimulationOutcome(faces = mapOf(0 to 4), unread = listOf(1))

    /** One die, read. */
    val oneRead = SimulationOutcome(faces = mapOf(0 to 1))

    /** Whatever was thrown, read — as many dice as a throw of up to three can have. */
    val allRead = SimulationOutcome(faces = mapOf(0 to 4))
  }
}
