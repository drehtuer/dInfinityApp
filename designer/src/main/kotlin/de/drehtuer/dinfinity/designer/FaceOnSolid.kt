package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
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
 * Where a canvas lands in the atlas cell of the face it was drawn for.
 *
 * A turn and a size about the middle of the cell, written as the two numbers
 * a rotation matrix needs rather than as an angle: an angle would be turned
 * back into these by every caller, and a sine and a cosine that have been
 * round-tripped through `atan2` are two rounding errors nobody asked for.
 *
 * It is a *similarity* and nothing more, on purpose. A drawing may come out
 * turned or a little large; it may not come out squashed. Where the canvas
 * outline really is the face's own shape — which is every outline but the
 * kite — a similarity lands it exactly, and where it is not, stretching one
 * into the other would distort every line somebody drew rather than leave a
 * margin (`docs/TODO.md`, 4.6).
 *
 * Not a `data class`, for the reason [FaceBasis] is not one: it is a frame a
 * drawing is put through, never a value anything compares, copies or prints.
 *
 * @param across how far one whole canvas width goes across the cell.
 * @param twist and how far it goes down it, which is the turn.
 * @param origin the canvas point that sits in the middle of the cell.
 */
class CellFit(
  val across: Double,
  val twist: Double,
  val origin: Dot,
) {
  /** How much bigger the drawing is in the cell than it was on the canvas. */
  val scale: Double get() = hypot(across, twist)

  /** Where [dot] — a fraction of the canvas — lands in the cell, `0..1`. */
  fun of(dot: Dot): Dot {
    val x = (dot.x - origin.x).toDouble()
    val y = (dot.y - origin.y).toDouble()
    return Dot(
      x = (MIDDLE + across * x + twist * y).toFloat(),
      y = (MIDDLE - twist * x + across * y).toFloat(),
    )
  }

  companion object {
    /** The middle of a cell, which every fit turns about. */
    const val MIDDLE: Double = 0.5

    /**
     * The canvas copied in as it is, which is what a cell with no corners to
     * match gets — and what the exporter used to do to every cell.
     */
    val SQUARE_ON: CellFit = CellFit(across = 1.0, twist = 0.0, origin = Dot(MIDDLE.toFloat(), MIDDLE.toFloat()))
  }
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
    val fit = fitFor(canvas, face, origin)
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

  /**
   * The same answer in the flat: where a canvas masked into [outline] lands
   * in [face]'s own **atlas cell**.
   *
   * [basisOf] puts the drawing on the solid, for the Solid tab to look at.
   * This puts it in the picture, for the exporter to paint — and until it
   * existed the exporter did neither, copying the canvas into the cell square
   * on and leaving the two halves of the designer showing different dice.
   *
   * The cell is sampled in the face's own frame ([SolidFace.cellOf]), so what
   * comes back is that frame composed with the fit: one turn and one scale
   * about the middle of the cell, which is all a similarity in the plane can
   * be. The turn is what the canvas's canonical outline is off by — nought on
   * a d6, up to sixty degrees on a d20 and a hundred and fifty-four on a d10
   * — and the scale is the canvas's 0.48 radius against the cell's half,
   * which is the thin bare rim every shape used to come out with
   * (`docs/TODO.md`, 4.6).
   *
   * **It moves the drawing, not the atlas.** Which cell a face is, and how a
   * cell is read, are unchanged and cannot change: every set ever published
   * is painted to that rule (`docs/dice-sets.md`, "Up is `+z`"). What was
   * wrong was only where this app put its own ink.
   */
  fun cellFitOf(
    face: SolidFace,
    outline: FaceOutline,
  ): CellFit {
    val canvas = FaceShapes.corners(outline)
    // A canvas with no corners to match is copied in square on, which for the
    // disc is right: its circle is the cell's circle whichever way it is
    // turned.
    if (canvas.size != face.corners.size) return CellFit.SQUARE_ON
    val origin = middleOf(canvas)
    val drawn = flipped(canvas, origin)
    val real = face.corners.map(face::flatOf)
    val turn = bestFit(drawn, real).turn
    // **The size is the one that covers, not the one that fits best.** A mask
    // smaller than the polygon the die shows leaves bare resin round the edge
    // of every face, with the printed label showing through it, and that is a
    // worse answer than a drawing a little larger than it was meant to be:
    // what falls outside the real polygon is on no face and is never sampled.
    // For the regular outlines the two sizes are the same number, because the
    // canvas polygon and the face are the same shape; for a kite they are not
    // (`docs/TODO.md`, 4.6).
    // Turning the face back by the fit rather than the drawing forwards by
    // it: the size needed is the same either way round, and this leaves the
    // canvas's own corners untouched.
    val back = real.map { (x, y) -> x * cos(turn) + y * sin(turn) to y * cos(turn) - x * sin(turn) }
    val size = covering(drawn, back)
    // `cellOf` measures in halves of the face's own radius, so the scale that
    // carries the canvas onto the face carries it into the cell divided by
    // the whole of that.
    val perCell = size / (2 * face.radius)
    return CellFit(across = perCell * cos(turn), twist = perCell * sin(turn), origin = origin)
  }

  /**
   * The smallest size at which [drawn] contains every one of [real].
   *
   * Both are convex and both are written about their own middle, so a corner
   * is inside the polygon exactly when it is on the inner side of all of its
   * edges. How far *past* an edge a corner reaches is the ratio of the two
   * supports along that edge's normal, and the size needed is the largest
   * such ratio over every corner and every edge.
   *
   * Internal rather than private so that the two cases the catalogue's own
   * polygons never produce — a boundary wound the other way, and an edge that
   * passes through the middle — are a test rather than a claim in a comment.
   */
  internal fun covering(
    drawn: List<Pair<Double, Double>>,
    real: List<Pair<Double, Double>>,
  ): Double {
    var needed = 0.0
    drawn.indices.forEach { at ->
      val (ax, ay) = drawn[at]
      val (bx, by) = drawn[(at + 1) % drawn.size]
      // The edge's normal, pointed outwards. Which of the two perpendiculars
      // that is depends on which way round the polygon is wound, and the sign
      // of the edge's own height is what says: a normal pointing inwards
      // makes it negative.
      val turn = if ((by - ay) * ax + (ax - bx) * ay < 0) -1.0 else 1.0
      val nx = (by - ay) * turn
      val ny = (ax - bx) * turn
      val height = nx * ax + ny * ay
      if (height <= FLAT) return@forEach
      real.forEach { (x, y) -> needed = max(needed, (nx * x + ny * y) / height) }
    }
    return needed
  }

  /**
   * [canvas] about [origin], with the screen's downwards `y` turned over.
   *
   * The canvas counts down the screen and a face is looked at from outside,
   * so one of the two has to be turned over before the corners can be matched
   * — and turning it over reverses the order they are wound in.
   */
  private fun flipped(
    canvas: List<Dot>,
    origin: Dot,
  ): List<Pair<Double, Double>> =
    canvas.map { (it.x - origin.x).toDouble() to -(it.y - origin.y).toDouble() }.reversed()

  /**
   * The turn and the size that carry a canvas masked into [canvas], about
   * [origin], onto [face]'s real polygon.
   */
  private fun fitFor(
    canvas: List<Dot>,
    face: SolidFace,
    origin: Dot,
  ): Fit = bestFit(flipped(canvas, origin), face.corners.map(face::flatOf))

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

  /**
   * Below this an edge passes through the middle of its own polygon, which no
   * edge of a convex outline does — so it is a degenerate one to skip rather
   * than a divisor to trust.
   */
  private const val FLAT = 1e-12
}
