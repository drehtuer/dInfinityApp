package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Finding a die's artwork on disk, with the decoder standing in.
 *
 * There is no Android here on purpose: which folder a package lives in, which
 * paths may be read out of it and what is refused before a decoder is asked
 * are all plain Kotlin over a temporary folder, the same way [InstalledSets]
 * is tested (`docs/architecture.md`, decision 40).
 */
class InstalledArtworkTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun `a die's atlas is read out of the folder its package was installed in`() {
    var asked: Triple<Int, String, Int?>? = null
    val artwork =
      artwork { bytes, file, faces ->
        asked = Triple(bytes.size, file, faces)
        AtlasDecode.Drawn(image())
      }

    val decoded = artwork.read("brass", "textures/d6.png")

    assertEquals(30, decoded.drawn?.width)
    assertEquals("the atlas of a cube is cut into six cells", 6, asked?.third)
    assertEquals("brass/textures/d6.png", asked?.second)
  }

  @Test
  fun `a package that is not installed has no artwork`() {
    val decoded = artwork().read("nobody", "textures/d6.png")

    assertNull(decoded.drawn)
    assertEquals(ValidationCode.ReferencedFileMissing, decoded.messages.single().code)
  }

  @Test
  fun `a path that tries to leave the package is refused before anything is opened`() {
    var decoded = 0
    val artwork =
      artwork { _, _, _ ->
        decoded++
        AtlasDecode.Drawn(image())
      }

    listOf("../../etc/passwd.png", "/etc/passwd.png", "..\\secrets.png", "textures/d6.exe", "").forEach { hostile ->
      val refused = artwork.read("brass", hostile)
      assertNull("'$hostile' was read", refused.drawn)
      assertEquals(ValidationCode.BadFileReference, refused.messages.single().code)
    }
    assertEquals("a decoder was handed a path that leaves the package", 0, decoded)
  }

  @Test
  fun `a texture the package does not have is reported, not decoded`() {
    val decoded = artwork().read("brass", "textures/gone.png")

    assertNull(decoded.drawn)
    assertEquals(ValidationCode.ReferencedFileMissing, decoded.messages.single().code)
  }

  @Test
  fun `a texture over the byte limit is refused before a decoder sees it`() {
    val root = installed()
    File(root, "brass/textures/huge.png").writeBytes(ByteArray((DiceSetLimits.MAX_TEXTURE_BYTES + 1).toInt()))
    var decoded = 0
    val artwork =
      InstalledArtwork(InstalledSets(root)) { _, _, _ ->
        decoded++
        AtlasDecode.Drawn(image())
      }

    val refused = artwork.read("brass", "textures/huge.png")

    assertEquals(ValidationCode.FileTooLarge, refused.messages.single().code)
    assertEquals(0, decoded)
  }

  @Test
  fun `a package that no longer validates has no artwork either`() {
    val root = installed()
    File(root, "brass/diceset.toml").writeText("this is not toml =")

    val decoded =
      InstalledArtwork(InstalledSets(root)) { _, _, _ -> AtlasDecode.Drawn(image()) }
        .read("brass", "textures/d6.png")

    assertNull("half a package is not a package", decoded.drawn)
    assertTrue(decoded.messages.isNotEmpty())
  }

  @Test
  fun `an atlas no die wears is decoded with no grid to check it against`() {
    val root = installed()
    File(root, "brass/textures/spare.png").writeBytes(png(8, 8))
    var faces: Int? = -1
    val artwork =
      InstalledArtwork(InstalledSets(root)) { _, _, wanted ->
        faces = wanted
        AtlasDecode.Drawn(image())
      }

    artwork.read("brass", "textures/spare.png")

    assertNull(faces)
  }

  @Test
  fun `a stamp names the size and the time, and changes when the file is rewritten`() {
    val root = installed()
    val artwork = InstalledArtwork(InstalledSets(root)) { _, _, _ -> AtlasDecode.Drawn(image()) }
    val before = artwork.stamp("brass", "textures/d6.png")

    val file = File(root, "brass/textures/d6.png")
    file.writeBytes(png(30, 20) + ByteArray(1))
    file.setLastModified(file.lastModified() + 2_000L)

    assertNotNull(before)
    assertNotEquals(before, artwork.stamp("brass", "textures/d6.png"))
  }

  @Test
  fun `there is no stamp for what is not there or may not be read`() {
    val artwork = artwork()

    assertNull(artwork.stamp("nobody", "textures/d6.png"))
    assertNull(artwork.stamp("brass", "textures/gone.png"))
    assertNull(artwork.stamp("brass", "../../etc/passwd.png"))
  }

  @Test
  fun `the bundled package's pictures are read from the files it was validated from`() {
    var asked: String? = null
    val artwork =
      InstalledArtwork(InstalledSets(installed()), bundled()) { _, file, _ ->
        asked = file
        AtlasDecode.Drawn(image())
      }

    val decoded = artwork.read(DiceSet.BUILTIN_ID, "tables/felt.webp")

    assertNotNull("the bundled felt was not found", decoded.drawn)
    assertEquals("builtin/tables/felt.webp", asked)
  }

  @Test
  fun `a folder on disk calling itself the bundled package is never reached`() {
    val root = installed()
    File(root, DiceSet.BUILTIN_ID).mkdirs()
    File(root, "${DiceSet.BUILTIN_ID}/tables").mkdirs()
    File(root, "${DiceSet.BUILTIN_ID}/tables/other.png").writeBytes(png(8, 8))
    val artwork = InstalledArtwork(InstalledSets(root), bundled()) { _, _, _ -> AtlasDecode.Drawn(image()) }

    val decoded = artwork.read(DiceSet.BUILTIN_ID, "tables/other.png")

    assertNull("a stranger's folder answered for the bundled package", decoded.drawn)
    assertNull(artwork.stamp(DiceSet.BUILTIN_ID, "tables/other.png"))
  }

  @Test
  fun `a hostile path is refused for the bundled package as for any other`() {
    var decoded = 0
    val artwork =
      InstalledArtwork(InstalledSets(installed()), bundled()) { _, _, _ ->
        decoded++
        AtlasDecode.Drawn(image())
      }

    listOf("../diceset.toml", "/tables/felt.webp", "tables/felt.exe", "tables/../../felt.webp").forEach { hostile ->
      assertNull("'$hostile' was read", artwork.read(DiceSet.BUILTIN_ID, hostile).drawn)
      assertNull("'$hostile' was stamped", artwork.stamp(DiceSet.BUILTIN_ID, hostile))
    }
    assertEquals(0, decoded)
  }

  @Test
  fun `the bundled package is stamped once for its life, and not for a file it lacks`() {
    val artwork = InstalledArtwork(InstalledSets(installed()), bundled()) { _, _, _ -> AtlasDecode.Drawn(image()) }

    val stamp = artwork.stamp(DiceSet.BUILTIN_ID, "tables/felt.webp")

    assertNotNull(stamp)
    assertEquals(stamp, artwork.stamp(DiceSet.BUILTIN_ID, "tables/felt.webp"))
    assertNull(artwork.stamp(DiceSet.BUILTIN_ID, "tables/gone.webp"))
  }

  @Test
  fun `a table's texture in an installed package is read with no grid, like any picture no die wears`() {
    var faces: Int? = -1
    val artwork =
      artwork { _, _, wanted ->
        faces = wanted
        AtlasDecode.Drawn(image())
      }
    File(installed(), "brass/textures/felt.png").writeBytes(png(8, 8))

    assertNotNull(artwork.read("brass", "textures/felt.png").drawn)
    assertNull(faces)
  }

  /** The bundled package, as the app hands it in: a set and the files behind it. */
  private fun bundled(): InstalledArtwork.BundledPackage =
    InstalledArtwork.BundledPackage(
      set =
        DiceSet(
          id = DiceSet.BUILTIN_ID,
          name = "Built-in",
          version = "1.0.0",
          tables = listOf(TableLook(id = "felt", name = "Felt", floorTexturePath = "tables/felt.webp")),
        ),
      files = PackageFiles.of(mapOf("tables/felt.webp" to png(8, 8))),
    )

  private fun artwork(
    decode: (ByteArray, String, Int?) -> AtlasDecode = { _, _, _ -> AtlasDecode.Drawn(image()) },
  ): InstalledArtwork = InstalledArtwork(InstalledSets(installed()), decode = decode)

  /** A `dicesets/` folder holding one package whose d6 wears an atlas. */
  private fun installed(): File {
    val root = File(folder.root, "dicesets")
    if (root.isDirectory) return root
    val set = File(root, "brass").apply { mkdirs() }
    File(set, "textures").mkdirs()
    File(set, "textures/d6.png").writeBytes(png(30, 20))
    File(set, "diceset.toml").writeText(
      """
      format = 1

      [set]
      id = "brass"
      name = "Brass"
      version = "1.0.0"

      [[die]]
      id = "d6"
      shape = "cube"
      faces = [1, 2, 3, 4, 5, 6]
      texture = "textures/d6.png"
      """.trimIndent(),
    )
    return root
  }

  /** A PNG as far as its header goes, which is as far as the validator reads. */
  private fun png(
    width: Int,
    height: Int,
  ): ByteArray {
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val header =
      byteArrayOf(0, 0, 0, 13) + "IHDR".toByteArray() + bigEndian(width) + bigEndian(height) +
        byteArrayOf(8, 6, 0, 0, 0, 0, 0, 0, 0)
    return signature + header + "IEND".toByteArray()
  }

  private fun bigEndian(value: Int): ByteArray =
    byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

  private fun image(): AtlasImage = AtlasImage(30, 20, ByteArray(30 * 20 * AtlasImage.CHANNELS))
}
