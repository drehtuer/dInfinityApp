package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieMaterial
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The radius the solver rounds a die by, which the renderer draws it with.
 *
 * Pinned because it moves every roll: a different radius is a different die
 * under every golden and every fairness figure (`docs/physics-and-rendering.md`,
 * "Dice bodies").
 */
class HullMarginTest {
  @Test
  fun `a 16 mm die asks for just under half a millimetre`() {
    assertEquals(0.48, HullMargin.requestedMm(DieMaterial(sizeMm = 16.0)), 1e-12)
  }

  @Test
  fun `the radius is a share of the size, so a bigger die asks for more`() {
    assertEquals(0.9, HullMargin.requestedMm(DieMaterial(sizeMm = 30.0)), 1e-12)
  }

  @Test
  fun `a size outside the set file's limits asks for what the clamped size would`() {
    val wild = DieMaterial(sizeMm = 10_000.0)

    assertEquals(wild.clampedToLimits().sizeMm * HullMargin.SHARE, HullMargin.requestedMm(wild), 1e-12)
  }

  @Test
  fun `no corner of the solver's die stands more than half a millimetre inside the sharp one`() {
    assertEquals(0.5, HullMargin.MAX_ERROR_MM, 0.0)
  }

  @Test
  fun `a die that does not say how round it is asks for exactly what every die asked for before`() {
    // Bit for bit, not to a tolerance: the goldens replay the solver, and a
    // radius a rounding error away is a different roll.
    val material = DieMaterial()
    assertEquals(material.clampedToLimits().sizeMm * 0.03, HullMargin.requestedMm(material), 0.0)
    assertEquals(HullMargin.MAX_ERROR_MM, HullMargin.maxErrorMm(material), 0.0)
    assertEquals(0.03, HullMargin.SHARE, 0.0)
  }

  @Test
  fun `each die asks for its own rounding`() {
    assertEquals(0.96, HullMargin.requestedMm(DieMaterial(sizeMm = 16.0, edgeRounding = 0.06)), 1e-12)
    assertEquals(1.92, HullMargin.requestedMm(DieMaterial(sizeMm = 16.0, edgeRounding = 0.12)), 1e-12)
  }

  @Test
  fun `the error a corner may have grows with the rounding asked for`() {
    assertEquals(1.0, HullMargin.maxErrorMm(DieMaterial(edgeRounding = 0.06)), 1e-12)
    assertEquals(2.0, HullMargin.maxErrorMm(DieMaterial(edgeRounding = 0.12)), 1e-12)
    assertEquals(0.25, HullMargin.maxErrorMm(DieMaterial(edgeRounding = 0.015)), 1e-12)
  }

  @Test
  fun `a rounding outside the set file's limits asks for what the clamped one would`() {
    val wild = DieMaterial(edgeRounding = 3.0)
    assertEquals(16.0 * DieMaterial.EdgeRoundingRange.endInclusive, HullMargin.requestedMm(wild), 1e-12)
    assertEquals(2.0, HullMargin.maxErrorMm(wild), 1e-12)
  }
}
