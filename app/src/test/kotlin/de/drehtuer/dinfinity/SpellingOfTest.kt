package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What **Roll it** writes into the tray
 * (`docs/face-designer.md`, "Flow"; `docs/dice-notation.md`).
 *
 * The rule is *what would resolve*, because the designer's row lists dice by
 * id across every installed set and so has no answer to "which set is this
 * one". A bare `1d20` when the default set has one; the set named when it does
 * not and another set does; and nothing at all for a die plain notation cannot
 * name.
 */
class SpellingOfTest {
  private val builtin = BuiltinDiceSet.set
  private val brass =
    builtin.copy(
      id = "brass",
      name = "Brass & Bone",
      dice = builtin.dice + Die.standard(id = "skull-d6", shape = DieShape.Cube),
    )

  @Test
  fun `a die the default set has is written plainly`() {
    val catalogue = DiceCatalog.of(listOf(builtin, brass))

    assertEquals("1d20", spellingOf(die("d20", builtin), catalogue))
  }

  @Test
  fun `and still plainly when the default set is somebody else's`() {
    // `brass` has a d20 of its own, so a bare `1d20` rolls a d20 either way.
    val catalogue = DiceCatalog.of(listOf(builtin, brass), defaultSetId = "brass")

    assertEquals("1d20", spellingOf(die("d20", builtin), catalogue))
  }

  @Test
  fun `a die the default set does not have names the set that does`() {
    // The case a bare spelling gets wrong: `1d18` against a default set with
    // no d18 is a formula that refuses to resolve, which is a worse answer to
    // "roll this" than no button at all.
    val small = builtin.copy(dice = builtin.dice.filterNot { it.id == "d18" })
    val catalogue = DiceCatalog.of(listOf(small, brass))

    assertEquals("brass:1d18", spellingOf(die("d18", brass), catalogue))
  }

  @Test
  fun `a set's own die has no spelling a formula could carry`() {
    // Plain notation names `dN`, `d%` and `dF` and nothing else
    // (`docs/architecture.md`, decision 31), so Roll it is not offered for a
    // `skull-d6` rather than offered and broken.
    val catalogue = DiceCatalog.of(listOf(builtin, brass))

    assertNull(spellingOf(die("skull-d6", brass), catalogue))
  }

  @Test
  fun `a die of no installed set has no spelling either`() {
    val catalogue = DiceCatalog.of(listOf(builtin))

    assertNull(spellingOf(Die.standard(id = "d7", shape = DieShape.Cube), catalogue))
  }

  private fun die(
    id: String,
    set: DiceSet,
  ): Die = set.dice.first { it.id == id }
}
