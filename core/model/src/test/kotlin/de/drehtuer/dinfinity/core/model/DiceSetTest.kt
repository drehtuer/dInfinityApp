package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiceSetTest {
  private val d6 = Die.standard("d6", DieShape.Cube)
  private val d20 = Die.standard("d20", DieShape.Icosahedron)

  @Test
  fun `a set finds the die a formula names`() {
    val set = DiceSet(id = "brass", name = "Brass", version = "1.0.0", dice = listOf(d6, d20))
    assertEquals(d20, set.die("d20"))
  }

  @Test
  fun `a die the set does not define is absent, not an error`() {
    val set = DiceSet(id = "brass", name = "Brass", version = "1.0.0", dice = listOf(d6))
    assertNull(set.die("d12"))
  }

  @Test
  fun `a set finds the table look a pin names`() {
    val felt = TableLook(id = "felt", name = "Felt")
    val set = DiceSet(id = "brass", name = "Brass", version = "1.0.0", tables = listOf(felt))
    assertEquals(felt, set.table("felt"))
    assertNull(set.table("oak"))
  }

  @Test
  fun `a package may carry only tables`() {
    val pack = DiceSet(id = "tables", name = "Tables", version = "1.0.0", tables = listOf(TableLook("oak", "Oak")))
    assertTrue(pack.dice.isEmpty())
    assertEquals(1, pack.tables.size)
  }

  @Test
  fun `two dice with the same id cannot be one set`() {
    val clash =
      assertFailsWith<IllegalArgumentException> {
        DiceSet(id = "brass", name = "Brass", version = "1.0.0", dice = listOf(d6, d6))
      }
    assertTrue("same id" in clash.message.orEmpty(), clash.message.orEmpty())
  }

  @Test
  fun `two tables with the same id cannot be one package`() {
    assertFailsWith<IllegalArgumentException> {
      DiceSet(
        id = "brass",
        name = "Brass",
        version = "1.0.0",
        tables = listOf(TableLook("felt", "Felt"), TableLook("felt", "Also felt")),
      )
    }
  }

  @Test
  fun `the bundled and the personal package have the ids the rest of the app expects`() {
    assertEquals("builtin", DiceSet.BUILTIN_ID)
    assertEquals("mine", DiceSet.PERSONAL_ID)
  }

  @Test
  fun `a set id is a slug of three to forty characters`() {
    assertTrue(DiceSet.IdPattern.matches("brass-and-bone"))
    assertTrue(DiceSet.IdPattern.matches("abc"))
    assertTrue(DiceSet.IdPattern.matches("a".repeat(40)))
  }

  @Test
  fun `a set id that is too short, too long or not a slug is not one`() {
    assertTrue(!DiceSet.IdPattern.matches("ab"))
    assertTrue(!DiceSet.IdPattern.matches("a".repeat(41)))
    assertTrue(!DiceSet.IdPattern.matches("Brass"))
    assertTrue(!DiceSet.IdPattern.matches("../etc"))
    assertTrue(!DiceSet.IdPattern.matches("-brass"))
  }

  @Test
  fun `the standard die ids are the ones plain notation resolves`() {
    assertEquals(
      listOf("d2", "d4", "d6", "d8", "d10", "d10-tens", "d12", "d18", "d20", "df"),
      DiceSet.StandardDieIds,
    )
  }

  @Test
  fun `the displayed fields are carried through`() {
    val set =
      DiceSet(
        id = "brass",
        name = "Brass & Bone",
        version = "1.2.0",
        author = "Ada Example",
        license = "CC-BY-4.0",
        description = "Brass numerals.",
        homepage = "https://example.org/brass",
      )
    assertEquals("Ada Example", set.author)
    assertEquals("CC-BY-4.0", set.license)
    assertEquals("Brass numerals.", set.description)
    assertEquals("https://example.org/brass", set.homepage)
  }

  @Test
  fun `the grid an atlas is cut into is the grid of the die that wears it`() {
    val set =
      DiceSet(
        id = "brass",
        name = "Brass",
        version = "1.0.0",
        dice =
          listOf(
            Die.standard("d6", DieShape.Cube).copy(texturePath = "textures/d6.png"),
            Die.standard("d20", DieShape.Icosahedron).copy(texturePath = "textures/d20.png"),
            Die.standard("d8", DieShape.Octahedron),
          ),
      )

    assertEquals(6, set.facesForTexture("textures/d6.png"))
    assertEquals(20, set.facesForTexture("textures/d20.png"))
  }

  @Test
  fun `an atlas no die wears is cut into no grid at all`() {
    val set =
      DiceSet(
        id = "brass",
        name = "Brass",
        version = "1.0.0",
        dice = listOf(Die.standard("d6", DieShape.Cube)),
      )

    assertNull(set.facesForTexture("textures/spare.png"))
  }
}
