package de.drehtuer.dinfinity.simulation.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rung 4 of the ladder, made mechanical: **nothing touches a die that has come
 * to rest** (`docs/physics-and-rendering.md`).
 *
 * This is the rule the whole app's credibility rests on. A die left stacked is
 * a bug; a die visibly shoved after it stopped turns a roll into an
 * arrangement in front of the player's eyes, which is worse.
 */
class CorrectionLadderTest {
  @Test
  fun `a die at rest may not be touched, however slowly it is going`() {
    assertFalse(CorrectionLadder.mayTouch(DieMotion.Stopped, atRest = true))
    assertFalse(CorrectionLadder.mayTouch(DieMotion(9.0, 0.01), atRest = true))
    assertFalse(CorrectionLadder.mayTouch(DieMotion(60.0, 2.0), atRest = true))
  }

  @Test
  fun `a die still slowing may be nudged`() {
    assertTrue(CorrectionLadder.mayTouch(DieMotion(60.0, 2.0), atRest = false))
  }

  @Test
  fun `a die going full tilt is left alone, because there is nothing to fix yet`() {
    assertFalse(CorrectionLadder.mayTouch(DieMotion(800.0, 30.0), atRest = false))
  }

  @Test
  fun `a die that has stopped but is not yet counted as at rest is left alone too`() {
    assertFalse(CorrectionLadder.mayTouch(DieMotion.Stopped, atRest = false))
  }

  @Test
  fun `the same roll always produces the same nudge`() {
    val motion = DieMotion(60.0, 2.0)
    assertEquals(
      CorrectionLadder.bias(seed = 42, dieIndex = 3, step = 900, motion = motion),
      CorrectionLadder.bias(seed = 42, dieIndex = 3, step = 900, motion = motion),
    )
  }

  @Test
  fun `different dice, steps and seeds get different nudges`() {
    val motion = DieMotion(60.0, 2.0)
    val one = CorrectionLadder.bias(1, 0, 10, motion)
    assertTrue(one != CorrectionLadder.bias(2, 0, 10, motion), "the seed should matter")
    assertTrue(one != CorrectionLadder.bias(1, 1, 10, motion), "which die should matter")
    assertTrue(one != CorrectionLadder.bias(1, 0, 11, motion), "when should matter")
  }

  @Test
  fun `a nudge is a share of the speed the die still has, so a slow die gets almost none`() {
    val fast = CorrectionLadder.bias(1, 0, 10, DieMotion(100.0, 2.0)).length
    val slow = CorrectionLadder.bias(1, 0, 10, DieMotion(10.0, 2.0)).length
    assertEquals(100.0 * CorrectionLadder.BIAS_SHARE_OF_SPEED, fast, 1e-9)
    assertEquals(10.0 * CorrectionLadder.BIAS_SHARE_OF_SPEED, slow, 1e-9)
    assertTrue(slow < fast)
  }

  @Test
  fun `a nudge never pushes a die down into the floor`() {
    (0 until 200).forEach { step ->
      assertTrue(CorrectionLadder.bias(7, step % 5, step, DieMotion(60.0, 2.0)).z >= 0.0)
    }
  }

  @Test
  fun `the budgets are the ones the device harness holds the physics to`() {
    assertEquals(0.005, CorrectionLadder.CORRECTION_BUDGET)
    assertEquals(0.0005, CorrectionLadder.RETHROW_BUDGET)
  }

  @Test
  fun `a clean roll is inside the budget`() {
    assertTrue(CorrectionLadder.withinBudget(outcome(dice = 20, corrections = 0, rethrows = 0)))
  }

  @Test
  fun `a roll that corrected too many dice is outside it`() {
    assertFalse(CorrectionLadder.withinBudget(outcome(dice = 20, corrections = 1, rethrows = 0)))
  }

  @Test
  fun `a roll that re-threw too many dice is outside it`() {
    assertFalse(CorrectionLadder.withinBudget(outcome(dice = 100, corrections = 0, rethrows = 1)))
  }

  @Test
  fun `a roll of nothing is trivially inside it`() {
    assertTrue(CorrectionLadder.withinBudget(SimulationOutcome(faces = emptyMap())))
  }

  private fun outcome(
    dice: Int,
    corrections: Int,
    rethrows: Int,
  ): SimulationOutcome =
    SimulationOutcome(
      faces = (0 until dice).associateWith { 0 },
      corrections = corrections,
      rethrows = rethrows,
    )
}
