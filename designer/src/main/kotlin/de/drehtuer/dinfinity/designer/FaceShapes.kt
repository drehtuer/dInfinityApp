package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.glyphs.LabelRoom
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
      FaceOutline.Square -> regular(sides = 4)
      FaceOutline.Pentagon -> regular(sides = 5)
      // A kite is not regular, and the d10's and the d18's are not the same
      // kite: each is its own face, measured off the solid.
      FaceOutline.PentagonalKite -> PENTAGONAL_KITE
      FaceOutline.EnneagonalKite -> ENNEAGONAL_KITE
    }

  /**
   * Where a guide number sits on [outline], and where a stamp of it lands.
   *
   * [GuideSpot.Middle] is the middle of the face. The three corner spots are
   * a triangle's corners, pulled in towards the centre so a number sits
   * *inside* the shape rather than on its edge — which is where a moulded d4
   * has them (`docs/dice-sets.md`, "The d4").
   *
   * **How far in is `core/glyphs`' answer** ([LabelRoom.inside]) rather than
   * one of this object's own, because the same pull-in decides where the tray
   * prints a d4's numbers and where "fill all faces with numbers" puts them.
   * A guide somebody traces and the number a stamp then drops in its place
   * have to be the same place (`docs/face-designer.md`, "The stamp").
   */
  fun spot(
    outline: FaceOutline,
    spot: GuideSpot,
  ): Dot {
    val corner = corner(outline, spot)
    val (x, y) = LabelRoom.inside(corner.x.toDouble() to corner.y.toDouble())
    return Dot(x = x.toFloat(), y = y.toFloat())
  }

  /**
   * The corner of [outline] a spot belongs to, before it is pulled inwards.
   *
   * A corner spot on an outline that is not a triangle has nowhere sensible to
   * go, and gets the middle: it cannot happen from a real die — only a
   * tetrahedron reads from its corners, and a tetrahedron's cells are
   * triangles — and a guide in the middle is better than one off the canvas.
   */
  fun corner(
    outline: FaceOutline,
    spot: GuideSpot,
  ): Dot {
    if (spot == GuideSpot.Middle) return CENTRE
    val corners = corners(outline).takeIf { outline == FaceOutline.Triangle } ?: return CENTRE
    return corners[CORNER_ORDER.getValue(spot)]
  }

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

  private val CORNER_ORDER =
    mapOf(
      GuideSpot.FirstCorner to 0,
      GuideSpot.SecondCorner to 1,
      GuideSpot.ThirdCorner to 2,
    )
}
