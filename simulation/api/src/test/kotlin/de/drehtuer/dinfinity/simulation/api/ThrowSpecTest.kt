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
