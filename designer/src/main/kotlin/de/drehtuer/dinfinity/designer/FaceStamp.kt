package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.glyphs.BuiltinFont
import de.drehtuer.dinfinity.core.glyphs.FaceLabel
import de.drehtuer.dinfinity.core.glyphs.LabelRoom
import de.drehtuer.dinfinity.core.glyphs.Placement
import de.drehtuer.dinfinity.core.glyphs.Typeface
import de.drehtuer.dinfinity.core.glyphs.Typesetter
import de.drehtuer.dinfinity.core.model.Die
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * How big a stamp is, as a multiple of the size that face would be printed at
 * (`docs/face-designer.md`, "The stamp").
 *
 * Three sizes rather than a slider, which is what the prototype offers
 * (`design/dInfinity.dc.html`, option `1v`), and they are multiples of the
 * *printed* size rather than fractions of the canvas: a number that fills a
 * d6's square would run off a d20's triangle, and "as big as this die's own
 * numbers" is the one size that means the same thing on every shape. The
 * largest still fits inside the face when it is stamped in the middle.
 */
enum class StampSize(
  val share: Double,
) {
  Small(SMALL),
  Medium(PRINTED),
  Large(LARGE),
}

private const val SMALL = 0.6
private const val PRINTED = 1.0
private const val LARGE = 1.4

/**
 * Putting a glyph of the built-in font on a face — by hand, or on every face
 * at once (`docs/face-designer.md`, "The stamp").
 *
 * **The font is the tray's and so is the placement.** `core/glyphs` holds the
 * outlines a die with no artwork is printed with, and
 * [de.drehtuer.dinfinity.core.glyphs.LabelRoom] solves how big a label may be
 * on a face and where on that face it goes; the tray asks it about the
 * polygon its mesh draws and this asks it about the polygon the canvas is
 * masked into. A die drawn from "fill all faces with numbers" and the same die
 * printed by the tray therefore agree about where a `6` sits, which they could
 * not be relied on to do if each had solved it for itself
 * (`docs/architecture.md`, decision 45).
 *
 * All of it is arithmetic over the stored dots, so all of it is tested here
 * rather than through a canvas (`docs/TODO.md`, "Coverage").
 */
object FaceStamp {
  /**
   * The glyphs of [text] as one mark, or null when there is nothing to put
   * down.
   *
   * Nothing to put down is a real answer rather than a failure: the font draws
   * digits, two signs, a times, a per cent and a full stop, and a label using
   * anything else is refused whole rather than stamped as the half of it the
   * font happens to have ([BuiltinFont.canDraw]).
   */
  fun of(
    text: String,
    at: Placement,
    colorArgb: Int,
    face: Typeface = BuiltinFont.face,
  ): Stamp? {
    val rings =
      Typesetter
        .lay(text, at, face)
        .map { contour -> contour.toList().chunked(2) { Dot(x = it[0].toFloat(), y = it[1].toFloat()) } }
        .filter { it.size >= CORNERS_OF_A_RING }
    return if (rings.isEmpty()) null else Stamp(rings = rings, colorArgb = colorArgb)
  }

  /**
   * A stamp of [text] centred on [point], at [size].
   *
   * Where the finger went, at a size measured against what this face's own
   * numbers would be — so the same press puts down the same-looking number on
   * a d6 and on a d20, and the mask clips whatever a stamp put down near an
   * edge leaves hanging over it, exactly as it clips a turned paste
   * (`FaceTransform`).
   */
  fun at(
    text: String,
    point: Dot,
    outline: FaceOutline,
    size: StampSize,
    colorArgb: Int,
  ): Stamp? {
    val printed = LabelRoom.centred(corners(outline), text) ?: return null
    return of(
      text = text,
      at =
        Placement(
          centreX = point.x.toDouble(),
          centreY = point.y.toDouble(),
          height = size.share * printed.height,
        ),
      colorArgb = colorArgb,
    )
  }

  /**
   * What "fill all faces with numbers" puts on cell [cell] of [die]: the
   * number the tray would print there, where the tray would print it.
   *
   * One mark for most dice. **Three for a d4 read from its corners**, one at
   * each corner of the triangle, turned to face its own corner — because a
   * d4's values belong to corners rather than to faces and every cell carries
   * the three it meets (`docs/dice-sets.md`, "The d4"). They land exactly
   * where the guide already showed them, because both are
   * [LabelRoom.inside] of the same corner.
   */
  fun numbers(
    die: Die,
    cell: Int,
    colorArgb: Int,
    face: Typeface = BuiltinFont.face,
  ): List<Mark> =
    when {
      cell !in die.faces.indices -> emptyList()
      FaceGuide.isCornerRead(die) -> atCorners(die, cell, colorArgb, face)
      else -> inTheMiddle(die, cell, colorArgb, face)
    }

  /** The one number a face-read solid carries, in the middle of its cell. */
  private fun inTheMiddle(
    die: Die,
    cell: Int,
    colorArgb: Int,
    face: Typeface,
  ): List<Mark> {
    val text = FaceLabel.textOf(die.faces[cell])
    val placement =
      LabelRoom.centred(
        corners = corners(FaceOutline.of(die.shape)),
        text = text,
        face = face,
        underlined = FaceLabel.isAmbiguous(text, die),
      ) ?: return emptyList()
    return listOfNotNull(of(text, placement, colorArgb, face))
  }

  /** The three a d4 carries, one at each corner, turned to face it. */
  private fun atCorners(
    die: Die,
    cell: Int,
    colorArgb: Int,
    face: Typeface,
  ): List<Mark> {
    val outline = FaceOutline.of(die.shape)
    val corners = corners(outline)
    return FaceGuide.cornersOf(die, cell).mapNotNull { (index, spot) ->
      val text = FaceLabel.textOf(die.faces[index])
      val corner = FaceShapes.corner(outline, spot)
      LabelRoom
        .cornered(
          corners = corners,
          corner = corner.x.toDouble() to corner.y.toDouble(),
          text = text,
          face = face,
          underlined = FaceLabel.isAmbiguous(text, die),
        )?.let { of(text, it, colorArgb, face) }
    }
  }

  /**
   * [draft] with every face that carries no stamp given its own number.
   *
   * **It leaves stamped faces alone**, so a second press changes nothing and a
   * face somebody has already lettered by hand is not written over. Faces that
   * were drawn on but not stamped *are* filled: the number lands over the
   * drawing the way a paste does, which is what "a starting point" means here.
   *
   * It is one undoable step **per face** rather than one for the die: undo
   * belongs to the face it was made on (`FaceDrawing`), so the number comes
   * off the face in front of the player with one press and off the rest as
   * they are reached.
   */
  fun fill(
    draft: Draft,
    colorArgb: Int,
    face: Typeface = BuiltinFont.face,
  ): Draft =
    draft.die.faces.indices.fold(draft) { so, cell ->
      if (so.face(cell).marks.any { it is Stamp }) {
        so
      } else {
        so.onFace(cell) { it.paste(numbers(draft.die, cell, colorArgb, face)) }
      }
    }

  /**
   * What the stamp is loaded with when the face in front of the player
   * changes: that face's own number.
   *
   * The commonest thing anybody stamps is the number that belongs there, and
   * the second commonest is a small edit of it, which is why it is offered as
   * a text somebody can change rather than as a fixed list of glyphs.
   */
  fun textOn(
    die: Die,
    cell: Int,
  ): String = if (cell in die.faces.indices) FaceLabel.textOf(die.faces[cell]) else ""

  /**
   * The polygon a label is judged against on a cell shaped like [outline].
   *
   * The canvas's own outline, in its own fractions, so what is stamped is held
   * to the same edge the drawing is masked to. **A disc is given corners**
   * here although `FaceShapes` refuses it one: the coin's mesh is a
   * twenty-four-sided prism and its cell is that polygon, so the number a
   * stamp solves against is the one the tray solves against rather than a
   * circle neither of them draws.
   */
  fun corners(outline: FaceOutline): List<Pair<Double, Double>> {
    val drawn = FaceShapes.corners(outline)
    if (drawn.isNotEmpty()) return drawn.map { it.x.toDouble() to it.y.toDouble() }
    return (0 until COIN_SIDES).map { side ->
      val angle = TURN * side / COIN_SIDES
      HALF + HALF * cos(angle) to HALF + HALF * sin(angle)
    }
  }

  /** How many sides the coin in the catalogue actually has (`simulation/api`'s `ShapeGeometry`). */
  private const val COIN_SIDES = 24

  /** Three corners is the fewest that can enclose anything. */
  private const val CORNERS_OF_A_RING = 3

  private const val HALF = 0.5
  private const val TURN = 2 * PI
}
