package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.core.model.Rounding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A roll's breakdown, as it is stored (`docs/statistics.md`, "Storage").
 *
 * The property that matters is not "it round-trips" but **what it carries**: a
 * breakdown means what it meant then, so everything the history screen shows
 * has to be in the text, and nothing may need looking up to draw a past roll.
 */
class BreakdownTest {
  @Test
  fun `a breakdown comes back as it went in`() {
    val read = Breakdown.read(Breakdown.of(fourD6DropLowest()))

    assertEquals(1, read.size)
    assertEquals("4d6dl1", read.single().notation)
    assertEquals(listOf(6, 5, 4, 1), read.single().dice.map { it.value })
    assertEquals(15L, read.single().subtotal)
  }

  @Test
  fun `a dropped die is kept, not hidden — a player wants to see the 1`() {
    val read = Breakdown.read(Breakdown.of(fourD6DropLowest())).single()

    assertEquals(4, read.dice.size)
    assertEquals(listOf(6, 5, 4), read.kept.map { it.value })
    assertTrue(DieNote.Dropped in read.dice.last().notes)
  }

  @Test
  fun `a natural maximum survives, because the history paints it`() {
    val read = Breakdown.read(Breakdown.of(fourD6DropLowest())).single()

    assertTrue(read.dice.first().naturalMax)
    assertTrue(read.dice.last().naturalMin)
  }

  @Test
  fun `the label a face carried is stored, because the die may be gone tomorrow`() {
    // A die with symbols on it is the case the whole design is for: "☠" cannot
    // be recomputed from the value once the set is uninstalled.
    val result = one(RolledDie(instanceIndex = 0, dieId = "skull", value = 1, label = "☠"))

    assertEquals(
      "☠",
      Breakdown
        .read(Breakdown.of(result))
        .single()
        .dice
        .single()
        .label,
    )
  }

  @Test
  fun `a label that is only the value again is not stored twice`() {
    // Fifty rolls a session is a lot of rows to store the same number in twice.
    val result = one(RolledDie(instanceIndex = 0, dieId = "d6", value = 4))

    assertFalse("\"label\"" in Breakdown.of(result))
    assertEquals(
      "4",
      Breakdown
        .read(Breakdown.of(result))
        .single()
        .dice
        .single()
        .label,
    )
  }

  @Test
  fun `which set the dice came from is stored, and so is the one that was asked for`() {
    val result =
      RollResult(
        formula = "brass:1d20",
        total = 20,
        groups =
          listOf(
            RolledGroup(
              id = 0,
              notation = "brass:1d20",
              setId = "builtin",
              requestedSetId = "brass",
              dice = listOf(RolledDie(instanceIndex = 0, dieId = "d20", value = 20)),
              subtotal = 20,
            ),
          ),
      )

    val read = Breakdown.read(Breakdown.of(result)).single()
    assertEquals("builtin", read.setId)
    assertEquals("brass", read.requestedSetId)
    assertTrue(read.fellBack)
  }

  @Test
  fun `a group that did not fall back does not say so twice`() {
    val read = Breakdown.read(Breakdown.of(fourD6DropLowest())).single()

    assertFalse(read.fellBack)
    assertEquals(read.setId, read.requestedSetId)
  }

  @Test
  fun `the total and the rounding it was computed under are in the text`() {
    val json = Breakdown.of(fourD6DropLowest().copy(rounding = Rounding.Nearest))

    assertTrue("15" in json)
    assertTrue("Nearest" in json)
  }

  @Test
  fun `the seed is never in it`() {
    // Determinism is for tests and bug reports, not a feature: the history has
    // no replay and never shows a seed (decision 13).
    assertFalse("seed" in Breakdown.of(fourD6DropLowest()))
  }

  @Test
  fun `something unreadable comes back empty rather than losing the roll`() {
    // A breakdown written by an older version is still a record of a roll
    // somebody made. The total is in its own column and still draws.
    assertEquals(emptyList<StoredGroup>(), Breakdown.read("not json at all"))
    assertEquals(emptyList<StoredGroup>(), Breakdown.read(""))
    assertEquals(emptyList<StoredGroup>(), Breakdown.read("""{"groups":"not a list"}"""))
  }

  @Test
  fun `a breakdown with a field this version does not know still reads`() {
    val json = """{"total":7,"groups":[{"notation":"1d6","set":"builtin","subtotal":7,"mood":"smug","dice":[]}]}"""

    assertEquals("1d6", Breakdown.read(json).single().notation)
  }

  @Test
  fun `a roll of nothing but arithmetic stores no groups and reads back as none`() {
    val result = RollResult(formula = "4 + 4", total = 8)

    assertEquals(emptyList<StoredGroup>(), Breakdown.read(Breakdown.of(result)))
  }

  private fun fourD6DropLowest() =
    RollResult(
      formula = "4d6dl1",
      total = 15,
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "4d6dl1",
            setId = "builtin",
            requestedSetId = "builtin",
            subtotal = 15,
            dice =
              listOf(
                RolledDie(instanceIndex = 0, dieId = "d6", value = 6, naturalMax = true),
                RolledDie(instanceIndex = 1, dieId = "d6", value = 5),
                RolledDie(instanceIndex = 2, dieId = "d6", value = 4),
                RolledDie(
                  instanceIndex = 3,
                  dieId = "d6",
                  value = 1,
                  naturalMin = true,
                  notes = setOf(DieNote.Dropped),
                ),
              ),
          ),
        ),
    )

  private fun one(die: RolledDie) =
    RollResult(
      formula = "1d6",
      total = die.value.toLong(),
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "1d6",
            setId = "builtin",
            requestedSetId = "builtin",
            dice = listOf(die),
            subtotal = die.value.toLong(),
          ),
        ),
    )
}
