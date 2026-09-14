package de.drehtuer.dinfinity.feature.designer

import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.FaceOutline
import de.drehtuer.dinfinity.designer.GuideSpot
import kotlin.math.PI
import kotlin.math.cos
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
      // A kite is not regular: two short edges at the top, two long ones to
      // the point. The waist sits above the middle, which is what makes a d10
      // face read as a kite rather than a diamond.
      FaceOutline.Kite ->
        listOf(Dot(HALF, TOP), Dot(RIGHT, WAIST), Dot(HALF, BOTTOM), Dot(LEFT, WAIST))
    }

  /**
   * Where a guide number sits on [outline].
   *
   * [GuideSpot.Middle] is the middle of the face. The three corner spots are
   * a triangle's corners, pulled in towards the centre so a number sits
   * *inside* the shape rather than on its edge — which is where a moulded d4
   * has them (`docs/dice-sets.md`, "The d4").
   *
   * A corner spot on an outline that is not a triangle has nowhere sensible to
   * go, and gets the middle: it cannot happen from a real die — only a
   * tetrahedron reads from its corners, and a tetrahedron's cells are
   * triangles — and a guide in the middle is better than one off the canvas.
   */
  fun spot(
    outline: FaceOutline,
    spot: GuideSpot,
  ): Dot {
    if (spot == GuideSpot.Middle) return CENTRE
    val corners = corners(outline).takeIf { outline == FaceOutline.Triangle } ?: return CENTRE
    val corner = corners[CORNER_ORDER.getValue(spot)]
    return Dot(
      x = corner.x + (CENTRE.x - corner.x) * INSET,
      y = corner.y + (CENTRE.y - corner.y) * INSET,
    )
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

  /** How far a guide number is pulled in from its corner, as a fraction of the way to the centre. */
  private const val INSET = 0.28f

  private const val HALF = 0.5f
  private const val TOP = 0.04f
  private const val BOTTOM = 0.96f
  private const val LEFT = 0.16f
  private const val RIGHT = 0.84f

  /** Where the kite's two side corners sit: above the middle, which is what makes it a kite. */
  private const val WAIST = 0.38f

  private val CORNER_ORDER =
    mapOf(
      GuideSpot.FirstCorner to 0,
      GuideSpot.SecondCorner to 1,
      GuideSpot.ThirdCorner to 2,
    )
}
