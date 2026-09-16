package de.drehtuer.dinfinity.designer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * A photograph really becoming a WebP, on a real decoder
 * (`docs/tables.md`, "Your own photo").
 *
 * This tier exists because the one below it cannot answer the question.
 * Robolectric's `Bitmap.compress` writes a *description string* rather than an
 * encoded image, so every JVM and Robolectric test of this path has asserted
 * over bytes that are not a picture — which means nothing in CI has ever
 * encoded a WebP, and "the file written into the personal package is a real
 * texture the validator will accept" was untested until here.
 *
 * What is *not* here is any arithmetic: [PhotoScaling] decides every size and
 * is tested on the JVM. This asks the two things only a device can answer —
 * does the encoder produce a real WebP, and does the two-pass decode keep a
 * huge picture from ever being allocated.
 */
@RunWith(AndroidJUnit4::class)
class BitmapPhotoDeviceTest {
  private val scaler = BitmapPhoto()

  @Test
  fun aPhotographBecomesARealWebpUnderTheTextureLimit() {
    val photo = jpeg(width = 4080, height = 3072)

    val written = requireNotNull(scaler.scaled(source(photo))) { "a plain photograph was refused" }

    assertTrue("what was written is not a WebP", isWebp(written))
    assertTrue(
      "a 2048-pixel photo came to ${written.size} bytes, past the ${DiceSetLimits.MAX_TEXTURE_BYTES}-byte limit",
      written.size <= DiceSetLimits.MAX_TEXTURE_BYTES,
    )
  }

  @Test
  fun theLongSideIsBroughtToTheTextureLimitAndTheAspectIsKept() {
    val written = requireNotNull(scaler.scaled(source(jpeg(width = 4080, height = 3072))))

    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(written, 0, written.size, size)

    assertEquals(DiceSetLimits.MAX_TEXTURE_PIXELS, size.outWidth)
    // 3072 / 4080 * 2048, which is what keeping the aspect comes to.
    assertEquals(1542, size.outHeight)
  }

  @Test
  fun aPictureSmallerThanTheLimitIsNotBlownUpToReachIt() {
    val written = requireNotNull(scaler.scaled(source(jpeg(width = 640, height = 480))))

    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(written, 0, written.size, size)

    assertEquals(640, size.outWidth)
    assertEquals(480, size.outHeight)
  }

  @Test
  fun aFileThatIsNotAPictureIsRefusedRatherThanThrown() {
    assertNull(scaler.scaled(source(ByteArray(2048) { it.toByte() })))
  }

  @Test
  fun anEmptyFileIsRefusedTheSameWay() {
    assertNull(scaler.scaled(source(ByteArray(0))))
  }

  @Test
  fun aSourceThatWillNotOpenIsRefusedRatherThanThrown() {
    assertNull(scaler.scaled(PhotoSource { null }))
  }

  @Test
  fun aVeryLargePictureIsScaledWithoutEverBeingHeldWholeInMemory() {
    // Forty-eight megapixels, which is an ordinary phone camera and about
    // 190 MB if it were ever decoded at full size. The bounds pass reads the
    // header and the subsampled pass never holds more than about four times
    // the pixels wanted, so the peak here should be a few tens of megabytes.
    val photo = jpeg(width = 8000, height = 6000)
    val runtime = Runtime.getRuntime()
    val before = runtime.totalMemory() - runtime.freeMemory()

    val written = requireNotNull(scaler.scaled(source(photo)))

    val peak = runtime.totalMemory() - runtime.freeMemory() - before
    assertTrue("what was written is not a WebP", isWebp(written))
    assertTrue(
      "the decode grew the heap by $peak bytes, which is a full-size decode rather than a subsampled one",
      peak < FULL_SIZE_WOULD_BE,
    )
  }

  /** A real JPEG of that size, with something in it that does not compress to nothing. */
  private fun jpeg(
    width: Int,
    height: Int,
  ): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    // Plain noise: a flat colour encodes to almost no bytes and would make the
    // size assertions meaningless.
    val row = IntArray(width) { x -> (x * PRIME) or OPAQUE }
    for (y in 0 until height) bitmap.setPixels(row, 0, width, 0, y, width, 1)
    val bytes = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)
    bitmap.recycle()
    return bytes.toByteArray()
  }

  private fun source(bytes: ByteArray): PhotoSource = PhotoSource { ByteArrayInputStream(bytes) }

  /** `RIFF....WEBP`, which is what a WebP file starts with. */
  private fun isWebp(bytes: ByteArray): Boolean =
    bytes.size > WEBP_HEADER_BYTES &&
      String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
      String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"

  private companion object {
    const val WEBP_HEADER_BYTES = 12
    const val JPEG_QUALITY = 90
    const val PRIME = 2_654_435_761L.toInt()
    const val OPAQUE = 0xFF000000.toInt()

    /**
     * Half of what 8000×6000 at four bytes a pixel would take.
     *
     * Deliberately generous: this is asking whether a *full-size* decode
     * happened, not measuring the peak. A subsampled decode of this picture
     * holds about a sixteenth of it.
     */
    const val FULL_SIZE_WOULD_BE = 8_000L * 6_000 * 4 / 2
  }
}
