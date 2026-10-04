package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.TableColorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A table's pictures, held to the rules a die's atlas is held to
 * (`docs/tables.md`, "Textures"; `docs/dice-sets.md`, "Textures").
 *
 * Every one of them is a file a stranger wrote, named by a path a stranger
 * wrote, so each key gets the same path check, size cap and header read before
 * anything decodes it — and a table can only ever name its own package's files.
 */
class TableTexturesTest {
  @Test
  fun `every picture a table names is read, with how big a copy of it is`() {
    val look = accepting(textured, *pictures).set.tables.single()

    assertEquals("tables/felt.webp", look.floorTexturePath)
    assertEquals("tables/felt-normal.png", look.floorNormalPath)
    assertEquals("tables/felt-roughness.png", look.floorRoughnessPath)
    assertEquals(80.0, look.floorTileMm)
    assertEquals("tables/oak.png", look.wallTexturePath)
    assertEquals("tables/oak-normal.png", look.wallNormalPath)
    assertEquals("tables/oak-roughness.png", look.wallRoughnessPath)
    assertEquals(300.0, look.wallTileMm)
    assertEquals(TableColorMode.Average, look.colorMode)
  }

  @Test
  fun `a look knows which package it came from, and the file cannot say otherwise`() {
    // `package_id` is not a key: a file that writes one is warned about and
    // ignored, and the package the look was read out of is what it gets.
    val result = accepting(textured.replace(TILE_LINE, "$TILE_LINE\npackage_id = \"builtin\""), *pictures)

    val look = result.set.tables.single()
    assertEquals("fixture", look.packageId)
    assertTrue(ValidationCode.UnknownKey in result.codes())
  }

  @Test
  fun `a look with no pictures says so`() {
    val look = accepting(minimalToml() + "\n\n[[table]]\nid = \"slate\"\nname = \"Slate\"\n").set.tables.single()

    assertNull(look.floorNormalPath)
    assertNull(look.floorTileMm)
    assertFalse(look.textured)
  }

  @Test
  fun `a path out of the package is refused, whichever map names it`() {
    MAPS.forEach { key ->
      listOf("../../etc/passwd.png", "/etc/passwd.png", "tables/../../x.png", "tables/run.sh").forEach { hostile ->
        val refused = rejecting(table("$key = \"$hostile\""))
        assertTrue(ValidationCode.BadFileReference in refused.codes(), "$key = $hostile was accepted")
      }
    }
  }

  @Test
  fun `a picture the package does not have is refused`() {
    MAPS.forEach { key ->
      val refused = rejecting(table("$key = \"tables/gone.png\""))
      assertTrue(ValidationCode.ReferencedFileMissing in refused.codes(), key)
    }
  }

  @Test
  fun `a picture too big to decode safely is refused from its header`() {
    val huge = DiceSetLimits.MAX_TEXTURE_PIXELS + 1
    MAPS.forEach { key ->
      val refused = rejecting(table("$key = \"tables/huge.png\""), "tables/huge.png" to png(huge, 8))
      assertTrue(ValidationCode.TextureTooLarge in refused.codes(), key)
    }
  }

  @Test
  fun `a file that is not a picture is refused, whatever it is called`() {
    MAPS.forEach { key ->
      val refused = rejecting(table("$key = \"tables/fake.png\""), "tables/fake.png" to "not a png".toByteArray())
      assertTrue(ValidationCode.TextureUnreadable in refused.codes(), key)
    }
  }

  @Test
  fun `a tile size outside its range is brought back, and says so`() {
    val result = accepting(table("floor_tile_mm = 1\nwall_tile_mm = 50000"))
    val look = result.set.tables.single()

    assertEquals(DiceSetLimits.TILE_MM.start, look.floorTileMm)
    assertEquals(DiceSetLimits.TILE_MM.endInclusive, look.wallTileMm)
    assertTrue(ValidationCode.Clamped in result.codes())
  }

  @Test
  fun `a look that names no colour mode multiplies, and one that names a stranger one is refused`() {
    val plain = accepting(table("")).set.tables.single()
    assertEquals(TableColorMode.Multiply, plain.colorMode)
    val refused = rejecting(table("color_mode = \"screen\""))
    assertTrue(ValidationCode.UnknownPreset in refused.codes())
  }

  @Test
  fun `a tile size that is not a number is refused`() {
    val refused = rejecting(table("floor_tile_mm = \"eighty\""))
    assertTrue(ValidationCode.WrongType in refused.codes())
  }

  private fun table(lines: String): String = minimalToml() + "\n\n[[table]]\nid = \"felt\"\nname = \"Felt\"\n$lines\n"

  private companion object {
    const val TILE_LINE = "floor_tile_mm = 80"

    val MAPS =
      listOf(
        "floor_texture",
        "floor_normal",
        "floor_roughness",
        "wall_texture",
        "wall_normal",
        "wall_roughness",
      )

    val textured =
      minimalToml() +
        """


        [[table]]
        id = "felt"
        name = "Felt"
        floor_texture = "tables/felt.webp"
        floor_normal = "tables/felt-normal.png"
        floor_roughness = "tables/felt-roughness.png"
        $TILE_LINE
        wall_texture = "tables/oak.png"
        wall_normal = "tables/oak-normal.png"
        wall_roughness = "tables/oak-roughness.png"
        wall_tile_mm = 300
        color_mode = "average"
        """.trimIndent()

    val pictures =
      arrayOf(
        "tables/felt.webp" to webp(),
        "tables/felt-normal.png" to png(64, 64),
        "tables/felt-roughness.png" to png(64, 64),
        "tables/oak.png" to png(64, 64),
        "tables/oak-normal.png" to png(64, 64),
        "tables/oak-roughness.png" to png(64, 64),
      )

    /** A lossless WebP of 64 by 64 as far as its header goes. */
    fun webp(): ByteArray {
      val size = 63 // width - 1 and height - 1, fourteen bits each
      val bits = size or (size shl 14)
      val chunk =
        "VP8L".toByteArray() + byteArrayOf(5, 0, 0, 0) + byteArrayOf(0x2F) +
          byteArrayOf(bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte())
      return "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray() + chunk
    }
  }
}
