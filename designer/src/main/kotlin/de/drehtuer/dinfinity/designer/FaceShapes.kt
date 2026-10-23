package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Where an outline's corners are on a square canvas, and where a guide sits.
 *
 * Arithmetic rather than drawing, and separate for the reason `ChartShapes` is
 * separate: a `Canvas` draw lambda is the one place a test cannot reach, so
 * everything that can be *wrong* is kept out of it. What is left in the
 * composable is a path and a mask.
 *
 * Everything here is in fractions of the canvas, `0..1` on both axes, because
 * that is what a stroke is stored in — the same numbers, so a drawing and its
 * outline cannot come to disagree about where the edge is.
 */
object FaceShapes {
  /**
   * The corners of [outline], anticlockwise from the top.
   *
   * A circle has none: it is the one outline that is not a polygon, and
   * `corners` for it is empty rather than a many-sided approximation nobody
   * would draw with.
   */
  fun corners(outline: FaceOutline): List<Dot> =
    when (outline) {
      FaceOutline.Circle -> emptyList()
      FaceOutline.Triangle -> regular(sides = 3)
      FaceOutline.LongRectangle -> LONG_RECTANGLE
      FaceOutline.Square -> regular(sides = 4)
      FaceOutline.Pentagon -> regular(sides = 5)
      // A kite is not regular, and the d10's and the d18's are not the same
      // kite: each is its own face, measured off the solid.
      FaceOutline.PentagonalKite -> PENTAGONAL_KITE
      FaceOutline.EnneagonalKite -> ENNEAGONAL_KITE
    }

  /**
   * Where a guide number sits, and where a stamp of it lands: the middle of
   * the face, on every outline.
   */
  val middle: Dot get() = CENTRE

  /**
   * [face]'s own polygon laid on the canvas: a kite, short tip up.
   *
   * **The face, measured off the solid, rather than a kite somebody chose the
   * proportions of.** The canvas outline is the mask a drawing is clipped to,
   * and the exporter and the Solid tab carry it onto the real face with a turn
   * and a size and nothing else (`FaceOnSolid`). That lands exactly only when
   * the two are the same shape. One hand-drawn kite for both trapezohedra was
   * neither die's, and the difference came out as a drawing grown past its
   * face until it covered it — 1.20 times on a d10, 1.37 on a d18 — and
   * clipped at the tip (`docs/face-designer.md`, "Export details").
   *
   * The tips are told apart by their edges: the short tip is the corner whose
   * two edges are the shortest pair, and the long point is the corner across
   * from it. The kite is turned so the line between them is upright with the
   * short tip at the top — which puts the waist above the middle, the thing
   * that makes a d10 face read as a kite rather than a diamond — and sized so
   * its longer side spans what a regular outline's does, centred on the
   * canvas.
   *
   * Corners come back top, right, bottom, left: the order the canvas has
   * always wound its outlines in. Internal so a test can put a face of its own
   * through it; the canvas only ever asks it about the two kites.
   */
  internal fun kiteOf(face: SolidFace): List<Dot> {
    val flat = face.corners.map(face::flatOf)
    require(flat.size == KITE_CORNERS) { "a kite has $KITE_CORNERS corners, not ${flat.size}" }
    val short = flat.indices.minBy { at -> edgesAt(flat, at) }
    val far = (short + 2) % flat.size
    val (tipX, tipY) = flat[short]
    val (pointX, pointY) = flat[far]
    val height = hypot(tipX - pointX, tipY - pointY)
    val upX = (tipX - pointX) / height
    val upY = (tipY - pointY) / height
    // Across the axis and along it from the long point: the kite in a frame of
    // its own, before it is put on the canvas.
    val placed =
      flat.map { (x, y) ->
        val dx = x - pointX
        val dy = y - pointY
        (dx * upY - dy * upX) to (dx * upX + dy * upY)
      }
    val width = placed.maxOf { it.first } - placed.minOf { it.first }
    val scale = SPAN / max(height, width)
    val canvas =
      placed.map { (across, along) ->
        Dot(x = (HALF + across * scale).toFloat(), y = (HALF + (height / 2 - along) * scale).toFloat())
      }
    val (left, right) =
      listOf(canvas[(short + 1) % canvas.size], canvas[(short + KITE_CORNERS - 1) % canvas.size]).sortedBy(Dot::x)
    return listOf(canvas[short], right, canvas[far], left)
  }

  /**
   * [face]'s own rectangle laid on the canvas, its long side upright and
   * spanning what a regular outline does: measured off the solid like the
   * kites, so the mask is the face. Corners come back in the order the
   * square's do — top right, bottom right, bottom left, top left.
   */
  internal fun rectangleOf(face: SolidFace): List<Dot> {
    val flat = face.corners.map(face::flatOf)
    val across = flat.maxOf { it.first } - flat.minOf { it.first }
    val upwards = flat.maxOf { it.second } - flat.minOf { it.second }
    val long = max(across, upwards)
    val halfWide = (RADIUS * minOf(across, upwards) / long).toFloat()
    val halfTall = RADIUS.toFloat()
    val middle = CENTRE
    return listOf(
      Dot(middle.x + halfWide, middle.y - halfTall),
      Dot(middle.x + halfWide, middle.y + halfTall),
      Dot(middle.x - halfWide, middle.y + halfTall),
      Dot(middle.x - halfWide, middle.y - halfTall),
    )
  }

  /** How long the two edges meeting at corner [at] of [polygon] are together. */
  private fun edgesAt(
    polygon: List<Pair<Double, Double>>,
    at: Int,
  ): Double {
    val (x, y) = polygon[at]
    val (nextX, nextY) = polygon[(at + 1) % polygon.size]
    val (lastX, lastY) = polygon[(at + polygon.size - 1) % polygon.size]
    return hypot(nextX - x, nextY - y) + hypot(lastX - x, lastY - y)
  }

  /**
   * A regular polygon with [sides] corners, filling the canvas.
   *
   * An odd-sided one points upwards — a triangle and a pentagon both read that
   * way, and it is how a d20 and a d12 face are moulded. An even-sided one is
   * turned half a step so it sits on a **flat edge** instead: a square with a
   * corner at the top is a diamond, which is what a d6 face is not.
   */
  private fun regular(sides: Int): List<Dot> {
    val upright = -PI / 2
    val flatTopped = if (sides % 2 == 0) PI / sides else 0.0
    return (0 until sides).map { corner ->
      val angle = upright + flatTopped + TURN * corner / sides
      Dot(
        x = (CENTRE.x + RADIUS * cos(angle)).toFloat(),
        y = (CENTRE.y + RADIUS * sin(angle)).toFloat(),
      )
    }
  }

  private val CENTRE = Dot(0.5f, 0.5f)
  private const val RADIUS = 0.48
  private const val TURN = 2 * PI
  private const val HALF = 0.5

  /** How far a kite reaches along its longer side: the 0.96 every regular outline spans. */
  private const val SPAN = 2 * RADIUS

  private const val KITE_CORNERS = 4

  // Measured once: `corners` is asked every frame the canvas draws, and the
  // solid does not change between frames.
  private val PENTAGONAL_KITE: List<Dot> = kiteOf(SolidFaces.of(DieShape.PentagonalTrapezohedron).first())
  private val ENNEAGONAL_KITE: List<Dot> = kiteOf(SolidFaces.of(DieShape.EnneagonalTrapezohedron).first())
  private val LONG_RECTANGLE: List<Dot> = rectangleOf(SolidFaces.of(DieShape.ElongatedSquareBipyramid).first())
}
