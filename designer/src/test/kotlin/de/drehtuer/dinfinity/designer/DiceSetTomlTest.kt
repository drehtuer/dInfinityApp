package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.core.model.TableLight
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableSound
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
  fun `what the dice are made of goes out as a defaults table, and comes back`() {
    // The three a person sets on the details screen, written once for the
    // whole package rather than once per die (`docs/dice-sets.md`, "Weight,
    // translucency and size, as a person sets them").
    val heavy = DieMaterial(sizeMm = 20.0, density = 2.4, translucency = 0.2)

    val text = DiceSetToml.write(DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(d6)), heavy)
    val back = read(DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(d6)), heavy)

    // Per cent in the file, a fraction in the model: the one key a set file
    // writes on a different scale from the one the renderer wants.
    assertTrue(text.contains("translucency = 20.0"))
    assertEquals(
      20.0,
      back.dice
        .single()
        .material.sizeMm,
      1e-12,
    )
    assertEquals(
      2.4,
      back.dice
        .single()
        .material.density,
      1e-12,
    )
    assertEquals(
      0.2,
      back.dice
        .single()
        .material.translucency,
      1e-12,
    )
  }

  @Test
  fun `a defaults table nobody moved off the defaults is not written at all`() {
    val text = DiceSetToml.write(DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(d6)))

    assertFalse(text.contains("[defaults]"))
  }

  @Test
  fun `only the number that moved is written`() {
    val text =
      DiceSetToml.write(
        DiceSet(id = "mine", name = "My dice", version = "1.0.0", dice = listOf(d6)),
        DieMaterial(density = 3.0),
      )

    assertTrue(text.contains("[defaults]"))
    assertTrue(text.contains("density = 3.0"))
    assertFalse(text.contains("size_mm"))
    assertFalse(text.contains("translucency"))
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

  @Test
  fun `a table is the look it was written from, once it has been read back`() {
    // A photo table, which is what puts `[[table]]` in a file the app writes.
    val look = PhotoTable.lookOf("photo-oak", "Oak table")
    val set = DiceSet(id = "mine", name = "My dice", version = "1.0.0", tables = listOf(look))

    val back = readWhole(set, "tables/photo-oak.webp" to Drawings.webp(64, 48)).tables.single()

    assertEquals("photo-oak", back.id)
    assertEquals("Oak table", back.name)
    assertEquals("tables/photo-oak.webp", back.floorTexturePath)
    assertEquals(look.floorColorArgb, back.floorColorArgb)
    assertEquals(TableLook.Tiling(1, 1), back.floorTiling)
  }

  @Test
  fun `only what differs from the reader's own defaults is written`() {
    // A key nobody wrote is a key nobody has to keep in step with `TableLook`.
    val plain = TableLook(id = "plain", name = "Plain")
    val text = DiceSetToml.write(DiceSet(id = "mine", name = "My dice", version = "1.0.0", tables = listOf(plain)))

    assertTrue("[[table]]" in text)
    assertFalse("a default colour was written out", "floor_color" in text)
    assertFalse("a default tiling was written out", "floor_tiling" in text)
    assertFalse("a default sound was written out", "sound =" in text)
    assertFalse("a default friction was written out", "friction" in text)
  }

  @Test
  fun `everything a look can say survives the trip`() {
    val loud =
      TableLook(
        id = "loud",
        name = "Loud",
        floorTexturePath = "tables/floor.png",
        floorTiling = TableLook.Tiling(3, 6),
        wallTexturePath = "tables/wall.png",
        wallTiling = TableLook.Tiling(8, 1),
        floorColorArgb = 0xFF1F5E3A.toInt(),
        wallColorArgb = 0xFF5A3A1E.toInt(),
        roughness = 0.25,
        metallic = 0.75,
        friction = 0.85,
        restitution = 0.45,
        sound = TableSound.Glass,
        light = TableLight.Cool,
      )

    // The two textures have to exist for the file checker to accept them, so
    // the package is written out whole rather than as a lone set file.
    val back =
      readWhole(
        DiceSet(id = "mine", name = "My dice", version = "1.0.0", tables = listOf(loud)),
        "tables/floor.png" to Drawings.png(64, 64),
        "tables/wall.png" to Drawings.png(64, 64),
      )

    assertEquals(loud, back.tables.single())
  }

  @Test
  fun `a decimal is written with a point, whatever the phone's language is`() {
    // A decimal comma is not TOML, and a formatter on a German phone writes one.
    val default = Locale.getDefault()
    try {
      Locale.setDefault(Locale.GERMANY)
      val look = TableLook(id = "grip", name = "Grip", friction = 0.85)
      val text = DiceSetToml.write(DiceSet(id = "mine", name = "My dice", version = "1.0.0", tables = listOf(look)))

      assertTrue("a decimal comma reached the file: $text", "friction = 0.85" in text)
    } finally {
      Locale.setDefault(default)
    }
  }

  /** `0` → `"00"`, `1` → `"10"`: a tens die's labels, which its values cannot say. */
  private fun tens(index: Int): String {
    val value = index * 10
    return if (value < 10) "00" else value.toString()
  }

  /** The same, for a set whose tables point at textures that have to be there. */
  private fun readWhole(
    set: DiceSet,
    vararg textures: Pair<String, ByteArray>,
  ): DiceSet {
    val files =
      mapOf(DiceSetValidator.DICE_SET_FILE to DiceSetToml.write(set).encodeToByteArray()) + textures.toMap()
    val result = DiceSetValidator.validate(PackageFiles.of(files))
    assertTrue("$result", result is ValidationResult.Valid)
    return (result as ValidationResult.Valid).set
  }

  /** The written file, through the real validator, as the set it describes. */
  private fun read(
    set: DiceSet,
    defaults: DieMaterial = DieMaterial(),
  ): DiceSet {
    val result = DiceSetValidator.validate(PackageFiles.ofDiceSetToml(DiceSetToml.write(set, defaults)))
    assertTrue("$result", result is ValidationResult.Valid)
    return (result as ValidationResult.Valid).set
  }
}
