package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.TableLight
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableSound
import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a photograph becomes once it is a table (`docs/tables.md`, "Your own
 * photo").
 *
 * The ids matter more than they look: one is written into a TOML file, becomes
 * a path inside a package and is compared against every other table's, so the
 * slug rule is checked against the one the validator itself applies.
 */
class PhotoTableTest {
  @Test
  fun `an id is a slug, prefixed so it cannot land on a drawn set's table`() {
    val id = PhotoTable.idOf("Green Felt", taken = emptySet())

    assertEquals("photo-green-felt", id)
    assertTrue("the id is not the slug the format allows", SLUG.matches(id))
    assertTrue(id.length in DiceSetLimits.TABLE_ID_LENGTH)
  }

  @Test
  fun `punctuation and accents become hyphens rather than a refusal`() {
    assertEquals("photo-ada-s-oak-table", PhotoTable.idOf("Ada's  oak table!!", taken = emptySet()))
    assertTrue(SLUG.matches(PhotoTable.idOf("café @ noon", taken = emptySet())))
  }

  @Test
  fun `a name with nothing a slug can use still gets an id`() {
    // Refusing somebody's photo over the alphabet they named it in would be a
    // poor reason.
    assertEquals("photo-photo", PhotoTable.idOf("!!!", taken = emptySet()))
    assertEquals("photo-photo", PhotoTable.idOf("漢字", taken = emptySet()))
  }

  @Test
  fun `a taken id is numbered rather than reused`() {
    val first = PhotoTable.idOf("Oak", taken = emptySet())
    val second = PhotoTable.idOf("Oak", taken = setOf(first))
    val third = PhotoTable.idOf("Oak", taken = setOf(first, second))

    assertEquals("photo-oak", first)
    assertEquals("photo-oak-2", second)
    assertEquals("photo-oak-3", third)
  }

  @Test
  fun `a very long name still makes an id the format accepts`() {
    val long = "a".repeat(200)
    val id = PhotoTable.idOf(long, taken = emptySet())
    val next = PhotoTable.idOf(long, taken = setOf(id))

    assertTrue("the id is longer than a table id may be", id.length in DiceSetLimits.TABLE_ID_LENGTH)
    assertTrue("the numbered id is longer than a table id may be", next.length in DiceSetLimits.TABLE_ID_LENGTH)
    assertTrue(SLUG.matches(id))
    assertTrue(SLUG.matches(next))
    assertNotEquals(id, next)
  }

  @Test
  fun `the texture goes in the package's own tables folder`() {
    assertEquals("tables/photo-oak.webp", PhotoTable.texturePathOf("photo-oak"))
    assertTrue(PhotoTable.EXTENSION in DiceSetLimits.IMAGE_EXTENSIONS)
  }

  @Test
  fun `a photo does not repeat across the floor, and does not change the physics`() {
    val look = PhotoTable.lookOf("photo-oak", "Oak")
    val untouched = TableLook(id = "photo-oak", name = "Oak")

    assertEquals(TableLook.Tiling(1, 1), look.floorTiling)
    assertEquals("tables/photo-oak.webp", look.floorTexturePath)
    assertEquals(TableSound.Felt, look.sound)
    assertEquals(TableLight.Neutral, look.light)
    assertEquals(untouched.friction, look.friction, 0.0)
    assertEquals(untouched.restitution, look.restitution, 0.0)
    assertEquals(untouched.roughness, look.roughness, 0.0)
    assertEquals("the picture is tinted by the floor colour", WHITE, look.floorColorArgb)
  }

  @Test
  fun `a name is tidied rather than taken as typed`() {
    assertEquals("Oak table", PhotoTable.nameOf("  Oak   table  "))
    assertEquals("Oak table", PhotoTable.nameOf("Oak\ttable"))
    assertEquals(PhotoTable.MAX_NAME_LENGTH, PhotoTable.nameOf("b".repeat(100)).length)
    assertTrue(PhotoTable.named("Oak"))
    assertFalse(PhotoTable.named("   "))
    assertFalse(PhotoTable.named(""))
  }

  @Test
  fun `a control character in a name does not reach the file`() {
    val tidied = PhotoTable.nameOf("Oak\u0007 table\u0000")

    assertEquals("Oak table", tidied)
  }

  @Test
  fun `the suggestion is a name rather than a file name`() {
    assertEquals("Oak table 02", PhotoTable.suggestionFrom("oak_table-02.jpg"))
    assertEquals("Felt", PhotoTable.suggestionFrom("/storage/emulated/0/Pictures/felt.png"))
    assertEquals("Felt", PhotoTable.suggestionFrom("C:\\Photos\\felt.webp"))
  }

  @Test
  fun `a file with nothing in its name suggests nothing at all`() {
    // An empty field is better than a table called "-".
    assertEquals("", PhotoTable.suggestionFrom("___.jpg"))
    assertEquals("", PhotoTable.suggestionFrom(""))
  }

  @Test
  fun `a file with no extension keeps its whole name`() {
    assertEquals("Felt", PhotoTable.suggestionFrom("felt"))
  }

  @Test
  fun `how many photos are kept is derived from the package's own texture budget`() {
    assertEquals(
      (DiceSetLimits.MAX_PACKAGE_TEXTURE_BYTES / DiceSetLimits.MAX_TEXTURE_BYTES).toInt(),
      PhotoTable.MAX_PHOTOS,
    )
    assertTrue("no photo would fit at all", PhotoTable.MAX_PHOTOS >= 1)
  }

  @Test
  fun `a photo is compared by its bytes rather than by its array`() {
    // Two readings of the same photo off the same disk are the same photo.
    val one = TablePhoto("photo-oak", "Oak", byteArrayOf(1, 2, 3))
    val same = TablePhoto("photo-oak", "Oak", byteArrayOf(1, 2, 3))
    val other = TablePhoto("photo-oak", "Oak", byteArrayOf(1, 2, 4))

    assertEquals(one, same)
    assertEquals(one, one)
    assertEquals(one.hashCode(), same.hashCode())
    assertNotEquals(one, other)
    assertFalse("a photo equalled a string", one.equals("not a photo"))
  }

  private companion object {
    val SLUG = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?")
    const val WHITE = 0xFFFFFFFF.toInt()
  }
}
