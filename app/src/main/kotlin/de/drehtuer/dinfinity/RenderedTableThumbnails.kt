package de.drehtuer.dinfinity

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.feature.tables.TableThumbnails
import de.drehtuer.dinfinity.render.filament.Snapshot
import de.drehtuer.dinfinity.render.filament.ThumbnailDie
import de.drehtuer.dinfinity.render.filament.TrayThumbnails

/**
 * The table picker's pictures, joined to the renderer that draws them
 * (`docs/tables.md`, "Thumbnails").
 *
 * The two ends of the seam: `feature/tables` asks for a picture of a look and
 * knows nothing about a graphics engine; `render/filament` draws a frame and
 * knows nothing about a screen or a `Bitmap`. This is the join, in the one
 * module allowed to know both (`docs/architecture.md`, Modules) — the same
 * shape as `TablePhotoLibrary` behind `TablePhotos`.
 *
 * Three things happen here and nothing else. Which die stands on the table is
 * asked of the catalogue, which is `:app`'s to hold. The pixels become a
 * bitmap, which is Android's. And the answer is carried to the main thread,
 * because it is drawn on the roll thread and Compose state belongs where
 * Compose draws.
 *
 * @param pictures where a frame comes from, on the thread the engine is on.
 * @param die which die to stand on the table, asked each time because a
 *   package may have been installed since the last picture.
 * @param toMain how a picture reaches the thread that will draw it.
 */
class RenderedTableThumbnails(
  private val pictures: TrayThumbnails,
  private val die: () -> ThumbnailDie?,
  private val toMain: (Runnable) -> Unit = Handler(Looper.getMainLooper())::post,
) : TableThumbnails {
  override fun of(
    pin: TablePin,
    look: TableLook,
    onDrawn: (ImageBitmap) -> Unit,
  ) {
    pictures.of(key = keyOf(pin, look), look = look, die = die()) { frame ->
      val image = bitmapOf(frame)
      toMain { onDrawn(image) }
    }
  }

  private companion object {
    /**
     * What a picture is filed under: which table, and what that table is.
     *
     * The pin alone would be enough for a package's look, which cannot change
     * without the package being reinstalled. It is not enough for a
     * photograph: removing `photo-oak` and making another table out of another
     * photograph gives back the same id, and the picture kept under it would
     * be of the table that is gone (`docs/tables.md`, "Your own photo").
     */
    fun keyOf(
      pin: TablePin,
      look: TableLook,
    ): String = "${pin.setId}/${pin.tableId}#${look.hashCode()}"

    /**
     * The frame, as something Compose can draw.
     *
     * Through [Snapshot.argb] rather than by copying the bytes into a bitmap's
     * buffer. Both are one copy; only one of them is *unambiguous*. A bitmap's
     * bytes are laid out by the platform and read back in the machine's
     * endianness, so a buffer copy is right on the assumption that both are
     * what this file thought — and when they are not, the table comes out with
     * its reds and blues swapped and nothing says so.
     */
    fun bitmapOf(frame: Snapshot): ImageBitmap =
      Bitmap
        .createBitmap(frame.argb(), frame.width, frame.height, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
  }
}
