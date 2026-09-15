package de.drehtuer.dinfinity.core.glyphs

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Outlines into a signed distance field: for every pixel, how far it is from
 * the edge of the ink and which side of it it is on.
 *
 * This is why the numbers stay sharp. A rasterised digit is a picture of a
 * digit at one size, and a die is looked at from wherever the player pinches
 * to; a distance field is the *shape*, and the shader recovers a crisp edge
 * from it at whatever size the die happens to be drawn
 * (`docs/physics-and-rendering.md`, "Rendering").
 *
 * The field is a byte per pixel, and the edge is at [EDGE]: below it is
 * outside the ink, above it is inside. Distances are measured in cell widths
 * and clamped to [SPREAD], because a byte holds 256 values and spending them
 * on how far away the far corner of a cell is would spend them on nothing —
 * everything the shader does happens within a pixel or two of the edge.
 */
object SignedDistanceField {
  /** The value the edge of the ink sits at. Half a byte, so both sides get the same room. */
  const val EDGE: Int = 128

  /**
   * How far from the edge the field still says anything, as a fraction of the
   * cell.
   *
   * A sixteenth of a cell is four pixels at [DEFAULT_SIZE], which is more than
   * a fragment shader ever asks for and enough that a number could be given an
   * outline later without the field having to be rebuilt.
   */
  const val SPREAD: Double = 1.0 / 16.0

  /** How many pixels a cell is rasterised at. */
  const val DEFAULT_SIZE: Int = 64

  /**
   * The field for one cell, [size] by [size] pixels, row by row from the top.
   *
   * [contours] are closed rings of `x, y` pairs in cell coordinates — `0..1`
   * on both axes, `(0, 0)` at the top left, which is what [Typesetter] hands
   * out. Inside is decided by the **nonzero winding rule**, the rule the
   * outlines were drawn under, so the hole in a `0` is a hole rather than more
   * ink.
   *
   * A cell with no ink in it is every pixel at zero rather than an absence:
   * the atlas is one image and a face with nothing printed on it still has to
   * occupy its cell.
   */
  fun cell(
    contours: List<DoubleArray>,
    size: Int = DEFAULT_SIZE,
    spread: Double = SPREAD,
  ): ByteArray {
    require(size > 0) { "a cell of $size pixels has nothing in it" }
    require(spread > 0) { "a spread of $spread would divide by nothing" }
    val field = ByteArray(size * size)
    val edges = edgesOf(contours)
    if (edges.isEmpty()) return field

    val reach = bounds(edges, spread)
    val step = 1.0 / size
    for (row in 0 until size) {
      // The middle of the pixel, not its corner: a field sampled at the corner
      // is half a pixel out everywhere, which shows as numbers that sit low
      // and to the left.
      val y = (row + HALF) * step
      for (column in 0 until size) {
        val x = (column + HALF) * step
        field[row * size + column] =
          if (reach.holds(x, y)) value(x, y, edges, spread) else 0
      }
    }
    return field
  }

  /** Every edge of every contour, as `x0, y0, x1, y1` quadruples. */
  private fun edgesOf(contours: List<DoubleArray>): DoubleArray {
    val total = contours.sumOf { it.size }
    val edges = DoubleArray(total * 2)
    var out = 0
    contours.forEach { points ->
      val corners = points.size / 2
      for (corner in 0 until corners) {
        val next = (corner + 1) % corners
        edges[out++] = points[corner * 2]
        edges[out++] = points[corner * 2 + 1]
        edges[out++] = points[next * 2]
        edges[out++] = points[next * 2 + 1]
      }
    }
    return edges
  }

  /**
   * What the field says at one point.
   *
   * Two separate questions, deliberately: how far the nearest edge is, and
   * which side of the outline the point is on. Distance alone cannot tell the
   * inside of a `0` from its counter, and winding alone cannot say how far.
   */
  private fun value(
    x: Double,
    y: Double,
    edges: DoubleArray,
    spread: Double,
  ): Byte {
    var nearest = Double.MAX_VALUE
    var winding = 0
    var index = 0
    while (index < edges.size) {
      val ax = edges[index + FROM_X]
      val ay = edges[index + FROM_Y]
      val bx = edges[index + TO_X]
      val by = edges[index + TO_Y]
      nearest = minOf(nearest, squaredDistanceTo(x, y, ax, ay, bx, by))
      winding += crossings(x, y, ax, ay, bx, by)
      index += EDGE_NUMBERS
    }
    val distance = sqrt(nearest) / spread
    val signed = if (winding != 0) distance else -distance
    return (EDGE + signed * EDGE).coerceIn(0.0, MAX_BYTE).roundToInt().toByte()
  }

  /** The square of the distance from a point to a segment. Squared, to put off a `sqrt`. */
  @Suppress("LongParameterList")
  private fun squaredDistanceTo(
    x: Double,
    y: Double,
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
  ): Double {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val along =
      if (lengthSquared <= 0.0) 0.0 else (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    val offX = x - (ax + along * dx)
    val offY = y - (ay + along * dy)
    return offX * offX + offY * offY
  }

  /**
   * What this edge contributes to the winding number at the point: a ray cast
   * in the `+x` direction, counted up for an edge crossing it downwards and
   * down for one crossing it upwards.
   */
  @Suppress("LongParameterList")
  private fun crossings(
    x: Double,
    y: Double,
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
  ): Int {
    // A half-open test on y — `ay <= y < by` — is what stops a corner sitting
    // exactly on the ray being counted by both of the edges that meet there.
    if (ay <= y) {
      if (by > y && side(ax, ay, bx, by, x, y) > 0) return 1
    } else if (by <= y && side(ax, ay, bx, by, x, y) < 0) {
      return -1
    }
    return 0
  }

  /** Which side of the line through `a → b` the point is on. */
  @Suppress("LongParameterList")
  private fun side(
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
    x: Double,
    y: Double,
  ): Double = (bx - ax) * (y - ay) - (x - ax) * (by - ay)

  /**
   * The ink's own rectangle, grown by the spread.
   *
   * Most of a cell is nowhere near a number, and a pixel outside this
   * rectangle is outside the ink by more than the field can say — so it is
   * zero without any edge being looked at. It is the difference between
   * building a d20's atlas in a blink and building it slowly enough to see.
   */
  private fun bounds(
    edges: DoubleArray,
    spread: Double,
  ): Reach {
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    var index = 0
    while (index < edges.size) {
      val x = edges[index]
      val y = edges[index + 1]
      if (x < minX) minX = x
      if (x > maxX) maxX = x
      if (y < minY) minY = y
      if (y > maxY) maxY = y
      index += 2
    }
    return Reach(minX - spread, minY - spread, maxX + spread, maxY + spread)
  }

  private data class Reach(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
  ) {
    fun holds(
      x: Double,
      y: Double,
    ): Boolean = x >= minX && x <= maxX && y >= minY && y <= maxY
  }

  /** The field is a byte, and this is the largest one. */
  private const val MAX_BYTE = 255.0

  private const val HALF = 0.5

  /** An edge is stored as `x0, y0, x1, y1`, and these are where each of them sits. */
  private const val FROM_X = 0
  private const val FROM_Y = 1
  private const val TO_X = 2
  private const val TO_Y = 3
  private const val EDGE_NUMBERS = 4
}

/** How far into the ink a byte of the field is, `-1` at the spread outside to `1` inside. */
fun Byte.asDistance(): Double = ((toInt() and BYTE) - SignedDistanceField.EDGE) / SignedDistanceField.EDGE.toDouble()

/** Whether a byte of the field is inside the ink. */
fun Byte.isInk(): Boolean = (toInt() and BYTE) >= SignedDistanceField.EDGE

private const val BYTE = 0xFF
