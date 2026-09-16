package de.drehtuer.dinfinity.feature.designer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.FaceOutline
import de.drehtuer.dinfinity.designer.FaceShapes
import de.drehtuer.dinfinity.designer.Fill
import de.drehtuer.dinfinity.designer.GuideMark
import de.drehtuer.dinfinity.designer.Mark
import de.drehtuer.dinfinity.designer.Stamp
import de.drehtuer.dinfinity.designer.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke

// Putting the canvas on the screen: an outline, the marks on it and the guide
// under them (`docs/face-designer.md`).
//
// Apart from `DesignerScreen` because it is the other half of that file's job
// and a different kind of thing — everything here is a path and a colour, with
// no state, no layout and nothing to decide. What each mark *is* was settled in
// `designer/` before any of it reached a `DrawScope`, which is the line this
// module draws everywhere (`docs/architecture.md`, decision 55).

/** Where on the canvas a finger went, as the fractions a mark is stored in. */
internal fun Offset.asDot(
  width: Float,
  height: Float,
): Dot = Dot(x = (x / width).coerceIn(0f, 1f), y = (y / height).coerceIn(0f, 1f))

/** The outline as a path across a canvas of [width] by [height]. */
internal fun Path.follow(
  outline: FaceOutline,
  width: Float,
  height: Float,
) {
  val corners = FaceShapes.corners(outline)
  if (corners.isEmpty()) {
    // The coin, which is the one outline that is not a polygon.
    addOval(Rect(0f, 0f, width, height))
    return
  }
  corners.forEachIndexed { index, dot ->
    val x = dot.x * width
    val y = dot.y * height
    if (index == 0) moveTo(x, y) else lineTo(x, y)
  }
  close()
}

/**
 * One mark: a line of the pen, or a region the bucket coloured in.
 *
 * The fill is a closed path rather than a rectangle, because the region it was
 * given is whatever shape enclosed the tap — the canvas square for the face
 * itself, and the player's own outline for anything smaller (`FaceFill`).
 */
internal fun DrawScope.drawMark(mark: Mark) {
  when (mark) {
    is Stroke -> drawStroke(mark)
    is Fill -> drawFill(mark)
    is Stamp -> drawStamp(mark)
  }
}

/**
 * A stamped glyph: every ring of it as one shape, under the even-odd rule.
 *
 * Even-odd is what leaves the hole in a `0` open — the rings of a glyph are
 * wound against each other, and a counter drawn as a shape of its own would be
 * a blob where the hole is (`designer`'s `Stamp`).
 */
internal fun DrawScope.drawStamp(stamp: Stamp) = drawRings(stamp.rings, Color(stamp.colorArgb))

/**
 * Closed rings as one filled shape.
 *
 * Shared by the stamp and the guide because the guide *is* a stamp — the same
 * outlines in the same place, drawn in the screen's own faint ink rather than
 * in the pen's (`FaceGuide`).
 */
internal fun DrawScope.drawRings(
  rings: List<List<Dot>>,
  colour: Color,
) {
  val path =
    Path().apply {
      fillType = PathFillType.EvenOdd
      rings.forEach { ring ->
        trace(ring, size.width, size.height)
        close()
      }
    }
  drawPath(path = path, color = colour)
}

internal fun DrawScope.drawFill(fill: Fill) {
  val path = Path().apply { trace(fill.dots, size.width, size.height) }
  path.close()
  drawPath(path = path, color = Color(fill.colorArgb))
}

internal fun DrawScope.drawStroke(stroke: Stroke) {
  val path = Path().apply { trace(stroke.dots, size.width, size.height) }
  drawPath(
    path = path,
    // The eraser paints the canvas's own white rather than cutting a hole:
    // the drawing is a list of strokes and a hole would be a fourth kind of
    // thing to store, to undo and to rasterise.
    color = if (stroke.erases) Color.White else Color(stroke.colorArgb),
    style = DrawStroke(width = stroke.width * size.width, cap = StrokeCap.Round),
  )
}

/** [dots] as a path across a canvas of [width] by [height]. */
internal fun Path.trace(
  dots: List<Dot>,
  width: Float,
  height: Float,
) {
  dots.forEachIndexed { index, dot ->
    val x = dot.x * width
    val y = dot.y * height
    if (index == 0) moveTo(x, y) else lineTo(x, y)
  }
}

/**
 * One number under the drawing, faintly, as something to trace.
 *
 * **The numeral itself, not a dot where it goes.** Text on a `DrawScope` needs
 * a measurer, which this file has no reason to hold — but `core/glyphs` is one
 * and `designer` has already asked it, so what arrives here is the outline of
 * the number the tray would print, in the place the tray would print it, and
 * drawing it is drawing a shape. A face with nothing printed on it arrives as
 * the dot this used to be, so there is no case to tell apart here
 * (`FaceGuide`).
 */
internal fun DrawScope.drawGuide(
  mark: GuideMark,
  colour: Color,
) = drawRings(mark.rings, colour)
