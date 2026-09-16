package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.SavedRollSource
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.DiceSimulator
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestingPlace
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  private val oak = TableLook(id = "oak", name = "Oak")
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
    val machine = machine()

    machine.type("500d6")

    val refused = machine.state as RollState.TooMany
    assertEquals(500, refused.diceCount)
    assertTrue("the refusal does not say what would fit", refused.largestThatFits in 1 until 500)
    assertTrue("the refusal reads as a number and nothing else", refused.reason.contains("500 dice"))
    assertNull("a refused roll produced a throw to make", machine.throwDice())
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
    val machine = machine()
    machine.type("1d6!")
    machine.throwDice()

    machine.play(settledAt(mapOf(0 to 5)), simulator)

    val settled = machine.state as RollState.Settled
    assertEquals("the exploded die was not simulated", 1, simulator.runs)
    assertEquals("a six and then a one", 7L, settled.result.total)
    assertEquals("the extra die was thrown into the same tray", table, simulator.specs.single().table)
  }

  @Test
  fun `an exploding die is thrown into the tray it set off, among the dice already down`() {
    // The die an explosion adds lands where the player can see it, beside the
    // die that set it off. The dice already down travel with the throw and no
    // body is created for any of them: a die that has come to rest is finished
    // (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll
    // adds").
    val machine = machine(seed = 77L)
    machine.type("1d6!")
    val first = requireNotNull(machine.throwDice())

    val landed = machine.settled(settledAt(mapOf(0 to 5)))

    val next = requireNotNull(landed as? Landed.OneMore).spec
    assertEquals("an added throw is one die", 1, next.dice.size)
    assertEquals("the die it set off was not on the table", 1, next.among.size)
    assertEquals(
      spot(0),
      next.among
        .single()
        .at.position,
    )
    assertEquals(first.table, next.table)
    assertEquals(first.geometry, next.geometry)
    assertEquals("an added die was not given a stream of its own", Seeds.derived(first.seed, 1), next.seed)
    // The earned throw carries no shake of its own yet: it is waiting for the
    // hand that will throw it, and that hand has not moved.
    assertTrue("a throw nobody has made yet was already driven by something", next.shake.isEmpty())
    assertTrue("the screen did not ask for the shake it is waiting on", machine.state is RollState.ShakeAgain)
  }

  @Test
  fun `every six in a throw earns a die, and they are all thrown together`() {
    // Three sixes are three dice, owed the instant the dice stop, and a player
    // throws them in one handful. Asking for a shake each would be asking three
    // times for one act (`docs/dice-notation.md`, "Evaluation").
    val machine = machine(seed = 77L)
    machine.type("4d6!")
    machine.throwDice()

    val landed = machine.settled(settledAt(mapOf(0 to 5, 1 to 1, 2 to 5, 3 to 5)))

    val next = requireNotNull(landed as? Landed.OneMore).spec
    assertEquals("three sixes earned three dice", 3, next.dice.size)
    assertEquals("the round was not numbered from zero", listOf(0, 1, 2), next.dice.map { it.index })
    assertEquals("the dice already down did not travel with the round", 4, next.among.size)
    assertEquals(RollState.ShakeAgain(diceCount = 4, waiting = 3), machine.state)
  }

  @Test
  fun `the die an explosion earns is thrown by the hand that asks for it`() {
    val machine = machine(seed = 77L)
    machine.type("1d6!")
    machine.throwDice()
    machine.settled(settledAt(mapOf(0 to 5)))
    val hand = listOf(ShakeSample(stepIndex = 0, accelerationMmPerSecond2 = Vector3(1.0, 2.0, 3.0), gravity = DOWN))

    val thrown = requireNotNull(machine.throwEarned(hand))

    assertEquals("the earned die was thrown by somebody else's shake", hand, thrown.shake)
    assertTrue("the screen is not rolling once the earned die is in the air", machine.state is RollState.Rolling)
    assertFalse("the same throw could be taken twice", machine.awaitingShake)
    assertNull("and taking it again threw a second die", machine.throwEarned(hand))
  }

  @Test
  fun `typing over a chain that is waiting drops the throw it earned`() {
    // The formula is the roll. A chain waiting on a shake belongs to a formula
    // nobody is asking for any more.
    val machine = machine(seed = 77L)
    machine.type("1d6!")
    machine.throwDice()
    machine.settled(settledAt(mapOf(0 to 5)))

    machine.type("2d20")
    machine.throwDice()

    assertFalse("a throw earned by a formula nobody typed was still waiting", machine.awaitingShake)
  }

  @Test
  fun `an added die is the size of the dice it joins`() {
    // It is dropped among dice the capacity rule shrank, and a full-size die
    // landing among them would be a die from a different roll
    // (`docs/tables.md`, "Capacity rule").
    val machine = machine()
    machine.type("40d6!")
    val first = requireNotNull(machine.throwDice())

    val landed = machine.settled(settledAt((0 until 40).associateWith { 5 }, across = 0.5))

    assertTrue("forty dice were not shrunk at all", first.dieScale < 1.0)
    assertEquals(first.dieScale, requireNotNull(landed as? Landed.OneMore).spec.dieScale, 0.0)
  }

  @Test
  fun `a chain of explosions stops when the tray has no room for another die`() {
    // The honest end of a chain the depth limit does not reach: forty dice
    // spread over the tray leave no patch of floor wide enough to drop another
    // onto. The alternative is a die dropped on the pile, which is the one
    // thing this app does not do (`docs/tables.md`, "Capacity rule").
    val machine = machine()
    machine.type("40d6!")
    machine.throwDice()

    val thrown = requireNotNull(machine.play(settledAt((0 until 40).associateWith { 5 })))

    assertEquals("a die was added to a tray with no room for one", 40, thrown.result.dice.size)
    assertTrue(
      "a chain that ran out of table did not say so",
      thrown.result.dice.all { DieNote.TrayFull in it.notes },
    )
    assertEquals("forty sixes", 240L, thrown.result.total)
  }

  @Test
  fun `typing while a die an explosion added is in the air abandons the roll`() {
    val machine = machine()
    machine.type("1d6!")
    machine.throwDice()
    machine.settled(settledAt(mapOf(0 to 5)))

    machine.type("1d6")

    assertNull("a roll nobody was waiting for went on", machine.settled(settledAt(mapOf(0 to 0))))
    assertTrue(machine.state is RollState.Ready)
  }

  @Test
  fun `a roll's re-throws are counted over every throw it took`() {
    // They are all one roll, however many times the table was thrown onto.
    val machine = machine()
    machine.type("1d6!")
    machine.throwDice()

    machine.settled(settledAt(mapOf(0 to 5)).copy(rethrows = 1))
    val landed = machine.settled(settledAt(mapOf(0 to 0)).copy(rethrows = 2, forcedSettles = 1))

    val result = requireNotNull(landed as? Landed.Complete).thrown.result
    assertEquals(3, result.rethrows)
    assertEquals(1, result.forcedSettles)
  }

  @Test
  fun `rounding the result again does not throw the dice again`() {
    val simulator = CountingSimulator()
    val machine = machine()
    machine.type("1d20 / 3")
    machine.throwDice()
    machine.play(SimulationOutcome(faces = mapOf(0 to 6)), simulator)
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
    val machine = RollMachine(catalog, geometry, { table })

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

  @Test
  fun `the picker offers the default set until another is chosen`() {
    val machine = machine(catalog = twoSets())

    assertEquals(BuiltinDiceSet.set.id, machine.pickingFrom)
    assertEquals(
      "a bare d20 is the default set's d20",
      "1d20",
      machine.pickable.first { it.notation == "d20" }.notation(1),
    )
  }

  @Test
  fun `a die picked from another set is written with that set in front of it`() {
    // Otherwise the tap would write `1d20`, which means the *default* set's
    // d20, and the row would be offering dice it cannot actually roll.
    val machine = machine(catalog = twoSets())

    machine.pickFrom(BRASS)
    machine.add(machine.pickable.first { it.notation == "d20" })

    assertEquals("$BRASS:1d20", machine.text)
  }

  @Test
  fun `choosing a set leaves the formula exactly as it was`() {
    // What is already written was written on purpose. A chooser that rewrote
    // `3d6` because somebody looked at another set would be editing a roll
    // nobody asked it to edit.
    val machine = machine(catalog = twoSets())
    machine.type("3d6 + 2")

    machine.pickFrom(BRASS)

    assertEquals("3d6 + 2", machine.text)
  }

  @Test
  fun `the badges follow the row when the row changes`() {
    val machine = machine(catalog = twoSets())
    machine.type("$BRASS:2d20")

    // Against the default set's dice, `brass:2d20` is a group the picker
    // cannot spell, so nothing is badged.
    assertEquals(emptyMap<Any, Int>(), machine.counts.filterValues { it > 0 })

    machine.pickFrom(BRASS)

    assertEquals(2, machine.counts[machine.pickable.first { it.notation == "d20" }])
  }

  @Test
  fun `a set that is not installed is not a set to pick from`() {
    val machine = machine(catalog = twoSets())

    machine.pickFrom("nothing-by-that-name")

    assertEquals(BuiltinDiceSet.set.id, machine.pickingFrom)
  }

  @Test
  fun `going back to the default set writes bare notation again`() {
    val machine = machine(catalog = twoSets())
    machine.pickFrom(BRASS)

    machine.pickFrom(BuiltinDiceSet.set.id)

    assertEquals("1d20", machine.pickable.first { it.notation == "d20" }.notation(1))
  }

  @Test
  fun `a throw from a saved roll is remembered as that roll's`() {
    // Without it every throw is recorded as belonging to nothing, and the
    // saved-roll statistics screen is one that can never have anything on it
    // (`docs/statistics.md`, per saved roll and per group).
    val machine = machine()
    machine.type("8d6", SavedRollSource(rollId = "fireball", groupId = "thorin"))

    machine.throwDice()
    val thrown = requireNotNull(machine.play(SimulationOutcome(faces = (0 until 8).associateWith { 0 })))

    assertEquals("fireball", thrown.savedRollId)
    assertEquals("thorin", thrown.groupId)
  }

  @Test
  fun `a formula somebody typed belongs to no saved roll`() {
    val machine = machine()
    machine.type("8d6")

    machine.throwDice()
    val thrown = requireNotNull(machine.play(SimulationOutcome(faces = (0 until 8).associateWith { 0 })))

    assertNull("a typed formula was attributed to a saved roll", thrown.savedRollId)
    assertNull(thrown.groupId)
  }

  @Test
  fun `typing over a saved roll makes it somebody's own formula again`() {
    // A roll that was Fireball and has been edited is not Fireball's throw.
    val machine = machine()
    machine.type("8d6", SavedRollSource(rollId = "fireball", groupId = "thorin"))

    machine.type("8d6 + 1")
    machine.throwDice()
    val thrown = requireNotNull(machine.play(SimulationOutcome(faces = (0 until 8).associateWith { 0 })))

    assertNull("an edited formula was still attributed to the saved roll", thrown.savedRollId)
  }

  @Test
  fun `a throw from a saved roll lands on the table that roll pinned`() {
    // The precedence itself is `tablePinFor`'s and is tested there; what is
    // asserted here is that the machine asks with the *throw's* pin, so the
    // table a saved roll pinned is the table the dice are actually thrown onto
    // rather than only the one the tray happens to be drawing
    // (`docs/tables.md`, "Selecting a table").
    val machine = machine()
    machine.type("8d6", SavedRollSource("fireball", "thorin", TablePin("brass", "oak")))

    val spec = requireNotNull(machine.throwDice())

    assertEquals(oak, spec.table)
    assertEquals("the tray was left drawing a different table than the dice landed on", oak, machine.table)
  }

  @Test
  fun `a formula somebody typed lands on the app's own table`() {
    val machine = machine()
    machine.type("8d6")

    assertEquals(table, requireNotNull(machine.throwDice()).table)
  }

  @Test
  fun `typing over a pinned saved roll puts the app's table back`() {
    // The pin goes with the attribution, because it came with it: a roll that
    // was Fireball and has been typed over is not thrown on Fireball's table.
    val machine = machine()
    machine.type("8d6", SavedRollSource("fireball", "thorin", TablePin("brass", "oak")))

    machine.type("8d6 + 1")

    assertEquals(table, machine.table)
  }

  @Test
  fun `tapping a die onto a saved roll is an edit like any other`() {
    // The picker goes through `type`, which is the point: a tap is an edit, so
    // it drops the attribution the same way a keystroke does.
    val machine = machine()
    machine.type("8d6", SavedRollSource(rollId = "fireball", groupId = "thorin"))

    machine.add(machine.pickable.first { it.notation == "d6" })
    machine.throwDice()
    val thrown = requireNotNull(machine.play(SimulationOutcome(faces = (0 until 9).associateWith { 0 })))

    assertNull("a picked die left the throw attributed to the saved roll", thrown.savedRollId)
  }

  @Test
  fun `a throw that has landed is described by the spec that would replay it`() {
    // The spec a shake-driven throw *starts* as has no shake in it: the dice
    // are spawned the moment the shake is confirmed and the samples arrive
    // afterwards. What the record has to be is the spec with those samples
    // written back into it (`docs/physics-and-rendering.md`, "Shake input").
    val machine = machine()
    machine.type("1d6")
    val spec = requireNotNull(machine.throwDice())
    val hand = hand(3)

    val thrown = requireNotNull(machine.play(SimulationOutcome(faces = mapOf(0 to 0)), hand = hand))

    assertTrue("the throw went out with a shake it could not have had", spec.shake.isEmpty())
    assertEquals(spec.copy(shake = hand), thrown.thrown)
    assertEquals("the seed no longer belongs to the spec it would replay", spec.seed, thrown.seed)
  }

  @Test
  fun `a tapped throw is replayable from its seed alone`() {
    val machine = machine()
    machine.type("1d6")
    val spec = requireNotNull(machine.throwDice())

    val thrown = requireNotNull(machine.play(SimulationOutcome(faces = mapOf(0 to 0))))

    assertEquals(spec, thrown.thrown)
    assertTrue(thrown.thrown.shake.isEmpty())
  }

  @Test
  fun `a throw abandoned in the air takes its shake with it`() {
    // Editing the formula abandons whatever is in the air. Nothing landed, so
    // there is no record to keep and the samples that reached the roll go
    // nowhere.
    val machine = machine()
    machine.type("1d6")
    machine.throwDice()

    machine.type("2d6")

    assertNull(
      "a roll nobody waited for was written down",
      machine.settled(SimulationOutcome(faces = mapOf(0 to 0)), hand(3)),
    )
  }

  @Test
  fun `re-rounding a throw does not hand the record out a second time`() {
    // The dice do not move, so it is the same roll — and the same record, given
    // out once.
    val machine = machine()
    machine.type("1d6 / 2")
    machine.throwDice()
    machine.settled(SimulationOutcome(faces = mapOf(0 to 4)), hand(2))

    machine.round(Rounding.Up)

    assertNull(machine.settled(SimulationOutcome(faces = mapOf(0 to 4)), hand(2)))
  }

  /** A hand moving sideways for [moments] simulation steps. */
  private fun hand(moments: Int): List<ShakeSample> =
    List(moments) { step ->
      ShakeSample(
        stepIndex = step,
        accelerationMmPerSecond2 = Vector3(5_000.0, 0.0, 0.0),
        gravity = Vector3(0.0, 0.0, -1.0),
      )
    }

  /** The bundled set and one more, which is when the chooser is worth drawing. */
  private fun twoSets(): DiceCatalog =
    DiceCatalog.of(
      listOf(BuiltinDiceSet.set, BuiltinDiceSet.set.copy(id = BRASS, name = "Brass")),
      BuiltinDiceSet.set.id,
    )

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

  /**
   * Plays a throw through to a total, throwing every die the roll adds to
   * itself — which is what [RollPresenter] does with a tray in front of it.
   *
   * A roll is not over when its dice stop: `1d6!` asks for another die and only
   * then for a total, so a test that called `settled` once and stopped would be
   * testing half of one.
   */
  private fun RollMachine.play(
    outcome: SimulationOutcome,
    simulator: CountingSimulator = CountingSimulator(),
    hand: List<ShakeSample> = emptyList(),
  ): FinishedThrow? {
    var landed = settled(outcome, hand)
    while (landed is Landed.OneMore) {
      landed = settled(simulator.run(landed.spec))
    }
    return (landed as? Landed.Complete)?.thrown
  }

  /**
   * A throw that came to [faces], with every die resting somewhere real.
   *
   * Where the dice stopped matters the moment a formula explodes: the die that
   * follows is dropped into the floor these left clear, so a throw reporting
   * nowhere would be a throw an added die had no reason to avoid.
   */
  private fun settledAt(
    faces: Map<Int, Int>,
    across: Double = 1.0,
  ): SimulationOutcome =
    SimulationOutcome(
      faces = faces,
      restingAt = faces.keys.associateWith { index -> RestingPlace(spot(index, across), Quaternion.Identity) },
    )

  /**
   * One cell of a coarse grid over [across] of the tray's length.
   *
   * Spread over the whole of it, dice this size leave no gap wide enough for
   * another — which is what a chain of explosions running out of table looks
   * like. Spread over half of it, the other half is clear.
   */
  private fun spot(
    index: Int,
    across: Double = 1.0,
  ): Vector3 =
    Vector3(
      x = -geometry.longSideMm / 2 + (index % COLUMNS + HALF) * (geometry.longSideMm * across / COLUMNS),
      y = -geometry.shortSideMm / 2 + (index / COLUMNS + HALF) * (geometry.shortSideMm / ROWS),
      z = REST_HEIGHT_MM,
    )

  /**
   * A simulator that counts what it was asked and always lands on one face.
   *
   * It puts each die down where the tray would have dropped it, so a chain of
   * explosions fills the tray up the way a real one does and the question "is
   * there still room" has something to be about.
   */
  private class CountingSimulator(
    private val face: Int = 0,
  ) : DiceSimulator {
    val specs = mutableListOf<ThrowSpec>()
    val runs: Int get() = specs.size

    override fun run(spec: ThrowSpec): SimulationOutcome {
      specs += spec
      val dropped =
        ClearSpace.clearestPoint(
          geometry = spec.geometry,
          dieRadiusMm = spec.largestDieRadiusMm,
          taken = spec.among.map { it.at.position },
        ) ?: Vector3.Zero
      return SimulationOutcome(
        faces = spec.dice.indices.associateWith { face },
        restingAt =
          spec.dice.indices.associateWith {
            RestingPlace(dropped.copy(z = spec.largestDieRadiusMm), Quaternion.Identity)
          },
      )
    }
  }

  private companion object {
    const val FIXED_TIME = 1_757_000_000_000L

    /** A coarse grid over the tray, so a test's settled dice are spread over it. */
    const val COLUMNS = 10
    const val ROWS = 4
    const val HALF = 0.5
    const val REST_HEIGHT_MM = 8.0

    /** A second installed set, which is when a chooser is worth drawing. */
    const val BRASS = "brass"
    val DOWN = Vector3(0.0, 0.0, -9_806.65)
  }
}
