package de.drehtuer.dinfinity.designer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream

/**
 * The scaler that actually decodes pixels (`docs/tables.md`, "Your own photo").
 *
 * **Nothing here decides how big anything is.** [PhotoScaling] has already said
 * what the target size is, which power of two to subsample by to reach it, and
 * how many bytes the result may be. What is left is `BitmapFactory`, a scale
 * and an encoder — which is the same seam [BitmapAtlas] sits behind.
 *
 * The decode is in two passes, and that order is the security of the whole
 * feature:
 *
 * 1. **Bounds only.** `inJustDecodeBounds` reads the header and allocates no
 *    pixels at all. A file claiming to be thirty thousand pixels square costs
 *    a few bytes to find out about, where decoding it first would cost three
 *    and a half gigabytes — which is not a slow app, it is a dead one.
 * 2. **Subsampled.** The second pass carries an `inSampleSize`, so the decoder
 *    never holds more than about four times the pixels that are wanted, and
 *    the exact size is reached by one ordinary scale afterwards. A full-size
 *    decode of an attacker-controlled image does not happen on any path.
 *
 * Everything is caught rather than thrown, for [BitmapAtlas]'s reason: a phone
 * that will not give up the memory for one photograph is a phone the table
 * picker still has to survive, and "that photo could not be used" is a far
 * better outcome than a crash.
 */
class BitmapPhoto : PhotoScaler {
  override fun scaled(source: PhotoSource): ByteArray? {
    val size = bounds(source) ?: return null
    // The ladder, largest first. The first rung that encodes small enough is
    // the answer; in practice that is always the first one.
    return PhotoScaling.steps(size).firstNotNullOfOrNull { step -> written(source, size, step) }
  }

  /**
   * What the picture's header says it is, without decoding a pixel of it.
   *
   * An unreadable file leaves `outWidth` and `outHeight` at -1, which
   * [PhotoSize.real] is false for — so "this is not a picture" and "this
   * picture is absurd" come back the same way, and neither reaches a decode.
   */
  private fun bounds(source: PhotoSource): PhotoSize? =
    runCatching {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      source.open()?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
      PhotoSize(width = options.outWidth, height = options.outHeight)
    }.getOrNull()?.takeIf(PhotoSize::real)

  /** [source] decoded down to [to] and encoded, or null when it did not fit. */
  private fun written(
    source: PhotoSource,
    from: PhotoSize,
    to: PhotoSize,
  ): ByteArray? = encoded(source, from, to)?.takeIf { PhotoScaling.fits(it.size) }

  private fun encoded(
    source: PhotoSource,
    from: PhotoSize,
    to: PhotoSize,
  ): ByteArray? =
    runCatching {
      val options = BitmapFactory.Options().apply { inSampleSize = PhotoScaling.sampleSize(from, to) }
      val decoded =
        source.open()?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
          ?: return@runCatching null
      try {
        exactly(decoded, to)
      } finally {
        decoded.recycle()
      }
    }.getOrNull()

  /**
   * [decoded] brought to exactly [to] and encoded.
   *
   * A scale that lands on the size the bitmap already is hands the *same*
   * bitmap back, so the result is recycled only when it is a different one —
   * recycling the one the caller is about to give back would be a double free
   * of the only picture there is.
   */
  private fun exactly(
    decoded: Bitmap,
    to: PhotoSize,
  ): ByteArray? {
    val scaled = decoded.scale(to.width, to.height)
    return try {
      compress(scaled)
    } finally {
      if (scaled !== decoded) scaled.recycle()
    }
  }

  private fun compress(bitmap: Bitmap): ByteArray? {
    val bytes = ByteArrayOutputStream()
    val written = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, PhotoScaling.QUALITY, bytes)
    return if (written) bytes.toByteArray() else null
  }
}
