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
  fun `a throw that has landed is its total`() {
    val settled = RollState.Settled(result = RollResult(formula = "3d6", total = 11), divides = false)

    assertEquals(TrayReading.Settled(11), TrayReading.of(settled))
  }
}
