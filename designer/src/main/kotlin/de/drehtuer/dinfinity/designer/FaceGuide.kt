package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.glyphs.BuiltinFont
import de.drehtuer.dinfinity.core.glyphs.Typeface
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead
import kotlin.math.PI
import kotlin.math.cos
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
 * checked**: the guide for a cell is computed from the corner values, and
 * there is no way to express a d4 whose cells disagree. A wrong one cannot be
 * drawn because it cannot be said.
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
   * Which catalogue face each corner of cell [cell] reads, in corner order.
   *
   * Cell `i` is the triangle *opposite* corner `i`, so its corners are the
   * three that are not `i`, and each carries that corner's own face —
   * `faces[c]`, because a vertex-read die indexes its faces by vertex.
   *
   * Two cells sharing an edge share two corners, and both read those corners'
   * values from the same place. There is no second copy to disagree with.
   *
   * The faces rather than their values, because what is *printed* at a corner
   * is the face's label and only the guide reduces it to a number
   * (`FaceStamp.numbers`).
   */
  fun cornersOf(
    die: Die,
    cell: Int,
  ): List<Pair<Int, GuideSpot>> =
    if (!isCornerRead(die) || cell !in die.faces.indices) {
      emptyList()
    } else {
      die.faces.indices
        .filter { it != cell }
        .mapIndexed { position, corner -> corner to SPOTS[position] }
    }

  private val SPOTS = listOf(GuideSpot.FirstCorner, GuideSpot.SecondCorner, GuideSpot.ThirdCorner)

  /** How big the dot is, as a fraction of the canvas — what the screen drew before. */
  private const val DOT_RADIUS = 0.05

  /** Enough sides that a dot this small reads as round. */
  private const val DOT_SIDES = 16

  private const val TURN = 2 * PI
}
