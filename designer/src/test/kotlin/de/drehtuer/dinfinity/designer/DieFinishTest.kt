package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The face designer's Material menu and Edges control, as numbers
 * (`docs/face-designer.md`, "Material and edges").
 */
class DieFinishTest {
  @Test
  fun `a die nobody has said anything about is plastic, rounded as every die always was`() {
    assertEquals(MaterialPreset.Plastic, MaterialPreset.of(DieFinish.STANDARD))
    assertEquals(Roundness.Standard, Roundness.of(DieFinish.STANDARD.edgeRounding))
    assertEquals(DieMaterial().edgeRounding, Roundness.Standard.share, 0.0)
  }

  @Test
  fun `plastic is exactly the default material`() {
    val standard = DieMaterial()
    assertEquals(standard.roughness, MaterialPreset.Plastic.roughness, 0.0)
    assertEquals(standard.metallic, MaterialPreset.Plastic.metallic, 0.0)
    assertEquals(standard.translucency, MaterialPreset.Plastic.translucency, 0.0)
  }

  @Test
  fun `each preset is what it is called`() {
    assertEquals(0.25, MaterialPreset.Pearl.translucency, 0.0)
    assertEquals(0.6, MaterialPreset.Resin.translucency, 0.0)
    assertEquals(1.0, MaterialPreset.Glass.translucency, 0.0)
    assertTrue(MaterialPreset.Glass.roughness < MaterialPreset.Resin.roughness)
    assertEquals(1.0, MaterialPreset.Metal.metallic, 0.0)
    assertEquals(0.0, MaterialPreset.Metal.translucency, 0.0)
    assertTrue(MaterialPreset.Stone.roughness > MaterialPreset.Plastic.roughness)
  }

  @Test
  fun `every preset comes back as itself, whatever the rounding`() {
    MaterialPreset.entries.forEach { preset ->
      Roundness.entries.forEach { roundness ->
        val finish = DieFinish.STANDARD.madeOf(preset).rounded(roundness)
        assertEquals(preset, MaterialPreset.of(finish))
        assertEquals(roundness, Roundness.of(finish.edgeRounding))
      }
    }
  }

  @Test
  fun `no two presets are the same material`() {
    val materials = MaterialPreset.entries.map { Triple(it.roughness, it.metallic, it.translucency) }
    assertEquals(materials.size, materials.toSet().size)
  }

  @Test
  fun `a die made of none of them is custom`() {
    val brass = DieFinish.of(DieMaterial(roughness = 0.2, metallic = 0.9))
    assertNull(MaterialPreset.of(brass))
    assertNull(Roundness.of(0.05))
  }

  @Test
  fun `choosing a material keeps the rounding, and choosing the edges keeps the material`() {
    val custom = DieFinish.of(DieMaterial(roughness = 0.2, metallic = 0.9, edgeRounding = 0.05))

    val glass = custom.madeOf(MaterialPreset.Glass)
    assertEquals(0.05, glass.edgeRounding, 0.0)
    assertEquals(MaterialPreset.Glass, MaterialPreset.of(glass))

    val round = custom.rounded(Roundness.VeryRounded)
    assertEquals(0.12, round.edgeRounding, 0.0)
    assertEquals(0.9, round.metallic, 0.0)
  }

  @Test
  fun `a finish is laid over a material and leaves the rest of it alone`() {
    val heavy = DieMaterial(colorArgb = 0x11223344, sizeMm = 20.0, density = 3.0, translucency = 0.4)

    val metal =
      DieFinish.STANDARD
        .madeOf(MaterialPreset.Metal)
        .rounded(Roundness.Rounded)
        .on(heavy)

    assertEquals(0x11223344, metal.colorArgb)
    assertEquals(20.0, metal.sizeMm, 0.0)
    assertEquals(3.0, metal.density, 0.0)
    assertEquals(0.0, metal.translucency, 0.0)
    assertEquals(1.0, metal.metallic, 0.0)
    assertEquals(0.06, metal.edgeRounding, 0.0)
  }

  @Test
  fun `a finish read off a material is inside the format's limits`() {
    val wild =
      DieFinish.of(
        DieMaterial(roughness = 9.0, metallic = -1.0, translucency = Double.NaN, edgeRounding = 1.0),
      )

    assertEquals(DieFinish(1.0, 0.0, 0.0, DieMaterial.EdgeRoundingRange.endInclusive), wild)
  }

  @Test
  fun `the edge steps run from the least a set may ask for to the most`() {
    assertEquals(DieMaterial.EdgeRoundingRange.start, Roundness.Sharp.share, 0.0)
    assertEquals(DieMaterial.EdgeRoundingRange.endInclusive, Roundness.VeryRounded.share, 0.0)
    assertEquals(Roundness.entries.sortedBy(Roundness::share), Roundness.entries)
  }

  @Test
  fun `a value read back from a set file is the same step`() {
    // What a set file writes as `edge_rounding = 0.06` parses back as the
    // closest double to it, and a per cent as a fraction: neither may turn a
    // step or a preset into "Custom".
    assertEquals(Roundness.Rounded, Roundness.of("0.06".toDouble()))
    assertEquals(MaterialPreset.Resin, MaterialPreset.of(DieFinish(0.15, 0.0, "60.0".toDouble() / 100, 0.03)))
  }
}
