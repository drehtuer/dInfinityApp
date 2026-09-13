package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The numbers `docs/dice-sets.md` and `docs/tables.md` publish as the limits a
 * package is held to. They are asserted here rather than only in the validator
 * because the documents quote them: if one moves, this test says so and the
 * document has to move with it in the same pull request (`.claude/CLAUDE.md`).
 */
class ModelLimitsTest {
  @Test
  fun `a die's size is bounded at 8 and 40 millimetres`() {
    assertEquals(8.0..40.0, DieMaterial.SizeMmRange)
  }

  @Test
  fun `a die's density runs from balsa to brass`() {
    assertEquals(0.5..8.0, DieMaterial.DensityRange)
  }

  @Test
  fun `a die's restitution stops at the bounciest that still settles`() {
    assertEquals(0.0..0.8, DieMaterial.RestitutionRange)
  }

  @Test
  fun `a die's friction never reaches zero`() {
    assertEquals(0.1..1.0, DieMaterial.FrictionRange)
  }

  @Test
  fun `roughness and metallic are plain unit ranges`() {
    assertEquals(0.0..1.0, DieMaterial.UnitRange)
  }

  @Test
  fun `a table may be grippier or more slippery than a die, within reason`() {
    assertEquals(0.2..1.0, TableLook.FrictionRange)
    assertEquals(0.0..0.6, TableLook.RestitutionRange)
  }

  @Test
  fun `a texture repeats at least once and at most thirty-two times`() {
    assertEquals(1..32, TableLook.TilingRange)
  }
}
