package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.core.model.TableLight
import de.drehtuer.dinfinity.core.model.TableSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a package that passes actually turns into. */
class DiceSetReadingTest {
  private val full =
    """
    format = 1

    [set]
    id = "brass-and-bone"
    name = "Brass & Bone"
    version = "1.2.0"
    author = "Ada Example"
    license = "CC-BY-4.0"
    description = "Brass numerals on bone-coloured resin."
    homepage = "https://github.com/ada/brass-and-bone"

    [defaults]
    color = "#e8dcc0"
    number_color = "#8a6d1e"
    roughness = 0.35
    size_mm = 16
    density = 1.2

    [[die]]
    id = "d6"
    shape = "cube"
    faces = [1, 2, 3, 4, 5, 6]

    [[die]]
    id = "d20"
    shape = "icosahedron"
    faces = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]
    color = "#2b2b2b"

    [[die]]
    id = "d4"
    shape = "tetrahedron"
    read = "vertex-up"
    faces = [1, 2, 3, 4]

    [[die]]
    id = "d10-tens"
    shape = "pentagonal-trapezohedron"
    faces = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90]
    labels = ["00", "10", "20", "30", "40", "50", "60", "70", "80", "90"]

    [[table]]
    id = "bone-felt"
    name = "Bone felt"
    floor_tiling = [3, 6]
    floor_color = "#d9cbb0"
    wall_color = "#3a2a18"
    sound = "felt"
    light = "warm"
    """.trimIndent()

  @Test
  fun `the set's own details are read`() {
    val set = accepting(full).set
    assertEquals("brass-and-bone", set.id)
    assertEquals("Brass & Bone", set.name)
    assertEquals("1.2.0", set.version)
    assertEquals("Ada Example", set.author)
    assertEquals("CC-BY-4.0", set.license)
    assertEquals("Brass numerals on bone-coloured resin.", set.description)
    assertEquals("https://github.com/ada/brass-and-bone", set.homepage)
  }

  @Test
  fun `every die is read, in file order`() {
    assertEquals(listOf("d6", "d20", "d4", "d10-tens"), accepting(full).set.dice.map { it.id })
  }

  @Test
  fun `a die takes its shape and its faces`() {
    val d20 = accepting(full).set.die("d20")!!
    assertEquals(DieShape.Icosahedron, d20.shape)
    assertEquals((1..20).toList(), d20.values())
  }

  @Test
  fun `a d4 declares that it is read from a vertex`() {
    assertEquals(FaceRead.VertexUp, accepting(full).set.die("d4")!!.read)
  }

  @Test
  fun `labels are used where they are given, and the face value where they are not`() {
    assertEquals(
      listOf("00", "10", "20", "30", "40", "50", "60", "70", "80", "90"),
      accepting(full)
        .set
        .die("d10-tens")!!
        .faces
        .map { it.label },
    )
    assertEquals(
      listOf("1", "2", "3", "4", "5", "6"),
      accepting(full)
        .set
        .die("d6")!!
        .faces
        .map { it.label },
    )
  }

  @Test
  fun `defaults apply to every die, and a per-die override beats them`() {
    val set = accepting(full).set
    assertEquals(0xFFE8DCC0.toInt(), set.die("d6")!!.material.colorArgb)
    assertEquals(0xFF2B2B2B.toInt(), set.die("d20")!!.material.colorArgb)
    assertEquals(0.35, set.die("d20")!!.material.roughness)
  }

  @Test
  fun `a six-digit colour is opaque`() {
    assertEquals(
      0xFF.toInt() shl 24,
      accepting(full)
        .set
        .die("d6")!!
        .material.colorArgb and (0xFF shl 24),
    )
  }

  @Test
  fun `an eight-digit colour keeps its alpha`() {
    val set = accepting(minimalToml("color = \"#80112233\"")).set
    assertEquals(
      0x80112233.toInt(),
      set.dice
        .single()
        .material.colorArgb,
    )
  }

  @Test
  fun `a table look is read with its tiling and its presets`() {
    val look = accepting(full).set.tables.single()
    assertEquals("bone-felt", look.id)
    assertEquals("Bone felt", look.name)
    assertEquals(3, look.floorTiling.acrossShortSide)
    assertEquals(6, look.floorTiling.acrossLongSide)
    assertEquals(0xFFD9CBB0.toInt(), look.floorColorArgb)
    assertEquals(TableSound.Felt, look.sound)
    assertEquals(TableLight.Warm, look.light)
    assertNull(look.floorTexturePath)
  }

  @Test
  fun `a package may carry only tables`() {
    val valid =
      accepting(
        "format = 1\n\n[set]\nid = \"table-pack\"\nname = \"Tables\"\nversion = \"1.0.0\"\n" +
          "\n[[table]]\nid = \"slate\"\nname = \"Slate\"\n",
      )
    assertTrue(valid.set.dice.isEmpty())
    assertEquals(1, valid.set.tables.size)
    assertTrue(ValidationCode.StandardDieMissing !in valid.codes(), "a table pack is not missing dice")
  }

  @Test
  fun `a package with nothing optional set still reads`() {
    val set = accepting(minimalToml()).set
    assertNull(set.author)
    assertNull(set.license)
    assertNull(set.homepage)
    assertNull(set.dice.single().texturePath)
  }
}
