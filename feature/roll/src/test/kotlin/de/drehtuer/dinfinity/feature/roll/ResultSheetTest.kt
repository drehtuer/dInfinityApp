package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The breakdown under the total (`design/dInfinity.dc.html`, option 1f).
 *
 * The claim it exists for: **nobody has to add anything up, and nothing is
 * taken on trust.** Every die that landed is on the sheet, including the ones
 * the formula threw away — a player who wrote `4d6dl1` wants to see the 1 it
 * dropped, and a breakdown showing three dice under four on the table reads as
 * the app having lost one.
 */
@RunWith(RobolectricTestRunner::class)
class ResultSheetTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `every die that landed is on the sheet, dropped ones included`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    // Four dice thrown, four dice shown.
    listOf(0, 1, 2, 3).forEach { index ->
      compose.onNodeWithTag(RollTestTags.dieAt(index)).assertIsDisplayed()
    }
  }

  @Test
  fun `a dropped die is struck through rather than taken away`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    // The dropped die is the one showing 1, and it is still there to be seen.
    compose.onNodeWithTag(RollTestTags.dieAt(DROPPED)).assertIsDisplayed()
    compose.onNodeWithText("1").assertIsDisplayed()
  }

  @Test
  fun `the group's subtotal is shown, so nobody adds the dice up themselves`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    compose.onNodeWithTag(RollTestTags.subtotalOf(0)).assertIsDisplayed()
    // 5 + 4 + 6, with the 1 dropped.
    compose.onNodeWithText("15").assertIsDisplayed()
  }

  @Test
  fun `the formula is shown as it was typed, which is where the modifiers are`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    compose.onNodeWithTag(RollTestTags.SHEET_FORMULA).assertTextEquals("4d6dl1")
  }

  @Test
  fun `a named roll says its name before its formula`() {
    compose.setContent { ResultSheet(fourD6DropLowest().copy(label = "Fireball")) }

    compose.onNodeWithTag(RollTestTags.SHEET_FORMULA).assertTextEquals("Fireball · 4d6dl1")
  }

  @Test
  fun `a group that could not have the set it asked for says so`() {
    // Quietly handing a player different dice is the one thing a breakdown
    // must not do (`docs/dice-sets.md`).
    val fellBack =
      fourD6DropLowest().copy(
        groups = listOf(group().copy(setId = "builtin", requestedSetId = "brass")),
      )

    compose.setContent { ResultSheet(fellBack) }

    compose.onNodeWithTag(RollTestTags.fallbackOf(0)).assertIsDisplayed()
  }

  @Test
  fun `a group that got the set it asked for says nothing about sets`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    compose.onNodeWithTag(RollTestTags.fallbackOf(0)).assertDoesNotExist()
  }

  @Test
  fun `every group of a formula gets its own row`() {
    val two =
      fourD6DropLowest().copy(
        formula = "3d6 + 1d20",
        groups =
          listOf(
            group().copy(id = 0, notation = "3d6", subtotal = 11),
            group().copy(id = 1, notation = "1d20", subtotal = 17, dice = listOf(die(4, 17))),
          ),
      )

    compose.setContent { ResultSheet(two) }

    compose.onNodeWithTag(RollTestTags.subtotalOf(0)).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.subtotalOf(1)).assertIsDisplayed()
  }

  /** `4d6dl1`: three kept, one dropped, and the 6 is a natural maximum. */
  private fun fourD6DropLowest(): RollResult =
    RollResult(
      formula = "4d6dl1",
      total = 15,
      groups = listOf(group()),
    )

  private fun group(): RolledGroup =
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
          die(DROPPED, 1, naturalMin = true, notes = setOf(DieNote.Dropped)),
        ),
    )

  private fun die(
    index: Int,
    value: Int,
    naturalMax: Boolean = false,
    naturalMin: Boolean = false,
    notes: Set<DieNote> = emptySet(),
  ): RolledDie =
    RolledDie(
      instanceIndex = index,
      dieId = "d6",
      value = value,
      naturalMax = naturalMax,
      naturalMin = naturalMin,
      notes = notes,
    )

  private companion object {
    const val DROPPED = 3
  }
}
