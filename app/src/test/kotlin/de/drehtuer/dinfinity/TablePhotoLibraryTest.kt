package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.designer.AtlasPainter
import de.drehtuer.dinfinity.designer.DraftStore
import de.drehtuer.dinfinity.designer.MineSets
import de.drehtuer.dinfinity.designer.PhotoScaler
import de.drehtuer.dinfinity.designer.PhotoStore
import de.drehtuer.dinfinity.designer.PhotoTable
import de.drehtuer.dinfinity.feature.tables.PhotoOutcome
import de.drehtuer.dinfinity.feature.tables.PickedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * "Use a photo", joined up (`docs/tables.md`, "Your own photo").
 *
 * The real [MineSets] and the real validator, with a scaler that hands back
 * bytes rather than decoding any — decoding is `BitmapPhotoTest`'s, on the
 * device Robolectric provides. What is asserted here is the joining: that a
 * picture nothing can read is a refusal rather than a crash, that the
 * catalogue is re-read only when there is something new in it, and that the
 * validator's own lines are what the screen is given.
 */
class TablePhotoLibraryTest {
  @get:Rule
  val temporary: TemporaryFolder = TemporaryFolder()

  private var reread = 0

  private fun library(
    scaler: PhotoScaler,
    keeps: Int = PhotoTable.MAX_PHOTOS,
  ): TablePhotoLibrary =
    TablePhotoLibrary(
      mine =
        MineSets(
          drafts = DraftStore(temporary.newFolder("drafts")),
          root = root,
          painter = AtlasPainter.NONE,
          dice = { listOf(Die.standard(id = "d6", shape = DieShape.Cube)) },
          photos = PhotoStore(temporary.newFolder("photos"), limit = keeps),
        ),
      refresh = { reread++ },
      scaler = scaler,
      io = Dispatchers.Unconfined,
    )

  private val root: File by lazy { temporary.newFolder("dicesets") }

  @Test
  fun `a photo becomes a table in the personal package, and the catalogue is re-read`() =
    runTest {
      val outcome = library(scalerOf(webp(64, 48))).add(photo(), "Oak table")

      assertEquals(PhotoOutcome.Added(TablePin(DiceSet.PERSONAL_ID, "photo-oak-table")), outcome)
      assertEquals(1, reread)
      assertTrue(File(root, "mine/tables/photo-oak-table.webp").isFile)
    }

  @Test
  fun `a file that is not a picture is a refusal with a reason, not a crash`() {
    runTest {
      val outcome = library(PhotoScaler.NONE).add(photo(), "Oak")

      assertTrue("$outcome", outcome is PhotoOutcome.Refused)
      assertTrue((outcome as PhotoOutcome.Refused).reasons.single().isNotBlank())
      assertEquals("the catalogue was re-read for a photo that never landed", 0, reread)
    }
  }

  @Test
  fun `a photo the validator refuses hands back the lines it wrote`() {
    runTest {
      // 4096 px is past `MAX_TEXTURE_PIXELS`, which the validator reads out of
      // the header — the app's own output is not a privileged path.
      val outcome = library(scalerOf(webp(4096, 4096))).add(photo(), "Too big")

      val refused = outcome as PhotoOutcome.Refused
      assertTrue(refused.reasons.any { "4096" in it && it.startsWith("diceset.toml") })
      assertEquals(0, reread)
    }
  }

  @Test
  fun `a photo with no name says so rather than writing a nameless table`() {
    runTest {
      val outcome = library(scalerOf(webp(64, 48))).add(photo(), "   ")

      assertTrue("$outcome", outcome is PhotoOutcome.Refused)
      assertEquals(0, reread)
    }
  }

  @Test
  fun `a seventh photo says how many are kept`() =
    runTest {
      val photos = library(scalerOf(webp(64, 48)), keeps = 1)
      photos.add(photo(), "Oak")

      val outcome = photos.add(photo(), "Elm")

      val refused = outcome as PhotoOutcome.Refused
      assertTrue(refused.reasons.single(), "1" in refused.reasons.single())
    }

  @Test
  fun `removing a photo table re-reads the catalogue`() =
    runTest {
      val photos = library(scalerOf(webp(64, 48)))
      photos.add(photo(), "Oak")

      photos.remove(TablePin(DiceSet.PERSONAL_ID, "photo-oak"))

      assertEquals(2, reread)
      assertTrue("the personal package outlived the only thing in it", !File(root, "mine").exists())
    }

  @Test
  fun `only the personal package's tables can be removed from here`() =
    runTest {
      val photos = library(scalerOf(webp(64, 48)))
      photos.add(photo(), "Oak")

      photos.remove(TablePin("builtin", "oak"))

      assertEquals("a packaged look reached the personal package's remove", 1, reread)
      assertTrue(File(root, "mine/tables/photo-oak.webp").isFile)
    }

  private fun photo(): PickedPhoto = PickedPhoto(label = "oak.jpg") { ByteArrayInputStream(ByteArray(1)) }

  /**
   * A scaler that hands back [bytes] — after opening the source, because the
   * real one does and the way of opening it is the only thing that crosses the
   * seam.
   */
  private fun scalerOf(bytes: ByteArray): PhotoScaler =
    PhotoScaler { source ->
      source.open()?.use { it.readBytes() }
      bytes
    }

  /**
   * The first 25 bytes of a lossless WebP that is [width] by [height].
   *
   * A header rather than a picture, because that is exactly what the validator
   * reads before anything is decoded (`dicesets/format`'s `ImageHeader`), so a
   * package built out of one goes through the real validator and is really
   * checked.
   */
  private fun webp(
    width: Int,
    height: Int,
  ): ByteArray =
    "RIFF".toByteArray(Charsets.US_ASCII) +
      littleEndian(0) +
      "WEBP".toByteArray(Charsets.US_ASCII) +
      "VP8L".toByteArray(Charsets.US_ASCII) +
      littleEndian(0) +
      byteArrayOf(0x2F) +
      littleEndian((width - 1) or ((height - 1) shl 14))

  private fun littleEndian(value: Int): ByteArray =
    byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())
}
