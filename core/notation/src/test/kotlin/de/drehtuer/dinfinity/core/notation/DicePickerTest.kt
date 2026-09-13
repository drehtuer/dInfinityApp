package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dice added by tapping rather than by typing
 * (`design/dInfinity.dc.html`, option 1h).
 *
 * The claim under all of it: **a tap writes a formula somebody could have
 * typed, and never throws away one they did type.** The first half is what
 * keeps the graph, the breakdown and the statistics identical either way
 * (`docs/architecture.md`, decision 31); the second is what makes a mis-tap
 * survivable.
 */
class DicePickerTest {
  private val d6 = PickableDie("d6", Sides.Numeric(6))
  private val d20 = PickableDie("d20", Sides.Numeric(20))
  private val percent = PickableDie("d%", Sides.Percentile)

  @Test
  fun `a tap on an empty field starts the formula`() {
    assertEquals("1d6", DicePicker.add("", d6))
  }

  @Test
  fun `a tap on a die already in the formula counts it up`() {
    assertEquals("3d6", DicePicker.add("2d6", d6))
  }

  @Test
  fun `a die with no count written is one of them`() {
    assertEquals("2d6", DicePicker.add("d6", d6))
  }

  @Test
  fun `a tap on a die that is not there yet adds a group`() {
    assertEquals("2d6 + 1d20", DicePicker.add("2d6", d20))
  }

  @Test
  fun `a new group goes in front of the modifier, where the dice are`() {
    // `3d6 + 1d20 - 4` reads as dice and then arithmetic. `3d6 - 4 + 1d20`
    // is the same roll and a worse sentence.
    assertEquals("3d6 + 1d20 - 4", DicePicker.add("3d6 - 4", d20))
  }

  @Test
  fun `a formula that is nothing but a modifier gets its dice in front`() {
    assertEquals("1d20 + 4", DicePicker.add("4", d20))
  }

  @Test
  fun `the spelling a player used is the spelling they get back`() {
    // Only the spacing around the top-level operators is normalised: it is the
    // part being cut into. The `dl1` and the brackets are theirs.
    assertEquals("4d6dl1 + (1d8 + 2) * 2 + 1d20", DicePicker.add("4d6dl1 + (1d8 + 2) * 2", d20))
  }

  @Test
  fun `a label survives a tap and is not re-spaced`() {
    assertEquals("3d6 + 1d20 [Big  hit]", DicePicker.add("3d6 [Big  hit]", d20))
  }

  @Test
  fun `d100 and d percent are the same die and share a badge`() {
    assertEquals("3d100", DicePicker.add("2d100", percent))
    assertEquals(2, DicePicker.counts("2d%", listOf(percent))[percent])
  }

  @Test
  fun `a long press takes one die off`() {
    assertEquals("2d6", DicePicker.remove("3d6", d6))
  }

  @Test
  fun `taking the last die off takes the group with it`() {
    assertEquals("1d20", DicePicker.remove("1d6 + 1d20", d6))
  }

  @Test
  fun `taking the last die off the last group leaves an empty field`() {
    assertEquals("", DicePicker.remove("1d6", d6))
  }

  @Test
  fun `taking the first group away keeps the sign of what follows`() {
    // `1d20 - 4` without the d20 is minus four, not four.
    assertEquals("- 4", DicePicker.remove("1d20 - 4", d20))
  }

  @Test
  fun `a long press with nothing to take off does nothing`() {
    assertEquals("2d20", DicePicker.remove("2d20", d6))
  }

  @Test
  fun `a group with a modifier on it is not the picker's to change`() {
    // Taking one die out of `4d6dl1` would silently change what gets dropped.
    assertEquals(0, DicePicker.counts("4d6dl1", listOf(d6))[d6])
    assertEquals("4d6dl1", DicePicker.remove("4d6dl1", d6))
    assertEquals("4d6dl1 + 1d6", DicePicker.add("4d6dl1", d6))
  }

  @Test
  fun `a subtracted group is not counted and not removed`() {
    assertEquals(0, DicePicker.counts("2d20 - 1d6", listOf(d6))[d6])
    assertEquals("2d20 - 1d6", DicePicker.remove("2d20 - 1d6", d6))
  }

  @Test
  fun `dice inside brackets belong to the bracket, not to the row`() {
    // Flattening across a bracket would let a long press pull a die out of a
    // group it cannot be pulled out of, and invert the sign of the rest.
    assertEquals(0, DicePicker.counts("2d20 - (1d6 + 2)", listOf(d6))[d6])
    assertEquals("2d20 - (1d6 + 2)", DicePicker.remove("2d20 - (1d6 + 2)", d6))
  }

  @Test
  fun `a formula that does not parse is left exactly as it is`() {
    assertEquals("3d6 +", DicePicker.add("3d6 +", d20))
    assertEquals("3d6 +", DicePicker.remove("3d6 +", d6))
    assertTrue(DicePicker.counts("3d6 +", listOf(d6)).isEmpty())
  }

  @Test
  fun `a set reference is part of which die a badge counts`() {
    val brass = PickableDie("d20", Sides.Numeric(20), setRef = "brass")
    assertEquals(0, DicePicker.counts("2d20", listOf(brass))[brass])
    assertEquals("2d20 + brass:1d20", DicePicker.add("2d20", brass))
    assertEquals("brass:3d20", DicePicker.add("brass:2d20", brass))
    assertEquals("brass:1d20", DicePicker.remove("brass:2d20", brass))
  }

  @Test
  fun `counts come from the whole top-level sum`() {
    val counted = DicePicker.counts("2d6 + 1d20 + 3d6", listOf(d6, d20))
    assertEquals(5, counted[d6])
    assertEquals(1, counted[d20])
  }

  @Test
  fun `the row offers the standard dice the set defines and nothing else`() {
    // A set's own `skull-d6` has no spelling plain notation can carry, so a
    // button for it would be a button whose taps disappear
    // (`docs/architecture.md`, decision 31).
    val set = setOf("d6", "d10", "d10-tens", "df", "skull-d6")

    assertEquals(
      listOf("d6", "d10", "d%", "dF"),
      DicePicker.offeredBy(set).map(PickableDie::notation),
    )
  }

  @Test
  fun `the row writes the set reference it was built with`() {
    val offered = DicePicker.offeredBy(setOf("d6"), setRef = "brass").single()

    assertEquals("brass:1d6", DicePicker.add("", offered))
  }

  @Test
  fun `everything the row writes parses back to what was tapped`() {
    // The property the whole picker rests on: a tap is a formula.
    var text = ""
    val row = listOf(d6, d20, percent)
    repeat(REPEATS) { turn ->
      text = DicePicker.add(text, row[turn % row.size])
      assertTrue(FormulaParser.parse(text) is ParseResult.Parsed, "a tap wrote something unreadable: $text")
    }
    assertEquals(REPEATS / row.size, DicePicker.counts(text, row)[d6])

    repeat(REPEATS) { turn ->
      text = DicePicker.remove(text, row[turn % row.size])
      assertTrue(text.isEmpty() || FormulaParser.parse(text) is ParseResult.Parsed, "a press left $text")
    }
    assertEquals("", text)
  }

  private fun setOf(vararg dieIds: String): DiceSet =
    DiceSet(
      id = "test",
      name = "Test",
      version = "1.0.0",
      dice = dieIds.map { Die.standard(id = it, shape = DieShape.Cube) },
    )

  private companion object {
    const val REPEATS = 9
  }
}
