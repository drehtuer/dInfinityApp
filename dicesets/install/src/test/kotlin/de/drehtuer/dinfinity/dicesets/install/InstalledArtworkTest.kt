package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits
import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import org.junit.Assert.assertEquals
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

  private fun artwork(
    decode: (ByteArray, String, Int?) -> AtlasDecode = { _, _, _ -> AtlasDecode.Drawn(image()) },
  ): InstalledArtwork = InstalledArtwork(InstalledSets(installed()), decode)

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
