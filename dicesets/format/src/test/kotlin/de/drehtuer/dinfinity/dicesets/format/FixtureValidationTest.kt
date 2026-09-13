package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.fixtures.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared fixtures, each rejected for the reason it was written to be
 * rejected for (`test-fixtures/`, `docs/dice-sets.md`).
 *
 * A fixture that fails for the wrong reason is worse than one that passes: it
 * looks like the validator is working when it is only unhappy.
 */
class FixtureValidationTest {
  @Test
  fun `the minimal fixture installs`() {
    val valid = accepting(Fixtures.diceSet("valid-minimal.toml"))
    assertEquals("fixture-minimal", valid.set.id)
    assertEquals(1, valid.set.dice.size)
    assertEquals("CC0-1.0", valid.set.license)
  }

  @Test
  fun `a shape outside the catalogue is refused`() {
    val rejected = rejecting(Fixtures.diceSet("invalid-unknown-shape.toml"))
    assertTrue(ValidationCode.UnknownShape in rejected.codes(), "${rejected.messages}")
    assertTrue(rejected.errors.any { "rhombic-triacontahedron" in it.text })
  }

  @Test
  fun `a mesh is refused, and says it is designed but not in this version`() {
    val rejected = rejecting(Fixtures.diceSet("invalid-mesh-not-in-v1.toml"))
    assertTrue(ValidationCode.UnknownShape in rejected.codes(), "${rejected.messages}")
    assertTrue(rejected.errors.any { "not in this version" in it.text }, "${rejected.errors}")
  }

  @Test
  fun `the wrong number of faces for the shape is refused`() {
    val rejected = rejecting(Fixtures.diceSet("invalid-face-count.toml"))
    assertTrue(ValidationCode.FaceCountMismatch in rejected.codes(), "${rejected.messages}")
    assertTrue(rejected.errors.any { "needs 6 faces, not 5" in it.text }, "${rejected.errors}")
  }

  @Test
  fun `two dice with the same id are refused`() {
    val rejected = rejecting(Fixtures.diceSet("invalid-duplicate-die-id.toml"))
    assertTrue(ValidationCode.DuplicateId in rejected.codes(), "${rejected.messages}")
  }

  @Test
  fun `a file reference that leaves the package is refused`() {
    val rejected = rejecting(Fixtures.diceSet("invalid-escaping-path.toml"))
    assertTrue(ValidationCode.BadFileReference in rejected.codes(), "${rejected.messages}")
    assertTrue(rejected.errors.any { "outside the package" in it.text }, "${rejected.errors}")
  }

  @Test
  fun `every fixture named invalid is in fact rejected`() {
    Fixtures.invalidDiceSets.forEach { name ->
      val result = DiceSetValidator.validate(PackageFiles.ofDiceSetToml(Fixtures.diceSet(name)))
      assertTrue(result is ValidationResult.Rejected, "$name installed, and should not have")
    }
  }

  @Test
  fun `every fixture named valid is in fact accepted`() {
    Fixtures.validDiceSets.forEach { name ->
      val result = DiceSetValidator.validate(PackageFiles.ofDiceSetToml(Fixtures.diceSet(name)))
      assertTrue(result is ValidationResult.Valid, "$name was rejected: ${(result as? ValidationResult.Rejected)}")
    }
  }

  @Test
  fun `a rejection points at the line that caused it`() {
    val rejected = rejecting(Fixtures.diceSet("invalid-unknown-shape.toml"))
    val shape = rejected.errors.first { it.code == ValidationCode.UnknownShape }
    assertEquals("diceset.toml", shape.file)
    assertTrue(shape.line != null && shape.line > 0, "no line for ${shape.text}")
    assertTrue(shape.toString().startsWith("diceset.toml:${shape.line}: error: "), shape.toString())
  }
}
