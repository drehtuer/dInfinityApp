package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.drehtuer.dinfinity.core.notation.Sides

/**
 * A die drawn as the outline a player recognises it by
 * (`design/dInfinity.dc.html`, option 1h).
 *
 * These are the prototype's own silhouettes, vertex for vertex, on the same
 * hundred-unit square it drew them on. They are **pictures of dice, not
 * projections of the solids** the simulation collides: a d20 is a hexagon here
 * because that is the shape of an icosahedron seen from across a table, and
 * rendering the real hull small enough to sit on a button would produce a
 * grey blob nobody could tell from a d12.
 *
 * The real solids live in `simulation/api` and are what a roll is made of
 * (`docs/architecture.md`, decision 35). Nothing here is ever asked what a die
 * *is*.
 */
@Composable
fun DieSilhouette(
  sides: Sides,
  fill: Color,
  ink: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val outline = outlineOf(sides)
    if (outline == null) {
      drawCircle(color = fill, radius = size.minDimension * COIN_RADIUS, center = center)
      drawCircle(color = ink, radius = size.minDimension * COIN_RADIUS, center = center, style = stroke())
      return@Canvas
    }
    val path = pathOf(outline, size)
    drawPath(path, color = fill)
    drawPath(path, color = ink, style = stroke())
  }
}

private fun DrawScope.stroke(): Stroke = Stroke(width = size.minDimension * INK_WIDTH)

internal fun pathOf(
  corners: List<Offset>,
  size: Size,
): Path =
  Path().apply {
    corners.forEachIndexed { index, corner ->
      val x = corner.x / DRAWN_ON * size.width
      val y = corner.y / DRAWN_ON * size.height
      if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
  }

/**
 * The corners of [sides] on the hundred-unit square, or `null` for the coin,
 * which is the one die with no corners.
 *
 * Separate from the drawing so that *which picture a die wears* can be
 * asserted without a GPU. The drawing itself is three calls to Compose and is
 * the device suite's to look at.
 *
 * A die the catalogue does not have a picture of is drawn as a cube. It is the
 * shape a player reads as "a die" when they cannot tell which one, and a blank
 * button would be worse than a wrong one.
 */
internal fun outlineOf(sides: Sides): List<Offset>? =
  when (sides) {
    is Sides.Percentile -> KITE
    is Sides.Fudge -> SQUARE
    is Sides.Numeric ->
      when (sides.value) {
        COIN_SIDES -> null
        else -> OUTLINES[sides.value] ?: SQUARE
      }
  }

private fun corners(vararg xy: Float): List<Offset> = xy.toList().chunked(2) { Offset(it[0], it[1]) }

private val TRIANGLE = corners(50f, 8f, 92f, 84f, 8f, 84f)
private val SQUARE = corners(14f, 14f, 86f, 14f, 86f, 86f, 14f, 86f)
private val HEXAGON = corners(50f, 6f, 90f, 28f, 90f, 72f, 50f, 94f, 10f, 72f, 10f, 28f)
private val KITE = corners(50f, 4f, 88f, 42f, 50f, 96f, 12f, 42f)
private val PENTAGON = corners(50f, 6f, 94f, 38f, 77f, 92f, 23f, 92f, 6f, 38f)
private val OCTAGON = corners(32f, 6f, 68f, 6f, 94f, 32f, 94f, 68f, 68f, 94f, 32f, 94f, 6f, 68f, 6f, 32f)
private val TALL_HEXAGON = corners(50f, 5f, 89f, 27.5f, 89f, 72.5f, 50f, 95f, 11f, 72.5f, 11f, 27.5f)

/** Which outline each numeric die wears, by its number of sides. */
private val OUTLINES: Map<Int, List<Offset>> =
  mapOf(
    4 to TRIANGLE,
    6 to SQUARE,
    8 to HEXAGON,
    10 to KITE,
    12 to PENTAGON,
    18 to OCTAGON,
    20 to TALL_HEXAGON,
  )

/** The square the prototype drew them on. */
private const val DRAWN_ON = 100f
private const val COIN_SIDES = 2
private const val COIN_RADIUS = 0.44f
private const val INK_WIDTH = 0.06f
