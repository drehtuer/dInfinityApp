package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollResultTest {
  @Test
  fun `a dropped die stays in the breakdown so the player can see it`() {
    val group =
      RolledGroup(
        id = 0,
        notation = "4d6dl1",
        setId = "builtin",
        requestedSetId = "builtin",
        dice =
          listOf(
            die(0, 1, notes = setOf(DieNote.Dropped)),
            die(1, 4),
            die(2, 5),
            die(3, 6),
          ),
        subtotal = 15,
      )
    assertEquals(4, group.dice.size)
    assertEquals(listOf(4, 5, 6), group.kept.map(RolledDie::value))
    assertFalse(group.dice.first().kept)
  }

  @Test
  fun `a die with nothing to say about it counts`() {
    assertTrue(die(0, 3).kept)
  }

  @Test
  fun `the result gathers every group's dice in throw order`() {
    val result =
      RollResult(
        formula = "3d6 + 1d20",
        total = 25,
        groups =
          listOf(
            RolledGroup(0, "3d6", "builtin", "builtin", listOf(die(0, 2), die(1, 3), die(2, 5)), 10),
            RolledGroup(1, "1d20", "builtin", "builtin", listOf(die(3, 15)), 15),
          ),
      )
    assertEquals(listOf(0, 1, 2, 3), result.dice.map(RolledDie::instanceIndex))
    assertEquals(25L, result.total)
  }

  @Test
  fun `a kept natural max is what the sheet paints in the accent`() {
    val result = result(die(0, 20, naturalMax = true))
    assertTrue(result.hasNaturalMax)
  }

  @Test
  fun `a dropped natural max is not celebrated`() {
    val result = result(die(0, 20, naturalMax = true, notes = setOf(DieNote.Dropped)))
    assertFalse(result.hasNaturalMax)
  }

  @Test
  fun `a roll with no natural max says so`() {
    assertFalse(result(die(0, 7)).hasNaturalMax)
  }

  @Test
  fun `a natural low is marked too, because statistics count them`() {
    assertTrue(die(0, 1, naturalMin = true).naturalMin)
  }

  @Test
  fun `a group names the set it fell back to`() {
    val group = RolledGroup(0, "1d12", setId = "builtin", requestedSetId = "brass")
    assertTrue(group.fellBack)
    assertFalse(RolledGroup(0, "1d12", setId = "brass", requestedSetId = "brass").fellBack)
  }

  @Test
  fun `a die prints what its face said, not its value`() {
    assertEquals("💀", die(0, 1).copy(label = "💀").label)
    assertEquals("7", die(0, 7).label)
  }

  @Test
  fun `a clean roll needed no re-throws and no forced settles`() {
    val result = result(die(0, 4))
    assertEquals(0, result.rethrows)
    assertEquals(0, result.forcedSettles)
  }

  @Test
  fun `re-throws and forced settles are counted, because they are physics bugs`() {
    val result = result(die(0, 4)).copy(rethrows = 2, forcedSettles = 1)
    assertEquals(2, result.rethrows)
    assertEquals(1, result.forcedSettles)
  }

  @Test
  fun `the rounding the total was computed under is carried, so it can be redone`() {
    assertEquals(Rounding.Default, result(die(0, 4)).rounding)
    assertEquals(Rounding.Up, result(die(0, 4)).copy(rounding = Rounding.Up).rounding)
  }

  @Test
  fun `every note a breakdown can carry has a name`() {
    assertEquals(
      listOf(
        "Dropped",
        "Rerolled",
        "FromExplosion",
        "ClampedToMin",
        "PercentileTens",
        "PercentileUnits",
        "ExplosionLimitReached",
      ),
      DieNote.entries.map(DieNote::name),
    )
  }

  @Test
  fun `a result that adds nothing adds up on its own`() {
    // The plain case, and the one the sheet showed correctly all along: the
    // group subtotals are the total.
    val plain = scored(subtotal = 11, total = 11)

    assertEquals(emptyList<Long>(), plain.adjustments)
    assertTrue(plain.itemised, "dice alone did not add up")
  }

  @Test
  fun `a result with a modifier adds up once the modifier is counted`() {
    // What the sheet's promise comes to in code: the rows on screen — the
    // subtotals and these numbers — are all there is (`docs/dice-notation.md`,
    // "Evaluation", step 7).
    val withFour = scored(subtotal = 11, total = 15, adjustments = listOf(4L))

    assertTrue(withFour.itemised, "the rows did not add up to the total")
  }

  @Test
  fun `a total the rows cannot reach says so rather than pretending`() {
    // A product or a division is scored the same way and is just as correct,
    // but its total cannot be read off the rows. The sheet has to know which
    // of the two it is holding.
    val doubled = scored(subtotal = 11, total = 22)

    assertFalse(doubled.itemised, "a product's rows claimed to add up to its total")
  }

  @Test
  fun `taking away counts as adding a negative`() {
    val lessTwo = scored(subtotal = 11, total = 9, adjustments = listOf(-2L))

    assertTrue(lessTwo.itemised)
    assertEquals(listOf(-2L), lessTwo.adjustments)
  }

  private fun scored(
    subtotal: Long,
    total: Long,
    adjustments: List<Long> = emptyList(),
  ): RollResult =
    RollResult(
      formula = "3d6",
      total = total,
      groups = listOf(RolledGroup(0, "3d6", "builtin", "builtin", emptyList(), subtotal)),
      adjustments = adjustments,
    )

  private fun result(vararg dice: RolledDie): RollResult =
    RollResult(
      formula = "1d20",
      total = dice.sumOf { it.value }.toLong(),
      groups = listOf(RolledGroup(0, "1d20", "builtin", "builtin", dice.toList(), 0)),
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
}
