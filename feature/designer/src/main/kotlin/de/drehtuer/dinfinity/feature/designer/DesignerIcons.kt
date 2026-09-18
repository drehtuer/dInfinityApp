package de.drehtuer.dinfinity.feature.designer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The designer's tool glyphs, **taken from the prototype's own sprite**
 * (`design/dInfinityPhone.dc.html`, the inline `<svg>` of `<symbol>`s at the
 * head of the phone frame).
 *
 * The `d` attribute is copied across verbatim and parsed at run time, rather
 * than re-drawn as a sequence of `drawLine` calls the way the menu's three
 * bars are (`feature/settings`, `MenuButton`). Three straight lines are
 * cheaper to write out than to parse; a pencil of four curves is not, and a
 * hand transcription of one stops being the prototype's drawing the first time
 * either side is touched. Copying the string keeps the two literally equal,
 * and `DesignerIconsTest` reads the prototype to say so — the same bargain
 * `ModernistTest` strikes with the stylesheet.
 *
 * Everything here shares the sprite's own drawing rules — a [BOX] × [BOX] box,
 * `stroke-width: 2`, round caps and round joins, no fill — so a glyph is a
 * path and a width and nothing else.
 *
 * **Two of them are not in the sprite.** [PASTE] and [MIRROR] are controls the
 * prototype does not have at all, so they are drawn here in the sprite's idiom
 * and written down as something for the design to adopt rather than quietly
 * invented (`docs/design-handover.md`).
 */
internal object DesignerIcons {
  /** `#ic-pencil`. The three pens are this, each at the width it draws. */
  const val PENCIL: String =
    "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622" +
      "l4.353-1.32a2 2 0 0 0 .83-.497zM15 5l4 4"

  /** `#ic-eraser`. */
  const val ERASER: String =
    "m7 21-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21M22 21H7M5 11l9 9"

  /** `#ic-bucket`. */
  const val BUCKET: String =
    "m19 11-8-8-8.6 8.6a2 2 0 0 0 0 2.8l5.2 5.2c.8.8 2 .8 2.8 0L19 11ZM5 2l5 5M2 13h15" +
      "M22 20a2 2 0 1 1-4 0c0-1.6 1.7-2.4 2-4 .3 1.6 2 2.4 2 4Z"

  /** `#ic-type`: the text tool, which is what the stamp is. */
  const val TYPE: String = "M4 7V4h16v3M9 20h6M12 4v16"

  /** `#ic-image`: a picture under the drawing, which is what the guide is. */
  const val IMAGE: String =
    "M3 3h18v18H3zM9 11a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm12 4-3.086-3.086a2 2 0 0 0-2.828 0L6 21"

  /** `#ic-undo`. */
  const val UNDO: String = "M3 7v6h6M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"

  /** `#ic-redo`. */
  const val REDO: String = "M21 7v6h-6M3 17a9 9 0 0 1 9-9 9 9 0 0 1 6 2.3l3 2.7"

  /** `#ic-x`: taking everything off the face. */
  const val CLEAR: String = "M18 6 6 18M6 6l12 12"

  /** `#ic-copy`. */
  const val COPY: String = "M8 8h14v14H8zM4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"

  /** `#ic-download`: what the prototype marks a way out of the app with, and so what a save is. */
  const val SAVE: String = "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"

  /**
   * A clipboard with its clip, in the sprite's idiom. **Not in the prototype**
   * — the paste is one of the four controls the design does not have
   * (`docs/design-handover.md`).
   */
  const val PASTE: String = "M8 4H6a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-2M9 2h6v4H9z"

  /** An axis with a shape either side of it. **Not in the prototype**, for the reason [PASTE] is not. */
  const val MIRROR: String = "M12 2v20M9 7 4 12l5 5V7ZM15 7l5 5-5 5V7Z"

  /** The sprite's `viewBox="0 0 24 24"`. */
  const val BOX: Float = 24f

  /** The sprite's `stroke-width`, in that box's own units. */
  const val INK: Float = 2f

  /** Every glyph above, for the test that holds them to the prototype. */
  val all: Map<String, String>
    get() =
      mapOf(
        "ic-pencil" to PENCIL,
        "ic-eraser" to ERASER,
        "ic-bucket" to BUCKET,
        "ic-type" to TYPE,
        "ic-image" to IMAGE,
        "ic-undo" to UNDO,
        "ic-redo" to REDO,
        "ic-x" to CLEAR,
        "ic-copy" to COPY,
        "ic-download" to SAVE,
      )
}

/**
 * One of [DesignerIcons], at [tint].
 *
 * The parse is remembered on the path string: a glyph is drawn on every frame
 * its row is recomposed for, and re-parsing a dozen of them is work nobody
 * needs. What is left in the draw lambda is a scale and a stroke.
 *
 * @param ink how wide the glyph draws, in the sprite's own [DesignerIcons.BOX]
 *   units. The three pens differ by exactly this and nothing else — the medium
 *   pen *is* `#ic-pencil` as the prototype draws it, and the fine and broad
 *   ones are the same drawing at the width they put down, so the row says what
 *   each pen does instead of repeating one picture three times.
 */
@Composable
internal fun Glyph(
  path: String,
  tint: Color,
  modifier: Modifier = Modifier,
  size: Dp = GLYPH,
  ink: Float = DesignerIcons.INK,
) {
  val drawn = remember(path) { PathParser().parsePathString(path).toPath() }
  Canvas(modifier = modifier.size(size)) { strokeGlyph(drawn, tint, ink) }
}

/** The 24-unit box, scaled onto whatever room the canvas has. */
private fun DrawScope.strokeGlyph(
  path: Path,
  tint: Color,
  ink: Float,
) {
  scale(scale = size.minDimension / DesignerIcons.BOX, pivot = Offset.Zero) {
    drawPath(
      path = path,
      color = tint,
      style = Stroke(width = ink, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

/** How big a tool glyph is drawn inside its box, the design's own `.btn-icon` measure over again. */
private val GLYPH = 22.dp
