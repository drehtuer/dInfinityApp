package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.NotationErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which states are waiting on a hand, and how many dice that hand would throw
 * ([Awaiting]).
 *
 * Plain JVM, for the reason `TrayReading` is: what the screen says is a
 * mapping from a state to a sentence, and a mapping is arithmetic rather than
 * drawing. Only two states wait — a chain that earned a throw, and a throw
 * that gave up on some of its dice. **Advantage is not one of them**:
 * `2d20kh1` is a single throw followed by a selection, so there is nothing
 * left to wait for (`docs/dice-notation.md`, "Evaluation").
 */
class AwaitingTest {
  @Test
  fun `a chain that earned throws is waiting for the dice it earned`() {
    val awaiting = RollState.ShakeAgain(diceCount = 8, waiting = 3).awaiting()

    assertEquals(Awaiting(count = 3, stalled = false), awaiting)
  }

  @Test
  fun `a roll that gave up is waiting to throw the dice that never stopped`() {
    val awaiting = RollState.Stalled(unsettled = 2, read = 18).awaiting()

    assertEquals(Awaiting(count = 2, stalled = true), awaiting)
  }

  @Test
  fun `nothing else is waiting on anything`() {
    // Including a roll in the air, which already has the hand's moments, and
    // a roll that has landed, where the next shake is a new throw rather than
    // the rest of this one.
    assertNull(RollState.Empty.awaiting())
    assertNull(RollState.Invalid(NotationError(NotationErrorCode.Empty, "nothing typed", IntRange.EMPTY)).awaiting())
    assertNull(RollState.TooMany(diceCount = 500, largestThatFits = 100, reason = "too many").awaiting())
    assertNull(RollState.Ready(diceCount = 3, scale = 1.0).awaiting())
    assertNull(RollState.Rolling(diceCount = 3).awaiting())
    assertNull(RollState.Settled(RollResult(formula = "3d6", total = 11L)).awaiting())
  }
}
