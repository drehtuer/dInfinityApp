package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.DieMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `edge_rounding`, the one physics key a set file gained with the face
 * designer's roundness control (`docs/dice-sets.md`; `docs/architecture.md`,
 * decision 94).
 *
 * It is the solver's convex radius, so it is held to the rules every physics
 * value is: clamped with a warning when it is out of range, refused when it is
 * not a number at all, and — above everything — absent means exactly what it
 * meant before the key existed.
 */
class EdgeRoundingTest {
  private fun roundingOf(body: String): Double =
    accepting(minimalToml(body))
      .set.dice
      .single()
      .material.edgeRounding

  @Test
  fun `a set that does not say is rounded exactly as every set was before the key existed`() {
    assertEquals(DieMaterial.DEFAULT_EDGE_ROUNDING, roundingOf(""))
    assertTrue(accepting(minimalToml()).messages.none { it.code == ValidationCode.UnknownKey })
  }

  @Test
  fun `a die may be rounder than its set`() {
    assertEquals(0.06, roundingOf("edge_rounding = 0.06"), 1e-12)
  }

  @Test
  fun `the set's defaults reach every die that does not say otherwise`() {
    val toml =
      minimalToml()
        .replace("[[die]]", "[defaults]\nedge_rounding = 0.05\n\n[[die]]")
    assertEquals(
      0.05,
      accepting(toml)
        .set.dice
        .single()
        .material.edgeRounding,
      1e-12,
    )
  }

  @Test
  fun `a whole number is a number`() {
    // `edge_rounding = 0` is a value an author may well write, and it is
    // clamped rather than refused for being an integer.
    val result = accepting(minimalToml("edge_rounding = 0"))
    assertEquals(
      DieMaterial.EdgeRoundingRange.start,
      result.set.dice
        .single()
        .material.edgeRounding,
    )
    assertTrue(ValidationCode.Clamped in result.codes(), "${result.messages}")
  }

  @Test
  fun `a rounding past the limit is brought back, and the author told`() {
    val result = accepting(minimalToml("edge_rounding = 0.5"))
    assertEquals(
      DieMaterial.EdgeRoundingRange.endInclusive,
      result.set.dice
        .single()
        .material.edgeRounding,
    )
    val warning = result.messages.single { it.code == ValidationCode.Clamped }
    assertTrue("edge_rounding" in warning.text, warning.text)
  }

  @Test
  fun `a negative rounding is brought up to the least a die may have`() {
    assertEquals(DieMaterial.EdgeRoundingRange.start, roundingOf("edge_rounding = -3"))
  }

  @Test
  fun `a rounding that is not a number is refused`() {
    listOf("nan", "inf", "-inf").forEach { value ->
      val rejected = rejecting(minimalToml("edge_rounding = $value"))
      assertTrue(ValidationCode.NotFinite in rejected.codes(), "$value: ${rejected.messages}")
    }
  }

  @Test
  fun `a rounding written as text is refused`() {
    val rejected = rejecting(minimalToml("edge_rounding = \"round\""))
    assertTrue(ValidationCode.WrongType in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a rounding that is a table is refused rather than read`() {
    val rejected = rejecting(minimalToml("edge_rounding = { mm = 2 }"))
    assertTrue(ValidationCode.WrongType in rejected.codes(), "${rejected.messages}")
  }
}
