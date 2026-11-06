package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.RollRange
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
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

  /** A range the filled-share tests do not care about. */
  private val nothing = RollRange(lowest = 0, highest = 0)

  @Test
  fun `the progress rule is filled by the share of dice that have been read`() {
    assertEquals(0f, RollProgress(read = 0, of = 20, onTheTable = 0, range = nothing).filled, 0f)
    assertEquals(0.5f, RollProgress(read = 10, of = 20, onTheTable = 0, range = nothing).filled, 0f)
    assertEquals(1f, RollProgress(read = 20, of = 20, onTheTable = 0, range = nothing).filled, 0f)
  }

  @Test
  fun `a rule is never drawn past its own end, or divided by no dice at all`() {
    // Both edges are real. A chain adds dice to itself as it goes, so more can
    // be read than the formula planned; and a throw of nothing would divide by
    // zero. A rule drawn past its own end is a rule drawn outside the plate.
    assertEquals(1f, RollProgress(read = 24, of = 20, onTheTable = 0, range = nothing).filled, 0f)
    assertEquals(0f, RollProgress(read = 0, of = 0, onTheTable = 0, range = nothing).filled, 0f)
  }

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

    val again = requireNotNull(machine.throwAgain())

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
  fun `a roll with dice still to read is not complete`() {
    val machine = machine()
    machine.type("4d6")
    machine.throwDice()

    assertFalse(requireNotNull(machine.progress(mapOf(0 to 5))).complete)
  }

  @Test
  fun `a six on an exploding die says the roll can go higher than the seven it would be`() {
    // Every die the formula planned has been read, and the throw it earned has
    // not: the `+` on the readout is the only thing saying so.
    val machine = machine()
    machine.type("1d6!")
    machine.throwDice()

    val read = requireNotNull(machine.progress(mapOf(0 to 5)))

    assertEquals(read.of, read.read)
    assertEquals(RollRange(lowest = 7L, highest = 7L, more = true), read.range)
  }

  @Test
  fun `a throw an explosion earned waits for a hand`() {
    val machine = machine()
    machine.type("1d6!")
    machine.throwDice()
    assertFalse("nothing has been earned while the first die is in the air", machine.awaitingShake)

    machine.settled(SimulationOutcome(faces = mapOf(0 to 5)))

    assertTrue("the chain did not say it was waiting on a hand", machine.awaitingShake)
  }

  @Test
  fun `a chain's own round reports nothing about the roll`() {
    // "One of one" of a single added die says nothing about a roll of four
    // dice, so the readout keeps out of it rather than go backwards.
    val machine = machine()
    machine.type("4d6!")
    machine.throwDice()
    machine.settled(SimulationOutcome(faces = mapOf(0 to 5, 1 to 1, 2 to 1, 3 to 1)))
    requireNotNull(machine.throwEarned(emptyList()))

    assertNull(machine.progress(mapOf(0 to 2)))
  }

  @Test
  fun `a stall from a throw that was typed over changes nothing`() {
    // The tray answers from its own thread, so its answer can arrive after the
    // formula it was answering has gone.
    val machine = machine()
    machine.type("3d6")
    machine.throwDice()
    machine.type("2d20")

    assertFalse(machine.gaveUp(unsettled = listOf(0)))

    assertEquals(2, (machine.state as RollState.Ready).diceCount)
    assertFalse("a throw nobody is making was offered back", machine.awaitingRethrow)
  }

  @Test
  fun `a stall that names no dice is not a stall`() {
    val machine = machine()
    machine.type("3d6")
    machine.throwDice()

    assertFalse(machine.gaveUp(unsettled = emptyList()))

    assertEquals(RollState.Rolling(diceCount = 3), machine.state)
  }

  @Test
  fun `a stall after the dice have stopped does not overwrite what they came to`() {
    val machine = machine()
    machine.type("3d6")
    machine.throwDice()
    machine.settled(SimulationOutcome(faces = mapOf(0 to 1, 1 to 2), unread = listOf(2)))

    assertFalse(machine.gaveUp(unsettled = listOf(2)))

    assertEquals(RollState.ThrowAgain(unread = 1, read = 2), machine.state)
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
