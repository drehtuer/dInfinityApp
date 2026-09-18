package de.drehtuer.dinfinity.core.glyphs

import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a piece of text's ink lands, in the square it is being drawn into.
 *
 * The square is a cell of a die's texture atlas, so it runs `0..1` on both
 * axes with `(0, 0)` at the **top left** — image coordinates, the same way
 * `TextureCoordinate` counts (`docs/dice-sets.md`, "Shape catalogue"). Font
 * space has y up; [Typesetter] is where the two meet, so that nothing further
 * down has to remember which way round it is.
 *
 * @param centreX where the middle of the text goes, across the cell.
 * @param centreY the same, down it.
 * @param height how tall a digit is, as a fraction of the cell. The *figure*
 *   height rather than this string's own ink, so `1` and `8` come out the same
 *   size and a `0`'s overshoot does not shrink the face it is on.
 * @param turns how far the text is turned anticlockwise, in whole turns. Zero
 *   for every face-read solid; a d4's three numbers each face their own corner
 *   (`docs/dice-sets.md`, "The d4").
 * @param marked whether a full stop is printed after the text, so that a
 *   number which could be read as another number on the same die cannot be —
 *   `6.` against `9.`. What decides which faces need it is
 *   [FaceLabel.isAmbiguous], which is the only thing that knows what else is
 *   printed on the same die. It is the *text* that grows rather than a second
 *   shape drawn beside it, so a marked numeral is measured, centred and turned
 *   as one piece of writing.
 */
data class Placement(
  val centreX: Double,
  val centreY: Double,
  val height: Double,
  val turns: Double = 0.0,
  val marked: Boolean = false,
) {
  init {
    require(height > 0) { "text of height $height would draw nothing" }
  }
}

/**
 * Text into ink: the outlines of a string, placed in a cell.
 *
 * All of it is arithmetic over [Glyph]'s polygons, so all of it is testable on
 * a JVM — which is the point of the font being polygons at all. What comes out
 * is what [SignedDistanceField] turns into the field a shader reads.
 */
object Typesetter {
  /**
   * The outlines of [text], placed where [at] says, in cell coordinates.
   *
   * Returns nothing at all for text the font cannot draw. That is not this
   * object's decision to soften: whoever asked has a face's value to fall back
   * on and this does not (`BuiltinFont.canDraw`).
   */
  fun lay(
    text: String,
    at: Placement,
    face: Typeface = BuiltinFont.face,
  ): List<DoubleArray> {
    val glyphs = glyphsOf(printed(text, at.marked), face)
    val pens = pensOf(glyphs)
    val line = Line.of(glyphs, pens, at, face) ?: return emptyList()

    return glyphs.flatMapIndexed { index, glyph ->
      glyph.contours.map { points -> line.place(points, pens[index]) }
    }
  }

  /**
   * The mark on a number that could be read as another number on the same die.
   *
   * A trailing full stop rather than the bar underneath this used to draw
   * (`docs/dice-sets.md`, "Labels"): a bar is a second horizontal in a system
   * whose dice already have edges, and a dot is the one mark that cannot be
   * confused with the die. It is in the built-in font like any other character
   * (`docs/assets/README.md`), so nothing here draws a shape of its own.
   */
  const val MARK: String = "."

  /**
   * What is actually written for [text] when it is [marked], which is what
   * everything measuring or laying it out has to ask.
   *
   * One answer rather than a `+ "."` in four places: a `6.` is wider than a
   * `6`, and the room it needs on its face is solved from this
   * ([LabelRoom.centred]).
   */
  fun printed(
    text: String,
    marked: Boolean,
  ): String = if (marked) text + MARK else text

  /**
   * The glyphs of [text], or nothing at all when the font is missing any of
   * them.
   *
   * All or nothing rather than glyph by glyph: half a label is not a shorter
   * label, it is a wrong one, and the caller has a face's value to print
   * instead (`BuiltinFont.canDraw`).
   */
  private fun glyphsOf(
    text: String,
    face: Typeface,
  ): List<Glyph> = if (BuiltinFont.canDraw(text)) text.mapNotNull { face.glyphs[it] } else emptyList()

  /**
   * How wide [text] comes out at a given figure height, in the same units.
   *
   * Asked before the text is laid out, so that a four-character label can be
   * brought down to a size that fits its cell rather than printed over the
   * edge of it (`Face.MAX_LABEL_LENGTH`).
   */
  fun inkWidth(
    text: String,
    height: Double,
    face: Typeface = BuiltinFont.face,
  ): Double {
    val glyphs = glyphsOf(text, face)
    val ink = inkOf(glyphs, pensOf(glyphs)) ?: return 0.0
    return ink.width * height / face.figureHeight
  }

  /** Where each glyph's own origin sits along the line, in em units. */
  private fun pensOf(glyphs: List<Glyph>): List<Double> {
    var pen = 0.0
    return glyphs.map { glyph ->
      val here = pen
      pen += glyph.advance
      here
    }
  }

  /**
   * The ink of the whole line, in em units.
   *
   * Horizontally it is the ink, so that a `1` — which is narrow and sits well
   * inside its advance — is centred where the eye says it is rather than where
   * its metrics do. Vertically it is the figure box, which is the same for
   * every string and is what keeps the digits of a d20 the same size as those
   * of a d6.
   */
  private fun inkOf(
    glyphs: List<Glyph>,
    pens: List<Double>,
  ): Glyph.Bounds? =
    glyphs
      .mapIndexedNotNull { index, glyph ->
        glyph.inkBounds?.let { it.copy(minX = it.minX + pens[index], maxX = it.maxX + pens[index]) }
      }.reduceOrNull(Glyph.Bounds::plus)

  /**
   * One line of text being laid out: everything about it that is the same for
   * every contour of every glyph in it.
   *
   * A class rather than six arguments handed down, because that is what the
   * arguments were — a scale, a turn, where the middle of the ink sits, and
   * where it all lands.
   */
  private class Line(
    private val ink: Glyph.Bounds,
    private val at: Placement,
    face: Typeface,
  ) {
    private val figureHeight = face.figureHeight
    private val scale = at.height / figureHeight
    private val cosine = cos(at.turns * FULL_TURN)
    private val sine = sin(at.turns * FULL_TURN)

    /** [points], in font space, put where this line goes in the cell. */
    fun place(
      points: DoubleArray,
      pen: Double,
    ): DoubleArray {
      val out = DoubleArray(points.size)
      var index = 0
      while (index < points.size) {
        // Font space, with the line's middle brought to the origin.
        val x = (points[index] + pen - ink.centreX) * scale
        val y = (points[index + 1] - figureHeight / 2) * scale
        // Turned anticlockwise as seen on the face, which is clockwise in
        // image coordinates because they count downwards.
        out[index] = at.centreX + x * cosine - y * sine
        out[index + 1] = at.centreY - (x * sine + y * cosine)
        index += 2
      }
      return out
    }

    companion object {
      /** The line these glyphs make at this placement, or null when they have no ink. */
      fun of(
        glyphs: List<Glyph>,
        pens: List<Double>,
        at: Placement,
        face: Typeface,
      ): Line? = inkOf(glyphs, pens)?.let { Line(ink = it, at = at, face = face) }
    }
  }

  private const val FULL_TURN = 2 * Math.PI
}

/**
 * How tall a digit is, in em units: the ink of `1`, which has a flat top and a
 * flat foot and therefore no overshoot to make it disagree with the others.
 *
 * Every string is scaled by this rather than by its own ink, so a face reading
 * `0` is printed the same size as one reading `1` — an overshoot is a thing a
 * typeface does so that a round letter *looks* the same height, and scaling it
 * away would undo exactly that.
 */
val Typeface.figureHeight: Double
  get() = glyphs['1']?.inkBounds?.height ?: glyphs.values.mapNotNull { it.inkBounds?.height }.max()
