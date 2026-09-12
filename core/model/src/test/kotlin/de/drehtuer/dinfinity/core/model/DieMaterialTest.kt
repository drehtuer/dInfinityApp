package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DieMaterialTest {
  @Test
  fun `the default die is a 16 mm acrylic one`() {
    val material = DieMaterial()
    assertEquals(16.0, material.sizeMm)
    assertEquals(1.2, material.density)
  }

  @Test
  fun `the bounding radius is half the size, which is what capacity sums`() {
    assertEquals(8.0, DieMaterial(sizeMm = 16.0).boundingRadiusMm)
  }

  @Test
  fun `a value already inside its range is left alone`() {
    val material = DieMaterial(roughness = 0.4, sizeMm = 20.0, friction = 0.7)
    assertEquals(material, material.clampedToLimits())
  }

  @Test
  fun `an oversized die is clamped down to the largest a die may be`() {
    assertEquals(40.0, DieMaterial(sizeMm = 400.0).clampedToLimits().sizeMm)
  }

  @Test
  fun `a die smaller than the minimum is clamped up`() {
    assertEquals(8.0, DieMaterial(sizeMm = 0.5).clampedToLimits().sizeMm)
  }

  @Test
  fun `a frictionless die is clamped, because dice are not ball bearings`() {
    assertEquals(0.1, DieMaterial(friction = 0.0).clampedToLimits().friction)
  }

  @Test
  fun `a die that would never settle is clamped to the bounciest that does`() {
    assertEquals(0.8, DieMaterial(restitution = 5.0).clampedToLimits().restitution)
  }

  @Test
  fun `density, roughness and metallic are clamped too`() {
    val clamped = DieMaterial(density = 99.0, roughness = 4.0, metallic = -1.0).clampedToLimits()
    assertEquals(8.0, clamped.density)
    assertEquals(1.0, clamped.roughness)
    assertEquals(0.0, clamped.metallic)
  }

  @Test
  fun `a non-finite value falls back to the default rather than reaching the solver`() {
    val poisoned =
      DieMaterial(
        roughness = Double.NaN,
        metallic = Double.POSITIVE_INFINITY,
        sizeMm = Double.NaN,
        density = Double.NEGATIVE_INFINITY,
        restitution = Double.NaN,
        friction = Double.NaN,
      ).clampedToLimits()
    assertEquals(DieMaterial(), poisoned)
    assertTrue(poisoned.sizeMm.isFinite())
  }

  @Test
  fun `colours survive clamping untouched`() {
    val painted = DieMaterial(colorArgb = 0x11223344, numberColorArgb = 0x55667788)
    assertEquals(painted.colorArgb, painted.clampedToLimits().colorArgb)
    assertEquals(painted.numberColorArgb, painted.clampedToLimits().numberColorArgb)
  }

  @Test
  fun `clamp leaves a finite value inside its range alone`() {
    assertEquals(0.5, DieMaterial.clamp(0.5, DieMaterial.UnitRange, 0.0))
  }
}
