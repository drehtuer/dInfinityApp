package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RolledDie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which of the three a landed die is, without a screen.
 *
 * The sheet draws the answer as a colour and a strike, which is why it is
 * worth deciding somewhere a test can read: both the drawing and the words a
 * screen reader gets come from this one call, so they cannot come to disagree
 * (`docs/architecture.md`, "Accessibility").
 */
class DieReadingTest {
  @Test
  fun `an ordinary die is simply kept`() {
    assertEquals(DieReading.Kept, DieReading.of(die(value = 4)))
  }

  @Test
  fun `a die showing its highest face is called that`() {
    assertEquals(DieReading.NaturalMax, DieReading.of(die(value = 6, naturalMax = true)))
  }

  @Test
  fun `a dropped die is called that`() {
    assertEquals(DieReading.Dropped, DieReading.of(die(value = 1, notes = setOf(DieNote.Dropped))))
  }

  @Test
  fun `dropped wins over a natural maximum`() {
    // `4d6dh1` drops the 6. It is still a 6, and it still changed nothing —
    // and "did not count" is the more surprising of the two things to be told.
    val dropped = die(value = 6, naturalMax = true, notes = setOf(DieNote.Dropped))

    assertEquals(DieReading.Dropped, DieReading.of(dropped))
  }

  @Test
  fun `only the two that need words have words`() {
    // A die announced as "7, ordinary" on every row of a breakdown is noise,
    // and noise is what makes somebody turn the reader off.
    assertNull(DieReading.Kept.said)
    assertNotNull(DieReading.NaturalMax.said)
    assertNotNull(DieReading.Dropped.said)
  }

  @Test
  fun `the two that speak do not say the same thing`() {
    assertTrue(DieReading.NaturalMax.said != DieReading.Dropped.said)
  }

  private fun die(
    value: Int,
    naturalMax: Boolean = false,
    notes: Set<DieNote> = emptySet(),
  ): RolledDie =
    RolledDie(
      instanceIndex = 0,
      dieId = "d6",
      value = value,
      naturalMax = naturalMax,
      notes = notes,
    )
}
