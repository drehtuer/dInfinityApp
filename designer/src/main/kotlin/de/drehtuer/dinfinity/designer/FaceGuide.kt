package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.glyphs.BuiltinFont
import de.drehtuer.dinfinity.core.glyphs.Typeface
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Where a number sits on the canvas while a face is being drawn.
 *
 * Not pixels: the canvas is square and the outline is masked into it, so a
 * position is a place on the *shape* rather than a coordinate. What turns
 * these into pixels is the screen, which knows how big it is.
 */
enum class GuideSpot {
  /** The middle of the face, which is where a single number goes. */
  Middle,

  /** The three corners of a triangular cell, in the order its corners are numbered. */
  FirstCorner,
  SecondCorner,
  ThirdCorner,
}

/**
 * One number shown faintly under the drawing, as something to trace or ignore.
 *
 * **It is the numeral itself rather than a dot where the numeral goes.** It
 * used to be a dot: drawing text inside a `Canvas` wants a measurer, and the
 * screen had none. It has one now — `core/glyphs` holds the outlines the tray
 * prints with and solves where they sit on a face — so the guide is the very
 * shape "fill all faces with numbers" would put down, in the same place and
 * at the same size ([FaceStamp.printed]). Tracing it and stamping it cannot
 * come out differently, which a dot could only promise about the place.
 *
 * @param value what the die scores there. Kept beside the ink because it is
 *   the one thing about a face that is always a number, and what the strip
 *   under the canvas reads.
 * @param spot where on the face it sits.
 * @param rings the outline of the numeral, in fractions of the canvas, drawn
 *   as one shape under the even-odd rule so that the hole in a `0` stays open
 *   — the same rings a [Stamp] is made of. **A dot when there is nothing to
 *   draw**: a face an author left blank has no number, and the place is still
 *   worth showing.
 */
data class GuideMark(
  val value: Int,
  val spot: GuideSpot,
  val rings: List<List<Dot>>,
) {
  init {
    require(rings.isNotEmpty()) { "a guide with nothing to draw is a guide that was turned off" }
  }
}

/**
 * What the guide shows on one cell of a die being drawn
 * (`docs/face-designer.md`, "Flow"; `docs/dice-sets.md`, "The d4").
 *
 * For every shape but one this is a single number in the middle of the face:
 * the value that face scores, drawn faintly so somebody can trace it or turn
 * it off.
 *
 * **The d4 is the exception, and it is the reason this file exists.** A
 * tetrahedron resting on a face has no face pointing up, so its numbers belong
 * to *corners* and its cells are painted on faces. Cell `i` is the triangle
 * opposite corner `i`, so it carries the values of the other three corners,
 * one at each of its own corners. That is what a moulded d4 does: when a
 * corner points up, all three faces you can see carry that corner's number at
 * their apex.
 *
 * It follows that two cells sharing an edge must agree along it — both draw
 * the same value at each end — because the value belongs to the corner rather
 * than to either cell. `docs/TODO.md` asks for that to be "hard to do by
 * accident, not a warning afterwards", so **it is derived rather than
 * checked**, and derived *from the solid*: [SolidFaces] says which corner of
 * cell `i`'s real triangle is which readable position, and the guide puts
 * that position's number there ([cornersOf]). Two cells meeting along an edge
 * are asking about the same two corners of one tetrahedron, so there is no
 * second copy to disagree with. A wrong one cannot be drawn because it cannot
 * be said.
 *
 * Deriving it from the *index order* instead — the three faces that are not
 * this cell's, handed to the three corners as they come — is what this used
 * to do, and it is the kind of rule that is right often enough to look right:
 * it agrees with the tetrahedron on one edge in six.
 */
object FaceGuide {
  /**
   * The marks for cell [cell] of [die].
   *
   * @param face the typeface the numerals are traced from. The built-in one,
   *   always, in the app — it is a parameter so that a test can ask what the
   *   guide does for a face whose numeral the font has no outline for.
   * @return one mark for most shapes; three for a tetrahedron read from its
   *   vertices. Empty when [cell] is not a cell of this die.
   */
  fun of(
    die: Die,
    cell: Int,
    face: Typeface = BuiltinFont.face,
  ): List<GuideMark> {
    if (cell !in die.faces.indices) return emptyList()
    val printed = FaceStamp.printed(die, cell, face).associateBy(Numbering::spot)
    val outline = FaceOutline.of(die.shape)
    return spots(die, cell).map { (index, spot) ->
      GuideMark(
        value = die.faces[index].value,
        spot = spot,
        rings = printed[spot]?.let { FaceStamp.rings(it.text, it.at, face) }?.ifEmpty { null } ?: dot(outline, spot),
      )
    }
  }

  /**
   * True when this die's numbers belong to corners rather than faces.
   *
   * Both halves are asked. The shape says a tetrahedron *can* be read from a
   * vertex; the die's own `read` says whether it is — a set may paint a
   * tetrahedron to be read face-up instead, and then its cells carry one
   * number each like any other die (`docs/dice-sets.md`).
   */
  fun isCornerRead(die: Die): Boolean = die.shape == DieShape.Tetrahedron && die.read == FaceRead.VertexUp

  /**
   * Which face each of this cell's numbers comes from, and where it sits.
   *
   * One for a face-read solid — its own cell, in the middle — and a d4's three
   * corners for a die read from its vertices.
   */
  private fun spots(
    die: Die,
    cell: Int,
  ): List<Pair<Int, GuideSpot>> = if (isCornerRead(die)) cornersOf(die, cell) else listOf(cell to GuideSpot.Middle)

  /**
   * A filled dot at [spot], which is what the guide shows where there is no
   * number to draw.
   *
   * A face an author deliberately left blank is the case that reaches this: a
   * Fudge die's nought carries nothing, and printing a `0` on it would be the
   * app arguing with the set file (`FaceLabel.textOf`). The place is still
   * worth showing, so the dot the guide used to be everywhere survives here.
   *
   * A ring rather than a circle the screen draws, so that a guide is one kind
   * of thing however it came about and the draw lambda has no branch in it —
   * which is where a branch would be untestable.
   */
  private fun dot(
    outline: FaceOutline,
    spot: GuideSpot,
  ): List<List<Dot>> {
    val at = FaceShapes.spot(outline, spot)
    return listOf(
      (0 until DOT_SIDES).map { side ->
        val angle = TURN * side / DOT_SIDES
        Dot(x = at.x + (DOT_RADIUS * cos(angle)).toFloat(), y = at.y + (DOT_RADIUS * sin(angle)).toFloat())
      },
    )
  }

  /**
   * Which catalogue face each corner of cell [cell] reads, in spot order.
   *
   * Cell `i` is the triangle *opposite* corner `i`, so its corners are the
   * three that are not `i`, and each carries that corner's own face —
   * `faces[c]`, because a vertex-read die indexes its faces by vertex.
   *
   * **Which of the three goes where is read off the solid, not off the index
   * order.** It used to be the latter: the remaining three face indices handed
   * to the three spots as they came, which is a rule about arithmetic rather
   * than about a tetrahedron. It agrees with the solid on one of the six edges
   * and puts the other two corners the wrong way round on half the cells, so a
   * d4 drawn from the guide met its neighbour's number along one edge and two
   * strangers along the rest — which is what a phone showed
   * (`docs/face-designer.md`, "The d4 rule is derived, not checked").
   *
   * So it asks `simulation/api` instead. [SolidFaces] says which corner of the
   * real polygon is which readable position ([SolidFace.cornerReads]) — the
   * same answer the tray prints from — and where that corner lands in the cell
   * ([SolidFace.cellOf]), which is the cell the canvas is copied into whole
   * ([AtlasCell.at]). Pairing those against the canvas's own corners is all
   * that is left, and two cells sharing an edge cannot disagree along it
   * because they are asking about the same two corners of one solid.
   *
   * The faces rather than their values, because what is *printed* at a corner
   * is the face's label and only the guide reduces it to a number
   * (`FaceStamp.numbers`).
   */
  fun cornersOf(
    die: Die,
    cell: Int,
  ): List<Pair<Int, GuideSpot>> {
    if (!isCornerRead(die) || cell !in die.faces.indices) return emptyList()
    val surface = SolidFaces.of(die.shape)[cell]
    val places = SPOTS.map { FaceShapes.corner(FaceOutline.of(die.shape), it) }
    val wound = surface.corners.indices.sortedBy { round(surface.cellOf(surface.corners[it])) }
    val turn = alignment(wound.map { surface.cellOf(surface.corners[it]) }, places)
    return SPOTS.mapIndexed { at, spot -> surface.cornerReads[wound[(at + turn) % wound.size]] to spot }
  }

  /**
   * How far round the cell's corners have to be stepped to sit on the canvas's.
   *
   * Both lists are already in the same turning order — [round] sorts the
   * cell's corners the way a canvas outline's are written down — so all that
   * is unknown is where one starts against the other, and the answer is the
   * step that leaves the corners nearest their places. A whole step is a third
   * of a turn and the cells of a tetrahedron are within a sixteenth of one, so
   * nothing here is a close call; a rotation is chosen rather than each corner
   * taking whatever place is nearest so that three corners always land in
   * three different places, however far out a future shape's cell is turned.
   */
  private fun alignment(
    corners: List<Pair<Double, Double>>,
    places: List<Dot>,
  ): Int =
    corners.indices.minBy { turn ->
      places.indices.sumOf { at ->
        val (u, v) = corners[(at + turn) % corners.size]
        hypot(u - places[at].x, v - places[at].y)
      }
    }

  /** Where a point in the cell sits around its middle, for putting corners in turning order. */
  private fun round(cell: Pair<Double, Double>): Double = atan2(cell.second - MIDDLE, cell.first - MIDDLE)

  private val SPOTS = listOf(GuideSpot.FirstCorner, GuideSpot.SecondCorner, GuideSpot.ThirdCorner)

  /** The middle of a cell, which is the middle of the canvas copied into it. */
  private const val MIDDLE = 0.5

  /** How big the dot is, as a fraction of the canvas — what the screen drew before. */
  private const val DOT_RADIUS = 0.05

  /** Enough sides that a dot this small reads as round. */
  private const val DOT_SIDES = 16

  private const val TURN = 2 * PI
}
