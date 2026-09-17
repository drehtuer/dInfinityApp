package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.RollRange
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the roll screen reads while a throw is in the air, and what it offers
 * when one gives up (`docs/TODO.md`, Steps 4.1 and 5.5).
 *
 * Split from `RollMachineTest` because it is a different question: that one is
 * about what a formula comes to, this one about what a player is shown and
 * offered while it is still happening.
 */
class RollProgressTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")
  private val oak = TableLook(id = "oak", name = "Oak")
  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  @Test
  fun `a roll that gave up says so, and keeps the dice to offer back`() {
    val machine = machine()
    machine.type("4d6")
    machine.throwDice()

    assertTrue("the machine ignored a roll that gave up", machine.gaveUp(listOf(1, 3)))

    assertEquals(RollState.Stalled(unsettled = 2, read = 2), machine.state)
    assertTrue(machine.awaitingRethrow)
  }

  @Test
  fun `the dice thrown again are the ones that never settled, and only those`() {
    // The dice that were read are read: off the table, out of the way, and
    // throwing them again would throw away answers the roll already has.
    val machine = machine()
    machine.type("4d6")
    machine.throwDice()
    machine.gaveUp(listOf(1, 3))

    val again = requireNotNull(machine.throwUnsettled())

    assertEquals("the settled dice were thrown again too", 2, again.dice.size)
    assertFalse("a throw nobody has made was already driven by something", again.shake.isNotEmpty())
    assertFalse("the same dice could be thrown twice", machine.awaitingRethrow)
  }

  @Test
  fun `typing over a roll that gave up drops the dice it was holding`() {
    val machine = machine()
    machine.type("4d6")
    machine.throwDice()
    machine.gaveUp(listOf(1))

    machine.type("2d20")
    machine.throwDice()

    assertFalse("a throw nobody asked for any more was still waiting", machine.awaitingRethrow)
  }

  @Test
  fun `the readout narrows as the dice are counted off`() {
    // The dice leave the table as they are read, so the range is what a player
    // follows instead of them (`docs/TODO.md`, Step 5.5).
    val machine = machine()
    machine.type("4d6")
    machine.throwDice()

    val cold = requireNotNull(machine.progress(emptyMap()))
    val halfway = requireNotNull(machine.progress(mapOf(0 to 5, 1 to 5)))

    assertEquals(0, cold.read)
    assertEquals(4, cold.of)
    assertEquals(RollRange(4L, 24L), cold.range)
    assertEquals(2, halfway.read)
    assertEquals("two sixes are on the table", 12L, halfway.onTheTable)
    assertEquals("two sixes down leaves two dice to come", RollRange(14L, 24L), halfway.range)
  }

  @Test
  fun `a roll read to the last die has a range of one number`() {
    val machine = machine()
    machine.type("4d6")
    machine.throwDice()

    val done = requireNotNull(machine.progress(mapOf(0 to 5, 1 to 5, 2 to 5, 3 to 5)))

    assertEquals(RollRange(24L, 24L), done.range)
    assertTrue("a roll with every die read is not complete", done.complete)
  }

  @Test
  fun `what is on the table is not the roll's total when dice are dropped`() {
    // `4d6dl1` drops one of them, so the sum of the faces is not the answer —
    // which is exactly why the range is there and why this is not called a
    // total.
    val machine = machine()
    machine.type("4d6dl1")
    machine.throwDice()

    val all = requireNotNull(machine.progress(mapOf(0 to 5, 1 to 5, 2 to 5, 3 to 0)))

    assertEquals("the faces on the table add up to nineteen", 19L, all.onTheTable)
    assertEquals("but the roll drops the one, so it is eighteen", RollRange(18L, 18L), all.range)
  }

  @Test
  fun `there is nothing to report when nothing is in the air`() {
    val machine = machine()
    machine.type("4d6")

    assertNull(machine.progress(emptyMap()))
  }

  private fun machine(
    seed: Long = 1L,
    catalog: DiceCatalog = this.catalog,
  ) = RollMachine(
    catalog = catalog,
    geometry = geometry,
    // The look a pin resolves to is the app's to know, not the machine's, so
    // the seam is a function and this is the smallest thing that stands in for
    // the installed sets: the one pin these tests use, and the app's own table
    // for everything else.
    look = { pin -> if (pin == TablePin("brass", "oak")) oak else table },
    outside = Outside(seeds = { seed }, clock = { FIXED_TIME }),
  )

  private companion object {
    const val FIXED_TIME = 1_757_000_000_000L
  }
}
