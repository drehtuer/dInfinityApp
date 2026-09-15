package de.drehtuer.dinfinity.core.glyphs

/**
 * The font a die is printed with when it has no artwork
 * (`docs/physics-and-rendering.md`, "Rendering").
 *
 * It is real Archivo, converted to outlines by `tools/generate-font.py` — the
 * same source and the same reason as the mark (`docs/assets/README.md`). Live
 * text would render in whatever font the device happens to have, and a traced
 * approximation would be somebody's guess at a typeface; neither is a thing to
 * print a number on a die with.
 *
 * It is a **resource on the classpath** rather than an Android asset, for the
 * reason [de.drehtuer.dinfinity.core.model.ShapeAtlas]'s neighbour
 * `BuiltinDiceSet` gives: it then loads the same way in a unit test, in an
 * instrumented test and in the app, and needs no `Context` to reach.
 *
 * What it can draw is deliberately small — digits, the two signs, a times, a
 * per cent and a full stop. A label using anything else is not drawn as a row
 * of blanks: the face's *value* is drawn instead, which is the one thing about
 * a face the app can always write down (see [canDraw]).
 */
object BuiltinFont {
  /** Where the outlines sit in this module's resources. */
  const val RESOURCE: String = "glyphs/builtin-font.txt"

  /**
   * The face, read once.
   *
   * Read on the first die drawn in a session and never again: the file is
   * twelve kilobytes and parsing it is a few milliseconds, but it is a few
   * milliseconds on the roll thread.
   */
  val face: Typeface by lazy { load() }

  /** The glyph for [character], or null when the font has none. */
  fun glyph(character: Char): Glyph? = face.glyphs[character]

  /** Whether every character of [text] can be drawn. */
  fun canDraw(text: String): Boolean = text.isNotEmpty() && text.all { it in face.glyphs }

  /**
   * Reads the outlines off the classpath.
   *
   * A failure here cannot be recovered from and is not a player's problem —
   * the app has no numbers to print — so it throws rather than degrading to
   * blank dice, which is a bug that would ship unnoticed.
   */
  fun load(): Typeface {
    val stream =
      BuiltinFont::class.java.classLoader?.getResourceAsStream(RESOURCE)
        ?: error("the built-in font is missing from the classpath at $RESOURCE")
    return stream.use { Typeface.parse(it.reader(Charsets.UTF_8).readText()) }
  }
}

/**
 * A font: which family and weight it was cut from, and what it can draw.
 *
 * @param family the typeface the outlines came from, for whoever regenerates
 *   them and for the licence note that has to name it.
 * @param weight the axis value it was instantiated at, for the same reason.
 * @param glyphs one entry per character the font can draw.
 */
data class Typeface(
  val family: String,
  val weight: Int,
  val glyphs: Map<Char, Glyph>,
) {
  init {
    require(glyphs.isNotEmpty()) { "a typeface with no glyphs cannot print anything" }
  }

  companion object {
    /**
     * The typeface [text] describes.
     *
     * The format is the one `tools/generate-font.py` writes and the file
     * itself documents: a `font` line, then a `glyph` line per character with
     * its advance, then `contour` lines each followed by the `x y` pairs of a
     * closed ring. Blank lines and `#` comments are ignored, and a row of
     * numbers may be folded over as many lines as it takes — which is what
     * keeps a twelve-kilobyte file inside the hundred-and-twenty-column rule
     * the linters hold everything else to.
     *
     * It throws on anything it does not understand. This file is generated
     * and checked in; a parser that shrugged at a malformed line would turn a
     * broken generator into dice with a digit missing.
     */
    fun parse(text: String): Typeface {
      val reading = Reading()
      text.lineSequence().map(String::trim).forEach(reading::line)
      return reading.finish()
    }
  }
}

/**
 * A font file being read, one line at a time.
 *
 * A class rather than a fold, because reading this format is a small state
 * machine — which glyph, which contour — and a state machine written as one
 * function is a function nobody can follow.
 */
private class Reading {
  private var family: String? = null
  private var weight: Int? = null
  private val glyphs = mutableMapOf<Char, Glyph>()
  private var character: Char? = null
  private var advance = 0.0
  private val contours = mutableListOf<DoubleArray>()
  private val points = mutableListOf<Double>()

  /** Takes one line of the file. Blank lines and comments are nothing to read. */
  fun line(line: String) {
    if (line.isEmpty() || line.startsWith("#")) return
    val words = line.split(' ')
    when (words[0]) {
      "font" -> font(words, line)
      "glyph" -> glyph(words, line)
      "contour" -> endContour()
      else -> words.forEach { points += it.toDoubleOrNull() ?: error("'$it' is not a number") }
    }
  }

  /** The typeface the file described, once every line of it has been read. */
  fun finish(): Typeface {
    endGlyph()
    return Typeface(
      family = family ?: error("the font file has no 'font' line"),
      weight = weight ?: error("the font file has no weight"),
      glyphs = glyphs,
    )
  }

  private fun font(
    words: List<String>,
    line: String,
  ) {
    require(words.size == WORDS) { "a font line is 'font <family> <weight>', not '$line'" }
    family = words[1]
    weight = words[2].toIntOrNull() ?: error("'${words[2]}' is not a weight")
  }

  private fun glyph(
    words: List<String>,
    line: String,
  ) {
    endGlyph()
    require(words.size == WORDS) { "a glyph line is 'glyph <hex> <advance>', not '$line'" }
    character = words[1].toIntOrNull(HEX)?.toChar() ?: error("'${words[1]}' is not a code point")
    advance = words[2].toDoubleOrNull() ?: error("'${words[2]}' is not an advance")
  }

  private fun endContour() {
    if (points.isEmpty()) return
    require(points.size % 2 == 0) { "a contour is made of x,y pairs, and this one has ${points.size} numbers" }
    contours += points.toDoubleArray()
    points.clear()
  }

  private fun endGlyph() {
    endContour()
    val at = character ?: return
    glyphs[at] = Glyph(advance = advance, contours = contours.toList())
    contours.clear()
    character = null
  }

  private companion object {
    /** Both the `font` and the `glyph` line are a keyword and two values. */
    const val WORDS = 3
    const val HEX = 16
  }
}
