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
}
