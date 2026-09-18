package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The result sheet draws the total once
 * (`docs/design-handover.md`: "Where does the total go, and how many times? —
 * Once, in the result sheet").
 *
 * The rule has to be narrow, and these are the cases that say where its edges
 * are: a group whose subtotal is the whole answer is the number written twice,
 * and everything else is a row the reader needs to make the total add up.
 */
class SubtotalsTest {
  @Test
  fun `a lone group that is the whole total does not repeat it`() {
    // `1d20` at 42 dp in the middle of the screen and again at 20 dp hard
    // against the right edge, where the first device session read it as
    // clipped (`docs/TODO.md`, Step 4.1).
    assertFalse(Subtotals.shownOn(result(total = 14, groups = listOf(group(0, "1d20", 14)))))
  }

  @Test
  fun `two groups keep their subtotals, because the total is the sum of them`() {
    val two = listOf(group(0, "3d6", 11), group(1, "1d20", 14))

    assertTrue(Subtotals.shownOn(result(total = 25, groups = two)))
  }

  @Test
  fun `a group with a modifier beside it keeps its subtotal`() {
    // `4d6dl1 + 4`: the rows have to add up to nineteen, and they cannot do
    // that with the fifteen missing.
    val sheet = result(total = 19, groups = listOf(group(0, "4d6dl1", 15)), adjustments = listOf(4))

    assertTrue(Subtotals.shownOn(sheet))
  }

  @Test
  fun `a group whose subtotal is not the total keeps it`() {
    // `(3d6 + 5) / 2`: the arithmetic is the formula's, so eleven and eight
    // are two different facts and both belong on the sheet.
    assertTrue(Subtotals.shownOn(result(total = 8, groups = listOf(group(0, "3d6", 11)))))
  }

  @Test
  fun `a sheet with no groups at all asks for nothing`() {
    // There is no row to carry a subtotal, so the answer cannot be "hide one".
    assertTrue(Subtotals.shownOn(result(total = 0, groups = emptyList())))
  }

  private fun result(
    total: Long,
    groups: List<RolledGroup>,
    adjustments: List<Long> = emptyList(),
  ) = RollResult(formula = "f", total = total, groups = groups, adjustments = adjustments)

  private fun group(
    id: Int,
    notation: String,
    subtotal: Long,
  ) = RolledGroup(
    id = id,
    notation = notation,
    setId = "builtin",
    requestedSetId = "builtin",
    subtotal = subtotal,
    dice = listOf(RolledDie(instanceIndex = id, dieId = "d6", value = 1)),
  )
}
