package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The set file the app writes (`docs/dice-sets.md`, "`diceset.toml`").
 *
 * Written by hand, so the thing worth asserting is that it is read back by the
 * real parser as the set that went in. Every test here that says "and it
 * validates" is the same claim the export makes at run time: the app's own
 * output is not a privileged path.
 */
class DiceSetTomlTest {
  private val d6 = Die.standard(id = "d6", shape = DieShape.Cube)

  @Test
  fun `a set is the set it was written from, once it has been read back`() {
    val set = DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(d6))

    val back = read(set)

    assertEquals("mine", back.id)
    assertEquals("My dice", back.name)
    assertEquals("1.0.0", back.version)
    assertEquals(listOf("d6"), back.dice.map(Die::id))
    assertEquals(d6.values(), back.dice.single().values())
  }

  @Test
  fun `the author and the licence are written where a reader looks for them`() {
    val set =
      DiceSet(
        id = "mine",
        name = "My dice",
        version = "1.0.0",
        author = "Ada",
        license = SetLicense.ShareAlike.id,
        description = "Dice",
        dice = listOf(d6),
      )

    val back = read(set)

    assertEquals("Ada", back.author)
    assertEquals("CC-BY-SA-4.0", back.license)
    assertEquals("Dice", back.description)
  }

  @Test
  fun `a field nobody set is not written, so there is nothing to keep true`() {
    val text = DiceSetToml.write(DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(d6)))

    assertFalse(text.contains("author"))
    assertFalse(text.contains("license"))
    assertFalse(text.contains("description"))
    // `labels` repeats the face values when nobody changed them, and `read`
    // repeats the catalogue: two lines that could only ever go out of step
    // with what is above them.
    assertFalse(text.contains("labels"))
    assertFalse(text.contains("read ="))
  }

  @Test
  fun `a label a value cannot say is written down`() {
    val tens =
      Die(
        id = "d10-tens",
        shape = DieShape.PentagonalTrapezohedron,
        faces = List(10) { Face(index = it, value = it * 10, label = tens(it)) },
      )

    val back = read(DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(tens)))

    val faces = back.dice.single().faces

    assertEquals("00", faces.first().label)
  }

  @Test
  fun `a die read from a corner says so, because that is not what a coin does`() {
    val faceUpTetrahedron =
      Die(
        id = "flat-d4",
        shape = DieShape.Tetrahedron,
        faces = List(4) { Face.labelled(index = it, value = it + 1) },
        read = FaceRead.FaceUp,
      )

    // A tetrahedron is read vertex-up by nature, so a set that reads one
    // face-up has to say so or the file means something else.
    val back = read(DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(faceUpTetrahedron)))

    assertEquals(FaceRead.FaceUp, back.dice.single().read)
  }

  @Test
  fun `an atlas is pointed at by a relative path inside the package`() {
    val drawn = d6.copy(texturePath = DiceSetToml.texturePathOf("d6"))
    val text = DiceSetToml.write(DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(drawn)))

    assertTrue(text.contains("""texture = "textures/d6.png""""))
  }

  @Test
  fun `a name with a quote in it does not end the string it is in`() {
    val set =
      DiceSet(
        id = "mine",
        name = """The "good" dice""",
        version = "1.0.0",
        author = """back\slash""",
        dice = listOf(d6),
      )

    val back = read(set)

    assertEquals("""The "good" dice""", back.name)
    assertEquals("""back\slash""", back.author)
  }

  @Test
  fun `a newline typed into a name does not break the file in two`() {
    // A device's user name reaches this, and anything at all can be in one. A
    // raw newline inside a quoted TOML string is a syntax error, which would
    // be this object handing the validator a file it wrote itself and cannot
    // read back.
    val set = DiceSet(id = "mine", name = "one\ntwo\tthree", version = "1.0.0", dice = listOf(d6))

    assertEquals("one\ntwo\tthree", read(set).name)
  }

  /** `0` → `"00"`, `1` → `"10"`: a tens die's labels, which its values cannot say. */
  private fun tens(index: Int): String {
    val value = index * 10
    return if (value < 10) "00" else value.toString()
  }

  /** The written file, through the real validator, as the set it describes. */
  private fun read(set: DiceSet): DiceSet {
    val result = DiceSetValidator.validate(PackageFiles.ofDiceSetToml(DiceSetToml.write(set)))
    assertTrue("$result", result is ValidationResult.Valid)
    return (result as ValidationResult.Valid).set
  }
}
