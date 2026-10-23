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
import de.drehtuer.dinfinity.core.model.ShapeAtlas

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

  /**
   * What is printed in each cell of [die]'s atlas.
   *
   * One number in the middle of every face, for every die: the d4 too, since
   * it became a long die read from the face on top (decision 109). [mesh] is
   * asked for the face's shape rather than the catalogue, because a printed
   * face and a scored face are the same face only if they come from one
   * construction (`docs/architecture.md`, decision 45).
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
        marks = centred(die, die.faces[index], mesh.faces.first { it.index == index }, grid, column to row, face),
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
   *
   * [drawn] are the faces the die's artwork draws on, and they are **not
   * printed**: a face somebody drew carries the drawing and nothing else, and
   * a number showing through the clear parts of a drawing is a number nobody
   * drew (`docs/dice-sets.md`, "Labels, and the artwork over them";
   * `docs/architecture.md`, decision 96).
   */
  fun fieldOf(
    die: Die,
    mesh: DieMesh = DieMesh.of(die.shape),
    cellPixels: Int = SignedDistanceField.DEFAULT_SIZE,
    face: Typeface = BuiltinFont.face,
    drawn: Set<Int> = emptySet(),
  ): NumberField? {
    val cells = plan(die, mesh, face).filter { it.marks.isNotEmpty() && it.index !in drawn }
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
  private val known = mutableMapOf<Pair<Die, Set<Int>>, NumberField?>()

  /** How many dice have been built, which is what a test asks to see the cache work. */
  val built: Int get() = known.size

  /**
   * What [die] has printed on it, or null for one that prints nothing.
   *
   * **A die with artwork is printed where the artwork leaves a face clear.**
   * [drawn] are the faces its atlas draws on, which the stage reads off the
   * decoded pixels ([Stage.drawnCells]); those faces carry the drawing alone
   * and every other face carries its label (`docs/dice-sets.md`, "Labels,
   * and the artwork over them"). Within a printed face the material still
   * lays the artwork over the label by its alpha ([DiceMaterial.SOURCE]) —
   * which, on a face the atlas leaves clear, is the label.
   *
   * Null is still an answer: a die every one of whose labels is empty, or
   * every one of whose faces is drawn, has nothing to print.
   */
  fun of(
    die: Die,
    mesh: DieMesh = DieMesh.of(die.shape),
    drawn: Set<Int> = emptySet(),
  ): NumberField? = known.getOrPut(die to drawn) { DieNumbers.fieldOf(die, mesh, cellPixels, drawn = drawn) }
}
