package de.drehtuer.dinfinity.feature.roll

import android.view.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.drehtuer.dinfinity.core.model.DieInstance
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
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The dice pull-down at the top of the table ([DiceMenu]).
 *
 * Its own file rather than another corner of `RollScreenTest`, which the rest
 * of the screen already fills: the pull-down is a control of its own, like
 * the picker row inside it, and `PickerRowTest` is next door.
 *
 * What it checks is that the dice are *put away* — that is the whole point of
 * the thing. With the straight-down table view, a plate over the tray is a
 * place a die can land and not be seen
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 */
@RunWith(RobolectricTestRunner::class)
class DiceMenuTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the dice are put away until the pull-down is opened`() {
    // The picker was the third of four plates along the bottom edge, which on
    // a phone with the straight-down table view covered the felt a die may
    // well have landed on (`docs/physics-and-rendering.md`, "What is drawn
    // over the table").
    show()

    compose.onNodeWithTag(RollTestTags.DICE_MENU).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.PICKER).assertDoesNotExist()
  }

  @Test
  fun `opening it brings the dice out, and closing it puts them back`() {
    show()

    openDice()
    compose.onNodeWithTag(RollTestTags.PICKER).assertIsDisplayed()

    compose.onNodeWithTag(RollTestTags.DICE_MENU).performClick()
    compose.onNodeWithTag(RollTestTags.PICKER).assertDoesNotExist()
  }

  @Test
  fun `the shut menu still says how many dice are in the throw`() {
    // Otherwise putting the dice away would hide the one thing tapping them
    // did, and a player would have to open it again to check.
    show()
    typeFormula("4d6 + 1d20")

    compose.onNodeWithTag(RollTestTags.DICE_MENU_COUNT, useUnmergedTree = true).assertTextEquals("5")
  }

  @Test
  fun `opening the dice puts the formula editor away, and the other way round`() {
    // Both hang off the top edge and both push what is under them down. Two
    // open at once is the whole top half of the table covered, which is the
    // thing this layout exists to stop.
    show()

    typeFormula("1d20")
    compose.onNodeWithTag(RollTestTags.FORMULA).assertIsDisplayed()
    openDice()

    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.PICKER).assertIsDisplayed()

    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).performClick()

    compose.onNodeWithTag(RollTestTags.PICKER).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.FORMULA).assertIsDisplayed()
  }

  /** Opens the pull-down the way a player does, through its head. */
  private fun openDice() {
    compose.onNodeWithTag(RollTestTags.DICE_MENU).performClick()
  }

  /** Types a formula the way a player does: tap the line, then type. */
  private fun typeFormula(text: String) {
    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(text)
  }

  private fun show() {
    val presenter =
      RollPresenter(
        machine =
          RollMachine(
            catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
            geometry = TableGeometry.referenceDevice(),
            look = { TableLook(id = "plain", name = "Plain") },
            outside = Outside(seeds = { 1L }, clock = { 0L }),
          ),
        driver = DirectTray(),
        rolls = LandingRolls(mapOf(0 to 0)),
        toTheScreen = { it() },
      )
    compose.setContent { RollScreen(presenter = presenter) }
  }

  private open class DirectTray : Tray {
    val shaken = mutableListOf<ShakeSample>()

    /** How many times the screen has given this tray back. */
    var closes = 0
      private set

    /** How many throws this tray has been handed. */
    var throws = 0
      private set

    /** Every board this tray has been asked to show, in order. */
    val boards = mutableListOf<List<DieInstance>>()

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
      onCounted: (Map<Int, Int>) -> Unit,
      onStalled: (List<Int>) -> Unit,
      onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
    ) {
      throws++
      val live = start(HeadlessRenderer())
      while (live.running) live.advance(SettleRule.TIMESTEP_SECONDS)
      live.outcome?.let { onSettled(it, live.drivenBy) }
      live.close()
    }

    override fun waiting(spec: ThrowSpec) {
      boards += spec.dice
    }

    override fun shake(sample: ShakeSample) {
      shaken += sample
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

    override fun close() {
      closes++
    }
  }

  /**
   * A tray that reports some dice counted and then leaves the roll in the air,
   * which is what the screen looks like halfway through one.
   */

  private class LandingRolls(
    private val faces: Map<Int, Int>,
  ) : Rolls {
    override fun start(
      spec: ThrowSpec,
      watcher: Renderer,
    ): WatchedRoll =
      object : WatchedRoll {
        private var landed = false

        override val running: Boolean get() = !landed

        override val outcome: SimulationOutcome? get() = if (landed) SimulationOutcome(faces = faces) else null

        override val drivenBy: List<ShakeSample> = emptyList()

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
}
