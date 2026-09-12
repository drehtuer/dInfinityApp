package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Properties that have to hold for *every* formula, checked against a few
 * thousand generated ones.
 *
 * The seeds are fixed, so a failure here is a failure anyone can reproduce,
 * and the formula that caused it is printed in the message rather than left to
 * be guessed at.
 */
class NotationPropertyTest {
  private val catalog = DiceCatalog.of(listOf(StandardDice.set()))

  @Test
  fun `a parsed formula written back out parses to the same tree`() {
    forEachGenerated { text ->
      val once = parsed(text)
      val twice = parsed(render(once.root))
      assertEquals(render(once.root), render(twice.root), "round trip of '$text'")
    }
  }

  @Test
  fun `every generated formula resolves against the standard set`() {
    forEachGenerated { text ->
      val result = RollPlanner.plan(parsed(text), catalog)
      assertTrue(result is PlanResult.Planned, "'$text' should plan, but: $result")
    }
  }

  @Test
  fun `a total always lands inside the bound the planner proved`() {
    val random = Random(seed = 7)
    forEachGenerated { text ->
      val formula = parsed(text)
      val plan = (RollPlanner.plan(formula, catalog) as PlanResult.Planned).plan
      val bound = ResultBounds(plan.groups).of(formula.root)
      val result =
        RollEvaluator.score(
          formula = formula,
          plan = plan,
          outcome = ThrowOutcome(faces = plan.dice.associate { it.index to random.nextInt(it.die.faces.size) }),
          extra = ExtraThrow { die -> random.nextInt(die.faces.size) },
        )
      assertTrue(
        result.total.toBigInteger() in bound.min..bound.max,
        "'$text' came to ${result.total}, outside ${bound.min}..${bound.max}",
      )
    }
  }

  @Test
  fun `a group with no modifiers scores exactly the dice it threw`() {
    val random = Random(seed = 11)
    repeat(SAMPLES) {
      val count = random.nextInt(1, 9)
      val sides = listOf(4, 6, 8, 10, 12, 18, 20).random(random)
      val text = "${count}d$sides"
      val formula = parsed(text)
      val plan = (RollPlanner.plan(formula, catalog) as PlanResult.Planned).plan
      val faces = plan.dice.associate { it.index to random.nextInt(it.die.faces.size) }
      val result = RollEvaluator.score(formula, plan, ThrowOutcome(faces = faces))
      assertEquals(result.dice.sumOf { it.value.toLong() }, result.groups.single().subtotal, text)
      assertEquals(result.groups.single().subtotal, result.total, text)
    }
  }

  @Test
  fun `NdX averages what its faces average, over enough rolls`() {
    val random = Random(seed = 13)
    val formula = parsed("3d6")
    val plan = (RollPlanner.plan(formula, catalog) as PlanResult.Planned).plan
    val rolls = 20_000
    val total =
      (1..rolls).sumOf {
        val faces = plan.dice.associate { die -> die.index to random.nextInt(6) }
        RollEvaluator.score(formula, plan, ThrowOutcome(faces = faces)).total
      }
    val mean = total.toDouble() / rolls
    assertTrue(kotlin.math.abs(mean - 10.5) < 0.1, "3d6 averaged $mean, not about 10.5")
  }

  @Test
  fun `a dropped die is still in the breakdown, and a kept one is not dropped twice`() {
    val random = Random(seed = 17)
    repeat(SAMPLES) {
      val count = random.nextInt(2, 7)
      val keep = random.nextInt(1, count)
      val text = "${count}d6kh$keep"
      val formula = parsed(text)
      val plan = (RollPlanner.plan(formula, catalog) as PlanResult.Planned).plan
      val faces = plan.dice.associate { die -> die.index to random.nextInt(6) }
      val result = RollEvaluator.score(formula, plan, ThrowOutcome(faces = faces))
      assertEquals(count, result.dice.size, text)
      assertEquals(keep, result.dice.count(RolledDie::kept), text)
      assertEquals(result.dice.filter(RolledDie::kept).sumOf { it.value.toLong() }, result.total, text)
    }
  }

  @Test
  fun `rescoring under every rounding keeps the dice exactly as they landed`() {
    val random = Random(seed = 19)
    val formula = parsed("(4d6 + 3) / 2")
    val plan = (RollPlanner.plan(formula, catalog) as PlanResult.Planned).plan
    repeat(SAMPLES) {
      val faces = plan.dice.associate { die -> die.index to random.nextInt(6) }
      val result = RollEvaluator.score(formula, plan, ThrowOutcome(faces = faces))
      Rounding.entries.forEach { rounding ->
        val again = RollEvaluator.rescore(formula, result, rounding)
        assertEquals(result.groups, again.groups, "dice moved when rounding ${rounding.id}")
        assertTrue(again.total in (result.total - 1)..(result.total + 1), "rounding changed the roll, not the total")
      }
    }
  }

  @Test
  fun `no string, however odd, makes the parser throw`() {
    val random = Random(seed = 23)
    val alphabet = "0123456789dDkhlr!%()[]+-*/ minbrass:F".toList()
    repeat(FUZZ_SAMPLES) {
      val text = (1..random.nextInt(0, 24)).map { alphabet.random(random) }.joinToString("")
      val result = runCatching { FormulaParser.parse(text) }
      assertTrue(result.isSuccess, "parsing '$text' threw ${result.exceptionOrNull()}")
    }
  }

  @Test
  fun `no string, however odd, makes the planner throw`() {
    val random = Random(seed = 29)
    val alphabet = "0123456789dDkhlr!%()[]+-*/ minbrass:F".toList()
    repeat(FUZZ_SAMPLES) {
      val text = (1..random.nextInt(0, 24)).map { alphabet.random(random) }.joinToString("")
      val result = runCatching { RollPlanner.plan(text, catalog) }
      assertTrue(result.isSuccess, "planning '$text' threw ${result.exceptionOrNull()}")
    }
  }

  /** Runs [check] over a few hundred formulas the grammar can produce. */
  private fun forEachGenerated(check: (String) -> Unit) {
    val random = Random(seed = 31)
    repeat(SAMPLES) { check(FormulaGenerator(random).expression(depth = 0)) }
  }

  private companion object {
    const val SAMPLES = 300
    const val FUZZ_SAMPLES = 2_000
  }
}

/**
 * Builds formulas that the grammar accepts and the standard set resolves, so a
 * property can be stated about "any formula" rather than about the dozen
 * somebody thought to write down.
 */
private class FormulaGenerator(
  private val random: Random,
) {
  fun expression(depth: Int): String {
    val terms = random.nextInt(1, 4)
    return (1..terms).joinToString(separator = "") { index ->
      val operator = if (index == 1) "" else " ${listOf("+", "-", "*").random(random)} "
      operator + atom(depth)
    }
  }

  private fun atom(depth: Int): String =
    when {
      depth < MAX_DEPTH && random.nextInt(5) == 0 -> "(${expression(depth + 1)})"
      random.nextInt(3) == 0 -> random.nextInt(0, 20).toString()
      else -> dice()
    }

  private fun dice(): String {
    val count = random.nextInt(1, 7)
    val sides = listOf("4", "6", "8", "10", "12", "18", "20", "%", "F").random(random)
    return "$count d$sides${modifier(count)}".replace(" ", "")
  }

  private fun modifier(count: Int): String =
    when (random.nextInt(6)) {
      0 -> "kh${random.nextInt(1, count + 1)}"
      1 -> "kl${random.nextInt(1, count + 1)}"
      2 -> "dl${random.nextInt(1, count + 1)}"
      3 -> "min${random.nextInt(1, 4)}"
      4 -> "r${random.nextInt(1, 3)}"
      else -> ""
    }

  private companion object {
    const val MAX_DEPTH = 3
  }
}
