package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.glyphs.BuiltinFont
import de.drehtuer.dinfinity.core.glyphs.Typeface
import de.drehtuer.dinfinity.core.model.Die
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
  /**
   * The middle of the face, which is where every die's one number goes — the
   * d4's too, since it became a long die read from the face on top. Its three
   * corner spots went with the tetrahedron (decision 109).
   */
  Middle,
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
 * (`docs/face-designer.md`, "Flow"): a single number in the middle of the
 * face, the value that face scores, drawn faintly so somebody can trace it or
 * turn it off.
 */
object FaceGuide {
  /**
   * The marks for cell [cell] of [die].
   *
   * @param face the typeface the numerals are traced from. The built-in one,
   *   always, in the app — it is a parameter so that a test can ask what the
   *   guide does for a face whose numeral the font has no outline for.
   * @return one mark, or none when [cell] is not a cell of this die.
   */
  fun of(
    die: Die,
    cell: Int,
    face: Typeface = BuiltinFont.face,
  ): List<GuideMark> {
    if (cell !in die.faces.indices) return emptyList()
    val printed = FaceStamp.printed(die, cell, face).associateBy(Numbering::spot)
    val spot = GuideSpot.Middle
    return listOf(
      GuideMark(
        value = die.faces[cell].value,
        spot = spot,
        rings = printed[spot]?.let { FaceStamp.rings(it.text, it.at, face) }?.ifEmpty { null } ?: dot(),
      ),
    )
  }

  /**
   * A filled dot in the middle of the face, which is what the guide shows
   * where there is no number to draw.
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
  private fun dot(): List<List<Dot>> {
    val at = FaceShapes.middle
    return listOf(
      (0 until DOT_SIDES).map { side ->
        val angle = TURN * side / DOT_SIDES
        Dot(x = at.x + (DOT_RADIUS * cos(angle)).toFloat(), y = at.y + (DOT_RADIUS * sin(angle)).toFloat())
      },
    )
  }

  /** How big the dot is, as a fraction of the canvas — what the screen drew before. */
  private const val DOT_RADIUS = 0.05

  /** Enough sides that a dot this small reads as round. */
  private const val DOT_SIDES = 16

  private const val TURN = 2 * PI
}
