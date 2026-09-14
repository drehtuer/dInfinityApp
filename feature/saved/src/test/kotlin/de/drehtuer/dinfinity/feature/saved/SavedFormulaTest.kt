package de.drehtuer.dinfinity.feature.saved

import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a saved formula still means something, and what happens when it does
 * not (`docs/dice-notation.md`, "Saved rolls").
 *
 * A saved roll keeps its formula as **text**, so the set it names can be
 * uninstalled after it was written. What the list does about that was tested;
 * what happens when somebody throws one anyway was not, and the answer turned
 * out not to be the one three comments and a document claimed.
 */
class SavedFormulaTest {
  @Test
  fun `a formula naming an installed set resolves`() {
    assertTrue(SavedFormula.resolves("1d20 + 3", onlyBuiltin))
  }

  @Test
  fun `a formula naming a set that is not installed does not`() {
    assertFalse(SavedFormula.resolves("brass:1d20", onlyBuiltin))
  }

  @Test
  fun `nor does one that no longer parses at all`() {
    // A roll saved before a grammar changed, or edited by an import.
    assertFalse(SavedFormula.resolves("this is not a formula", onlyBuiltin))
  }

  @Test
  fun `a roll whose set is gone refuses to be planned rather than quietly using another`() {
    // **This is the one that was claimed the other way round.** Three comments
    // and `docs/dice-notation.md` said a broken saved roll "falls back to the
    // built-in set when it is thrown". It does not, and should not: somebody
    // who wrote `brass:1d20` asked for brass, and handing them the bundled d20
    // under the same name would be changing their dice without saying so.
    //
    // What the player gets is the formula in the field with the error under
    // it, which is the same thing they would get for typing it.
    val plan = planOf("brass:1d20")

    assertTrue("a missing set was silently substituted", plan is PlanResult.Failed)
    assertEquals(
      "no dice set 'brass' is installed",
      (plan as PlanResult.Failed).error.message,
    )
  }

  @Test
  fun `one bad set reference spoils the formula it is in, and not just its own die`() {
    // The refusal is about the formula, because a total made of some of the
    // dice somebody asked for is not the roll they asked for.
    assertTrue(planOf("brass:1d20 + 1d6") is PlanResult.Failed)
  }

  @Test
  fun `the fallback that does exist is the default set's, per die`() {
    // What `docs/dice-notation.md` step 2 actually describes: a *default* set
    // missing a die falls back to the bundled one for that die alone, so
    // `1d20 + 1d12` still rolls. A set reference is not that case.
    val sparse =
      BuiltinDiceSet.set.copy(id = "sparse", name = "Sparse", dice = BuiltinDiceSet.set.dice.filter { it.id == "d20" })
    val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set, sparse), defaultSetId = "sparse")

    assertTrue("a default set with no d12 stopped the whole formula", SavedFormula.resolves("1d20 + 1d12", catalog))
  }

  private fun planOf(formula: String): PlanResult =
    RollPlanner.plan(requireNotNull(FormulaParser.parseOrNull(formula)), onlyBuiltin)

  private val onlyBuiltin = DiceCatalog.of(listOf(BuiltinDiceSet.set))
}
