package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.ShapeAtlas

/**
 * One cell of an atlas, and what is drawn in it.
 *
 * @param index which face this is, which is also its place in the grid
 *   (`docs/dice-sets.md`, "Shape catalogue"): face *i* is cell *i*.
 * @param left the cell's left edge in the image, in pixels.
 * @param top the cell's top edge in the image, in pixels.
 * @param size how wide and high the cell is, in pixels. Square, always — a
 *   face has to be drawn upright in it.
 * @param marks what was drawn on it, in fractions of the canvas, in the order
 *   they go down: fills first, ink over them ([FaceDrawing]).
 */
data class AtlasCell(
  val index: Int,
  val left: Int,
  val top: Int,
  val size: Int,
  val marks: List<Mark>,
) {
  /** [dot] — a fraction of the canvas — as a point in the image. */
  fun at(dot: Dot): Dot = Dot(x = left + dot.x * size, y = top + dot.y * size)

  /** [fraction] of the canvas as a length in the image, e.g. a nib's width. */
  fun pixels(fraction: Float): Float = fraction * size

  /** [marks] as points in the image, ready to be drawn. */
  fun placed(): List<Mark> = marks.map { mark -> mark.at(mark.dots.map(::at)) }
}

/**
 * Where every part of a drawing goes in the image it is exported as.
 *
 * @param width the whole image, in pixels.
 * @param height the whole image, in pixels.
 * @param outline the shape each cell is masked into ([FaceOutline]).
 * @param corners that shape's corners, in fractions of a cell — empty for a
 *   disc, which is the one outline that is not a polygon ([FaceShapes]).
 * @param cells the cells that have something on them. A cell nobody drew on is
 *   **absent**, because an atlas leaves such a cell transparent and the die's
 *   printed label shows through it (`docs/dice-sets.md`, "Textures").
 */
data class AtlasPlan(
  val width: Int,
  val height: Int,
  val outline: FaceOutline,
  val corners: List<Dot>,
  val cells: List<AtlasCell>,
)

/**
 * A drawing, laid out as the atlas a dice set carries
 * (`docs/face-designer.md`, "Export details").
 *
 * **The deciding is here and the painting is not.** What size the image is,
 * which cell a face occupies, where a stroke's points land in it and which
 * cells are left empty are all arithmetic over numbers, and all of it can be
 * wrong; putting pixels down cannot be. So the plan is plain Kotlin a unit test
 * asserts on and [AtlasPainter] is the one part that needs a device — the same
 * line `docs/architecture.md` draws through the physics and the renderer
 * (decisions 40 and 47).
 *
 * The grid is [ShapeAtlas]'s rather than this object's, because the renderer
 * that samples an atlas and the validator that checks one both read it from
 * there: a designer with a grid of its own would draw dice whose faces are in
 * the wrong places on everybody else's phone.
 */
object Atlas {
  /**
   * How many pixels a cell gets each way (`docs/face-designer.md`, "Export
   * details").
   *
   * 256 puts a d20 at 1280×1024 and a d6 at 768×512 — sharp on a phone, well
   * inside the 2048-pixel limit a texture is held to and nowhere near the four
   * megabytes one file may be (`docs/dice-sets.md`, "Textures").
   */
  const val CELL_PIXELS: Int = 256

  /**
   * How [draft] is laid out, or null when nothing has been drawn on it.
   *
   * Null rather than an empty plan: a die with no marks on any face has no
   * atlas, and writing a wholly transparent image for it would be a texture
   * file that says nothing and costs a megabyte.
   */
  fun plan(
    draft: Draft,
    cellPixels: Int = CELL_PIXELS,
  ): AtlasPlan? {
    val shape = draft.die.shape
    val grid = ShapeAtlas.gridFor(shape)
    val drawn =
      draft.die.faces.indices
        .mapNotNull { index -> draft.faces[index]?.takeIf { !it.blank }?.let { index to it } }
    if (drawn.isEmpty()) return null
    return AtlasPlan(
      width = grid.columns * cellPixels,
      height = grid.rows * cellPixels,
      outline = draft.outline,
      corners = FaceShapes.corners(draft.outline),
      cells =
        drawn.map { (index, drawing) ->
          val (column, row) = ShapeAtlas.cellOf(shape, index)
          AtlasCell(
            index = index,
            left = column * cellPixels,
            top = row * cellPixels,
            size = cellPixels,
            marks = drawing.marks,
          )
        },
    )
  }
}
