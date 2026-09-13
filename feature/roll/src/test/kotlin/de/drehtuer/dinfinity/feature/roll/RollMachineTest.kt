package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.DiceSimulator
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The roll screen's state machine, which is the screen minus the pixels
 * (`docs/TODO.md`, Step 4.1).
 *
 * The rule these exist to hold is the app's first goal: **every number comes
 * off a die that was simulated.** There is no path here that scores a roll any
 * other way — not for a refused formula, not for the second die of an
 * exploding six, not when the screen is asked to round differently
 * (`.claude/CLAUDE.md`).
 */
class RollMachineTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")
  private val catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set))

  @Test
  fun `an empty field is not a roll waiting to happen`() {
    val machine = machine()

    assertEquals(RollState.Empty, machine.state)
    assertNull("an empty formula produced a throw", machine.throwDice())
  }

  @Test
  fun `a formula that does not read says where it stops reading`() {
    val machine = machine()

    machine.type("3d6 + ")

    val invalid = machine.state as RollState.Invalid
    assertTrue("the error has no range to put a squiggle under", !invalid.error.range.isEmpty())
    assertNull("a formula that does not read produced a throw", machine.throwDice())
  }

  @Test
  fun `a formula that reads is ready, at the scale the table allows`() {
    val machine = machine()

    machine.type("3d6 + 1d20 - 4")

    val ready = machine.state as RollState.Ready
    assertEquals(4, ready.diceCount)
    assertEquals("four dice do not need shrinking", 1.0, ready.scale, 0.0)
  }

  @Test
  fun `a throw the table cannot hold is refused before a single body is created`() {
    // The one case where "no roll happened" is the right answer, and the
    // simulator must never hear about it (`docs/tables.md`).
    val simulator = CountingSimulator()
    val machine = machine(simulator)

    machine.type("500d6")

    val refused = machine.state as RollState.TooMany
    assertEquals(500, refused.diceCount)
    assertTrue("the refusal does not say what would fit", refused.largestThatFits in 1 until 500)
    assertTrue("the refusal reads as a number and nothing else", refused.reason.contains("500 dice"))
    assertNull(machine.throwDice())
    assertEquals("a refused roll reached the engine", 0, simulator.runs)
  }

  @Test
  fun `a lot of dice that still fit are shrunk rather than refused`() {
    // Smaller dice that roll honestly beat big dice that jam.
    val machine = machine()

    machine.type("40d6")

    val ready = machine.state as RollState.Ready
    assertTrue("forty dice were not shrunk at all", ready.scale < 1.0)
    assertTrue("forty dice were shrunk past the floor", ready.scale >= TableCapacity.MIN_SCALE)
  }

  @Test
  fun `the throw carries the dice, the scale and the seed, and nothing that decides a face`() {
    val machine = machine(seed = 4242L)
    machine.type("2d20kh1")

    val spec = requireNotNull(machine.throwDice())

    assertEquals(2, spec.dice.size)
    assertEquals(4242L, spec.seed)
    assertEquals(geometry, spec.geometry)
    assertEquals(table, spec.table)
    assertTrue("a tap-to-roll throw carries a shake", spec.shake.isEmpty())
    assertEquals(2, (machine.state as RollState.Rolling).diceCount)
  }

  @Test
  fun `a shake is carried into the throw as it was recorded`() {
    val machine = machine()
    machine.type("1d20")
    val shake = listOf(ShakeSample(stepIndex = 0, accelerationMmPerSecond2 = Vector3(1.0, 2.0, 3.0), gravity = DOWN))

    val spec = requireNotNull(machine.throwDice(shake))

    assertEquals(shake, spec.shake)
  }

  @Test
  fun `the total is what the faces came to`() {
    // 3d6 with every die on face index 5, which is a six.
    val machine = machine()
    machine.type("3d6 + 4")
    machine.throwDice()

    machine.settled(SimulationOutcome(faces = mapOf(0 to 5, 1 to 5, 2 to 5)))

    val settled = machine.state as RollState.Settled
    assertEquals(22L, settled.result.total)
    assertEquals("3d6 + 4", settled.result.formula)
  }

  @Test
  fun `a die dropped by kh is in the breakdown but not in the total`() {
    val machine = machine()
    machine.type("2d20kh1")
    machine.throwDice()

    machine.settled(SimulationOutcome(faces = mapOf(0 to 2, 1 to 19)))

    val settled = machine.state as RollState.Settled
    assertEquals("the higher of a 3 and a 20 is 20", 20L, settled.result.total)
  }

  @Test
  fun `an exploding die is thrown again for real, not decided`() {
    // The whole app in one test. A six on a d6 explodes, and the die that
    // follows it goes through the simulator like every other die — there is no
    // branch that picks a number for it (`docs/architecture.md`, goal 1).
    val simulator = CountingSimulator(face = 0)
    val machine = machine(simulator)
    machine.type("1d6!")
    machine.throwDice()

    machine.settled(SimulationOutcome(faces = mapOf(0 to 5)))

    val settled = machine.state as RollState.Settled
    assertEquals("the exploded die was not simulated", 1, simulator.runs)
    assertEquals("a six and then a one", 7L, settled.result.total)
    assertEquals("the extra die was thrown into the same tray", table, simulator.specs.single().table)
  }

  @Test
  fun `rounding the result again does not throw the dice again`() {
    val simulator = CountingSimulator()
    val machine = machine(simulator)
    machine.type("1d20 / 3")
    machine.throwDice()
    machine.settled(SimulationOutcome(faces = mapOf(0 to 6)))
    val down = (machine.state as RollState.Settled).result.total

    machine.round(Rounding.Up)

    val up = (machine.state as RollState.Settled).result
    assertEquals("the dice were thrown again to change the rounding", 0, simulator.runs)
    assertEquals(Rounding.Up, up.rounding)
    assertEquals("a seven divided by three rounds down to two", 2L, down)
    assertEquals("and up to three", 3L, up.total)
  }

  @Test
  fun `editing the formula after a roll puts the result away`() {
    // A result that outlived the formula it came from is the one thing the
    // outcome graph refuses to show, and for the same reason.
    val machine = machine()
    machine.type("3d6")
    machine.throwDice()
    machine.settled(SimulationOutcome(faces = mapOf(0 to 0, 1 to 0, 2 to 0)))

    machine.type("3d6 + 1")

    assertTrue("the old result survived an edit", machine.state is RollState.Ready)
  }

  @Test
  fun `faces nobody asked for do not become a total`() {
    // A throw that was never made cannot land. The screen would otherwise be
    // one stray callback away from a total with no roll behind it.
    val machine = machine()
    machine.type("3d6")

    machine.settled(SimulationOutcome(faces = mapOf(0 to 0, 1 to 0, 2 to 0)))

    assertTrue("a roll nobody threw produced a total", machine.state is RollState.Ready)
  }

  @Test
  fun `a second throw cannot start while the first is in the air`() {
    val machine = machine()
    machine.type("3d6")
    machine.throwDice()

    assertNull("the dice were thrown again mid-roll", machine.throwDice())
  }

  @Test
  fun `the field keeps what was typed, valid or not`() {
    val machine = machine()

    machine.type("3d6 +")

    assertEquals("3d6 +", machine.text)
  }

  @Test
  fun `clearing puts the result away and leaves the formula alone`() {
    // The player has read the total and wants to throw again. The formula is
    // the one thing that should survive.
    val machine = machine()
    machine.type("3d6")
    machine.throwDice()
    machine.settled(SimulationOutcome(faces = mapOf(0 to 0, 1 to 0, 2 to 0)))

    machine.clear()

    assertEquals("3d6", machine.text)
    assertEquals(3, (machine.state as RollState.Ready).diceCount)
  }

  @Test
  fun `a machine built without a seed source gives every throw its own seed`() {
    // The default. A seed that repeated would make two rolls the same roll,
    // which is the one thing determinism must not turn into
    // (`docs/architecture.md`, decision 13).
    val machine = RollMachine(catalog, geometry, table, CountingSimulator())

    machine.type("1d20")
    val first = requireNotNull(machine.throwDice()).seed
    machine.settled(SimulationOutcome(faces = mapOf(0 to 0)))
    machine.clear()
    val second = requireNotNull(machine.throwDice()).seed

    assertNotEquals("two throws were given the same seed", first, second)
    assertTrue("a roll with no clock behind it", (machine.state as? RollState.Rolling) != null)
  }

  @Test
  fun `the picker row offers the standard dice the default set defines`() {
    val machine = machine()

    assertEquals(
      listOf("d2", "d4", "d6", "d8", "d10", "d%", "d12", "d18", "d20", "dF"),
      machine.pickable.map { it.notation },
    )
  }

  @Test
  fun `a tap on the row is an edit to the formula and nothing else`() {
    val machine = machine()

    machine.add(machine.pickable.first { it.notation == "d20" })

    assertEquals("1d20", machine.text)
    assertEquals(1, (machine.state as RollState.Ready).diceCount)
  }

  @Test
  fun `a tap on the row is refused by the table like any other formula`() {
    // The row goes through `type`, so the capacity rule sees it. Nothing is
    // special-cased for picked dice (`docs/tables.md`).
    val machine = machine()
    machine.type("500d6")

    machine.add(machine.pickable.first { it.notation == "d6" })

    assertTrue("a tap slipped past the capacity rule", machine.state is RollState.TooMany)
    assertNull("a refused formula produced a throw", machine.throwDice())
  }

  @Test
  fun `a tap while the dice are in the air abandons the throw, like typing`() {
    val machine = machine()
    machine.type("3d6")
    machine.throwDice()

    machine.add(machine.pickable.first { it.notation == "d6" })
    machine.settled(SimulationOutcome(faces = mapOf(0 to 0, 1 to 0, 2 to 0)))

    assertTrue("a throw nobody was waiting for was scored anyway", machine.state is RollState.Ready)
  }

  @Test
  fun `the counts follow the formula however it was written`() {
    val machine = machine()

    machine.type("2d6 + 1d20")

    val d6 = machine.pickable.first { it.notation == "d6" }
    val d20 = machine.pickable.first { it.notation == "d20" }
    assertEquals(2, machine.counts[d6])
    assertEquals(1, machine.counts[d20])
  }

  @Test
  fun `a long press takes one off and the counts follow`() {
    val machine = machine()
    machine.type("3d6")
    val d6 = machine.pickable.first { it.notation == "d6" }

    machine.remove(d6)

    assertEquals("2d6", machine.text)
    assertEquals(2, machine.counts[d6])
  }

  private fun machine(
    simulator: DiceSimulator = CountingSimulator(),
    seed: Long = 1L,
  ) = RollMachine(
    catalog = catalog,
    geometry = geometry,
    table = table,
    simulator = simulator,
    outside = Outside(seeds = { seed }, clock = { FIXED_TIME }),
  )

  /** A simulator that counts what it was asked and always lands on one face. */
  private class CountingSimulator(
    private val face: Int = 0,
  ) : DiceSimulator {
    val specs = mutableListOf<ThrowSpec>()
    val runs: Int get() = specs.size

    override fun run(spec: ThrowSpec): SimulationOutcome {
      specs += spec
      return SimulationOutcome(faces = spec.dice.indices.associateWith { face })
    }
  }

  private companion object {
    const val FIXED_TIME = 1_757_000_000_000L
    val DOWN = Vector3(0.0, 0.0, -9_806.65)
  }
}
