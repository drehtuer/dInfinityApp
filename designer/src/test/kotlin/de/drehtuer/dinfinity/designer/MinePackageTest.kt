package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.Severity
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The package a drawing becomes (`docs/face-designer.md`, "Export details").
 *
 * Every test here ends at the same place: the real `DiceSetValidator`, over the
 * real files. A package the app built and could not install is a bug caught
 * here rather than an install failure on somebody else's phone.
 */
class MinePackageTest {
  @Test
  fun `a package drawn on every catalogue shape validates`() {
    // The claim `docs/dice-sets.md` makes about the designer: what it writes
    // is a normal dice set. Every solid in the catalogue, in one package,
    // through the same validator a download goes through.
    val drawings = DieShape.entries.map { shape -> Drawings.drawn(Drawings.die(shape), 0) }

    val set = validate(drawings)

    assertEquals(DieShape.entries.map(DieShape::id), set.dice.map(Die::id))
    assertEquals(DieShape.entries.map(DieShape::faceCount), set.dice.map { it.faces.size })
  }

  @Test
  fun `each shape on its own validates too, with the atlas it was given`() {
    DieShape.entries.forEach { shape ->
      val set = validate(listOf(Drawings.drawn(Drawings.die(shape), 0)))
      assertEquals(shape.id, "textures/${shape.id}.png", set.dice.single().texturePath)
    }
  }

  @Test
  fun `a package with an atlas raises no warning about oblong cells`() {
    // 256 pixels a cell divides into the shape's grid exactly, so the
    // validator's "does not divide into square cells" warning must never fire
    // on the app's own output (`dicesets/format`'s FileChecker).
    val result =
      DiceSetValidator.validate(
        PackageFiles.of(
          MinePackage.of(
            drawings = DieShape.entries.map { Drawings.drawn(Drawings.die(it), 0) },
            license = SetLicense.Mit.id,
            author = null,
            painter = Drawings.headers(),
          ),
        ),
      )

    val complaints = result.messages.filter { it.text.contains("square cells") }
    assertTrue("$complaints", complaints.isEmpty())
  }

  @Test
  fun `the chosen licence is what the file says`() {
    val set = validate(listOf(Drawings.drawn(Drawings.die(DieShape.Cube), 0)), license = SetLicense.PublicDomain.id)

    assertEquals("CC0-1.0", set.license)
  }

  @Test
  fun `a package nobody has chosen a licence for still says so out loud`() {
    val set = validate(listOf(Drawings.drawn(Drawings.die(DieShape.Cube), 0)), license = SetLicense.UNSPECIFIED)

    // Written down rather than left out: a missing field cannot be told from
    // one an older version of the app never wrote.
    assertEquals(SetLicense.UNSPECIFIED, set.license)
    assertFalse(SetLicense.chosen(set.license))
  }

  @Test
  fun `the author is left out rather than made up`() {
    val set = validate(listOf(Drawings.drawn(Drawings.die(DieShape.Cube), 0)), author = null)

    assertNull(set.author)
  }

  @Test
  fun `a die whose atlas would not paint keeps its labels instead`() {
    val files =
      MinePackage.of(
        drawings = listOf(Drawings.drawn(Drawings.die(DieShape.Cube), 0)),
        license = SetLicense.Mit.id,
        author = null,
        painter = AtlasPainter.NONE,
      )

    // One unlucky allocation must not cost somebody the rest of their package.
    assertEquals(setOf(DiceSetValidator.DICE_SET_FILE), files.keys)
    val set = (DiceSetValidator.validate(PackageFiles.of(files)) as ValidationResult.Valid).set
    assertNull(set.dice.single().texturePath)
  }

  @Test
  fun `a die nobody drew on is not in the package`() {
    val files =
      MinePackage.of(
        drawings = listOf(Draft(die = Drawings.die(DieShape.Cube))),
        license = SetLicense.Mit.id,
        author = null,
        painter = Drawings.headers(),
      )

    val set = (DiceSetValidator.validate(PackageFiles.of(files)) as ValidationResult.Valid).set
    assertTrue(set.dice.isEmpty())
  }

  @Test
  fun `the package is called what the folder it installs into is called`() {
    val set = validate(listOf(Drawings.drawn(Drawings.die(DieShape.Cube), 0)))

    // A folder whose `diceset.toml` gives a different id is refused
    // (`docs/dice-sets.md`, "Runtime isolation"), so the exporter has exactly
    // one name to get right.
    assertEquals(MinePackage.ID, set.id)
    assertEquals("mine", set.id)
  }

  @Test
  fun `two drawings on the same die are one die`() {
    val cube = Drawings.die(DieShape.Cube)
    val set = validate(listOf(Drawings.drawn(cube, 0), Drawings.drawn(cube, 1)))

    assertEquals(1, set.dice.size)
  }

  @Test
  fun `a set of drawn dice is only ever warned about, never rejected`() {
    // A personal set has whichever dice somebody drew and no obligation to
    // have a d12. The validator says so as a warning and installs it
    // (`docs/dice-sets.md`, "Validation").
    val result =
      DiceSetValidator.validate(
        PackageFiles.of(
          MinePackage.of(
            drawings = listOf(Drawings.drawn(Drawings.die(DieShape.Cube), 0)),
            license = SetLicense.Mit.id,
            author = "Ada",
            painter = Drawings.headers(),
          ),
        ),
      )

    assertTrue(result is ValidationResult.Valid)
    assertTrue(result.messages.all { it.severity == Severity.Warning })
  }

  private fun validate(
    drawings: List<Draft>,
    license: String = SetLicense.Mit.id,
    author: String? = "Ada",
  ): DiceSet {
    val files = MinePackage.of(drawings, license, author, Drawings.headers())
    val result = DiceSetValidator.validate(PackageFiles.of(files))
    assertTrue("$result", result is ValidationResult.Valid)
    return (result as ValidationResult.Valid).set
  }
}
