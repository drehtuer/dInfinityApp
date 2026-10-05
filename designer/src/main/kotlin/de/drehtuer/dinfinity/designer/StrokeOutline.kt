package de.drehtuer.dinfinity.designer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A stroke of the pen as the closed shapes its ink covers
 * (`docs/face-designer.md`, "What the Solid view shows").
 *
 * The flat canvas draws a stroke as a line of a width, which is what a
 * `Canvas` can do and what a stroke is. The Solid tab cannot: a width laid on
 * a tilted face is wider one way than the other, and a line of one width
 * drawn over it would be a picture of a different stroke. What it *can* draw
 * is a closed shape, because a closed shape under a projection is still a
 * closed shape with its corners where they belong — so a stroke is handed to
 * it as the shapes its ink covers: a disc at every dot (the round cap and the
 * round join the canvas draws) and a quadrilateral along every segment.
 *
 * **Every ring is wound the same way**, so the pieces are drawn together under
 * the non-zero rule and come out as their union: where two of them overlap the
 * winding is two rather than nought, and nothing is cut out of the line where
 * it crosses itself. A projection turns every ring of one face the same way,
 * so it keeps that.
 */
object StrokeOutline {
  /** How many corners a dot's disc is drawn with. */
  const val DISC_CORNERS: Int = 12

  /**
   * The closed rings [stroke]'s ink covers, in fractions of the canvas, every
   * one of them wound anticlockwise on the page (`x` right, `y` down).
   *
   * Dots closer together than a quarter of the nib are dropped first, keeping
   * the first and the last: a finger samples far more often than a line
   * bends, and every dot kept is a disc and a segment more for the stage to
   * draw on every frame it turns.
   */
  fun ringsOf(stroke: Stroke): List<List<Dot>> {
    val radius = stroke.width / 2
    val dots = thinned(stroke.dots, stroke.width / THIN_SHARE)
    if (dots.isEmpty() || radius <= 0f) return emptyList()
    val discs = dots.map { disc(it, radius) }
    val segments = dots.zipWithNext().mapNotNull { (from, to) -> segment(from, to, radius) }
    return discs + segments
  }

  /** [dots] with every one closer than [least] to the last one kept left out, but never the last. */
  internal fun thinned(
    dots: List<Dot>,
    least: Float,
  ): List<Dot> {
    if (dots.size <= 2) return dots
    val kept = mutableListOf(dots.first())
    dots.drop(1).dropLast(1).forEach { dot ->
      if (distance(kept.last(), dot) >= least) kept += dot
    }
    kept += dots.last()
    return kept
  }

  private fun disc(
    centre: Dot,
    radius: Float,
  ): List<Dot> =
    (0 until DISC_CORNERS).map { corner ->
      // Anticlockwise on a page whose `y` runs down is a falling angle.
      val angle = -TURN * corner / DISC_CORNERS
      Dot(x = centre.x + radius * cos(angle).toFloat(), y = centre.y + radius * sin(angle).toFloat())
    }

  /** The band [radius] either side of the segment from [from] to [to], or null for a segment of no length. */
  private fun segment(
    from: Dot,
    to: Dot,
    radius: Float,
  ): List<Dot>? {
    val length = distance(from, to)
    if (length == 0f) return null
    // The normal to the left of the direction of travel, a nib's half-width long.
    val nx = -(to.y - from.y) / length * radius
    val ny = (to.x - from.x) / length * radius
    val ring =
      listOf(
        Dot(from.x + nx, from.y + ny),
        Dot(to.x + nx, to.y + ny),
        Dot(to.x - nx, to.y - ny),
        Dot(from.x - nx, from.y - ny),
      )
    return if (areaOf(ring) < 0) ring else ring.reversed()
  }

  /**
   * Twice the signed area of [ring], by the shoelace: negative for a ring
   * wound anticlockwise on a page whose `y` runs down, which is the way every
   * disc here is wound.
   */
  internal fun areaOf(ring: List<Dot>): Float =
    ring.indices
      .sumOf { at ->
        val a = ring[at]
        val b = ring[(at + 1) % ring.size]
        (a.x * b.y - b.x * a.y).toDouble()
      }.toFloat()

  private fun distance(
    a: Dot,
    b: Dot,
  ): Float = hypot(b.x - a.x, b.y - a.y)

  /** Dots closer than a nib's width over this are one dot as far as the stage can tell. */
  private const val THIN_SHARE = 4f

  /** A whole turn, in radians. */
  private const val TURN = 2 * PI
}
