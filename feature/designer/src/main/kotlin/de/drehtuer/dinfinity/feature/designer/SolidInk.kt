package de.drehtuer.dinfinity.feature.designer

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.designer.StageFace
import de.drehtuer.dinfinity.designer.StagePoint
import de.drehtuer.dinfinity.designer.StageShape
import de.drehtuer.dinfinity.ui.common.Modernist
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke

// Putting the die on the screen: a silhouette, the faces turned towards the
// viewer, and what is drawn on each of them (`docs/face-designer.md`, "The
// solid, not just the face").
//
// Apart from `SolidView` for the reason `FaceInk` is apart from
// `DesignerScreen`: everything here is a path and a colour, with no state, no
// layout and nothing to decide. Which faces are there at all, where their
// corners landed and what order they go down in was settled in `designer`'s
// `SolidStage` before any of it reached a `DrawScope`, which is the line this
// module draws everywhere (`docs/architecture.md`, decision 55).

/**
 * One face of the die: its paper, what is drawn on it, and its edge.
 *
 * **The face being drawn on reads as the face being drawn on**, on this tab
 * exactly as on the strip — a 4 dp outline in the accent's deep step over a
 * 16 % tint of the accent in the fill — so moving between the two tabs never
 * loses the player's place.
 */
internal fun DrawScope.drawFace(
  face: StageFace,
  colours: SolidColours,
  selected: Boolean,
) {
  val polygon = pathOf(listOf(face.outline), size)
  drawPath(path = polygon, color = colours.lit(face.light))
  if (selected) drawPath(path = polygon, color = colours.tint)
  // Clipped to the face rather than trusted to stay inside it: the canvas is a
  // square and the outline it is masked into is not, so a mark near a corner
  // of the canvas belongs to no face (`docs/face-designer.md`).
  clipPath(polygon) {
    face.marks.forEach { mark -> drawShape(mark) }
  }
  drawPath(
    path = polygon,
    color = if (selected) colours.chosen else colours.edge,
    style = DrawStroke(width = (if (selected) SELECTED_EDGE else Modernist.hairline).toPx()),
  )
}

/** The die's own body, which is what a coin's rim is drawn as. */
internal fun DrawScope.drawSolid(
  outline: List<StagePoint>,
  paper: Color,
) = drawPath(path = pathOf(listOf(outline), size), color = paper)

/**
 * One closed mark on a face, under the even-odd rule.
 *
 * The same rule the flat canvas draws a stamp with, and for the same reason:
 * it is what leaves the hole in a `0` open (`FaceInk`).
 */
internal fun DrawScope.drawShape(shape: StageShape) =
  drawPath(path = pathOf(shape.rings, size), color = Color(shape.colorArgb))

/**
 * Closed rings on the stage as one path.
 *
 * A ring with nothing in it is skipped rather than started and closed: a face
 * that is edge-on projects to no polygon at all, and `moveTo` on an empty list
 * would be a path with a point in it.
 */
internal fun pathOf(
  rings: List<List<StagePoint>>,
  size: Size,
): Path =
  Path().apply {
    fillType = PathFillType.EvenOdd
    rings.filter { it.isNotEmpty() }.forEach { ring ->
      ring.forEachIndexed { corner, point ->
        val x = point.x * size.width
        val y = point.y * size.height
        if (corner == 0) moveTo(x, y) else lineTo(x, y)
      }
      close()
    }
  }

/**
 * What the die is drawn in.
 *
 * The screen's own paper and ink rather than colours of this file's: a die on
 * this stage is a drawing of a die on a page, and the page is the app's
 * (`docs/design-handover.md`).
 *
 * Not a `data class`: it is five colours a draw lambda is handed, never a value
 * anything compares, copies or prints.
 */
internal class SolidColours(
  val paper: Color,
  private val shade: Color,
  val edge: Color,
  val chosen: Color,
  val tint: Color,
) {
  /**
   * The paper of a face catching [light] of the lamp.
   *
   * Shaded by mixing the screen's own ink into its paper rather than by dimming
   * towards black, so a die in a dark theme is lit by the same rule as one in a
   * light theme and neither ends up with a face the colour of the page behind
   * it.
   */
  fun lit(light: Float): Color = lerp(paper, shade, (1f - light) * SHADING)
}

/** How much of the screen's ink the darkest face carries. */
private const val SHADING = 0.5f

/** The outline the face being drawn on wears. */
private val SELECTED_EDGE = 4.dp
