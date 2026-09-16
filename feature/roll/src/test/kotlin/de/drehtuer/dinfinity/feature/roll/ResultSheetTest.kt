package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.NotationLimits
import org.junit.Assert.assertEquals
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

  @Test
  fun `a formula that divides offers Down, Nearest and Up`() {
    compose.setContent { ResultSheet(result = halved(), divides = true) }

    compose.onNodeWithTag(RollTestTags.ROUNDING).assertIsDisplayed()
    Rounding.entries.forEach { rounding ->
      compose.onNodeWithTag(RollTestTags.roundingOf(rounding)).assertIsDisplayed()
    }
  }

  @Test
  fun `a formula that never divides is offered nothing`() {
    // Three buttons that all give the same answer are worse than no buttons.
    compose.setContent { ResultSheet(result = fourD6DropLowest(), divides = false) }

    compose.onNodeWithTag(RollTestTags.ROUNDING).assertDoesNotExist()
  }

  @Test
  fun `the rounding the throw was scored under is the one shown as chosen`() {
    compose.setContent { ResultSheet(result = halved().copy(rounding = Rounding.Up), divides = true) }

    compose.onNodeWithTag(RollTestTags.roundingOf(Rounding.Up)).assertIsSelected()
    compose.onNodeWithTag(RollTestTags.roundingOf(Rounding.Down)).assertIsNotSelected()
  }

  @Test
  fun `choosing a rounding asks for it rather than deciding anything itself`() {
    // The sheet does not rescore. It says what was pressed and the machine
    // redoes the arithmetic from dice that already landed.
    val asked = mutableListOf<Rounding>()
    compose.setContent { ResultSheet(result = halved(), divides = true, onRound = asked::add) }

    compose.onNodeWithTag(RollTestTags.roundingOf(Rounding.Nearest)).performClick()

    assertEquals(listOf(Rounding.Nearest), asked)
  }

  /** `(3d6 + 5) / 2`: a throw the rounding control can actually change. */
  private fun halved(): RollResult =
    RollResult(
      formula = "(3d6 + 5) / 2",
      total = 8,
      rounding = Rounding.Down,
      groups = listOf(group().copy(notation = "3d6")),
    )

  /** `4d6dl1`: three kept, one dropped, and the 6 is a natural maximum. */
  @Test
  fun `a number the formula adds is a row of its own, so the rows add up`() {
    // It used to be visible only in the formula line at the top, so a
    // breakdown of `4d6dl1 + 4` showed rows adding to fifteen under a total of
    // nineteen (`docs/dice-notation.md`, "Evaluation", step 7).
    compose.setContent { ResultSheet(fourD6DropLowest().plus(4)) }

    compose.onNodeWithTag(RollTestTags.adjustmentOf(4)).assertIsDisplayed()
    compose.onNodeWithText("plus").assertIsDisplayed()
  }

  @Test
  fun `one that takes away says so`() {
    compose.setContent { ResultSheet(fourD6DropLowest().plus(-2)) }

    compose.onNodeWithTag(RollTestTags.adjustmentOf(-2)).assertIsDisplayed()
    compose.onNodeWithText("minus").assertIsDisplayed()
    // The sign is the word, so the number beside it is not written twice.
    compose.onNodeWithText("2").assertIsDisplayed()
  }

  @Test
  fun `a chain that stopped at the depth limit says so on the sheet`() {
    // Without the line this reads as an explosion that simply did not happen
    // (`docs/dice-notation.md`, "Limits").
    compose.setContent { ResultSheet(exploded(DieNote.ExplosionLimitReached)) }

    compose
      .onNodeWithTag(RollTestTags.chainLimitOf(0, ChainLimit.ExplosionDepth.name))
      .assertIsDisplayed()
  }

  @Test
  fun `a chain the tray had no room for says so too`() {
    compose.setContent { ResultSheet(exploded(DieNote.TrayFull)) }

    compose
      .onNodeWithTag(RollTestTags.chainLimitOf(0, ChainLimit.TrayFull.name))
      .assertIsDisplayed()
  }

  @Test
  fun `the depth limit line names the limit rather than leaving it a mystery`() {
    compose.setContent { ResultSheet(exploded(DieNote.ExplosionLimitReached)) }

    compose
      .onNodeWithTag(RollTestTags.chainLimitOf(0, ChainLimit.ExplosionDepth.name))
      .assertTextEquals("Exploding stopped at ${NotationLimits.MAX_EXPLOSION_DEPTH} dice.")
  }

  @Test
  fun `an ordinary throw says nothing about limits`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    ChainLimit.entries.forEach { limit ->
      compose.onNodeWithTag(RollTestTags.chainLimitOf(0, limit.name)).assertDoesNotExist()
    }
  }

  @Test
  fun `only the group that stopped says so`() {
    val two =
      fourD6DropLowest().copy(
        formula = "4d6dl1 + 8d6!",
        groups =
          listOf(
            group(),
            exploded(DieNote.TrayFull).groups.first().copy(id = 1),
          ),
      )

    compose.setContent { ResultSheet(two) }

    compose.onNodeWithTag(RollTestTags.chainLimitOf(0, ChainLimit.TrayFull.name)).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.chainLimitOf(1, ChainLimit.TrayFull.name)).assertIsDisplayed()
  }

  /** `8d6!` whose chain stopped, for [note]'s reason. */
  private fun exploded(note: DieNote): RollResult =
    RollResult(
      formula = "8d6!",
      total = 12,
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "8d6!",
            setId = "builtin",
            requestedSetId = "builtin",
            subtotal = 12,
            dice =
              listOf(
                die(0, 6, notes = setOf(DieNote.FromExplosion)),
                die(1, 6, naturalMax = true, notes = setOf(DieNote.FromExplosion, note)),
              ),
          ),
        ),
    )

  @Test
  fun `a formula that adds nothing has no such row`() {
    compose.setContent { ResultSheet(fourD6DropLowest()) }

    compose.onNodeWithTag(RollTestTags.adjustmentOf(0)).assertDoesNotExist()
  }

  private fun RollResult.plus(amount: Long): RollResult =
    copy(
      formula = "$formula ${if (amount < 0) "-" else "+"} ${kotlin.math.abs(amount)}",
      total = total + amount,
      adjustments = listOf(amount),
    )

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
