package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The face designer's Material menu and Edges control, as numbers
 * (`docs/face-designer.md`, "Material, colour and edges").
 */
class DieFinishTest {
  @Test
  fun `a die nobody has said anything about is plastic, rounded as every die always was`() {
    assertEquals(MaterialPreset.Plastic, MaterialPreset.of(DieFinish.STANDARD))
    assertEquals(DieMaterial().edgeRounding, DieFinish.STANDARD.edgeRounding, 0.0)
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
      listOf(0.015, 0.03, 0.065, 0.12).forEach { share ->
        val finish = DieFinish.STANDARD.madeOf(preset).rounded(share)
        assertEquals(preset, MaterialPreset.of(finish))
        assertEquals(share, finish.edgeRounding, 0.0)
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
  }

  @Test
  fun `choosing a material keeps the rounding, and choosing the edges keeps the material`() {
    val custom = DieFinish.of(DieMaterial(roughness = 0.2, metallic = 0.9, edgeRounding = 0.05))

    val glass = custom.madeOf(MaterialPreset.Glass)
    assertEquals(0.05, glass.edgeRounding, 0.0)
    assertEquals(MaterialPreset.Glass, MaterialPreset.of(glass))

    val round = custom.rounded(0.12)
    assertEquals(0.12, round.edgeRounding, 0.0)
    assertEquals(0.9, round.metallic, 0.0)
  }

  @Test
  fun `a finish is laid over a material and leaves the rest of it alone`() {
    val heavy = DieMaterial(colorArgb = 0x11223344, sizeMm = 20.0, density = 3.0, translucency = 0.4)

    val metal =
      DieFinish.STANDARD
        .madeOf(MaterialPreset.Metal)
        .rounded(0.06)
        .on(heavy)

    assertEquals("the colour is the finish's own now", DieMaterial.DEFAULT_COLOR_ARGB, metal.colorArgb)
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
  fun `the slider runs from the least a set may ask for to the most, in half per cents`() {
    assertEquals(DieMaterial.EdgeRoundingRange, EdgeRounding.RANGE)
    assertEquals(0.005, EdgeRounding.STEP, 0.0)
    // 1.5 % to 12 % in half per cents is twenty-two positions: two ends and
    // twenty between them, which is what Material's slider counts.
    assertEquals(20, EdgeRounding.BETWEEN)
  }

  @Test
  fun `a slider position lands on a step, inside the range, as the number a set file writes`() {
    assertEquals(0.06, EdgeRounding.snapped(0.0612), 0.0)
    assertEquals(0.065, EdgeRounding.snapped(0.0626), 0.0)
    assertEquals("0.06", EdgeRounding.snapped(0.06).toString())
    assertEquals(0.015, EdgeRounding.snapped(0.0), 0.0)
    assertEquals(0.12, EdgeRounding.snapped(0.5), 0.0)
    assertEquals(DieMaterial.DEFAULT_EDGE_ROUNDING, EdgeRounding.snapped(Double.NaN), 0.0)
    assertEquals("a finish is rounded through it", 0.045, DieFinish.STANDARD.rounded(0.044).edgeRounding, 0.0)
  }

  @Test
  fun `a value read back from a set file is the same material`() {
    // A per cent read back as a fraction may not turn a preset into "Custom".
    assertEquals(MaterialPreset.Resin, MaterialPreset.of(DieFinish(0.15, 0.0, "60.0".toDouble() / 100, 0.03)))
  }

  @Test
  fun `a colour is chosen whole, opaque, with numbers that read on it`() {
    val glass = DieFinish.STANDARD.madeOf(MaterialPreset.Glass).rounded(0.06)

    val navy = glass.coloured(0x001A237E)
    val bone = navy.coloured(DieMaterial.DEFAULT_COLOR_ARGB)

    assertEquals("the alpha a colour came with is not the die's", 0xFF1A237E.toInt(), navy.colorArgb)
    assertEquals(0xFFFFFFFF.toInt(), navy.numberColorArgb)
    assertEquals(DieMaterial.DEFAULT_NUMBER_COLOR_ARGB, bone.numberColorArgb)
    assertEquals(
      "the material and the edges stay",
      glass.copy(colorArgb = navy.colorArgb, numberColorArgb = navy.numberColorArgb),
      navy,
    )
  }

  @Test
  fun `a finish carries its colours onto a material and reads them back off one`() {
    val red = DieFinish.STANDARD.coloured(0xFFEC3013.toInt())

    val material = red.on(DieMaterial(sizeMm = 20.0))

    assertEquals(0xFFEC3013.toInt(), material.colorArgb)
    assertEquals(red, DieFinish.of(material))
    assertEquals(20.0, material.sizeMm, 0.0)
  }
}
