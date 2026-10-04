package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The visit's dropped steps, added up from snapshots alone
 * (`docs/TODO.md`, Step 5.6 — is the first throw of a session different?).
 */
class DroppedTallyTest {
  @Test
  fun `a visit with no roll has dropped nothing`() {
    assertEquals(0L, DroppedTally().total)
  }

  @Test
  fun `within one roll the total is that roll's figure, not a sum of every frame`() {
    val tally = DroppedTally()
    tally.saw(RollDiagnostics(steps = 4, droppedSteps = 10))
    tally.saw(RollDiagnostics(steps = 8, droppedSteps = 10))

    assertEquals(14L, tally.saw(RollDiagnostics(steps = 12, droppedSteps = 14)))
  }

  @Test
  fun `a snapshot with fewer steps is a new roll, and the old one is kept`() {
    val tally = DroppedTally()
    tally.saw(RollDiagnostics(steps = 300, droppedSteps = 20))

    assertEquals(20L, tally.saw(RollDiagnostics(steps = 0)))
    assertEquals(25L, tally.saw(RollDiagnostics(steps = 4, droppedSteps = 5)))
  }

  @Test
  fun `a snapshot with fewer dropped steps is a new roll too`() {
    // Two rolls can reach the same step count; the dropped steps of one roll
    // never go down, so a drop in them is a new roll as surely as the steps.
    val tally = DroppedTally()
    tally.saw(RollDiagnostics(steps = 8, droppedSteps = 6))

    assertEquals(7L, tally.saw(RollDiagnostics(steps = 8, droppedSteps = 1)))
  }
}
