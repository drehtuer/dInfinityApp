package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.glyphs.BuiltinFont
import de.drehtuer.dinfinity.core.glyphs.Placement
import de.drehtuer.dinfinity.core.glyphs.SignedDistanceField
import de.drehtuer.dinfinity.core.glyphs.Typeface
import de.drehtuer.dinfinity.core.glyphs.Typesetter
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * What is printed on a die that has no artwork
 * (`docs/physics-and-rendering.md`, "Rendering").
 *
 * The built-in set has no textures at all, so this is what the dice in
 * everybody's hand on the first launch are made of. It is laid out in the
 * shape's own atlas grid, cell by cell — the same grid an author's image fills
 * and the same grid the face designer draws into — so a printed die and a
 * drawn one are the same kind of surface with the same coordinates, and the
 * renderer samples them the same way (`docs/dice-sets.md`, "Shape catalogue").
 *
 * It is decided here and drawn nowhere: what comes out is a field of bytes,
 * and the only thing that needs a GPU is uploading it. That is the same line
 * [Stage] draws, for the same reason — where a `6` sits on a face is judgement
 * and belongs where a test can reach it.
 */
object DieNumbers {
  /**
   * How much of the room a face has, a number takes up.
   *
   * *Of the room the face actually has*, not of its cell. A cell is the circle
   * drawn round a face, and how much of one a face fills depends entirely on
   * what polygon it is — a dodecahedron's pentagon fills most of it, a d20's
   * triangle half of it, and a d18's kite a quarter. A number sized against
   * the cell therefore comes out right on a d6 and crowding the edges on a
   * d20, which is what it did on the Pixel 10a.
   *
   * So the size is solved rather than chosen: the largest box of this label's
   * own proportions that fits inside this face, times this fraction. What is
   * left to judge is the fraction — how much smaller than the room a numeral
   * should be — and that needs a phone (`docs/TODO.md`, Step 5.6).
   */
  const val FACE_SHARE: Double = 0.78

  /**
   * How tall a d4's numbers are, as a fraction of the cell.
   *
   * Of the cell rather than solved against the edges the way [FACE_SHARE] is,
   * because these do not sit in the middle of the face: three of them share
   * one triangle, each near its own corner, and what bounds them is each other
   * rather than the edges.
   */
  const val CORNER_HEIGHT: Double = 0.20

  /**
   * How far out from the middle of the cell a d4's numbers sit, as a fraction
   * of the way to the corner they belong to.
   *
   * Not all the way: a number printed *at* a corner runs off the edge of the
   * triangle, and a real d4 prints them just inside it.
   */
  const val CORNER_REACH: Double = 0.62

  /**
   * What is printed in each cell of [die]'s atlas.
   *
   * [mesh] is asked rather than the catalogue, and only for the d4: the
   * corners of a cell are where the *mesh* put them, and a second construction
   * of the same thing is how a printed face and a scored face come to disagree
   * (`docs/architecture.md`, decision 45).
   */
  fun plan(
    die: Die,
    mesh: DieMesh = DieMesh.of(die.shape),
    face: Typeface = BuiltinFont.face,
  ): List<PrintedCell> {
    val grid = ShapeAtlas.gridFor(die.shape)
    return die.faces.indices.map { index ->
      val (column, row) = ShapeAtlas.cellOf(die.shape, index)
      PrintedCell(
        index = index,
        column = column,
        row = row,
        marks =
          when (die.shape.naturalRead) {
            FaceRead.FaceUp ->
              centred(die, die.faces[index], mesh.faces.first { it.index == index }, grid, column to row, face)
            FaceRead.VertexUp -> corners(die, mesh, index, grid, face)
          },
      )
    }
  }

  /**
   * The whole atlas as a signed distance field, [cellPixels] to a cell, or
   * null when there is nothing to print.
   *
   * Null rather than a blank image, because "this die prints nothing" is a
   * thing the renderer has to know: it is the difference between drawing a
   * plain resin d6 and drawing one with an invisible number on it.
   */
  fun fieldOf(
    die: Die,
    mesh: DieMesh = DieMesh.of(die.shape),
    cellPixels: Int = SignedDistanceField.DEFAULT_SIZE,
    face: Typeface = BuiltinFont.face,
  ): NumberField? {
    val cells = plan(die, mesh, face).filter { it.marks.isNotEmpty() }
    if (cells.isEmpty()) return null
    val grid = ShapeAtlas.gridFor(die.shape)
    val width = grid.columns * cellPixels
    val height = grid.rows * cellPixels
    val pixels = ByteArray(width * height)
    cells.forEach { cell ->
      val contours = cell.marks.flatMap { Typesetter.lay(it.text, it.placement, face) }
      val field = SignedDistanceField.cell(contours, cellPixels)
      blit(field, pixels, cellPixels, width, cell.column, cell.row)
    }
    return NumberField(width = width, height = height, pixels = pixels)
  }

  /** One cell of the field into the image it belongs in. */
  @Suppress("LongParameterList")
  private fun blit(
    field: ByteArray,
    into: ByteArray,
    cellPixels: Int,
    width: Int,
    column: Int,
    row: Int,
  ) {
    for (line in 0 until cellPixels) {
      val from = line * cellPixels
      val to = (row * cellPixels + line) * width + column * cellPixels
      field.copyInto(into, destinationOffset = to, startIndex = from, endIndex = from + cellPixels)
    }
  }

  /** A face-read solid prints one thing, in the middle of its cell. */
  @Suppress("LongParameterList")
  private fun centred(
    die: Die,
    at: Face,
    surface: MeshFace,
    grid: ShapeAtlas.Grid,
    cell: Pair<Int, Int>,
    face: Typeface,
  ): List<Mark> {
    val text = textOf(at)
    if (text.isEmpty()) return emptyList()
    val room = FaceRoom.on(surface, grid, cell, Typesetter.inkWidth(text, 1.0, face), FACE_SHARE)
    return listOf(
      Mark(
        text,
        Placement(
          centreX = room.centreX,
          centreY = room.centreY,
          height = FACE_SHARE * room.height,
          underlined = isAmbiguous(text, die),
        ),
      ),
    )
  }

  /**
   * Whether [text] has to be underlined to be told from what it becomes when
   * the die is the other way up.
   *
   * The rule a real die follows, written down rather than hard-coded to `6`
   * and `9`: turn the label about, and if what comes out is a *different*
   * label that this same die also carries, a player cannot tell the two apart
   * and both get a bar. A d6 has no `9`, so its `6` needs no underline — which
   * is exactly what a moulded d6 does, and why the rule is worth stating this
   * way rather than as two characters by name.
   *
   * An `8` turns into itself and a `2` turns into nothing readable, so neither
   * is ever underlined.
   */
  fun isAmbiguous(
    text: String,
    die: Die,
  ): Boolean {
    val turned = turnedAbout(text) ?: return false
    if (turned == text) return false
    return die.faces.any { textOf(it) == turned }
  }

  /** [text] read upside down, or null when it does not read as anything. */
  private fun turnedAbout(text: String): String? {
    val turned = StringBuilder()
    text.reversed().forEach { turned.append(TURNS_INTO[it] ?: return null) }
    return turned.toString()
  }

  /**
   * A d4 prints three, one at each corner, each turned to face its own corner.
   *
   * The value at a corner is the value of the *catalogue face* that corner is,
   * because a d4's readable positions are its corners (`docs/dice-sets.md`,
   * "The d4"). So the number that ends up at the top of a settled d4 appears
   * on all three faces a player can see, and two faces sharing an edge agree
   * along it — which is what makes the reading unambiguous rather than a
   * convention somebody has to know.
   */
  private fun corners(
    die: Die,
    mesh: DieMesh,
    index: Int,
    grid: ShapeAtlas.Grid,
    face: Typeface,
  ): List<Mark> {
    val surface = mesh.faces.first { it.index == index }
    val directions = ShapeGeometry.directionsOf(die.shape).map(Vector3::normalised)
    val cell = ShapeAtlas.cellOf(die.shape, index)
    val (column, row) = cell
    val corners = FaceRoom.cornersOf(surface, grid, cell)
    return surface.positions.mapIndexedNotNull { corner, position ->
      val at = nearest(directions, position.normalised())
      val uv = surface.uvs[corner]
      // The corner in this cell's own coordinates, which is what a placement
      // is measured in: the atlas runs over the whole grid, a cell runs 0..1.
      val outX = HALF + (uv.u * grid.columns - column - HALF) * CORNER_REACH
      val outY = HALF + (uv.v * grid.rows - row - HALF) * CORNER_REACH
      val text = textOf(die.faces[at])
      if (text.isEmpty()) {
        null
      } else {
        Mark(
          text = text,
          placement =
            Placement(
              centreX = outX,
              centreY = outY,
              // Held to what the triangle has at that corner. A number placed
              // at a corner is nearer two edges than anything in the middle
              // is, and a d4 whose numbers ran over its own edges would be the
              // one die in the set that could not be read.
              height =
                minOf(
                  CORNER_HEIGHT,
                  FaceRoom.heightAt(corners, Typesetter.inkWidth(text, 1.0, face), outX, outY),
                ),
              // Up, for this number, is the way its own corner lies.
              turns = Exact.atan2(-(outX - HALF), -(outY - HALF)) / FULL_TURN,
              underlined = isAmbiguous(text, die),
            ),
        )
      }
    }
  }

  /** Which of [directions] [position] is, by the only measure a unit solid has. */
  private fun nearest(
    directions: List<Vector3>,
    position: Vector3,
  ): Int = directions.indices.minBy { (directions[it] - position).length }

  /**
   * What a face is printed with: its label, or its value when the built-in
   * font cannot draw the label.
   *
   * A set may label a face `💀`, and the font has no skull. Printing a row of
   * blanks would make the die unreadable and printing a box would be a lie
   * about what the author wrote, so the *value* is printed — the one thing
   * about that face the app can always write down, and the thing the player is
   * about to read off it anyway (`docs/dice-sets.md`).
   *
   * An **empty** label is different and is left empty: a face with nothing on
   * it is a face an author asked for, and a blank side is what half a Fudge
   * die is.
   */
  fun textOf(at: Face): String =
    when {
      // A face an author deliberately left blank stays blank. A Fudge die's
      // nought is a real face of a real die and printing a `0` on it would be
      // the app arguing with the set file.
      at.label.isEmpty() -> ""
      BuiltinFont.canDraw(at.label) -> at.label
      else -> at.value.toString()
    }

  /**
   * What each character becomes when the die is turned about.
   *
   * Only the characters that still read as something: a `2` upside down is a
   * squiggle, and a label containing one can never be mistaken for another.
   */
  private val TURNS_INTO: Map<Char, Char> = mapOf('0' to '0', '1' to '1', '6' to '9', '8' to '8', '9' to '6')

  private const val HALF = 0.5
  private const val FULL_TURN = 2 * Math.PI
}

/**
 * One cell of a die's printed atlas: which face it is, where it sits in the
 * grid, and what is written in it.
 */
data class PrintedCell(
  val index: Int,
  val column: Int,
  val row: Int,
  val marks: List<Mark>,
)

/** One piece of text printed in a cell, and where in that cell it goes. */
data class Mark(
  val text: String,
  val placement: Placement,
)

/**
 * A die's numbers as a signed distance field: one byte a pixel, rows from the
 * top, laid out in the shape's atlas grid.
 *
 * A field rather than a picture, so that a number stays sharp however far the
 * player pinches in — the shader recovers the edge from the distance rather
 * than magnifying a rasterised one (`SignedDistanceField`).
 */
class NumberField(
  val width: Int,
  val height: Int,
  val pixels: ByteArray,
) {
  init {
    require(pixels.size == width * height) {
      "a field of $width by $height is ${width * height} bytes, not ${pixels.size}"
    }
  }
}

/**
 * Each die's printed numbers, built once and kept.
 *
 * `20d20` is twenty of the same die, and turning the same labels into the same
 * distance field twenty times is the kind of work that shows as a pause between
 * pressing Roll and the dice appearing. A [Die] is a value, so one entry
 * answers for every copy of it.
 *
 * One of these belongs to a renderer and goes when the renderer does, which is
 * when the player leaves the roll screen (`docs/architecture.md`, decision 49).
 * A visit is a handful of dice thrown over and over, so the second throw of a
 * d20 costs nothing and the map never grows past the dice somebody actually
 * rolled.
 */
class PrintedDice(
  private val cellPixels: Int = SignedDistanceField.DEFAULT_SIZE,
) {
  private val known = mutableMapOf<Die, NumberField?>()

  /** How many dice have been built, which is what a test asks to see the cache work. */
  val built: Int get() = known.size

  /**
   * What [die] has printed on it, or null for one that prints nothing.
   *
   * A die whose author supplied an atlas prints nothing: the artwork is what is
   * on that face, and the app has no business writing over it
   * (`docs/dice-sets.md`).
   */
  fun of(
    die: Die,
    mesh: DieMesh = DieMesh.of(die.shape),
  ): NumberField? {
    if (die.texturePath != null) return null
    return known.getOrPut(die) { DieNumbers.fieldOf(die, mesh, cellPixels) }
  }
}
