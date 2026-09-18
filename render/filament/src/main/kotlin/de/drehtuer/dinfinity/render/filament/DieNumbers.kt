package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.glyphs.BuiltinFont
import de.drehtuer.dinfinity.core.glyphs.FaceLabel
import de.drehtuer.dinfinity.core.glyphs.LabelRoom
import de.drehtuer.dinfinity.core.glyphs.Placement
import de.drehtuer.dinfinity.core.glyphs.SignedDistanceField
import de.drehtuer.dinfinity.core.glyphs.Typeface
import de.drehtuer.dinfinity.core.glyphs.Typesetter
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.core.model.ShapeAtlas
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
   * Named here because this is where the judgement is made — it is the one
   * knob left in how big a printed numeral is, and the phone is what settles
   * it (`docs/TODO.md`, Step 5.6). The number itself lives with the solve it
   * belongs to, in `core/glyphs`, because the face designer prints at the same
   * size (`docs/face-designer.md`, "The stamp").
   */
  const val FACE_SHARE: Double = LabelRoom.FACE_SHARE

  /** How tall a d4's numbers are, as a fraction of the cell ([LabelRoom.CORNER_HEIGHT]). */
  const val CORNER_HEIGHT: Double = LabelRoom.CORNER_HEIGHT

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
    val placement =
      LabelRoom.centred(
        corners = FaceRoom.cornersOf(surface, grid, cell),
        text = text,
        face = face,
        marked = isAmbiguous(text, die),
      ) ?: return emptyList()
    return listOf(Mark(text, placement))
  }

  /**
   * Whether [text] has to be marked to be told from what it becomes when the
   * die is the other way up — a `6` on a die that also has a `9`, printed
   * `6.`.
   *
   * The rule is `core/glyphs`' ([FaceLabel.isAmbiguous]), because the face
   * designer puts the same dot after the same numbers.
   */
  fun isAmbiguous(
    text: String,
    die: Die,
  ): Boolean = FaceLabel.isAmbiguous(text, die)

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
      val text = textOf(die.faces[at])
      // The corner in this cell's own coordinates, which is what a placement
      // is measured in: the atlas runs over the whole grid, a cell runs 0..1.
      LabelRoom
        .cornered(
          corners = corners,
          corner = uv.u * grid.columns - column to uv.v * grid.rows - row,
          text = text,
          face = face,
          marked = isAmbiguous(text, die),
        )?.let { Mark(text = text, placement = it) }
    }
  }

  /** Which of [directions] [position] is, by the only measure a unit solid has. */
  private fun nearest(
    directions: List<Vector3>,
    position: Vector3,
  ): Int = directions.indices.minBy { (directions[it] - position).length }

  /**
   * What a face is printed with: its label, or its value when the built-in
   * font cannot draw the label ([FaceLabel.textOf]).
   */
  fun textOf(at: Face): String = FaceLabel.textOf(at)
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
   * **A die with artwork is printed too.** An atlas may leave a face's cell
   * clear, and `docs/dice-sets.md` ("Textures") says that face carries its
   * label — so the field is built whatever the die's `texture` says, and which
   * of the two a face actually shows is settled in the material, by the
   * artwork's alpha, per pixel ([DiceMaterial.SOURCE]). Deciding it here
   * instead would mean deciding it per *cell*, from pixels this side of the
   * renderer has never seen.
   *
   * Null is still an answer: a die every one of whose labels is empty has
   * nothing to print, which is what [DieNumbers.fieldOf] says about it.
   */
  fun of(
    die: Die,
    mesh: DieMesh = DieMesh.of(die.shape),
  ): NumberField? = known.getOrPut(die) { DieNumbers.fieldOf(die, mesh, cellPixels) }
}
