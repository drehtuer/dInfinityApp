package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.DieShape
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * How a die's texture is laid out: a fixed grid of square cells, face *i* in
 * cell *i*, reading left to right and top to bottom
 * (`docs/dice-sets.md`, "Shape catalogue").
 *
 * The layout belongs to the app, not to the package. That is what makes a
 * hand-drawn atlas and a hand-authored one interchangeable: the face designer
 * writes exactly this grid, so a set drawn with a finger and a set drawn in
 * Photoshop are the same kind of file (`docs/face-designer.md`).
 */
object ShapeAtlas {
  /** A grid of cells, as wide as [columns] and as tall as [rows]. */
  data class Grid(
    val columns: Int,
    val rows: Int,
  ) {
    /** Cells with no face in them, which an author may leave transparent. */
    fun spareCells(faces: Int): Int = columns * rows - faces
  }

  /**
   * The grid a shape's atlas uses: as square as it can be, widest first.
   *
   * Square because a phone's texture memory likes it and because a cell has to
   * be square for a face to be drawn upright in it; widest-first so a d20 is
   * 5×4 rather than 4×5 and a strip of faces reads the way a sentence does.
   */
  fun gridFor(shape: DieShape): Grid = gridFor(shape.faceCount)

  /** The same, for any number of faces. */
  fun gridFor(faces: Int): Grid {
    require(faces >= 1) { "a die with $faces faces has no atlas" }
    val columns = ceil(sqrt(faces.toDouble())).toInt()
    return Grid(columns = columns, rows = ceil(faces.toDouble() / columns).toInt())
  }

  /** The cell face [index] of [shape] occupies, as a column and a row. */
  fun cellOf(
    shape: DieShape,
    index: Int,
  ): Pair<Int, Int> {
    require(index in 0 until shape.faceCount) { "${shape.id} has no face $index" }
    val grid = gridFor(shape)
    return index % grid.columns to index / grid.columns
  }
}
