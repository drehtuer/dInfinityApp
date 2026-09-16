package de.drehtuer.dinfinity.feature.tables

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import kotlin.math.roundToInt

/**
 * Where a picture of a table comes from (`docs/tables.md`, "Thumbnails").
 *
 * An interface for the reason [TablePhotos] is one: what is behind it is a
 * Filament engine on a thread this module has never heard of, and a feature
 * module that could name one would be a feature module that could open one
 * (`docs/architecture.md`, Modules). `:app` joins the two ends up.
 *
 * It is also how the fallback stays honest. Nothing here promises a picture:
 * a device whose driver will not draw one, a look whose turn has not come, and
 * a build with nothing wired at all are the same thing to this screen — no
 * callback, and the swatch stays.
 */
fun interface TableThumbnails {
  /**
   * Asks for a picture of [look].
   *
   * [onDrawn] arrives on the main thread, once, or never. It is never called
   * before this returns, so a caller may ask from a composition without
   * writing to the state it is reading.
   */
  fun of(
    pin: TablePin,
    look: TableLook,
    onDrawn: (ImageBitmap) -> Unit,
  )
}

/**
 * How big a table's picture is on the screen.
 *
 * Here rather than inside the screen because the thing that *draws* one has to
 * know it too, and a renderer asked for a different shape than the row has
 * leaves a tray that does not touch the edges of its own picture. The tray's
 * long side runs up the screen (`docs/tables.md`, "Geometry"), so the box is
 * taller than it is wide — which is also what gives a 16 mm die enough of a
 * row to be recognisable as a d20.
 *
 * The swatch that stands in for a picture is drawn at exactly this size, so a
 * list does not jump about as pictures arrive in it.
 */
object TableThumbnailBox {
  /** Across the row. */
  val WIDTH: Dp = 44.dp

  /** And down it. */
  val HEIGHT: Dp = 64.dp

  /** How wide the box is on a screen of this [density], in pixels. */
  fun widthPx(density: Float): Int = pixels(WIDTH, density)

  /** And how tall. */
  fun heightPx(density: Float): Int = pixels(HEIGHT, density)

  private fun pixels(
    size: Dp,
    density: Float,
  ): Int = (size.value * density).roundToInt().coerceAtLeast(1)
}
