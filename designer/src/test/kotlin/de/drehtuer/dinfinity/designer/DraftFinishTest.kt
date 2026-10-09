package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.Severity
import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A die's material and roundness, from the menu to the tray: into the draft,
 * through the draft file, into the package and back out of the validator
 * (`docs/face-designer.md`, "Material, colour and edges").
 */
class DraftFinishTest {
  @get:Rule
  val folder = TemporaryFolder()

  private val d20 = Drawings.die(DieShape.Icosahedron)
  private val glass = DieFinish.STANDARD.madeOf(MaterialPreset.Glass).rounded(0.06)

  @Test
  fun `a draft shows what its die was copied as until somebody chooses`() {
    val metal = Die.standard("d6", DieShape.Cube, DieMaterial(metallic = 1.0, roughness = 0.3, edgeRounding = 0.05))

    assertEquals(DieFinish.STANDARD, Draft(d20).shownFinish)
    assertEquals(DieFinish.of(metal.material), Draft(metal).shownFinish)
    assertEquals(glass, Draft(d20, finish = glass).shownFinish)
  }

  @Test
  fun `a die with a material chosen and nothing drawn is not blank`() {
    assertTrue(Draft(d20).blank)
    assertFalse(Draft(d20, finish = glass).blank)
  }

  @Test
  fun `the finish survives the draft file`() {
    val drawn = Drawings.drawn(d20, 0).copy(finish = glass)

    val back = DraftFile.read(DraftFile.write(drawn), d20)

    assertEquals(glass, back?.finish)
    assertEquals(drawn.faces.keys, back?.faces?.keys)
  }

  @Test
  fun `a draft with nothing but a finish survives too`() {
    val back = DraftFile.read(DraftFile.write(Draft(d20, finish = glass)), d20)

    assertEquals(glass, back?.finish)
    assertTrue(back!!.faces.isEmpty())
  }

  @Test
  fun `the body colour survives the draft file and the package`() {
    val red = glass.coloured(RED)
    val back = DraftFile.read(DraftFile.write(Draft(d20, finish = red)), d20)

    assertEquals(red, back?.finish)
    val material = validate(listOf(Draft(d20, finish = red))).dice.single().material
    assertEquals(RED, material.colorArgb)
    assertEquals("numbers on a dark green are white", PaperInk.WHITE, material.numberColorArgb)
  }

  @Test
  fun `a finish written before there was a colour reads as the built-in colour`() {
    val text =
      DraftFile
        .write(Draft(d20, finish = glass))
        .replace(Regex(",?\"(number_)?color\":-?[0-9]+"), "")

    val back = DraftFile.read(text, d20)?.finish

    assertEquals(glass, back)
    assertEquals(DieMaterial.DEFAULT_COLOR_ARGB, back?.colorArgb)
  }

  @Test
  fun `a draft written before finishes existed reads as one with none`() {
    val back = DraftFile.read(DraftFile.write(Drawings.drawn(d20, 0)), d20)

    assertNull(back?.finish)
    assertFalse(DraftFile.write(Drawings.drawn(d20, 0)).contains("finish"))
  }

  @Test
  fun `a finish with a field missing or not a number is dropped, and the drawing kept`() {
    val text = DraftFile.write(Drawings.drawn(d20, 0).copy(finish = glass))
    val missing = text.replace("\"edge_rounding\"", "\"edge\"")
    val poisoned = text.replace(Regex("\"metallic\":[0-9.]+"), "\"metallic\":\"shiny\"")
    val notAnObject = text.replace(Regex("\"finish\":\\{[^}]*\\}"), "\"finish\":7")

    listOf(missing, poisoned, notAnObject).forEach { edited ->
      val back = DraftFile.read(edited, d20)
      assertNull(edited, back?.finish)
      assertEquals(edited, setOf(0), back?.faces?.keys)
    }
  }

  @Test
  fun `a finish edited past the format's limits on disk is brought back inside them`() {
    val text =
      DraftFile
        .write(
          Draft(d20, finish = glass),
        ).replace(Regex("\"edge_rounding\":[0-9.]+"), "\"edge_rounding\":5")

    assertEquals(DieMaterial.EdgeRoundingRange.endInclusive, DraftFile.read(text, d20)?.finish?.edgeRounding)
  }

  @Test
  fun `the store keeps a die whose only change is its material`() {
    val store = DraftStore(folder.newFolder("drafts"))

    store.save(Draft(d20, finish = glass))

    assertEquals(glass, store.load(d20).finish)
    assertEquals(listOf(d20.id), store.known())
  }

  @Test
  fun `a chosen finish goes into the package and comes back out of the validator`() {
    val set = validate(listOf(Drawings.drawn(d20, 0).copy(finish = glass)))
    val material = set.dice.single().material

    assertEquals(MaterialPreset.Glass, MaterialPreset.of(DieFinish.of(material)))
    assertEquals(0.06, material.edgeRounding, 0.0)
  }

  @Test
  fun `every preset and every rounding survives the package`() {
    MaterialPreset.entries.forEach { preset ->
      listOf(0.015, 0.03, 0.045, 0.06).forEach { share ->
        val finish = DieFinish.STANDARD.madeOf(preset).rounded(share)
        val material = validate(listOf(Draft(d20, finish = finish))).dice.single().material
        assertEquals("$preset $share", preset, MaterialPreset.of(DieFinish.of(material)))
        assertEquals("$preset $share", share, material.edgeRounding, 0.0)
      }
    }
  }

  @Test
  fun `a die with a material of its own keeps it when the rest of the set is made translucent`() {
    // The details screen's stepper moves `[defaults]`; a die somebody made
    // plastic on purpose stays plastic.
    val plastic = DieFinish.STANDARD.madeOf(MaterialPreset.Plastic)
    val drawings = listOf(Draft(d20, finish = plastic), Drawings.drawn(Drawings.die(DieShape.Cube), 0))
    val dice = validate(drawings, physical = DieMaterial(translucency = 0.4)).dice.associateBy(Die::id)

    assertEquals(0.0, dice.getValue("icosahedron").material.translucency, 0.0)
    assertEquals(0.4, dice.getValue("cube").material.translucency, 1e-12)
  }

  @Test
  fun `a die copied as standard carries nothing of its own, and one copied as metal carries its metal`() {
    val metal = Die.standard("metal-d6", DieShape.Cube, DieMaterial(metallic = 1.0, roughness = 0.3))
    val drawings = listOf(Drawings.drawn(d20, 0), Drawings.drawn(metal, 0))

    assertNull(MinePackage.finishOf(drawings[0]))
    assertEquals(DieFinish.of(metal.material), MinePackage.finishOf(drawings[1]))
    val dice = validate(drawings).dice.associateBy(Die::id)
    assertEquals(1.0, dice.getValue("metal-d6").material.metallic, 0.0)
    assertEquals(DieMaterial().edgeRounding, dice.getValue("icosahedron").material.edgeRounding, 0.0)
  }

  @Test
  fun `a package with no finish anywhere writes no finish keys, so every built die is as it was`() {
    val files = MinePackage.of(listOf(Drawings.drawn(d20, 0)), SetLicense.Mit.id, null, Drawings.headers())
    val toml = files.getValue(DiceSetValidator.DICE_SET_FILE).decodeToString()

    assertFalse(toml.contains("edge_rounding"))
    assertFalse(toml.contains("metallic"))
  }

  @Test
  fun `a finish written explicitly says all six keys, and the validator says nothing about them`() {
    val text =
      DiceSetToml.write(
        DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(d20)),
        finishes = mapOf(d20.id to glass),
      )
    listOf(
      "color = \"#e8dcc0\"",
      "number_color = \"#2b2b2b\"",
      "roughness = 0.05",
      "metallic = 0.0",
      "translucency = 100.0",
      "edge_rounding = 0.06",
    ).forEach {
      assertTrue(it, text.contains(it))
    }
    val result = DiceSetValidator.validate(PackageFiles.ofDiceSetToml(text))
    val about = result.messages.filter { it.code in setOf(ValidationCode.Clamped, ValidationCode.UnknownKey) }
    assertTrue("$about", about.isEmpty())
    assertTrue("${result.messages}", result.messages.none { it.severity == Severity.Error })
  }

  private fun validate(
    drawings: List<Draft>,
    physical: DieMaterial = DieMaterial(),
  ): DiceSet {
    val files = MinePackage.of(drawings, SetLicense.Mit.id, "Ada", Drawings.headers(), physical = physical)
    val result = DiceSetValidator.validate(PackageFiles.of(files))
    assertTrue("$result", result is ValidationResult.Valid)
    return (result as ValidationResult.Valid).set
  }

  private companion object {
    const val RED: Int = 0xFF1F5E3A.toInt()
  }
}
