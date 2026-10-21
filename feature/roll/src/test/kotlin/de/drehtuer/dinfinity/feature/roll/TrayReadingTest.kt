package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.NotationErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the tray is said to hold, in every state it can be in.
 *
 * The tray is a drawing surface with nothing under it, so this is the whole of
 * what a screen reader ever learns about the app's home screen
 * (`docs/architecture.md`, "Accessibility"). Worth a `when` the compiler checks
 * rather than a lookup at the draw site: a state nobody gave words to would be
 * a silent tray, which is exactly the bug this exists to prevent.
 */
class TrayReadingTest {
  @Test
  fun `nothing typed is an empty table`() {
    assertEquals(TrayReading.Empty, TrayReading.of(RollState.Empty))
  }

  @Test
  fun `a formula that does not read leaves the table empty`() {
    // No body is ever created for one of these, so the tray really is empty.
    // Why is said under the formula, where it can be fixed.
    val invalid =
      RollState.Invalid(
        NotationError(code = NotationErrorCode.Empty, message = "nothing to roll", range = 0..0),
      )

    assertEquals(TrayReading.Empty, TrayReading.of(invalid))
  }

  @Test
  fun `a throw the table refuses leaves it empty too`() {
    val refused = RollState.TooMany(diceCount = 500, largestThatFits = 40, reason = "too many")

    assertEquals(TrayReading.Empty, TrayReading.of(refused))
  }

  @Test
  fun `dice waiting to be thrown are counted`() {
    assertEquals(TrayReading.Ready(3), TrayReading.of(RollState.Ready(diceCount = 3, scale = 1.0)))
  }

  @Test
  fun `dice in the air are counted`() {
    assertEquals(TrayReading.Rolling(8), TrayReading.of(RollState.Rolling(diceCount = 8)))
  }

  @Test
  fun `a chain waiting on a hand counts the dice already down`() {
    // Not the throws it earned: those are not on the table yet, and what a
    // screen reader is told is what is there to find.
    assertEquals(
      TrayReading.ShakeAgain(4),
      TrayReading.of(RollState.ShakeAgain(diceCount = 4, waiting = 2)),
    )
  }

  @Test
  fun `a roll that gave up counts the dice that never stopped`() {
    assertEquals(TrayReading.Stalled(2), TrayReading.of(RollState.Stalled(unsettled = 2, read = 6)))
  }

  @Test
  fun `dice nobody could read are counted, and only those`() {
    // What a screen reader hears over a tray waiting for a shake: how many
    // dice the shake will throw again, not how many are on the table.
    assertEquals(TrayReading.ThrowAgain(1), TrayReading.of(RollState.ThrowAgain(unread = 1, read = 3)))
  }

  @Test
  fun `a throw that has landed is its total`() {
    val settled = RollState.Settled(result = RollResult(formula = "3d6", total = 11), divides = false)

    assertEquals(TrayReading.Settled(11), TrayReading.of(settled))
  }

  @Test
  fun `a landed throw with dice picked up says how many the shake will throw`() {
    val settled = RollState.Settled(result = RollResult(formula = "3d6", total = 11), divides = false)

    assertEquals(TrayReading.Settled(11, picked = 2), TrayReading.of(settled, picked = 2))
    // Only a landed roll has dice to pick; a count handed to any other state
    // is not said, because there is nothing it could be about.
    assertEquals(TrayReading.Rolling(3), TrayReading.of(RollState.Rolling(diceCount = 3), picked = 2))
  }
}
