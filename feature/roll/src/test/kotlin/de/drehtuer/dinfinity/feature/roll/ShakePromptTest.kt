package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.NotationErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which states the prompt over the tray speaks for, and how many dice it says
 * the shake will throw ([ShakePrompt], decision 84).
 *
 * Plain JVM, for the reason `TrayReading` is: what the screen says is a
 * mapping from a state to a sentence. Four things owe a shake — dice nobody
 * could read, dice that never stopped, dice a chain earned, and dice a finger
 * picked up. **Advantage is not one of them**: `2d20kh1` is a single throw
 * followed by a selection, so there is nothing left to wait for.
 */
class ShakePromptTest {
  @Test
  fun `a throw that left dice nobody could read asks to re-throw those`() {
    assertEquals(
      ShakePrompt(ShakePrompt.Why.AGAIN, 1),
      ShakePrompt.of(RollState.ThrowAgain(unread = 1, read = 3), picked = 0),
    )
  }

  @Test
  fun `a roll that gave up asks to re-throw the dice that never stopped`() {
    assertEquals(
      ShakePrompt(ShakePrompt.Why.AGAIN, 2),
      ShakePrompt.of(RollState.Stalled(unsettled = 2, read = 18), picked = 0),
    )
  }

  @Test
  fun `a chain that earned throws asks for the dice it earned`() {
    assertEquals(
      ShakePrompt(ShakePrompt.Why.EARNED, 3),
      ShakePrompt.of(RollState.ShakeAgain(diceCount = 8, waiting = 3), picked = 0),
    )
  }

  @Test
  fun `a landed roll with dice picked asks to throw the picked dice`() {
    val landed = RollState.Settled(RollResult(formula = "3d6", total = 11L))

    assertEquals(ShakePrompt(ShakePrompt.Why.PICKED, 2), ShakePrompt.of(landed, picked = 2))
  }

  @Test
  fun `a landed roll with nothing picked asks for nothing`() {
    // The next shake throws the whole roll again, which is the ordinary throw
    // and is not owed to anything.
    assertNull(ShakePrompt.of(RollState.Settled(RollResult(formula = "3d6", total = 11L)), picked = 0))
  }

  @Test
  fun `nothing else asks for a shake, including dice in the air`() {
    // A roll in the air is what the shake that answered a prompt turns into,
    // so this is the prompt going away.
    listOf(
      RollState.Empty,
      RollState.Invalid(NotationError(NotationErrorCode.Empty, "nothing typed", IntRange.EMPTY)),
      RollState.TooMany(diceCount = 500, largestThatFits = 100, reason = "too many"),
      RollState.Ready(diceCount = 3, scale = 1.0),
      RollState.Rolling(diceCount = 3),
    ).forEach { state -> assertNull("$state asked for a shake", ShakePrompt.of(state, picked = 1)) }
  }
}
