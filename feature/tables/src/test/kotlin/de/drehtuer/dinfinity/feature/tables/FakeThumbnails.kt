package de.drehtuer.dinfinity.feature.tables

import androidx.compose.ui.graphics.ImageBitmap
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin

/**
 * A source of table pictures that draws nothing.
 *
 * This screen's part in a thumbnail is asking for one and drawing whatever
 * comes back, so what a test needs is something that answers — a real one
 * wants a Filament engine on a thread of its own, which has its own tests on
 * the JVM (`TrayThumbnailsTest`) and on a device (`TableThumbnailDeviceTest`).
 *
 * It answers when it is told to rather than straight away, because "the row is
 * drawn before its picture arrives" is the ordinary case and the one worth
 * being able to hold still.
 *
 * @param answer whether a look gets a picture at all. False is a device that
 *   cannot draw one, which is the case the swatch exists for.
 */
internal class FakeThumbnails(
  private val answer: Boolean = true,
) : TableThumbnails {
  /** Every look that has been asked about, in order, with repeats. */
  val asked: MutableList<TablePin> = mutableListOf()

  private val waiting = mutableMapOf<TablePin, (ImageBitmap) -> Unit>()

  override fun of(
    pin: TablePin,
    look: TableLook,
    onDrawn: (ImageBitmap) -> Unit,
  ) {
    asked += pin
    if (answer) waiting[pin] = onDrawn
  }

  /** Hands over the picture of [pin], as the renderer would when it had one. */
  fun draw(pin: TablePin): ImageBitmap {
    val picture = ImageBitmap(WIDTH, HEIGHT)
    waiting.remove(pin)?.invoke(picture)
    return picture
  }

  /** And hands over every picture that has been waited for. */
  fun drawAll() {
    waiting.keys.toList().forEach(::draw)
  }

  private companion object {
    const val WIDTH = 8
    const val HEIGHT = 12
  }
}
