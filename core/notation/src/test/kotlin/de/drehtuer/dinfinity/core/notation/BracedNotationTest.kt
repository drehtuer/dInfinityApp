package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieRole
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.PlannedGroup
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A set's own dice, named in braces: `3{skull-d6}kh1`, `3{brass:skull-d6}kh1`,
 * `3{skull:d6}kh1` (`docs/dice-notation.md`, "A set's own dice").
 *
 * Decision 31 left these to the picker because a slug and a modifier are made
 * of the same characters; the braces close the id before the modifiers start.
 * The parser lexes what is between them without asking any set, and
 * `DieResolver` decides whether a set has it.
 */
class BracedNotationTest {
  private val builtin = StandardDice.set()
  private val skull = Die.standard(id = "skull-d6", shape = DieShape.Cube)
  private val hundred = Die.standard(id = "d100", shape = DieShape.Icosahedron)
  private val brass = StandardDice.set(id = "brass", name = "Brass").let { it.copy(dice = it.dice + skull + hundred) }
  private val skullSet = StandardDice.set(id = "skull", name = "Skulls")
  private val catalog = DiceCatalog.of(listOf(builtin, brass, skullSet))

  // Parsing

  @Test
  fun `a braced id is the die whose own id that is`() {
    val node = parsed("3{skull-d6}kh1").diceNodes.single()

    assertEquals(Sides.Named("skull-d6"), node.sides)
    assertNull(node.setRef)
    assertEquals(3, node.count)
    assertEquals("3{skull-d6}kh1", render(node))
  }

  @Test
  fun `the set goes inside the braces`() {
    val node = parsed("3{brass:skull-d6}kh1").diceNodes.single()

    assertEquals("brass", node.setRef)
    assertEquals(Sides.Named("skull-d6"), node.sides)
    assertEquals("3{brass:skull-d6}kh1", render(node))
  }

  @Test
  fun `a standard id in braces is still a die named by its id`() {
    val node = parsed("3{skull:d6}kh1").diceNodes.single()

    assertEquals("skull", node.setRef)
    assertEquals(Sides.Named("d6"), node.sides)
  }

  @Test
  fun `a braced die with no count is one of them`() {
    assertEquals(1, parsed("{skull-d6}").diceNodes.single().count)
  }

  @Test
  fun `the braces end the id, so a modifier may follow at once in either case`() {
    // `skull-d6kh1` is the ambiguity decision 31 could not resolve; with the
    // braces it is not one.
    val modifiers = parsed("4{skull-d6}DL1!").diceNodes.single().modifiers

    assertEquals(listOf(DiceModifier.DropLowest::class, DiceModifier.Explode::class), modifiers.map { it::class })
  }

  @Test
  fun `a braced die takes part in arithmetic and keeps a label`() {
    val formula = parsed("2 * (1{brass:skull-d6} + 1d4) - {d100} [Bones]")

    assertEquals("((2 * (1{brass:skull-d6} + 1d4)) - 1{d100})", render(formula.root))
    assertEquals("Bones", formula.label)
  }

  @Test
  fun `d100 in braces is a die called d100, never the percentile pair`() {
    assertEquals(Sides.Named("d100"), parsed("{d100}").diceNodes.single().sides)
  }

  @Test
  fun `an id may start with a digit, which a plain setref cannot`() {
    // Set and die ids may start with a digit (`docs/dice-sets.md`); between
    // braces nothing has to guess where a number ends.
    val node = parsed("2{3dice:6-sided}").diceNodes.single()

    assertEquals("3dice", node.setRef)
    assertEquals(Sides.Named("6-sided"), node.sides)
  }

  @Test
  fun `the node covers count, braces and modifiers, so the breakdown quotes it whole`() {
    val formula = parsed("1d20 + 3{brass:skull-d6}kh1 + 2")
    val node = formula.diceNodes.last()

    assertEquals("3{brass:skull-d6}kh1", formula.text.substring(node.range))
    assertEquals(1, node.id)
  }

  @Test
  fun `braced dice count towards the dice limits like any other`() {
    assertEquals(1000, parsed("1000{skull-d6}").diceNodes.single().count)
    assertEquals(NotationErrorCode.GroupTooLarge, refused("1001{skull-d6}").code)
    assertEquals(NotationErrorCode.GroupTooLarge, refused("0{skull-d6}").code)
    assertEquals(NotationErrorCode.FormulaTooLarge, refused("999d6 + 2{skull-d6}").code)
  }

  @Test
  fun `a formula without braces parses exactly as it did`() {
    // Additive only: every file already written is read the same way.
    listOf("3d6 + 1d20 - 4", "brass:2d20kh1", "4d6dl1 [Stats]", "d%").forEach { text ->
      assertTrue(parsed(text).diceNodes.none { it.sides is Sides.Named }, text)
    }
  }

  // Errors

  @Test
  fun `empty braces ask for a die id`() {
    val error = refused("3{} + 2")

    assertEquals(NotationErrorCode.EmptyBraces, error.code)
    assertEquals(1..2, error.range)
    assertTrue("{skull-d6}" in error.message, error.message)
  }

  @Test
  fun `an unclosed brace points at the brace that opened it`() {
    val error = refused("1d6 + 3{skull-d6")

    assertEquals(NotationErrorCode.UnclosedBrace, error.code)
    assertEquals(7..7, error.range)
  }

  @Test
  fun `a brace at the very end is unclosed too`() {
    assertEquals(NotationErrorCode.UnclosedBrace, refused("3{").code)
  }

  @Test
  fun `a character that cannot be in an id is pointed at`() {
    val error = refused("3{Skull-d6}")

    assertEquals(NotationErrorCode.BadDieId, error.code)
    assertEquals(2..2, error.range)
    assertTrue("'S'" in error.message, error.message)
  }

  @Test
  fun `spaces are not part of an id`() {
    assertEquals(NotationErrorCode.BadDieId, refused("{skull d6}").code)
  }

  @Test
  fun `a braced die names at most one set`() {
    val error = refused("{a:b:c}")

    assertEquals(NotationErrorCode.BadDieId, error.code)
    assertEquals(4..4, error.range)
    assertTrue("one set" in error.message, error.message)
  }

  @Test
  fun `a colon with no set in front of it is refused`() {
    val error = refused("{:d6}")

    assertEquals(NotationErrorCode.BadDieId, error.code)
    assertTrue("{brass:d6}" in error.message, error.message)
  }

  @Test
  fun `a set with no die after it is refused`() {
    val error = refused("2{brass:}")

    assertEquals(NotationErrorCode.BadDieId, error.code)
    assertEquals(1..8, error.range)
    assertTrue("{brass:d6}" in error.message, error.message)
  }

  @Test
  fun `an id longer than any set may define is refused`() {
    val longest = "a".repeat(NotationLimits.MAX_ID_LENGTH)
    assertEquals(Sides.Named(longest), parsed("{$longest}").diceNodes.single().sides)
    assertEquals(NotationErrorCode.BadDieId, refused("{${longest}a}").code)
    assertEquals(NotationErrorCode.BadDieId, refused("{${longest}a:d6}").code)
  }

  @Test
  fun `a set written outside the braces is refused, saying where it goes`() {
    val error = refused("brass:3{skull-d6}")

    assertEquals(NotationErrorCode.UnexpectedCharacter, error.code)
    assertTrue("3{brass:skull-d6}" in error.message, error.message)
    assertTrue("1{brass:skull-d6}" in refused("brass:{skull-d6}").message)
  }

  @Test
  fun `a space between a count and its braces is two tokens`() {
    assertEquals(NotationErrorCode.UnexpectedCharacter, refused("3 {skull-d6}").code)
  }

  // Resolving

  @Test
  fun `a braced die resolves against the default set`() {
    val withBrass = DiceCatalog.of(listOf(builtin, brass), defaultSetId = "brass")
    val plan = planned("3{skull-d6}kh1", withBrass)

    assertEquals(listOf("skull-d6"), plan.dice.map { it.die.id }.distinct())
    assertEquals(setOf("brass"), plan.dice.map(DieInstance::setId).toSet())
    assertEquals(listOf("3{skull-d6}kh1"), plan.groups.map(PlannedGroup::notation))
  }

  @Test
  fun `a braced die with its set comes from that set`() {
    val plan = planned("3{brass:skull-d6}kh1", catalog)

    assertEquals(setOf("brass"), plan.dice.map(DieInstance::setId).toSet())
    assertEquals(3, plan.dice.size)
  }

  @Test
  fun `a standard id braced with a set is that set's die`() {
    val plan = planned("3{skull:d6}kh1", catalog)

    assertEquals(setOf("skull"), plan.dice.map(DieInstance::setId).toSet())
    assertEquals(setOf("d6"), plan.dice.map { it.die.id }.toSet())
  }

  @Test
  fun `a braced d100 is the set's own hundred, thrown as one die`() {
    val plan = planned("{brass:d100}", catalog)

    assertEquals(listOf(DieRole.Normal), plan.dice.map(DieInstance::role))
    assertEquals(hundred, plan.dice.single().die)
  }

  @Test
  fun `a braced die without a set falls back to the bundled set, like any die`() {
    val withBrass = DiceCatalog.of(listOf(builtin, brass.copy(dice = listOf(skull))), defaultSetId = "brass")
    val plan = planned("{d20}", withBrass)

    assertEquals(DiceSet.BUILTIN_ID, plan.dice.single().setId)
    assertTrue(plan.groups.single().fellBack)
  }

  @Test
  fun `an id no set has is refused, naming the die and the set`() {
    val error = refusedPlan("1d6 + 2{skull-d6}kh1", catalog)

    assertEquals(NotationErrorCode.UnknownDie, error.code)
    assertEquals("no skull-d6 in set \"builtin\"", error.message)
    assertEquals(6..19, error.range)
    assertNull(error.suggestion, "there is no honest nearest to a skull")
  }

  @Test
  fun `a braced set gets no fallback`() {
    val error = refusedPlan("{skull:skull-d6}", catalog)

    assertEquals(NotationErrorCode.UnknownDie, error.code)
    assertEquals("no skull-d6 in set \"skull\"", error.message)
  }

  @Test
  fun `a braced set that is not installed is refused`() {
    val error = refusedPlan("{copper:skull-d6}", catalog)

    assertEquals(NotationErrorCode.UnknownSet, error.code)
    assertTrue("copper" in error.message, error.message)
  }

  @Test
  fun `a braced dN that does not exist is offered the nearest, still braced`() {
    val error = refusedPlan("2{brass:d7}kh1 + 1", catalog)

    assertEquals("2{brass:d8}kh1 + 1", error.suggestion)
    assertTrue(FormulaParser.parse(checkNotNull(error.suggestion)) is ParseResult.Parsed)
  }

  // Scoring

  @Test
  fun `kh1 on a braced group keeps the highest of them`() {
    val plan = planned("3{brass:skull-d6}kh1 + 1", catalog)
    val faces = listOf(2, 6, 4).mapIndexed { position, value -> position to faceShowing(skull, value) }.toMap()

    val result =
      RollEvaluator.score(
        formula = parsed("3{brass:skull-d6}kh1 + 1"),
        plan = plan,
        outcome = ThrowOutcome(faces = faces),
      )

    assertEquals(7L, result.total)
    assertEquals(listOf(false, true, false), result.dice.map(RolledDie::kept))
    assertEquals("3{brass:skull-d6}kh1", result.groups.single().notation)
    assertEquals("brass", result.groups.single().setId)
  }

  private fun planned(
    text: String,
    catalog: DiceCatalog,
  ): RollPlan =
    when (val result = RollPlanner.plan(text, catalog)) {
      is PlanResult.Planned -> result.plan
      is PlanResult.Failed -> error("'$text' should plan, but: ${result.error.code} ${result.error.message}")
    }

  private fun refusedPlan(
    text: String,
    catalog: DiceCatalog,
  ): NotationError =
    when (val result = RollPlanner.plan(text, catalog)) {
      is PlanResult.Failed -> result.error
      is PlanResult.Planned -> error("'$text' should not plan, but it did")
    }

  private fun faceShowing(
    die: Die,
    value: Int,
  ): Int = die.faces.indexOfFirst { it.value == value }
}
