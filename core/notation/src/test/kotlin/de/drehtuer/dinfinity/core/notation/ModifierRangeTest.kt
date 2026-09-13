package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parts that carry a position rather than a value: which characters a
 * modifier occupies, what an error points at when nothing is suggested, and
 * what happens when a roll asks for a throw nobody offered it.
 */
class ModifierRangeTest {
  private val catalog = DiceCatalog.of(listOf(StandardDice.set()))

  @Test
  fun `every modifier knows the characters it was written as`() {
    val node = parsed("4d6r1min2dl1!").diceNodes.single()
    assertEquals(listOf(3..4, 5..8, 9..11, 12..12), node.modifiers.map(DiceModifier::range))
    assertEquals("r1", "4d6r1min2dl1!".substring(node.modifiers[0].range))
    assertEquals("min2", "4d6r1min2dl1!".substring(node.modifiers[1].range))
    assertEquals("dl1", "4d6r1min2dl1!".substring(node.modifiers[2].range))
    assertEquals("!", "4d6r1min2dl1!".substring(node.modifiers[3].range))
  }

  @Test
  fun `a keep or drop modifier points at itself, not at the group`() {
    val node = parsed("2d20kh1").diceNodes.single()
    assertEquals(4..6, node.modifiers.single().range)
  }

  @Test
  fun `a node's range covers the whole group, modifiers and all`() {
    assertEquals(0..6, parsed("2d20kh1").diceNodes.single().range)
    assertEquals(6..15, parsed("1d6 + brass:1d20").diceNodes.last().range)
  }

  @Test
  fun `an error suggests nothing when there is nothing honest to suggest`() {
    assertNull(refused("1d6 + @").suggestion)
  }

  @Test
  fun `a formula and an error carry their optional parts as absent, not as blanks`() {
    assertNull(parsed("3d6").label)
    val error = NotationError(NotationErrorCode.Empty, "nothing here", 0..0)
    assertNull(error.suggestion)
    assertEquals(0..0, error.range)
  }

  @Test
  fun `a roll that asks for a throw nobody offered fails loudly, naming the formula`() {
    val formula = parsed("1d6!")
    val plan = (RollPlanner.plan(formula, catalog) as PlanResult.Planned).plan
    val failure =
      assertFailsWith<IllegalStateException> {
        // Face 5 of a d6 is the 6, so the die explodes and reaches for the default source.
        RollEvaluator.score(formula, plan, ThrowOutcome(faces = mapOf(0 to 5)))
      }
    assertTrue("1d6!" in failure.message.orEmpty(), failure.message.orEmpty())
  }

  @Test
  fun `a plan knows the formula it came from`() {
    val plan: RollPlan = (RollPlanner.plan(parsed("3d6 [Fire]"), catalog) as PlanResult.Planned).plan
    assertEquals("3d6 [Fire]", plan.formula)
    assertEquals("Fire", plan.label)
  }
}
