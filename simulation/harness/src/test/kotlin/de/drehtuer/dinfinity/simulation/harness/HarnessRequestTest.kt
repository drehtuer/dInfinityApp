package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.Seeds
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The half of the harness that is somebody typing at a terminal.
 *
 * None of it needs a device, and the mistakes it catches are all made before
 * one is reached — which is why it is here rather than in the instrumented
 * test that reads the arguments.
 */
class HarnessRequestTest {
  @Test
  fun `no roll count means no run, so the harness can sit in the ordinary suite`() {
    assertNull(HarnessRequest.from { null })
  }

  @Test
  fun `a roll count that is not a number, or is none, is no run either`() {
    assertNull(HarnessRequest.from { if (it == HarnessRequest.ROLLS) "many" else null })
    assertNull(HarnessRequest.from { if (it == HarnessRequest.ROLLS) "0" else null })
    assertNull(HarnessRequest.from { if (it == HarnessRequest.ROLLS) "-5" else null })
  }

  @Test
  fun `a roll count on its own is twenty d20s, which is where Step 5 states its targets`() {
    val request = requireNotNull(HarnessRequest.from(arguments(rolls = "1000")))

    assertEquals(1_000, request.rolls)
    assertEquals(HarnessRequest.DEFAULT_DICE, request.diceCount)
    assertEquals(DieShape.Icosahedron, request.shape)
    assertEquals(HarnessRequest.DEFAULT_SEED, request.seed)
    assertEquals("20d20", request.label)
  }

  @Test
  fun `the label says what was thrown unless somebody says otherwise`() {
    assertEquals("100d4", requireNotNull(HarnessRequest.from(arguments("8", dice = "100", shape = "d4"))).label)
    assertEquals("soak", requireNotNull(HarnessRequest.from(arguments("8", label = "soak"))).label)
    // A label of nothing is not a label.
    assertEquals("20d20", requireNotNull(HarnessRequest.from(arguments("8", label = "  "))).label)
  }

  @Test
  fun `arguments arrive with whatever spacing a shell left on them`() {
    val request = requireNotNull(HarnessRequest.from(arguments(rolls = " 12 ", dice = " 5 ", seed = " 99 ")))

    assertEquals(12, request.rolls)
    assertEquals(5, request.diceCount)
    assertEquals(99L, request.seed)
  }

  @Test
  fun `a die can be named three ways, because all three are what people type`() {
    assertEquals(DieShape.Icosahedron, HarnessRequest.shapeOf("icosahedron"))
    assertEquals(DieShape.Icosahedron, HarnessRequest.shapeOf("d20"))
    assertEquals(DieShape.Icosahedron, HarnessRequest.shapeOf("20"))
    assertEquals(DieShape.Icosahedron, HarnessRequest.shapeOf(" D20 "))
    assertEquals(DieShape.Tetrahedron, HarnessRequest.shapeOf("d4"))
    assertEquals(DieShape.Coin, HarnessRequest.shapeOf("d2"))
    assertEquals(DieShape.Coin, HarnessRequest.shapeOf("coin"))
    assertEquals(DieShape.EnneagonalTrapezohedron, HarnessRequest.shapeOf("d18"))
  }

  @Test
  fun `a die that is not in the catalogue is refused with the catalogue`() {
    val failure = assertFailsWith<IllegalArgumentException> { HarnessRequest.shapeOf("d30") }

    assertTrue(failure.message.orEmpty().contains("icosahedron (d20)"), failure.message.orEmpty())
  }

  @Test
  fun `a throw of no dice, or of more than the engine takes, is refused here`() {
    assertFailsWith<IllegalArgumentException> { HarnessRequest.from(arguments("10", dice = "0")) }
    assertFailsWith<IllegalArgumentException> {
      HarnessRequest.from(arguments("10", dice = "${TableCapacity.MAX_DICE + 1}"))
    }
  }

  @Test
  fun `a plan shrinks its dice the way the capacity rule says, and nothing else does`() {
    val plan = requireNotNull(HarnessRequest.from(arguments("10", dice = "100", shape = "d20"))).plan()

    assertEquals(100, plan.dice.size)
    assertTrue(plan.dieScale < 1.0, "a full tray of d20s was not shrunk at all: ${plan.dieScale}")
    assertTrue(plan.dieScale >= TableCapacity.MIN_SCALE, "scale ${plan.dieScale}")
    assertEquals(plan.dieScale, plan.specFor(0).dieScale)
  }

  @Test
  fun `a single die is thrown at its own size`() {
    val plan = requireNotNull(HarnessRequest.from(arguments("10", dice = "1"))).plan()

    assertEquals(1.0, plan.dieScale)
  }

  @Test
  fun `a throw the table refuses is refused before a body is created`() {
    // A table too narrow to hold them even shrunk to the floor of the scale.
    val request = HarnessRequest(label = "x", shape = DieShape.Icosahedron, diceCount = 100, rolls = 1, seed = 1L)

    val failure = assertFailsWith<IllegalArgumentException> { request.plan(geometry = TableGeometry(NARROW_MM)) }
    assertTrue(failure.message.orEmpty().contains("don't fit on the table"), failure.message.orEmpty())
  }

  @Test
  fun `every die in the plan is the shape that was asked for, and keeps its throw order`() {
    val plan = requireNotNull(HarnessRequest.from(arguments("1", dice = "3", shape = "d4"))).plan()

    assertEquals(listOf(0, 1, 2), plan.dice.map { it.index })
    assertTrue(plan.dice.all { it.die.shape == DieShape.Tetrahedron })
  }

  @Test
  fun `each roll of a run is seeded from the run, stirred rather than counted`() {
    val plan = requireNotNull(HarnessRequest.from(arguments("3", seed = "7"))).plan()

    assertEquals(Seeds.derived(7L, 0), plan.specFor(0).seed)
    assertEquals(Seeds.derived(7L, 1), plan.specFor(1).seed)
    // Counted seeds would be one apart, which is the correlation `Seeds` exists
    // to break.
    assertNotEquals(plan.specFor(0).seed + 1, plan.specFor(1).seed)
  }

  @Test
  fun `a run replays from the one number it started with`() {
    val first = requireNotNull(HarnessRequest.from(arguments("3", seed = "7"))).plan()
    val again = requireNotNull(HarnessRequest.from(arguments("3", seed = "7"))).plan()

    assertEquals(first.specFor(2), again.specFor(2))
  }

  @Suppress("LongParameterList")
  private fun arguments(
    rolls: String,
    dice: String? = null,
    shape: String? = null,
    seed: String? = null,
    label: String? = null,
  ): (String) -> String? =
    { name ->
      when (name) {
        HarnessRequest.ROLLS -> rolls
        HarnessRequest.DICE -> dice
        HarnessRequest.SHAPE -> shape
        HarnessRequest.SEED -> seed
        HarnessRequest.LABEL -> label
        else -> null
      }
    }

  private companion object {
    /** A tray far narrower than any phone, so the capacity rule has to refuse. */
    const val NARROW_MM = 10.0
  }
}
