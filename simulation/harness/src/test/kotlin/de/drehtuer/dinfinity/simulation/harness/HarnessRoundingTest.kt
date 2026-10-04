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
 * `docs/architecture.md`, decision 94).
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
            HarnessRequest.EDGE_ROUNDING -> " 0.12 "
            else -> null
          }
        },
      )

    assertEquals(0.12, request.edgeRounding)
    assertTrue(request.plan().dice.all { it.die.material.edgeRounding == 0.12 })
    // Named for it, so the run's files do not overwrite the default run's.
    assertEquals("20d20-round0.12", request.label)
  }

  @Test
  fun `a rounding outside what a set file may say is refused, with the range`() {
    listOf("0", "0.5", "-0.03", "round", "NaN").forEach { value ->
      val failure = assertFailsWith<IllegalArgumentException>(value) { HarnessRequest.edgeRoundingOf(value) }
      assertTrue("0.015" in failure.message.orEmpty(), failure.message)
    }
  }

  @Test
  fun `both ends of the range are a die a set may have`() {
    assertEquals(0.015, HarnessRequest.edgeRoundingOf("0.015"))
    assertEquals(0.12, HarnessRequest.edgeRoundingOf("0.12"))
  }
}
