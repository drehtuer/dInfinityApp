package de.drehtuer.dinfinity.core.notation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the notation the app explains is the notation the app accepts
 * (`docs/dice-notation.md`; `docs/TODO.md`, 4.10).
 *
 * The reference screen exists so a player does not have to leave the app to
 * find out what `kh1` means. That is only worth anything while what it says is
 * true, and prose has no compiler — so every example in it is put through the
 * real parser here, and every modifier is checked to produce the modifier it
 * claims to.
 */
class NotationReferenceTest {
  @Test
  fun `every example is a formula the parser accepts`() {
    val refused =
      NotationReference.entries.filter { entry ->
        FormulaParser.parse(entry.example) !is ParseResult.Parsed
      }

    assertEquals(
      emptyList(),
      refused,
      "the screen offers formulas the app would refuse: ${refused.map(NotationEntry::example)}",
    )
  }

  @Test
  fun `every example actually uses the thing it is an example of`() {
    // A modifier entry whose example does not carry that modifier is an
    // example of nothing. `min2` reading as a `dl` would pass the parse check
    // above and still be wrong on screen.
    modifierEntries().forEach { entry ->
      val modifiers = modifiersIn(entry.example)
      assertTrue(
        modifiers.any { rendered(it) == entry.syntax },
        "'${entry.example}' is offered as an example of '${entry.syntax}' and does not use it",
      )
    }
  }

  @Test
  fun `the modifiers on the screen are exactly the ones the parser takes`() {
    // A *new* modifier is the compiler's job: `NotationReference.describe` is
    // an exhaustive `when` and will not build without a branch for it. What is
    // checked here is the other direction and the other mistake — a modifier
    // that was described and then left out of the list the screen is built
    // from, or an entry describing something the parser would refuse.
    //
    // Four formulas and not one, because only one of `kh` `kl` `dh` `dl` may
    // be on a group: they all choose which dice count, and two of them on one
    // group is a question with no answer (`ModifierScanner`).
    val everything =
      listOf("4d6kh1", "4d6kl1", "4d6dh1", "4d6dl1", "4d6!r1min2")
        .flatMap(::modifiersIn)
        .map(::rendered)
        .toSet()
    val explained = modifierEntries().map(NotationEntry::syntax).toSet()

    assertEquals(emptySet(), everything - explained, "a modifier the parser takes is not on the screen")
    assertEquals(emptySet(), explained - everything, "the screen explains a modifier the parser would refuse")
  }

  @Test
  fun `the limits quoted are the limits enforced`() {
    // Written out rather than interpolated, so that a limit moving in
    // `NotationLimits` fails here and is looked at rather than silently
    // reprinted on the screen with its new value and its old prose.
    val quoted = NotationReference.limits.associate { it.what to it.value }

    assertEquals("1000", quoted["Dice in one formula"])
    assertEquals("20", quoted["Explosions in a row"])
    assertEquals("8", quoted["Brackets inside brackets"])
    assertEquals("60", quoted["Characters in a label"])
  }

  @Test
  fun `nothing is explained twice`() {
    val syntax = NotationReference.entries.map(NotationEntry::syntax)

    assertEquals(syntax.distinct(), syntax, "the same thing is on the screen more than once")
  }

  @Test
  fun `every section has something in it`() {
    NotationReference.sections.forEach { section ->
      assertTrue(section.entries.isNotEmpty(), "'${section.title}' is a heading with nothing under it")
      assertTrue(section.blurb.isNotBlank(), "'${section.title}' has no blurb")
    }
  }

  /** The entries that describe a modifier, which is every one in those two sections. */
  private fun modifierEntries(): List<NotationEntry> =
    NotationReference.sections
      .filter { it.title == "Keeping and dropping" || it.title == "Changing what a die scores" }
      .flatMap(NotationSection::entries)

  private fun modifiersIn(formula: String): List<DiceModifier> {
    val parsed = FormulaParser.parse(formula)
    assertTrue(parsed is ParseResult.Parsed, "'$formula' does not parse")
    val dice = diceIn(parsed.formula.root)
    assertNotNull(dice, "'$formula' has no dice in it")
    return dice.modifiers
  }

  private fun diceIn(node: FormulaNode): DiceNode? =
    when (node) {
      is DiceNode -> node
      is BinaryNode -> diceIn(node.left) ?: diceIn(node.right)
      is NegateNode -> diceIn(node.operand)
      is NumberNode -> null
    }

  /** A modifier as it is written, which is what an entry's `syntax` is. */
  private fun rendered(modifier: DiceModifier): String =
    when (modifier) {
      is DiceModifier.KeepHighest -> "kh${modifier.n}"
      is DiceModifier.KeepLowest -> "kl${modifier.n}"
      is DiceModifier.DropHighest -> "dh${modifier.n}"
      is DiceModifier.DropLowest -> "dl${modifier.n}"
      is DiceModifier.Explode -> "!"
      is DiceModifier.Reroll -> "r${modifier.threshold}"
      is DiceModifier.Minimum -> "min${modifier.value}"
    }
}
