package de.drehtuer.dinfinity.core.notation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every example in the table at the top of `docs/dice-notation.md`, plus the
 * parts of the grammar the examples do not reach.
 */
class FormulaParserTest {
  @Test
  fun `d20 is one twenty-sided die`() {
    assertEquals("1d20", render(parsed("d20").root))
  }

  @Test
  fun `3d6 is three six-sided dice`() {
    assertEquals("3d6", render(parsed("3d6").root))
  }

  @Test
  fun `plus and minus chain left to right`() {
    assertEquals("((3d6 + 1d20) - 4)", render(parsed("3d6 + 1d20 - 4").root))
  }

  @Test
  fun `2d20kh1 is advantage`() {
    assertEquals("2d20kh1", render(parsed("2d20kh1").root))
  }

  @Test
  fun `2d20kl1 is disadvantage`() {
    assertEquals("2d20kl1", render(parsed("2d20kl1").root))
  }

  @Test
  fun `4d6dl1 is stat generation`() {
    assertEquals("4d6dl1", render(parsed("4d6dl1").root))
  }

  @Test
  fun `dh drops the highest`() {
    assertEquals("4d6dh1", render(parsed("4d6dh1").root))
  }

  @Test
  fun `d100 and d percent are the same thing`() {
    assertEquals("1d%", render(parsed("d100").root))
    assertEquals("1d%", render(parsed("d%").root))
  }

  @Test
  fun `8d6 bang explodes`() {
    assertEquals("8d6!", render(parsed("8d6!").root))
  }

  @Test
  fun `brackets and multiplication`() {
    assertEquals("(2 * (1d8 + 3))", render(parsed("2 * (1d8 + 3)").root))
  }

  @Test
  fun `multiplication binds tighter than addition`() {
    assertEquals("(2 + (3 * 4))", render(parsed("2 + 3 * 4").root))
  }

  @Test
  fun `division binds as tightly as multiplication, left to right`() {
    assertEquals("((8 / 2) * 3)", render(parsed("8 / 2 * 3").root))
  }

  @Test
  fun `a trailing label is kept and takes no part in the maths`() {
    val fire = parsed("1d6 + 1d4 [Fire]")
    assertEquals("Fire", fire.label)
    assertEquals("(1d6 + 1d4)", render(fire.root))
  }

  @Test
  fun `a label is trimmed, and an empty one is no label at all`() {
    assertEquals("Fire", parsed("1d6 [  Fire  ]").label)
    assertNull(parsed("1d6 []").label)
  }

  @Test
  fun `a setref takes the dice from a named set`() {
    assertEquals("brass:1d20", render(parsed("brass:1d20").root))
  }

  @Test
  fun `a setref may carry a count and modifiers`() {
    assertEquals("brass-and-bone:2d20kh1", render(parsed("brass-and-bone:2d20kh1").root))
  }

  @Test
  fun `dF is the fudge die`() {
    assertEquals("4dF", render(parsed("4dF").root))
  }

  @Test
  fun `r rerolls and min clamps`() {
    assertEquals("4d6r1", render(parsed("4d6r1").root))
    assertEquals("4d6min2", render(parsed("4d6min2").root))
  }

  @Test
  fun `modifiers stack in the order they are written`() {
    assertEquals("4d6r1min2dl1!", render(parsed("4d6r1min2dl1!").root))
  }

  @Test
  fun `a leading minus negates one atom`() {
    assertEquals("(2d6 + -1d4)", render(parsed("2d6 + -1d4").root))
    assertEquals("-4", render(parsed("-4").root))
  }

  @Test
  fun `a formula may be a plain number`() {
    assertEquals("4", render(parsed("4").root))
  }

  @Test
  fun `whitespace between tokens means nothing`() {
    assertEquals("((3d6 + 1d20) - 4)", render(parsed("  3d6+1d20-4  ").root))
    assertEquals("(2 * (1d8 + 3))", render(parsed("2*(1d8+3)").root))
  }

  @Test
  fun `notation is case-insensitive except for set ids`() {
    assertEquals("2d20kh1", render(parsed("2D20KH1").root))
    assertEquals("1dF", render(parsed("1df").root))
    assertEquals("4d6dl1", render(parsed("4D6DL1").root))
  }

  @Test
  fun `brackets may nest to the documented depth`() {
    val depth = NotationLimits.MAX_PARENTHESIS_DEPTH
    assertEquals("1d6", render(parsed("(".repeat(depth) + "1d6" + ")".repeat(depth)).root))
  }

  @Test
  fun `dice nodes are numbered in source order, which is throw order`() {
    val formula = parsed("3d6 + 1d20 + 2d8")
    assertEquals(listOf(0, 1, 2), formula.diceNodes.map(DiceNode::id))
    assertEquals(listOf(6, 20, 8), formula.diceNodes.map { (it.sides as Sides.Numeric).value })
  }

  @Test
  fun `a formula with no dice has no dice nodes`() {
    assertEquals(emptyList(), parsed("2 * (3 + 4)").diceNodes)
  }

  @Test
  fun `parseOrNull gives the formula or nothing`() {
    assertEquals("1d20", FormulaParser.parseOrNull("d20")?.let { render(it.root) })
    assertNull(FormulaParser.parseOrNull("3d6 +"))
  }

  @Test
  fun `the formula keeps the text it was typed as`() {
    assertEquals("  3d6 + 2 ", parsed("  3d6 + 2 ").text)
  }
}
