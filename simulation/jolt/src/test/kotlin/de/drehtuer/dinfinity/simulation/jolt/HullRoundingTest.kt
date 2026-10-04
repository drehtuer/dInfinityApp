package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.DieMaterial
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What crosses the bridge for a die's rounding (`HullRounding`).
 *
 * The first test is the one the goldens rest on: a die whose set says nothing
 * about `edge_rounding` must hand Jolt the very floats it was handed before
 * the key existed — the 0.48 mm radius as it always was, and Jolt's own
 * `0.05f` for the corner error, which the solver used to take by default.
 */
class HullRoundingTest {
  @Test
  fun `a die that says nothing hands Jolt exactly what it always did`() {
    val rounding = HullRounding.of(DieMaterial())
    assertEquals((16.0 * 0.03 / 10.0).toFloat(), rounding.convexRadius, 0f)
    assertEquals(JOLT_DEFAULT_MAX_ERROR, rounding.maxError, 0f)
  }

  @Test
  fun `a rounder die asks for more, and lets Jolt cut its corners back further`() {
    val rounding = HullRounding.of(DieMaterial(edgeRounding = 0.12))
    assertEquals(0.192f, rounding.convexRadius, 1e-6f)
    assertEquals(0.2f, rounding.maxError, 1e-6f)
  }

  @Test
  fun `the radius is of the nominal size, clamped`() {
    val rounding = HullRounding.of(DieMaterial(sizeMm = 400.0, edgeRounding = 9.0))
    assertEquals((40.0 * 0.12 / 10.0).toFloat(), rounding.convexRadius, 1e-7f)
  }

  @Test
  fun `a centimetre is the solver's unit`() {
    assertEquals(1f, Units.mmToUnits(10.0), 0f)
    assertEquals(10.0, Units.unitsToMm(1f), 0.0)
  }

  private companion object {
    /** `ConvexHullShapeSettings::mMaxErrorConvexRadius`'s initialiser in Jolt's header. */
    const val JOLT_DEFAULT_MAX_ERROR = 0.05f
  }
}
