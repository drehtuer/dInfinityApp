package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThrowSpecTest {
  private val table = TableLook(id = "plain", name = "Plain")
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `a throw carries the dice, the scale, the seed and the tray`() {
    val spec = spec(count = 3, scale = 0.8)
    assertEquals(3, spec.dice.size)
    assertEquals(0.8, spec.dieScale)
    assertEquals(7L, spec.seed)
    assertEquals(table, spec.table)
    assertEquals(geometry, spec.geometry)
    assertTrue(spec.shake.isEmpty())
  }

  @Test
  fun `a scale outside what the capacity rule allows is a bug`() {
    assertFailsWith<IllegalArgumentException> { spec(count = 1, scale = 0.3) }
    assertFailsWith<IllegalArgumentException> { spec(count = 1, scale = 1.5) }
  }

  @Test
  fun `more dice than the engine takes is a bug, not a slow roll`() {
    assertFailsWith<IllegalArgumentException> { spec(count = TableCapacity.MAX_DICE + 1, scale = 1.0) }
  }

  @Test
  fun `a round thrown into a tray that already holds dice may be several dice`() {
    // It used to be exactly one, because two dice asked `ClearSpace` the same
    // question and were dropped onto the same patch of floor. The spawn stands
    // each die of a round where the last one went before placing the next, so a
    // round is now as many dice as the chains earned — three sixes in `8d6!`
    // earn three throws and a player throws them together
    // (`docs/dice-notation.md`, "Evaluation").
    assertEquals(2, spec(count = 2, scale = 1.0).copy(among = down()).dice.size)
    assertEquals(1, spec(count = 1, scale = 1.0).copy(among = down()).among.size)
  }

  @Test
  fun `a throw into a tray that already holds dice still has to throw something`() {
    assertFailsWith<IllegalArgumentException> { spec(count = 0, scale = 1.0).copy(among = down()) }
  }

  @Test
  fun `the dice already down are not dice in the throw`() {
    // The whole rule in one assertion: an added round's world holds only the
    // dice being thrown, so there is nothing in it that a settled die could be
    // shoved by.
    val added = spec(count = 1, scale = 1.0).copy(among = down())

    assertEquals(1, added.dice.size)
  }

  @Test
  fun `a throw needs room for the biggest die in it`() {
    // A throw can mix a d4 and a d20, and cells sized for the d4 would put the
    // d20 through its neighbour's wall before anything had been thrown.
    assertEquals(StandardDice.d20.material.boundingRadiusMm, spec(count = 3, scale = 1.0).largestDieRadiusMm)
    assertEquals(StandardDice.d20.material.boundingRadiusMm / 2, spec(count = 3, scale = 0.5).largestDieRadiusMm)
  }

  @Test
  fun `a shake is a list of quantised moments, each tied to a step`() {
    val shake = listOf(ShakeSample(stepIndex = 4, accelerationMmPerSecond2 = Vector3.Up, gravity = -Vector3.Up))
    assertEquals(
      4,
      spec(1, 1.0)
        .copy(shake = shake)
        .shake
        .single()
        .stepIndex,
    )
  }

  @Test
  fun `an outcome reports what the dice did and what the physics had to do about it`() {
    val outcome =
      SimulationOutcome(faces = mapOf(0 to 3, 1 to 5), steps = 400, corrections = 1, rethrows = 1, forcedSettles = 0)
    assertEquals(2, outcome.diceCount)
    assertEquals(3, outcome.faces[0])
    assertTrue(outcome.clean)
  }

  @Test
  fun `a roll that had to be forced or touched at rest is not clean`() {
    assertFalse(SimulationOutcome(faces = mapOf(0 to 1), forcedSettles = 1).clean)
    assertFalse(SimulationOutcome(faces = mapOf(0 to 1), postRestCorrections = 1).clean)
  }

  @Test
  fun `an outcome cannot claim to have run past the cap`() {
    assertFailsWith<IllegalArgumentException> {
      SimulationOutcome(faces = mapOf(0 to 1), steps = SettleRule.HARD_CAP_STEPS + 1)
    }
  }

  @Test
  fun `an outcome carries the two numbers the device harness cannot work out for itself`() {
    val outcome =
      SimulationOutcome(
        faces = mapOf(0 to 1, 1 to 4),
        stackedAtRest = 1,
        deepestDiePenetrationMm = 0.08,
      )

    assertEquals(1, outcome.stackedAtRest)
    assertEquals(0.08, outcome.deepestDiePenetrationMm, 0.0)
  }

  @Test
  fun `more dice cannot be stacked than were thrown`() {
    assertFailsWith<IllegalArgumentException> {
      SimulationOutcome(faces = mapOf(0 to 1), stackedAtRest = 2)
    }
  }

  @Test
  fun `an overlap is a depth, so it cannot be negative`() {
    assertFailsWith<IllegalArgumentException> {
      SimulationOutcome(faces = mapOf(0 to 1), deepestDiePenetrationMm = -0.1)
    }
  }

  @Test
  fun `a simulator gives the same faces for the same throw`() {
    val simulator = FakeDiceSimulator()
    val spec = spec(count = 5, scale = 1.0)
    assertEquals(simulator.run(spec).faces, simulator.run(spec).faces)
  }

  @Test
  fun `a different seed is a different roll`() {
    val simulator = FakeDiceSimulator()
    assertTrue(simulator.run(spec(5, 1.0)).faces != simulator.run(spec(5, 1.0).copy(seed = 8)).faces)
  }

  @Test
  fun `every die in the throw is reported, and every face it reports exists`() {
    val outcome = FakeDiceSimulator().run(spec(count = 10, scale = 1.0))
    assertEquals((0 until 10).toSet(), outcome.faces.keys)
    outcome.faces.values.forEach { face -> assertTrue(face in 0 until StandardDice.d20.faces.size) }
  }

  @Test
  fun `a moment names a step the roll will take, or it names nothing`() {
    // The bound is the twelve-second cap at 120 Hz: every step a roll can take,
    // and a step holds one sample. Past it there is no step to drive
    // (`docs/physics-and-rendering.md`, "Shake input").
    assertEquals(SettleRule.HARD_CAP_STEPS, ShakeSample.MAX_RECORDED)
    assertTrue(moment(0).drivesAStep)
    assertTrue(moment(ShakeSample.MAX_RECORDED - 1).drivesAStep)
    assertFalse(moment(ShakeSample.MAX_RECORDED).drivesAStep, "a moment past the cap claimed a step")
  }

  @Test
  fun `a moment before the shake began drives nothing either`() {
    // There is no step before the dice were spawned, and a negative index is a
    // clock that went backwards rather than a moment of a throw.
    assertFalse(moment(-1).drivesAStep)
  }

  /** One die already at rest in the tray, in the middle of it. */
  private fun down(): List<DieAtRest> =
    listOf(DieAtRest(StandardDice.d20, "builtin", RestingPlace(Vector3(0.0, 0.0, 8.0), Quaternion.Identity)))

  private fun moment(step: Int): ShakeSample =
    ShakeSample(
      stepIndex = step,
      accelerationMmPerSecond2 = Vector3.Zero,
      gravity = Vector3.Zero,
    )

  private fun spec(
    count: Int,
    scale: Double,
  ): ThrowSpec =
    ThrowSpec(
      dice =
        List(count) {
          DieInstance(
            index = it,
            groupId = 0,
            setId = "builtin",
            requestedSetId = "builtin",
            die = StandardDice.d20,
          )
        },
      geometry = geometry,
      table = table,
      seed = 7L,
      dieScale = scale,
    )
}
