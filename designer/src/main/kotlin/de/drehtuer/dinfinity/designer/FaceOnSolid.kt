package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Where a canvas lands on a real face of the solid.
 *
 * Two vectors and a point: [origin] is the canvas point that sits at the
 * middle of the face, [right] is what one whole canvas width goes to and
 * [down] is what one whole canvas height goes to. Everything the Solid tab
 * draws goes through this, so a fill, a stamp and the outline itself cannot
 * land in three different places.
 *
 * Not a `data class`: it is a frame a drawing is put through, never a value
 * anything compares, copies or prints.
 */
class FaceBasis(
  private val centre: Vector3,
  val right: Vector3,
  val down: Vector3,
  private val origin: Dot,
) {
  /** Where [dot] lands on the solid. */
  fun pointOf(dot: Dot): Vector3 = centre + right * (dot.x - origin.x).toDouble() + down * (dot.y - origin.y).toDouble()
}

/**
 * Putting a drawn face on the face it was drawn for
 * (`docs/face-designer.md`, "The solid, not just the face").
 *
 * The canvas masks every cell into the same canonical outline — a triangle on
 * its point, a square on an edge, a kite with its short tip up ([FaceShapes])
 * — while the real face is a polygon sitting at whatever angle the solid's own
 * construction left it at. Something has to say which corner of the drawing
 * belongs to which corner of the face, and this is it: the turn and the size
 * that carry the canonical outline onto the real polygon with the least left
 * over.
 *
 * **It is one solve rather than a rule per shape.** The design describes the
 * answer shape by shape — "a square face puts an edge up, a kite puts the
 * short tip on its own symmetry axis, a regular face takes its most upright
 * far vertex" — and every one of those falls out of matching the two polygons:
 * the square's edges land on edges because that is the turn that fits, and the
 * kite's tip lands on the tip because a kite is the one outline here whose
 * corners are not all the same distance from the middle. What is left is the
 * regular case, where every turn fits equally well and the tie is broken by
 * taking the one that puts the drawing's own up nearest the tray's.
 */
object FaceOnSolid {
  /**
   * The basis that puts a canvas masked into [outline] onto [face].
   *
   * A canvas whose outline is not a polygon — the d2's disc — has no corners
   * to match, so it is laid on the face's own frame with its circle on the
   * face's circle, which is what the atlas does with every cell
   * (`docs/dice-sets.md`, "Up is `+z`").
   */
  fun basisOf(
    face: SolidFace,
    outline: FaceOutline,
  ): FaceBasis {
    val canvas = FaceShapes.corners(outline)
    if (canvas.size != face.corners.size) return roundBasis(face)
    val origin = middleOf(canvas)
    // The canvas counts down the screen and a face is looked at from outside,
    // so one of the two has to be turned over before the corners can be
    // matched — and turning it over reverses the order they are wound in.
    val drawn = canvas.map { (it.x - origin.x).toDouble() to -(it.y - origin.y).toDouble() }.reversed()
    val real = face.corners.map(face::flatOf)
    val fit = bestFit(drawn, real)
    // The drawing turned by `fit.turn` and grown by `fit.scale`, written out
    // as where its own two axes end up: across the canvas, and down it — and
    // down the canvas is *against* the face's up, which is the turning-over
    // the corners were matched through.
    val across = fit.scale * cos(fit.turn)
    val twist = fit.scale * sin(fit.turn)
    return FaceBasis(
      centre = face.centre,
      right = face.along * across + face.up * twist,
      down = face.along * twist + face.up * -across,
      origin = origin,
    )
  }

  /** A canvas with no corners, laid on the face's own frame. */
  private fun roundBasis(face: SolidFace): FaceBasis {
    val scale = face.radius / HALF
    return FaceBasis(
      centre = face.centre,
      right = face.along * scale,
      down = face.up * -scale,
      origin = Dot(HALF.toFloat(), HALF.toFloat()),
    )
  }

  /** The middle of the drawn outline: the mean of its corners. */
  private fun middleOf(corners: List<Dot>): Dot =
    Dot(
      x = corners.map(Dot::x).average().toFloat(),
      y = corners.map(Dot::y).average().toFloat(),
    )

  /**
   * How far to turn the drawing and how much to grow it, over every way its
   * corners could be paired with the face's.
   *
   * There are as many pairings as there are corners — the drawing's first
   * corner can go to any of the face's, and the rest follow it round — and the
   * one that fits best is the one whose matched corners pull hardest in one
   * direction. Ties are what a regular polygon leaves, because every turn of
   * it is as good as every other; they are settled by [upright].
   */
  private fun bestFit(
    drawn: List<Pair<Double, Double>>,
    real: List<Pair<Double, Double>>,
  ): Fit {
    val spread = drawn.sumOf { (x, y) -> x * x + y * y }
    val fits = drawn.indices.map { start -> fitOf(drawn, real, start, spread) }
    val best = fits.maxOf(Fit::pull)
    return fits.filter { it.pull >= best - TIE }.maxBy { upright(it.turn) }
  }

  /**
   * The turn and the size that carry [drawn] onto [real] when the drawing's
   * first corner is paired with the face's corner [start].
   *
   * The two sums are the whole of it: how much the paired corners agree, and
   * how much they twist against each other. The angle between them is the turn
   * that lines them up, and how long the pair of them is says how much bigger
   * the face is than the drawing — and, across the pairings, which of them was
   * the right one.
   */
  private fun fitOf(
    drawn: List<Pair<Double, Double>>,
    real: List<Pair<Double, Double>>,
    start: Int,
    spread: Double,
  ): Fit {
    var agreement = 0.0
    var twist = 0.0
    drawn.forEachIndexed { corner, (x, y) ->
      val (u, v) = real[(corner + start) % real.size]
      agreement += x * u + y * v
      twist += x * v - y * u
    }
    val pull = hypot(agreement, twist)
    return Fit(turn = atan2(twist, agreement), scale = pull / spread, pull = pull)
  }

  /**
   * How upright a drawing turned by [turn] would stand, for settling a tie.
   *
   * The drawing's own up is `(0, 1)` in the face's frame before it is turned;
   * after it, its share of the face's up is the cosine of the turn. Taking the
   * largest is "the most upright far vertex" of the design's description, and
   * for a face that lies flat every turn scores the same and the first one
   * wins — which is the honest answer, because a face pointing straight up has
   * no up of its own to line anything against.
   */
  private fun upright(turn: Double): Double = cos(turn)

  /** One way of laying the drawing on the face, and how well it fits. */
  private data class Fit(
    val turn: Double,
    val scale: Double,
    val pull: Double,
  )

  /**
   * How near two pairings have to score before they count as equally good.
   *
   * A regular polygon's turns are exactly as good as each other in arithmetic
   * that is exact to the last few bits of a double, so this only has to be
   * bigger than that rounding.
   */
  private const val TIE = 1e-9

  private const val HALF = 0.5
}
