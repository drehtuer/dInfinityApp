package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieRole
import de.drehtuer.dinfinity.core.model.PlannedGroup
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollPlannerTest {
  private val builtin = StandardDice.set()
  private val catalog = DiceCatalog.of(listOf(builtin))

  @Test
  fun `a formula resolves to the dice it names, numbered in throw order`() {
    val plan = planned("3d6 + 1d20", catalog)
    assertEquals(listOf("d6", "d6", "d6", "d20"), plan.dice.map { it.die.id })
    assertEquals(listOf(0, 1, 2, 3), plan.dice.map(DieInstance::index))
    assertEquals(listOf(0, 0, 0, 1), plan.dice.map(DieInstance::groupId))
  }

  @Test
  fun `a group keeps the notation it was written as, for the breakdown`() {
    assertEquals(listOf("2d20kh1", "6"), planned("2d20kh1 + 6", catalog).groups.map(PlannedGroup::notation) + "6")
  }

  @Test
  fun `a group that explodes says so, because the table has to make room`() {
    val plan = planned("8d6!", catalog)
    assertTrue(plan.groups.single().explodes)
    assertEquals(16, plan.capacityDiceCount)
  }

  @Test
  fun `the label travels with the plan`() {
    assertEquals("Attack", planned("2d20kh1 + 6 [Attack]", catalog).label)
  }

  @Test
  fun `a die the default set lacks comes from the bundled set, per die`() {
    val brass = StandardDice.set(id = "brass", without = setOf("d12"), name = "Brass")
    val mixed = DiceCatalog.of(listOf(builtin, brass), defaultSetId = "brass")
    val plan = planned("1d20 + 1d12", mixed)
    assertEquals(listOf("brass", DiceSet.BUILTIN_ID), plan.dice.map(DieInstance::setId))
    assertFalse(plan.groups.first().fellBack)
    assertTrue(plan.groups.last().fellBack)
  }

  @Test
  fun `a setref gets no fallback, because it asked for that set by name`() {
    val brass = StandardDice.set(id = "brass", without = setOf("d12"), name = "Brass")
    val mixed = DiceCatalog.of(listOf(builtin, brass))
    val error = refusedPlan("brass:1d12", mixed)
    assertEquals(NotationErrorCode.UnknownDie, error.code)
    assertTrue("brass" in error.message, error.message)
  }

  @Test
  fun `a setref naming a set that is not installed is refused`() {
    val error = refusedPlan("brass:1d20", catalog)
    assertEquals(NotationErrorCode.UnknownSet, error.code)
    assertTrue("brass" in error.message, error.message)
  }

  @Test
  fun `a die nobody has is refused, naming the set and offering the nearest`() {
    val error = refusedPlan("3d6 + 1d7 - 4", catalog)
    assertEquals(NotationErrorCode.UnknownDie, error.code)
    assertEquals("no d7 in set \"builtin\"", error.message)
    assertEquals("3d6 + 1d8 - 4", error.suggestion)
  }

  @Test
  fun `the suggestion points at the group that is wrong`() {
    assertEquals(6..8, refusedPlan("3d6 + 1d7 - 4", catalog).range)
  }

  @Test
  fun `the suggestion keeps the group's count and modifiers`() {
    assertEquals("2d8kh1", refusedPlan("2d7kh1", catalog).suggestion)
  }

  @Test
  fun `a d100 is a tens and a units d10 from the same set`() {
    val plan = planned("1d100", catalog)
    assertEquals(listOf("d10-tens", "d10"), plan.dice.map { it.die.id })
    assertEquals(listOf(DieRole.PercentileTens, DieRole.PercentileUnits), plan.dice.map(DieInstance::role))
  }

  @Test
  fun `d percent plans exactly as d100 does`() {
    assertEquals(planned("1d100", catalog).dice, planned("1d%", catalog).dice)
  }

  @Test
  fun `a set with no tens d10 gets one made from its d10`() {
    val plain = StandardDice.set(without = setOf("d10-tens"))
    val plan = planned("1d%", DiceCatalog.of(listOf(plain)))
    assertEquals(listOf("d10", "d10"), plan.dice.map { it.die.id })
    assertEquals(
      setOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90),
      plan.dice
        .first()
        .die
        .values()
        .toSet(),
    )
    assertEquals(
      "10",
      plan.dice
        .first()
        .die.faces
        .first()
        .label,
    )
  }

  @Test
  fun `the units half of a pair reads its ten as a zero, as a real d10 does`() {
    assertEquals(
      listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0),
      planned("1d%", catalog)
        .dice
        .last()
        .die
        .values(),
    )
  }

  @Test
  fun `a percentile group puts two dice on the table per pair`() {
    assertEquals(4, planned("2d%", catalog).dice.size)
  }

  @Test
  fun `dF resolves to the set's fudge die`() {
    assertEquals(
      listOf(-1, -1, 0, 0, 1, 1),
      planned("4dF", catalog)
        .dice
        .first()
        .die
        .values(),
    )
  }

  @Test
  fun `a formula with no dice plans to no dice at all`() {
    val plan = planned("2 * (3 + 4)", catalog)
    assertTrue(plan.dice.isEmpty())
    assertEquals(0, plan.capacityDiceCount)
  }

  @Test
  fun `keeping more dice than the group rolls is refused`() {
    val error = refusedPlan("2d20kh3", catalog)
    assertEquals(NotationErrorCode.KeepDropOutOfRange, error.code)
    assertTrue("3 of 2" in error.message, error.message)
    assertEquals(4..6, error.range)
  }

  @Test
  fun `dropping more dice than the group rolls is refused`() {
    assertEquals(NotationErrorCode.KeepDropOutOfRange, refusedPlan("4d6dl5", catalog).code)
  }

  @Test
  fun `keeping none is refused, because it can only be a typo`() {
    assertEquals(NotationErrorCode.KeepDropOutOfRange, refusedPlan("2d20kh0", catalog).code)
  }

  @Test
  fun `keeping every die is allowed, pointless as it is`() {
    assertEquals(2, planned("2d20kh2", catalog).dice.size)
  }

  @Test
  fun `a die whose every face is its highest would explode for ever, and is refused`() {
    val loaded = builtin.copy(dice = builtin.dice.map { if (it.id == "d6") it.copy(faces = sixes()) else it })
    val error = refusedPlan("1d6!", DiceCatalog.of(listOf(loaded)))
    assertEquals(NotationErrorCode.ExplodesForever, error.code)
  }

  @Test
  fun `dividing by a literal zero is refused before the dice are thrown`() {
    val error = refusedPlan("1d6 / 0", catalog)
    assertEquals(NotationErrorCode.DivisionByZero, error.code)
  }

  @Test
  fun `dividing by a die that can roll zero is refused too`() {
    assertEquals(NotationErrorCode.DivisionByZero, refusedPlan("1d6 / 1dF", catalog).code)
  }

  @Test
  fun `dividing by a die that cannot roll zero is fine`() {
    assertEquals(2, planned("1d6 / 1d4", catalog).dice.size)
  }

  @Test
  fun `a result too large to add up is refused while it is still text`() {
    val huge = "1000000000 * 1000000000 * 1000000000"
    assertEquals(NotationErrorCode.ResultTooLarge, refusedPlan(huge, catalog).code)
  }

  @Test
  fun `the largest honest formula still plans`() {
    assertEquals(NotationErrorCode.ResultTooLarge, refusedPlan("1000000000 * 1000000000 * 20", catalog).code)
    assertTrue(planned("1000000000 * 1000000000", catalog).dice.isEmpty())
  }

  @Test
  fun `planning from text parses first`() {
    assertEquals(NotationErrorCode.UnexpectedEnd, (RollPlanner.plan("3d6 +", catalog) as PlanResult.Failed).error.code)
    assertEquals(3, (RollPlanner.plan("3d6", catalog) as PlanResult.Planned).plan.dice.size)
  }

  @Test
  fun `a catalogue without the bundled set has no floor to fall back to`() {
    val onlyBrass = listOf(StandardDice.set(id = "brass", name = "Brass"))
    val failure = runCatching { DiceCatalog.of(onlyBrass) }.exceptionOrNull()
    assertTrue(failure is IllegalArgumentException, "$failure")
  }

  @Test
  fun `a default set that is not installed is refused`() {
    val failure = runCatching { DiceCatalog.of(listOf(builtin), defaultSetId = "brass") }.exceptionOrNull()
    assertTrue(failure is IllegalArgumentException, "$failure")
  }

  @Test
  fun `a catalogue finds the sets it was given and nothing else`() {
    assertEquals(builtin, catalog.set(DiceSet.BUILTIN_ID))
    assertEquals(null, catalog.set("brass"))
    assertEquals(DiceSet.BUILTIN_ID, catalog.defaultSetId)
  }

  private fun sixes() = StandardDice.d6.faces.map { it.copy(value = 6) }

  private fun planned(
    text: String,
    catalog: DiceCatalog,
  ): RollPlan =
    when (val result = RollPlanner.plan(parsed(text), catalog)) {
      is PlanResult.Planned -> result.plan
      is PlanResult.Failed -> error("'$text' should plan, but: ${result.error.code} ${result.error.message}")
    }

  private fun refusedPlan(
    text: String,
    catalog: DiceCatalog,
  ): NotationError =
    when (val result = RollPlanner.plan(parsed(text), catalog)) {
      is PlanResult.Failed -> result.error
      is PlanResult.Planned -> error("'$text' should not plan, but it did")
    }
}
