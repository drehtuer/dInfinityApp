package de.drehtuer.dinfinity.feature.roll

import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.PickableDie
import de.drehtuer.dinfinity.core.notation.Sides
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the roll screen says to somebody who cannot see it
 * (`docs/architecture.md`, "Accessibility").
 *
 * The two things this screen carries in colour alone are a die that showed its
 * highest face and a die that was dropped — one printed in the accent, one
 * struck through, neither of which reaches a screen reader. And the tray
 * itself is a drawing surface with nothing underneath it, so without a label
 * the app's home screen is an empty rectangle.
 *
 * A pass with no test is a pass that rots, which is why these assert the
 * semantics rather than the pixels.
 */
@RunWith(RobolectricTestRunner::class)
class RollAccessibilityTest {
  @get:Rule
  val compose = createComposeRule()

  private val d6 = PickableDie("d6", Sides.Numeric(6))
  private val d20 = PickableDie("d20", Sides.Numeric(20))

  @Test
  fun `a die that showed its highest face says so, not only in the accent`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    compose.onNodeWithContentDescription("6, highest face").assertIsDisplayed()
  }

  @Test
  fun `a dropped die says so, not only by being struck through`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    compose.onNodeWithContentDescription("1, dropped").assertIsDisplayed()
  }

  @Test
  fun `an ordinary die is left to read as its own number`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    // No label of its own: the text is the whole of what there is to say, and
    // "5, ordinary" on every row is noise.
    compose.onNodeWithTag(RollTestTags.dieAt(0)).assertIsDisplayed()
    compose.onNodeWithContentDescription("5, highest face").assertDoesNotExist()
    compose.onNodeWithContentDescription("5, dropped").assertDoesNotExist()
  }

  @Test
  fun `the dice pull-down says what it is, what a press does and whether it is open`() {
    // A chevron is a picture: it says nothing to a listener, and the head
    // looks much the same open and shut.
    compose.setContent { DiceMenuUnder(expanded = false) }

    compose.onNodeWithContentDescription("Dice").assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.DICE_MENU).assert(saying("Closed"))
  }

  @Test
  fun `and says so once it is open`() {
    compose.setContent { DiceMenuUnder(expanded = true) }

    compose.onNodeWithTag(RollTestTags.DICE_MENU).assert(saying("Open"))
  }

  @Test
  fun `the count on a shut pull-down is read out with it, not left to the badge`() {
    // The badge is a number in a coloured box, which is a drawing. A listener
    // hears the menu's own name, so the count has to be part of it.
    compose.setContent { DiceMenuUnder(expanded = false, counts = mapOf(d6 to 4)) }

    compose.onNodeWithContentDescription("Dice, 4").assertIsDisplayed()
  }

  @Test
  fun `the pull-down's head is worth pressing`() {
    compose.setContent { DiceMenuUnder(expanded = false) }

    compose.onNodeWithTag(RollTestTags.DICE_MENU).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `the result's two ways on are named in words`() {
    // They are on the sheet now rather than on plates over the felt, and a
    // button whose label is a word needs nothing else — but a listener has to
    // be able to find them at all.
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    compose.onNodeWithTag(RollTestTags.ODDS).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.SAVE_AS_ROLL).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.ODDS).assertHeightIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag(RollTestTags.SAVE_AS_ROLL).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  /** What a screen reader is told about which rest a thing is in. */
  private fun saying(state: String): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

  @Composable
  private fun DiceMenuUnder(
    expanded: Boolean,
    counts: Map<PickableDie, Int> = emptyMap(),
  ) {
    DiceMenu(
      dice = listOf(d6, d20),
      counts = counts,
      sets = emptyList(),
      pickingFrom = "builtin",
      expanded = expanded,
      onExpand = {},
      onAdd = {},
      onRemove = {},
      onChoose = {},
    )
  }

  @Test
  fun `the tray is not a silent rectangle`() {
    compose.setContent {
      DiceTray(
        driver = SilentTray(),
        geometry = TableGeometry.referenceDevice(),
        describing = "Dice tray, the dice have landed on 11",
      )
    }

    compose.onNodeWithContentDescription("Dice tray, the dice have landed on 11").assertIsDisplayed()
  }

  private fun fourD6DropLowest(): RollResult =
    RollResult(
      formula = "4d6dl1",
      total = 15,
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "4d6dl1",
            setId = "builtin",
            requestedSetId = "builtin",
            subtotal = 15,
            dice =
              listOf(
                die(0, 5),
                die(1, 4),
                die(2, 6, naturalMax = true),
                die(3, 1, notes = setOf(DieNote.Dropped)),
              ),
          ),
        ),
    )

  private fun die(
    index: Int,
    value: Int,
    naturalMax: Boolean = false,
    notes: Set<DieNote> = emptySet(),
  ): RolledDie =
    RolledDie(
      instanceIndex = index,
      dieId = "d6",
      value = value,
      naturalMax = naturalMax,
      notes = notes,
    )

  /** A tray that is handed a surface and does nothing with it. */
  private class SilentTray : Tray {
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
    ) = Unit

    override fun shake(sample: ShakeSample) = Unit

    override fun table(
      geometry: TableGeometry,
      look: TableLook,
    ) = Unit

    override fun look(view: TrayView) = Unit

    override fun clear() = Unit

    override fun close() = Unit
  }
}
