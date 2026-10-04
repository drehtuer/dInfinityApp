package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dice that came to rest where they cannot be read, and the shake that throws
 * them again (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked
 * dice"; `docs/architecture.md`, decision 70).
 *
 * The roll used to throw them again by itself. It stops now: the dice that
 * were read stay read, the others lie where they fell, the screen asks for a
 * shake, and the shake throws those and only those. These are the machine's
 * half of that — what it waits in, what the shake throws, and what the roll
 * comes to once every die has a face.
 */
class UnreadDiceTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")
  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  @Test
  fun `a throw that leaves a die unread waits for a shake and scores nothing`() {
    val machine = machine()
    machine.type("4d6")
    machine.throwDice()

    val landed = machine.settled(pass(read = mapOf(0 to 1, 1 to 2, 3 to 4), unread = listOf(2)))

    assertEquals(Landed.Unread(dice = 1), landed)
    assertEquals(RollState.ThrowAgain(unread = 1, read = 3), machine.state)
    assertTrue(machine.awaitingRethrow)
    assertFalse("a die nobody could read earned a chain's throw", machine.awaitingShake)
  }

  @Test
  fun `the shake throws only that die, driven by the hand that shook`() {
    val machine = machine(seed = 31L)
    machine.type("4d6")
    val first = requireNotNull(machine.throwDice())
    machine.settled(pass(read = mapOf(0 to 1, 1 to 2, 3 to 4), unread = listOf(2)))
    val shake = hand()

    val again = requireNotNull(machine.throwAgain(shake))

    assertEquals("the dice that were read went back in the air", 1, again.dice.size)
    assertEquals(first.dice[2].die, again.dice.single().die)
    assertEquals("a throw of its own numbers its dice from nought", 0, again.dice.single().index)
    assertEquals("the read dice are lifted by this throw, not drawn among it", emptyList<Any>(), again.among)
    assertEquals(Seeds.again(first.seed, 1), again.seed)
    assertEquals(first.dieScale, again.dieScale, 0.0)
    assertEquals("the die was thrown by somebody else's shake", shake, again.shake)
    assertEquals(RollState.Rolling(diceCount = 1), machine.state)
    assertFalse("the same die could be thrown twice", machine.awaitingRethrow)
    assertNull("a second shake threw it again", machine.throwAgain(shake))
  }

  @Test
  fun `its face goes where the plan knows it, and the roll is written once, when every die is read`() {
    val machine = machine()
    machine.type("4d6")
    machine.throwDice()
    assertTrue(machine.settled(pass(read = mapOf(0 to 1, 1 to 2, 3 to 4), unread = listOf(2))) is Landed.Unread)
    machine.throwAgain()

    val landed = machine.settled(pass(read = mapOf(0 to 5)))

    val thrown = requireNotNull(landed as? Landed.Complete).thrown
    // The same faces read in one pass come to the same roll: nothing about
    // the die that waited is different but when it was read.
    assertEquals(oneThrow("4d6", mapOf(0 to 1, 1 to 2, 2 to 5, 3 to 4)).total, thrown.result.total)
    assertEquals("the die thrown again was not counted as one", 1, thrown.result.rethrows)
    assertTrue(machine.state is RollState.Settled)
  }

  @Test
  fun `a die that lands cocked again waits for another shake, and is seeded afresh`() {
    val machine = machine(seed = 31L)
    machine.type("3d6")
    val first = requireNotNull(machine.throwDice())
    machine.settled(pass(read = mapOf(0 to 1), unread = listOf(1, 2)))
    val second = requireNotNull(machine.throwAgain())

    // Its own positions: die 0 of this throw is the plan's die 1.
    val landed = machine.settled(pass(read = mapOf(0 to 3), unread = listOf(1)))

    assertEquals(Landed.Unread(dice = 1), landed)
    assertEquals(RollState.ThrowAgain(unread = 1, read = 2), machine.state)
    val third = requireNotNull(machine.throwAgain())
    assertEquals(first.dice[2].die, third.dice.single().die)
    assertNotEquals("two passes were thrown by one stream", second.seed, third.seed)

    val done = requireNotNull(machine.settled(pass(read = mapOf(0 to 0))) as? Landed.Complete).thrown
    assertEquals(oneThrow("3d6", mapOf(0 to 1, 1 to 3, 2 to 0)).total, done.result.total)
    assertEquals("three dice were thrown again", 3, done.result.rethrows)
  }

  @Test
  fun `the dice nobody could read are thrown before a chain earns its next die`() {
    // Both want a shake and they cannot share one: an explosion is decided by
    // a face, and a die that has not been read has none yet. So the unread die
    // goes first and the chain waits for the shake after.
    val machine = machine()
    machine.type("2d6!")
    machine.throwDice()

    val first = machine.settled(pass(read = mapOf(0 to 5), unread = listOf(1)))

    assertTrue("a chain earned a throw before every die was read", first is Landed.Unread)
    assertFalse(machine.awaitingShake)
    assertNull("the shake was taken for an explosion", machine.throwEarned(hand()))

    machine.throwAgain()
    val second = machine.settled(pass(read = mapOf(0 to 5)))

    val earned = requireNotNull(second as? Landed.OneMore).spec
    assertEquals("two sixes earn two dice", 2, earned.dice.size)
    assertEquals(RollState.ShakeAgain(diceCount = 1, waiting = 2), machine.state)
  }

  @Test
  fun `a die an explosion added that lands cocked waits too, and comes back as the chain's`() {
    val machine = machine()
    machine.type("2d6!")
    machine.throwDice()
    machine.settled(pass(read = mapOf(0 to 5, 1 to 1)))
    val earned = requireNotNull(machine.throwEarned(hand()))

    assertTrue(machine.settled(pass(read = emptyMap(), unread = listOf(0))) is Landed.Unread)
    val again = requireNotNull(machine.throwAgain())

    assertEquals("the dice already down were not carried", earned.among, again.among)
    assertEquals(Seeds.again(earned.seed, 1), again.seed)
    val done = requireNotNull(machine.settled(pass(read = mapOf(0 to 2))) as? Landed.Complete).thrown
    val (six, two, three) = listOf(5, 1, 2).map { face -> oneThrow("1d6", mapOf(0 to face)).total }
    assertEquals("a six, a two, and the three the six earned", six + two + three, done.result.total)
  }

  @Test
  fun `a chain's die that gives up comes back as the chain's, not as one of the plan's`() {
    // The stalled path shares the wait. It used to file a stalled die by the
    // plan's indices whichever throw had stalled, which put a chain's die at
    // the plan's first die.
    val machine = machine()
    machine.type("2d6!")
    machine.throwDice()
    machine.settled(pass(read = mapOf(0 to 5, 1 to 1)))
    machine.throwEarned(hand())

    assertTrue(machine.gaveUp(unsettled = listOf(0)))
    assertEquals(RollState.Stalled(unsettled = 1, read = 0), machine.state)
    machine.throwAgain()

    val done = requireNotNull(machine.settled(pass(read = mapOf(0 to 2))) as? Landed.Complete).thrown
    assertEquals(3, done.result.dice.size)
  }

  @Test
  fun `the count follows the plan's dice across passes`() {
    val machine = machine()
    machine.type("4d6")
    machine.throwDice()
    machine.settled(pass(read = mapOf(0 to 1, 1 to 2, 3 to 4), unread = listOf(2)))
    machine.throwAgain()

    val progress = requireNotNull(machine.progress(mapOf(0 to 5)))

    assertEquals("the dice read before the shake were forgotten", 4, progress.read)
    assertEquals(4, progress.of)
    assertTrue(progress.complete)
  }

  @Test
  fun `the record keeps the hand that threw the roll, not the one that threw a die again`() {
    val machine = machine()
    machine.type("2d6")
    machine.throwDice()
    machine.settled(pass(read = mapOf(0 to 1), unread = listOf(1)), hand(moments = 3))
    machine.throwAgain(hand(moments = 1))

    val done = requireNotNull(machine.settled(pass(read = mapOf(0 to 2)), hand(moments = 5)) as? Landed.Complete)

    assertEquals(hand(moments = 3), done.thrown.thrown.shake)
  }

  @Test
  fun `and a throw that gave up is not described by the hand that threw its dice again`() {
    val machine = machine()
    machine.type("2d6")
    machine.throwDice()
    machine.gaveUp(unsettled = listOf(1), read = mapOf(0 to 1))
    machine.throwAgain(hand(moments = 1))

    val done = requireNotNull(machine.settled(pass(read = mapOf(0 to 2)), hand(moments = 4)) as? Landed.Complete)

    assertTrue(
      "the re-throw's hand was recorded as the roll's",
      done.thrown.thrown.shake
        .isEmpty(),
    )
  }

  @Test
  fun `typing over a throw that is waiting drops the dice it was holding`() {
    val machine = machine()
    machine.type("2d6")
    machine.throwDice()
    machine.settled(pass(read = mapOf(0 to 1), unread = listOf(1)))

    machine.type("1d20")

    assertFalse(machine.awaitingRethrow)
    assertNull("a die from a formula nobody wants any more was thrown", machine.throwAgain())
    assertTrue(machine.state is RollState.Ready)
  }

  @Test
  fun `an answer for a throw that is waiting on a hand is a stray one`() {
    val machine = machine()
    machine.type("2d6")
    machine.throwDice()
    machine.settled(pass(read = mapOf(0 to 1), unread = listOf(1)))

    assertNull(machine.settled(pass(read = mapOf(0 to 1, 1 to 1))))
    assertFalse("a throw that is not in the air gave up", machine.gaveUp(listOf(1)))
    assertEquals(RollState.ThrowAgain(unread = 1, read = 1), machine.state)
  }

  /** What [formula] comes to when every face is read on the first pass. */
  private fun oneThrow(
    formula: String,
    faces: Map<Int, Int>,
  ) = run {
    val machine = machine()
    machine.type(formula)
    machine.throwDice()
    requireNotNull(machine.settled(pass(read = faces)) as? Landed.Complete).thrown.result
  }

  /** A pass that read [read] and left [unread] lying where it could not be read. */
  private fun pass(
    read: Map<Int, Int>,
    unread: List<Int> = emptyList(),
  ): SimulationOutcome =
    SimulationOutcome(
      faces = read,
      unread = unread,
      restingAt =
        if (unread.isEmpty()) {
          read.keys.associateWith { RestingPlace(Vector3(it * APART_MM - APART_MM, 0.0, 8.0), Quaternion.Identity) }
        } else {
          emptyMap()
        },
    )

  private fun hand(moments: Int = 2): List<ShakeSample> =
    List(moments) { ShakeSample(it, Vector3(5_000.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0)) }

  private fun machine(seed: Long = 1L) =
    RollMachine(
      catalog = catalog,
      geometry = geometry,
      look = { table },
      outside = Outside(seeds = { seed }, clock = { 0L }),
    )

  private companion object {
    /** Far enough apart that the floor an explosion is dropped onto is clear. */
    const val APART_MM = 40.0
  }
}
