package de.drehtuer.dinfinity.designer

import kotlin.math.abs
import kotlin.math.hypot

/**
 * A polygon in fractions of the canvas, and the two questions asked of one.
 *
 * Arithmetic in its own object for the reason `FaceShapes` is: a `Canvas` draw
 * lambda is the one place a test cannot reach, so everything that can be
 * *wrong* is kept out of it (`docs/TODO.md`, "Coverage").
 */
object Polygon {
  /**
   * True when [point] is inside [boundary].
   *
   * The even-odd rule, by casting a ray to the right and counting the edges it
   * crosses: an odd count is inside. It needs no winding direction and no
   * convexity, which matters because the boundary here is whatever shape
   * somebody's finger drew.
   *
   * A boundary of fewer than three dots encloses nothing and is never inside.
   */
  fun contains(
    boundary: List<Dot>,
    point: Dot,
  ): Boolean {
    if (boundary.size < CORNERS_OF_A_REGION) return false
    var inside = false
    var previous = boundary.last()
    boundary.forEach { corner ->
      val straddles = (corner.y > point.y) != (previous.y > point.y)
      if (straddles) {
        val cut = corner.x + (point.y - corner.y) / (previous.y - corner.y) * (previous.x - corner.x)
        if (point.x < cut) inside = !inside
      }
      previous = corner
    }
    return inside
  }

  /**
   * How much of the canvas [boundary] covers, as a fraction of it.
   *
   * The shoelace formula, unsigned: which way round the dots go is the
   * finger's business rather than the area's. A boundary of fewer than three
   * dots has none.
   */
  fun area(boundary: List<Dot>): Float {
    if (boundary.size < CORNERS_OF_A_REGION) return 0f
    var twice = 0f
    var previous = boundary.last()
    boundary.forEach { corner ->
      twice += previous.x * corner.y - corner.x * previous.y
      previous = corner
    }
    return abs(twice) / 2f
  }

  /** Three dots is the fewest that can enclose anything. */
  private const val CORNERS_OF_A_REGION = 3
}

/**
 * What the bucket leaves behind (`docs/face-designer.md`, "The fill bucket").
 *
 * A drawing here is vectors rather than pixels, so there is nothing to flood:
 * a fill is a region *added* to the drawing, and the only question the bucket
 * has to answer is which region. It answers it the way a raster flood fill
 * would look as though it had — the smallest closed shape the tap landed
 * inside, and the face itself when it landed on bare paper.
 *
 * All of it is arithmetic over the stored dots, so the bucket is tested here
 * rather than through a canvas.
 */
object FaceFill {
  /**
   * The whole cell: the canvas square.
   *
   * The square rather than a copy of the cell's outline, because every
   * renderer already clips the drawing to that outline — the screen and the
   * exporter both do — so a fill of the square *is* a fill of the face. A fill
   * carrying its own copy of the polygon could come to disagree with the mask,
   * and then the paper would be a different shape from the face.
   */
  val FACE: List<Dot> =
    listOf(Dot(0f, 0f), Dot(1f, 0f), Dot(1f, 1f), Dot(0f, 1f))

  /**
   * How near a stroke's two ends must be, as a fraction of the canvas, for it
   * to count as a shape somebody meant to close.
   *
   * A finger does not land back on the pixel it started from, and demanding
   * that it did would make the bucket useless. Eight hundredths is about a
   * fingertip on a phone-sized canvas.
   */
  const val CLOSE_ENOUGH: Float = 0.08f

  /**
   * The fill a tap at [point] makes on a face carrying [marks], in [colorArgb].
   *
   * The smallest closed pen stroke the tap is inside, or [FACE] when it is
   * inside none — which is the whole-face fill the design shows as the
   * bucket's ordinary use (`design/dInfinity.dc.html`, option `1v`).
   *
   * Smallest rather than first or last, because shapes nest: a tap in the eye
   * of a drawn skull is inside the eye and inside the skull, and what the
   * finger meant is the eye.
   *
   * **Eraser strokes do not bound a region and fills do not either.** The
   * eraser takes ink away rather than drawing a line to fill against, and a
   * fill is paper — filling inside the paper is what filling the face already
   * does.
   */
  fun at(
    point: Dot,
    marks: List<Mark>,
    colorArgb: Int,
  ): Fill {
    val region =
      marks
        .filterIsInstance<Stroke>()
        .filter { !it.erases && closed(it) && Polygon.contains(it.dots, point) }
        .minByOrNull { Polygon.area(it.dots) }
        ?.dots
        ?: FACE
    return Fill(dots = region, colorArgb = colorArgb)
  }

  /**
   * True when [stroke] comes back to where it started.
   *
   * Two dots cannot enclose anything however near their ends are, so a line
   * drawn back over itself is still a line.
   */
  fun closed(stroke: Stroke): Boolean {
    if (stroke.dots.size < DOTS_OF_A_SHAPE) return false
    val first = stroke.dots.first()
    val last = stroke.dots.last()
    return hypot(last.x - first.x, last.y - first.y) <= CLOSE_ENOUGH
  }

  /** The fewest dots a closed shape can have: three corners. */
  private const val DOTS_OF_A_SHAPE = 3
}
