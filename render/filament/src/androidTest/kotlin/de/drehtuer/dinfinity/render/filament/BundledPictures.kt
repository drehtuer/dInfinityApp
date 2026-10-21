package de.drehtuer.dinfinity.render.filament

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import java.nio.ByteBuffer

/**
 * The bundled package's pictures for a device test, by [AtlasKey].
 *
 * What `:app` wires up for the player is `InstalledArtwork` over the same
 * files; this module cannot name `dicesets/install` and a test does not need
 * its size checks, only the pixels. Keys for any other package answer nothing,
 * which is what a package that is not installed does.
 */
object BundledPictures : (String) -> AtlasImage? {
  override fun invoke(key: String): AtlasImage? {
    val (setId, path) = AtlasKey.split(key) ?: return null
    if (setId != BuiltinDiceSet.set.id) return null
    return BuiltinDiceSet.files().read(path)?.let(::decoded)
  }

  /** [bytes] as straight-alpha RGBA, rows from the top. */
  fun decoded(bytes: ByteArray): AtlasImage? {
    val options =
      BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPremultiplied = false
        inScaled = false
      }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val pixels = ByteBuffer.allocate(bitmap.byteCount)
    bitmap.copyPixelsToBuffer(pixels)
    val image = AtlasImage(bitmap.width, bitmap.height, pixels.array())
    bitmap.recycle()
    return image
  }
}
