package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.DieMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One test per warning in `docs/dice-sets.md`, "Validation".
 *
 * A warning means the package installs and the user is told. That distinction
 * matters: a set that rolls a bit bouncier than its author meant is still a
 * set, and refusing it would be the app deciding what somebody else's dice are
 * allowed to be like (`docs/dice-sets.md`, rule 3).
 */
class ValidationWarningTest {
  @Test
  fun `a physics value outside its range is brought back and the author told`() {
    val valid = accepting(minimalToml("restitution = 9.0"))
    assertTrue(ValidationCode.Clamped in valid.codes(), "${valid.messages}")
    assertEquals(
      DieMaterial.RestitutionRange.endInclusive,
      valid.set.dice
        .single()
        .material.restitution,
    )
    assertTrue(valid.warnings.any { "9.0" in it.text }, "${valid.warnings}")
  }

  @Test
  fun `a die that is far too big to be one is brought back`() {
    val valid = accepting(minimalToml("size_mm = 400"))
    assertEquals(
      DieMaterial.SizeMmRange.endInclusive,
      valid.set.dice
        .single()
        .material.sizeMm,
    )
    assertTrue(ValidationCode.Clamped in valid.codes())
  }

  @Test
  fun `a label longer than a face can show is cut down and the author told`() {
    val valid = accepting(minimalToml("""labels = ["one", "two", "three", "four", "five", "six"]"""))
    assertTrue(ValidationCode.LabelTruncated in valid.codes(), "${valid.messages}")
    assertEquals(
      listOf("one", "two", "thre", "four", "five", "six"),
      valid.set.dice
        .single()
        .faces
        .map { it.label },
    )
  }

  @Test
  fun `a set that leaves standard dice out installs, and says which`() {
    val valid = accepting(minimalToml())
    val missing = valid.warnings.single { it.code == ValidationCode.StandardDieMissing }
    assertTrue("d20" in missing.text, missing.text)
    assertTrue("bundled set" in missing.text, missing.text)
  }

  @Test
  fun `a key this version does not know is ignored, and the author told`() {
    val valid = accepting(minimalToml("sparkles = true"))
    val unknown = valid.warnings.single { it.code == ValidationCode.UnknownKey }
    assertTrue("sparkles" in unknown.text, unknown.text)
  }

  @Test
  fun `an unknown key at the top of the file is a warning too, so the format can grow`() {
    val valid = accepting("credits = \"me\"\n\n" + minimalToml())
    assertTrue(ValidationCode.UnknownKey in valid.codes(), "${valid.messages}")
  }

  @Test
  fun `an atlas whose cells would not be square is a warning, not a refusal`() {
    // A cube's atlas is a 3x2 grid, so 300x300 gives 100-wide, 150-tall cells.
    val valid = accepting(minimalToml("texture = \"d6.png\""), "d6.png" to png(300, 300))
    assertTrue(ValidationCode.AtlasNotSquare in valid.codes(), "${valid.messages}")
    assertEquals(
      "d6.png",
      valid.set.dice
        .single()
        .texturePath,
    )
  }

  @Test
  fun `an atlas that does divide into square cells says nothing`() {
    val valid = accepting(minimalToml("texture = \"d6.png\""), "d6.png" to png(300, 200))
    assertTrue(ValidationCode.AtlasNotSquare !in valid.codes(), "${valid.messages}")
  }

  @Test
  fun `a tiling outside its range is brought back`() {
    val valid =
      accepting(minimalToml() + "\n\n[[table]]\nid = \"oak\"\nname = \"Oak\"\nfloor_tiling = [0, 99]\n")
    assertTrue(ValidationCode.Clamped in valid.codes(), "${valid.messages}")
    assertEquals(
      DiceSetLimits.TILING.first,
      valid.set.tables
        .single()
        .floorTiling.acrossShortSide,
    )
    assertEquals(
      DiceSetLimits.TILING.last,
      valid.set.tables
        .single()
        .floorTiling.acrossLongSide,
    )
  }

  @Test
  fun `a warning does not stop the set from installing`() {
    val valid = accepting(minimalToml("restitution = 9.0"))
    assertEquals("fixture", valid.set.id)
    assertEquals(1, valid.set.dice.size)
  }
}
