package de.drehtuer.dinfinity.core.glyphs

/**
 * One character of the built-in font, as closed polygons.
 *
 * Em units, baseline at zero, up positive — the space a typeface is drawn in,
 * kept rather than converted so that two characters beside each other line up
 * the way the designer of the typeface meant them to.
 *
 * Curves are already flattened: the outlines are generated from the font by
 * `tools/generate-font.py`, and a die's numbers become a signed distance field
 * once per die rather than being re-evaluated per frame, so a cubic on the
 * phone would buy arithmetic nobody can see
 * (`docs/physics-and-rendering.md`).
 *
 * @param advance how far the pen moves after drawing this character, in ems.
 * @param contours the outlines, each a closed ring of `x, y` pairs. A counter
 *   — the hole in a `0` — is a contour of its own, wound the other way, and
 *   [SignedDistanceField] uses the winding to tell ink from hole.
 */
data class Glyph(
  val advance: Double,
  val contours: List<DoubleArray>,
) {
  init {
    require(advance > 0) { "a glyph advances by something, not $advance" }
    require(contours.all { it.size >= POINTS_IN_A_TRIANGLE * 2 && it.size % 2 == 0 }) {
      "a contour is at least three x,y pairs"
    }
  }

  /** How far the ink reaches, as `minX, minY, maxX, maxY`, or null when there is none. */
  val inkBounds: Bounds? by lazy {
    if (contours.isEmpty()) {
      null
    } else {
      var minX = Double.MAX_VALUE
      var minY = Double.MAX_VALUE
      var maxX = -Double.MAX_VALUE
      var maxY = -Double.MAX_VALUE
      contours.forEach { points ->
        var index = 0
        while (index < points.size) {
          val x = points[index]
          val y = points[index + 1]
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
          index += 2
        }
      }
      Bounds(minX, minY, maxX, maxY)
    }
  }

  /** A rectangle in whatever space the thing it bounds is drawn in. */
  data class Bounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
  ) {
    /** How wide it is. Zero for a character with no ink, such as a space. */
    val width: Double get() = maxX - minX

    /** How tall it is. */
    val height: Double get() = maxY - minY

    /** The two together, as the middle of the rectangle. */
    val centreX: Double get() = (minX + maxX) / 2

    /** The same, up the page. */
    val centreY: Double get() = (minY + maxY) / 2

    /** The smallest rectangle holding both. */
    operator fun plus(other: Bounds): Bounds =
      Bounds(
        minX = minOf(minX, other.minX),
        minY = minOf(minY, other.minY),
        maxX = maxOf(maxX, other.maxX),
        maxY = maxOf(maxY, other.maxY),
      )
  }

  private companion object {
    const val POINTS_IN_A_TRIANGLE = 3
  }
}
