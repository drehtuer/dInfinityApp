package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The installed sets changing while the roll screen is open ([RollMachine.use]):
 * the start-up read of the folder finishing after a cold start opened it, or a
 * set installed or removed on another screen (`docs/dice-sets.md`, "Sets that
 * come installed").
 */
class RollMachineCataloguesTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")
  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  @Test
  fun `sets read after the screen opened reach the chooser, and a formula naming one is ready`() {
    // A cold start: the screen opens on the bundled set alone, and the folder
    // is read a moment later (`docs/dice-sets.md`, "Sets that come installed").
    val machine = machine()
    machine.type("$BRASS:3d6")
    assertTrue("a set nobody has yet planned a throw", machine.state is RollState.Invalid)
    assertEquals(1, machine.choosableSets.size)

    machine.use(twoSets())

    assertEquals(listOf(BuiltinDiceSet.set.id, BRASS), machine.choosableSets.map { it.id })
    assertEquals(2, machine.sets)
    assertEquals(3, (machine.state as RollState.Ready).diceCount)
    assertEquals("$BRASS:3d6", machine.text)
  }

  @Test
  fun `the picker stays on its set when the sets change around it, and leaves one that went`() {
    val machine = machine(catalog = twoSets())
    machine.pickFrom(BRASS)

    machine.use(DiceCatalog.of(listOf(BuiltinDiceSet.set, BuiltinDiceSet.set.copy(id = BRASS, name = "Brass"), SKULLS)))
    assertEquals(BRASS, machine.pickingFrom)

    machine.use(catalog)
    assertEquals(BuiltinDiceSet.set.id, machine.pickingFrom)
  }

  @Test
  fun `sets that change under a throw wait for it to land, and leave its result alone`() {
    val machine = machine()
    machine.type("1d20")
    val spec = requireNotNull(machine.throwDice())
    assertTrue(machine.throwing)

    machine.use(twoSets())
    assertEquals("the chooser changed under dice in the air", 1, machine.choosableSets.size)

    machine.settled(settledAt(spec.dice.associate { it.index to 7 }))
    assertFalse(machine.throwing)
    assertEquals(2, machine.choosableSets.size)
    assertTrue("the result was planned over", machine.state is RollState.Settled)
  }

  @Test
  fun `sets that change under a throw are taken up when an edit abandons it`() {
    val machine = machine()
    machine.type("1d20")
    requireNotNull(machine.throwDice())
    machine.use(twoSets())

    machine.type("$BRASS:1d6")

    assertTrue(machine.state is RollState.Ready)
  }

  @Test
  fun `the same sets again change nothing`() {
    val machine = machine(catalog = twoSets())
    machine.type("2d6")
    val before = machine.state

    machine.use(twoSets())

    assertSame(before, machine.state)
  }

  /** The bundled set and one more, which is when the chooser is worth drawing. */
  private fun twoSets(): DiceCatalog =
    DiceCatalog.of(listOf(BuiltinDiceSet.set, BuiltinDiceSet.set.copy(id = BRASS, name = "Brass")))

  private fun machine(catalog: DiceCatalog = this.catalog) =
    RollMachine(
      catalog = catalog,
      geometry = geometry,
      look = { table },
      outside = Outside(seeds = { 1L }, clock = { 0L }),
    )

  /** A throw that came to [faces], every die resting at its own spot on the floor. */
  private fun settledAt(faces: Map<Int, Int>): SimulationOutcome =
    SimulationOutcome(
      faces = faces,
      restingAt =
        faces.keys.associateWith { index ->
          RestingPlace(Vector3(x = index * SPACING_MM, y = 0.0, z = 0.0), Quaternion.Identity)
        },
    )

  private companion object {
    const val BRASS = "brass"
    const val SPACING_MM = 30.0

    /** A third set, for a catalogue that grew. */
    val SKULLS = BuiltinDiceSet.set.copy(id = "skulls", name = "Skulls")
  }
}
