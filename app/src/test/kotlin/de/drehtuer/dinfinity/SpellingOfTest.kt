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

  /** The personal package as the designer's own exporter writes it: dice with atlases. */
  private val mine =
    builtin.copy(
      id = DiceSet.PERSONAL_ID,
      name = "My dice",
      dice = builtin.dice.map { it.copy(texturePath = "textures/${it.id}.png") },
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

  @Test
  fun `a set asked for by name is named, even where a bare spelling would resolve`() {
    // The whole of why Roll it can throw a drawing. A bare `1d20` means
    // whichever set is the default, and the set the drawing was just saved
    // into is not that one — so the artwork would be on a die nobody threw
    // (`DrawnSets`; `docs/face-designer.md`, "Flow", step 4).
    val catalogue = DiceCatalog.of(listOf(builtin, mine))

    assertEquals("mine:1d20", spellingOf(die("d20", mine), catalogue, preferred = DiceSet.PERSONAL_ID))
  }

  @Test
  fun `a set asked for that has not got the die falls back to what would resolve`() {
    // A face nobody drew on is not in the personal package, so there is no
    // `mine:1d20` to throw — and a plain d20 is a better answer than none.
    val empty = mine.copy(dice = emptyList())
    val catalogue = DiceCatalog.of(listOf(builtin, empty))

    assertEquals("1d20", spellingOf(die("d20", builtin), catalogue, preferred = DiceSet.PERSONAL_ID))
  }

  @Test
  fun `a set asked for that is not installed at all falls back the same way`() {
    val catalogue = DiceCatalog.of(listOf(builtin))

    assertEquals("1d20", spellingOf(die("d20", builtin), catalogue, preferred = DiceSet.PERSONAL_ID))
  }

  @Test
  fun `the personal set is not something to draw on`() {
    // The chooser lists shapes to draw *on*, and a die of "My dice" is a
    // drawing already — the same `d20` with an atlas over it. It used to be
    // offered *instead* of the plain one: the bundled set comes first, so
    // `distinctBy` kept the untextured copy and dropped the personal one.
    val catalogue = DiceCatalog.of(listOf(builtin, mine))

    val bases = basesIn(catalogue)

    assertEquals("a die is offered twice", bases.size, bases.distinctBy(Die::id).size)
    assertNull("the personal set is a thing to draw on", bases.firstOrNull { it.texturePath != null })
    assertEquals(builtin.dice.size, bases.size)
  }

  @Test
  fun `every other set's dice are there to draw on`() {
    val catalogue = DiceCatalog.of(listOf(builtin, brass))

    assertEquals("brass's own die is not offered", 1, basesIn(catalogue).count { it.id == "skull-d6" })
  }

  private fun die(
    id: String,
    set: DiceSet,
  ): Die = set.dice.first { it.id == id }
}
