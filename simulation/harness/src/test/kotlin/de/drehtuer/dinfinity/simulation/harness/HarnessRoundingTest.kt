package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.core.model.DieMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Throwing rounder dice than the built-in set has, from the terminal
 * (`harness.edgeRounding`, and `FairnessTest`'s `edgeRounding`;
 * `docs/architecture.md`, decision 94) — and grippier or bouncier ones
 * (`harness.friction`, `harness.restitution`).
 */
class HarnessRoundingTest {
  @Test
  fun `no rounding asked for is the default die, exactly`() {
    assertNull(HarnessRequest.edgeRoundingOf(null))
    assertNull(HarnessRequest.edgeRoundingOf("  "))
    assertEquals(DieMaterial(), HarnessRequest.materialOf(null))

    val request = requireNotNull(HarnessRequest.from { if (it == HarnessRequest.ROLLS) "10" else null })
    assertNull(request.edgeRounding)
    assertTrue(request.plan().dice.all { it.die.material == DieMaterial() })
  }

  @Test
  fun `a rounding asked for reaches every die of the throw`() {
    val request =
      requireNotNull(
        HarnessRequest.from { name ->
          when (name) {
            HarnessRequest.ROLLS -> "10"
            HarnessRequest.EDGE_ROUNDING -> " 0.06 "
            else -> null
          }
        },
      )

    assertEquals(0.06, request.edgeRounding)
    assertTrue(request.plan().dice.all { it.die.material.edgeRounding == 0.06 })
    // Named for it, so the run's files do not overwrite the default run's.
    assertEquals("20d20-round0.06", request.label)
  }

  @Test
  fun `a rounding outside what a set file may say is refused, with the range`() {
    // 0.12 among them: it was once the top of the range, and the Pixel 10a's
    // coins stopped settling there (`docs/physics-and-rendering.md`, "How
    // round a die may be").
    listOf("0", "0.5", "0.12", "-0.03", "round", "NaN").forEach { value ->
      val failure = assertFailsWith<IllegalArgumentException>(value) { HarnessRequest.edgeRoundingOf(value) }
      assertTrue("0.015" in failure.message.orEmpty(), failure.message)
    }
  }

  @Test
  fun `both ends of the range are a die a set may have`() {
    assertEquals(0.015, HarnessRequest.edgeRoundingOf("0.015"))
    assertEquals(0.06, HarnessRequest.edgeRoundingOf("0.06"))
  }

  @Test
  fun `a run can ask for its own friction and bounce, each on its own`() {
    val request =
      HarnessRequest.from(
        mapOf(
          "harness.rolls" to "10",
          "harness.friction" to "0.7",
          "harness.restitution" to "0.6",
        )::get,
      )!!
    assertEquals(0.7, request.friction)
    assertEquals(0.6, request.restitution)
    val die =
      request
        .plan()
        .dice
        .first()
        .die.material
    assertEquals(0.7, die.friction)
    assertEquals(0.6, die.restitution)
    assertEquals(DieMaterial().edgeRounding, die.edgeRounding)
    assertEquals("20d20-friction0.7-bounce0.6", request.label)
    assertEquals(DieMaterial().copy(friction = 0.8), HarnessRequest.materialOf(null, friction = 0.8))
  }

  @Test
  fun `a friction or bounce a set file could not say is refused, not clamped`() {
    val refused = listOf("harness.friction" to "0.05", "harness.friction" to "lots", "harness.restitution" to "0.9")
    refused.forEach { (key, value) ->
      assertFailsWith<IllegalArgumentException>("$key=$value") {
        HarnessRequest.from(mapOf("harness.rolls" to "10", key to value)::get)
      }
    }
    assertNull(HarnessRequest.boundedOf(" ", "friction", DieMaterial.FrictionRange))
  }
}
