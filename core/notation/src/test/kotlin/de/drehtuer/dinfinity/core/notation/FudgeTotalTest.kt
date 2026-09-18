package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RolledDie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FudgeTotalTest {
  @Test
  fun `a fudge roll that came out ahead says so with a plus`() {
    assertEquals("+2", FudgeTotal.writeRoll(2L, fudge(1, 1, 0, 0)))
  }

  @Test
  fun `and one that came out behind says so with a minus, not a hyphen`() {
    assertEquals("−2", FudgeTotal.writeRoll(-2L, fudge(-1, -1, 0, 0)))
  }

  @Test
  fun `the plus and the minus are the two the app was disagreeing about`() {
    // This is the whole bug: a minus printed its sign and a plus printed
    // nothing, so a player saw `-1` and `1` off the same die.
    assertEquals("−1", FudgeTotal.writeRoll(-1L, fudge(-1)))
    assertEquals("+1", FudgeTotal.writeRoll(1L, fudge(1)))
  }

  @Test
  fun `nothing either way takes no sign`() {
    assertEquals("0", FudgeTotal.writeRoll(0L, fudge(0)))
    assertEquals("0", FudgeTotal.writeRoll(0L, fudge(1, -1)))
  }

  @Test
  fun `an ordinary die is a count and is written as one`() {
    assertEquals("7", FudgeTotal.writeRoll(7L, listOf(die("d6", 3), die("d6", 4))))
    assertEquals("-3", FudgeTotal.writeRoll(-3L, listOf(die("d6", 1))))
  }

  @Test
  fun `a fudge die beside an ordinary one is a count, because the d6 is`() {
    assertEquals("4", FudgeTotal.writeRoll(4L, listOf(die("df", 1), die("d6", 3))))
    assertFalse(FudgeTotal.isFudgeRoll(listOf(die("df", 1), die("d6", 3))))
  }

  @Test
  fun `no dice at all is arithmetic, and arithmetic is a number`() {
    assertEquals("4", FudgeTotal.writeRoll(4L, emptyList()))
    assertFalse(FudgeTotal.isFudge(emptyList<String>()))
  }

  @Test
  fun `a dropped fudge die is still a fudge die`() {
    // What decides the spelling is what was thrown, not what counted: a
    // `4dFkh3` is still a Fudge roll and still reads as a sign.
    val dice = fudge(1, 1, 0) + RolledDie(instanceIndex = 3, dieId = "df", value = -1, notes = setOf(DieNote.Dropped))
    assertTrue(FudgeTotal.isFudgeRoll(dice))
    assertEquals("+2", FudgeTotal.writeRoll(2L, dice))
  }

  @Test
  fun `the minus is the one the faces are labelled with`() {
    // `dicesets/builtin`'s `df` is labelled with U+2212, and a total set
    // beside those dice should be drawn with the same glyph.
    assertEquals("−", FudgeTotal.MINUS)
    assertTrue(FudgeTotal.writeRoll(-1L, fudge(-1)).startsWith(FudgeTotal.MINUS))
  }

  @Test
  fun `the history asks the same question of its own kind of die`() {
    // The history stores `StoredDie`, not `RolledDie`, so the rule is keyed
    // on ids and both callers reach the same one.
    assertTrue(FudgeTotal.isFudge(listOf("df", "df")))
    assertFalse(FudgeTotal.isFudge(listOf("df", "d6")))
    assertEquals("+2", FudgeTotal.write(2L, listOf("df", "df")))
  }

  private fun fudge(vararg values: Int): List<RolledDie> = values.mapIndexed { index, value -> die("df", value, index) }

  private fun die(
    id: String,
    value: Int,
    index: Int = 0,
  ): RolledDie = RolledDie(instanceIndex = index, dieId = id, value = value)
}
