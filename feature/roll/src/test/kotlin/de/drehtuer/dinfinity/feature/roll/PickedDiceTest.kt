package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A die picked up by hand and thrown again by the next shake
 * (`docs/physics-and-rendering.md`, "Picking a die up and throwing it again";
 * `docs/architecture.md`, decisions 68 and 76).
 *
 * The machine without a screen: a finger is a position in the list of dice on
 * the table, and a shake is [RollMachine.throwPicked]. What these hold is the
 * part that keeps a hand from becoming the invisible hand — nothing moves,
 * nothing a chain hangs off can be picked, the replaced face stays in the
 * record, and the shake throws the picked dice and nothing else.
 */
class PickedDiceTest {
  private val geometry = TableGeometry.referenceDevice()
  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  @Test
  fun `a finger picks a die up, and a second touch on it puts it back`() {
    val machine = landed("3d6", faces = listOf(0, 1, 2))

    assertTrue(machine.pick(1))
    assertEquals(setOf(1), machine.picked)
    assertTrue(machine.pick(1))
    assertEquals("a second touch did not put the die back", emptySet<Int>(), machine.picked)
  }

  @Test
  fun `picking moves nothing and throws nothing`() {
    val machine = landed("3d6", faces = listOf(0, 1, 2))
    val before = machine.onTheTable

    machine.pick(0)

    assertEquals("a pick moved a die at rest", before, machine.onTheTable)
    assertTrue("a pick put the total away", machine.state is RollState.Settled)
  }

  @Test
  fun `the shake throws the picked die, and only it, among the dice where they lie`() {
    val machine = landed("3d6", faces = listOf(0, 1, 2))
    val lying = machine.onTheTable
    machine.pick(1)

    val spec = requireNotNull(machine.throwPicked())

    assertEquals("the shake threw more than the picked die", 1, spec.dice.size)
    assertEquals(
      "d6",
      spec.dice
        .single()
        .die.id,
    )
    assertEquals("the die that was picked up left the table", lying, spec.among)
    assertEquals(Seeds.byHand(SEED, 1), spec.seed)
    assertEquals(RollState.Rolling(diceCount = 1), machine.state)
    assertTrue("a roll in the air still offered dice to pick", machine.onTheTable.isEmpty())
  }

  @Test
  fun `the roll is scored again, with the replaced face struck through beside the new one`() {
    val machine = landed("3d6", faces = listOf(0, 1, 2))
    val d6 = machine.onTheTable.first().die
    machine.pick(1)
    val spec = requireNotNull(machine.throwPicked())

    val finished = requireNotNull((machine.settled(landing(spec, face = 5)) as Landed.Complete).thrown)

    val values = finished.result.dice.map { it.value }
    assertEquals(listOf(d6.valueAt(0), d6.valueAt(1), d6.valueAt(5), d6.valueAt(2)), values)
    val (struck, replacement) =
      finished.result.dice
        .drop(1)
        .take(2)
    assertFalse("the replaced face still counts", struck.kept)
    assertTrue(DieNote.Rerolled in struck.notes && DieNote.Rerolled in replacement.notes)
    assertEquals(
      "the total was not rescored from the new face",
      (d6.valueAt(0) + d6.valueAt(5) + d6.valueAt(2)).toLong(),
      finished.result.total,
    )
    assertEquals("the record does not say which die a hand threw again", setOf(1), finished.thrownAgain)
    assertEquals("the replay is not the roll's first throw", SEED, finished.seed)
  }

  @Test
  fun `the die thrown again lies beside the one it replaced, and only the new one can be picked`() {
    val machine = landed("2d6", faces = listOf(0, 1))
    machine.pick(0)
    val spec = requireNotNull(machine.throwPicked())
    machine.settled(landing(spec, face = 3))

    val table = machine.onTheTable
    assertEquals("the die that was picked up is not still lying where it fell", 3, table.size)
    assertFalse("the struck-through die could be picked again", machine.pick(0))
    assertTrue("the die that replaced it could not be picked", machine.pick(2))
    val again = requireNotNull(machine.throwPicked())

    val finished = (machine.settled(landing(again, face = 4)) as Landed.Complete).thrown
    assertEquals("a die thrown three times did not keep both earlier faces", 4, finished.result.dice.size)
    assertEquals(Seeds.byHand(SEED, 2), again.seed)
  }

  @Test
  fun `a shake with nothing picked has no throw by hand to make`() {
    val machine = landed("3d6", faces = listOf(0, 1, 2))

    assertNull(machine.throwPicked())
    assertTrue(machine.state is RollState.Settled)
  }

  @Test
  fun `a group that explodes offers none of its dice, and the rest of the formula is still offered`() {
    val machine = landed("1d6! + 1d20", faces = listOf(lowest("d6"), 3))

    assertFalse("a die a chain hangs off was picked", machine.pick(0))
    assertTrue("the d20 beside it was refused with it", machine.pick(1))
  }

  @Test
  fun `a die a chain added cannot be picked`() {
    val machine = machine()
    machine.type("1d6!")
    machine.throwDice()
    val d6 = machineDie("d6")
    val highest = d6.faces.indexOfFirst { it.value == d6.maxValue }
    val more = machine.settled(settled(listOf(highest))) as Landed.OneMore
    machine.throwEarned(emptyList())
    machine.settled(landing(more.spec, face = lowest("d6")))

    assertEquals(2, machine.onTheTable.size)
    assertFalse(machine.pick(1))
  }

  @Test
  fun `nothing is offered while dice are waiting to be read`() {
    // Unread dice first: the shake owed to them is the shake, and a pick would
    // be a second throw owed to the same hand (decision 70).
    val machine = machine()
    machine.type("2d6")
    machine.throwDice()
    machine.settled(settled(listOf(0, 1)).copy(faces = mapOf(0 to 0), unread = listOf(1)))

    assertTrue(machine.state is RollState.ThrowAgain)
    assertTrue(machine.onTheTable.isEmpty())
    assertFalse(machine.pick(0))
    assertNull(machine.throwPicked())
  }

  @Test
  fun `a tray with no clear floor left refuses the pick rather than drop a die on the pile`() {
    val machine = landed("40d6", faces = List(40) { 0 })

    assertFalse(machine.pick(0))
    assertTrue(machine.picked.isEmpty())
  }

  @Test
  fun `a touch on a position that is not a die picks nothing`() {
    val machine = landed("2d6", faces = listOf(0, 1))

    assertFalse(machine.pick(7))
    assertFalse(machine.pick(-1))
  }

  @Test
  fun `editing the formula puts every pick back`() {
    val machine = landed("3d6", faces = listOf(0, 1, 2))
    machine.pick(0)

    machine.type("3d6 + 1")

    assertTrue(machine.picked.isEmpty())
    assertNull(machine.throwPicked())
  }

  @Test
  fun `a throw by hand is scored under the rounding the sheet was showing`() {
    val machine = landed("3d6 / 2", faces = listOf(0, 1, 2))
    machine.round(Rounding.Up)
    machine.pick(0)
    val spec = requireNotNull(machine.throwPicked())

    val finished = (machine.settled(landing(spec, face = 0)) as Landed.Complete).thrown

    assertEquals(Rounding.Up, finished.result.rounding)
  }

  @Test
  fun `a throw by hand that lands a die cocked waits for the next shake before it is scored`() {
    val machine = landed("2d6", faces = listOf(0, 1))
    machine.pick(0)
    val spec = requireNotNull(machine.throwPicked())

    val cocked = machine.settled(SimulationOutcome(faces = emptyMap(), unread = listOf(0)))

    assertEquals(Landed.Unread(dice = 1), cocked)
    assertNull("a roll in the air reported progress against the wrong dice", machine.progress(mapOf(0 to 1)))
    val again = requireNotNull(machine.throwAgain())
    assertEquals(1, again.dice.size)
    assertNotNull(machine.settled(landing(again, face = 2)) as? Landed.Complete)
    assertEquals(spec.among, again.among)
  }

  private fun machineDie(id: String) = BuiltinDiceSet.set.dice.first { it.id == id }

  /** The face of the built-in [id] that shows its lowest value, which nothing explodes on. */
  private fun lowest(id: String): Int =
    machineDie(id).let { die -> die.faces.indexOfFirst { it.value == die.minValue } }

  /** A machine whose roll of [formula] has landed on [faces], each die somewhere of its own. */
  private fun landed(
    formula: String,
    faces: List<Int>,
  ): RollMachine {
    val machine = machine()
    machine.type(formula)
    requireNotNull(machine.throwDice())
    val landed = machine.settled(settled(faces))
    assertTrue("the roll did not land: $landed", landed is Landed.Complete)
    return machine
  }

  private fun machine() =
    RollMachine(
      catalog = catalog,
      geometry = geometry,
      look = { TableLook(id = "plain", name = "Plain") },
      outside = Outside(seeds = { SEED }, clock = { 0L }),
    )

  /** Every die on a face of its own, spread over a coarse grid of the whole tray. */
  private fun settled(faces: List<Int>): SimulationOutcome =
    SimulationOutcome(
      faces = faces.withIndex().associate { (at, face) -> at to face },
      restingAt = faces.indices.associateWith { RestingPlace(spot(it), Quaternion.Identity) },
    )

  /** [spec]'s dice landing on [face], in the corner nothing in these tests uses. */
  private fun landing(
    spec: ThrowSpec,
    face: Int,
  ): SimulationOutcome =
    SimulationOutcome(
      faces = spec.dice.indices.associateWith { face },
      restingAt =
        spec.dice.indices.associateWith {
          RestingPlace(Vector3(0.0, it * SPREAD_MM, REST_HEIGHT_MM), Quaternion.Identity)
        },
    )

  private fun spot(index: Int): Vector3 =
    Vector3(
      x = -geometry.longSideMm / 2 + (index % COLUMNS + HALF) * (geometry.longSideMm / COLUMNS),
      y = -geometry.shortSideMm / 2 + (index / COLUMNS + HALF) * (geometry.shortSideMm / ROWS),
      z = REST_HEIGHT_MM,
    )

  private companion object {
    const val SEED = 7L
    const val COLUMNS = 10
    const val ROWS = 4
    const val HALF = 0.5
    const val REST_HEIGHT_MM = 8.0
    const val SPREAD_MM = 20.0
  }
}
