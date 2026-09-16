package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which dice a hand is allowed near.
 *
 * The rule is worth a test of its own rather than a line in a screen's,
 * because what it protects is the app's oldest promise: a die that another die
 * was thrown because of cannot be thrown again, or the roll ends up holding a
 * die the formula does not ask for — and the only ways out of that are to take
 * a die off the table or to keep one whose reason has gone
 * (`docs/physics-and-rendering.md`).
 *
 * So the assertions are mostly about **refusals**, and the ones about what is
 * offered are there to keep the refusal from swallowing everything.
 */
class PickUpTest {
  private val catalog = DiceCatalog.of(listOf(StandardDice.set()))

  @Test
  fun `a plain throw offers every die on the table`() {
    val (formula, result) = rolled("3d6", values = listOf(2, 3, 5))

    assertEquals(setOf(0, 1, 2), PickUp.from(formula, result))
  }

  @Test
  fun `a die the formula struck through can still be picked up`() {
    // The die a player most wants to throw again. Nothing was thrown because
    // of it, and which die `dl1` leaves out is arithmetic over the faces —
    // redone from whatever faces there are.
    val (formula, result) = rolled("4d6dl1", values = listOf(1, 4, 5, 6))

    assertEquals(setOf(0, 1, 2, 3), PickUp.from(formula, result))
    assertTrue(result.dice.any { DieNote.Dropped in it.notes })
  }

  @Test
  fun `a group that explodes offers nothing, whether or not it exploded`() {
    // Both, deliberately. The die that came up six has a seventh die lying
    // beside it; the die that came up two has not, and telling them apart from
    // a flat list of dice is exactly the guess this refuses to make.
    val exploded = rolled("2d6!", values = listOf(6, 2), extra = listOf(3))
    val quiet = rolled("2d6!", values = listOf(4, 2))

    assertEquals(emptySet(), PickUp.from(exploded.first, exploded.second))
    assertEquals(emptySet(), PickUp.from(quiet.first, quiet.second))
  }

  @Test
  fun `a group that rerolls offers nothing`() {
    val (formula, result) = rolled("2d6r1", values = listOf(1, 4), extra = listOf(5))

    assertEquals(emptySet(), PickUp.from(formula, result))
    assertTrue(result.dice.size > 2, "the reroll did not happen, so this asserts nothing")
  }

  @Test
  fun `only the chaining group of a formula is held back`() {
    // One formula, two groups, one answer each. A rule that worked per result
    // rather than per group would take the d20 away because the d6s explode.
    val (formula, result) = rolled("2d6! + 1d20", values = listOf(6, 2, 11), extra = listOf(3))

    assertEquals(setOf(2), PickUp.from(formula, result))
  }

  @Test
  fun `min and keep do not chain, so their dice are offered`() {
    val raised = rolled("2d6min3", values = listOf(1, 4))
    val kept = rolled("2d20kh1", values = listOf(7, 18))

    assertEquals(setOf(0, 1), PickUp.from(raised.first, raised.second))
    assertEquals(setOf(0, 1), PickUp.from(kept.first, kept.second))
  }

  @Test
  fun `both halves of a percentile pair are offered`() {
    // Two dice on the table that read as one number, and a hand at a table
    // picks either of them up.
    val (formula, result) = rolled("1d100", values = listOf(40, 3))

    assertEquals(setOf(0, 1), PickUp.from(formula, result))
  }

  @Test
  fun `a group the formula has nothing to say about offers nothing`() {
    // A group is matched to the part of the formula that asked for it by id,
    // and a group with no such part is a result and a formula that have come
    // apart. Nothing is offered out of it rather than everything: a die picked
    // out of a roll nobody is looking at is the worst answer available.
    val (_, result) = rolled("3d6 + 1d8", values = listOf(2, 3, 5, 4))

    assertEquals(setOf(0, 1, 2), PickUp.from(parsed("3d6"), result))
  }

  @Test
  fun `a result with no groups at all offers nothing`() {
    assertEquals(emptySet(), PickUp.from(parsed("4"), RollResult(formula = "4", total = 4L)))
  }

  @Test
  fun `the two modifiers that add dice are the two that hold a group back`() {
    assertTrue(PickUp.chains(node("2d6!")))
    assertTrue(PickUp.chains(node("2d6r1")))
    assertFalse(PickUp.chains(node("2d6")))
    assertFalse(PickUp.chains(node("4d6dl1min2")))
  }

  @Test
  fun `a group is asked about on its own`() {
    val (formula, result) = rolled("2d6! + 1d20", values = listOf(6, 2, 11), extra = listOf(3))
    val plain = result.groups.first { it.id == 1 }

    assertEquals(setOf(2), PickUp.inThe(plain, node("2d6! + 1d20", at = 1)))
    assertEquals(
      emptySet(),
      PickUp.inThe(result.groups.first { it.id == 0 }, node("2d6! + 1d20", at = 0)),
    )
  }

  private fun rolled(
    text: String,
    values: List<Int>,
    extra: List<Int> = emptyList(),
  ): Pair<Formula, RollResult> {
    val formula = parsed(text)
    val plan = plan(text)
    val faces = plan.dice.mapIndexed { position, instance -> position to faceShowing(instance.die, values[position]) }
    val result =
      RollEvaluator.score(
        formula = formula,
        plan = plan,
        outcome = ThrowOutcome(faces = faces.toMap()),
        extra = Queued(ArrayDeque(extra)),
      )
    return formula to result
  }

  private fun parsed(text: String): Formula = (FormulaParser.parse(text) as ParseResult.Parsed).formula

  private fun plan(text: String): RollPlan = (RollPlanner.plan(parsed(text), catalog) as PlanResult.Planned).plan

  private fun node(
    text: String,
    at: Int = 0,
  ): DiceNode = parsed(text).diceNodes.first { it.id == at }

  /** Hands back a face showing each value the test lined up, in order. */
  private class Queued(
    private val queue: ArrayDeque<Int>,
  ) : ExtraThrow {
    override fun roll(die: Die): Int {
      val value = requireNotNull(queue.removeFirstOrNull()) { "an extra ${die.id} the test did not line up" }
      return faceShowing(die, value)
    }
  }
}

/** The index of a face of [die] showing [value]; the test is wrong if there is none. */
private fun faceShowing(
  die: Die,
  value: Int,
): Int =
  die.faces.indexOfFirst { it.value == value }.also {
    require(it >= 0) { "${die.id} has no face showing $value" }
  }
