package de.drehtuer.dinfinity.core.model

/**
 * A die's artwork after it has been decoded: pixels, and nothing that knows
 * how they got there (`docs/dice-sets.md`, "Textures").
 *
 * The decode itself is Android's — `BitmapFactory` is the only thing in the
 * app allowed to look at a stranger's PNG — so what crosses back out of it is
 * this: a width, a height and a flat run of bytes. That is what lets every
 * *decision* about an atlas be made and tested on a JVM, which is the same
 * line `Stage` and `PhysicsWorld` draw (`docs/architecture.md`, decisions 40
 * and 55).
 *
 * **Straight alpha, not premultiplied.** The material blends the artwork over
 * the printed label by the artwork's own alpha, so a half-transparent pixel
 * has to still carry its full colour; premultiplied pixels would darken every
 * soft edge towards the body colour. The decoder asks for it explicitly,
 * because Android premultiplies by default.
 *
 * Rows run from the top, the same way [de.drehtuer.dinfinity.core.model.ShapeAtlas]
 * counts cells and the same way a die's printed numbers are laid out, so a
 * hand-drawn atlas and a printed one are the same surface with the same
 * coordinates.
 *
 * Not a `data class`: it holds an array, and two arrays with the same contents
 * are not equal.
 *
 * @param pixels red, green, blue and alpha per pixel, a byte each, rows from
 *   the top.
 */
class AtlasImage(
  val width: Int,
  val height: Int,
  val pixels: ByteArray,
) {
  init {
    require(width > 0 && height > 0) { "an atlas of $width by $height has no pixels" }
    require(pixels.size == width * height * CHANNELS) {
      "an atlas of $width by $height is ${width * height * CHANNELS} bytes, not ${pixels.size}"
    }
  }

  /** How see-through the pixel at [x], [y] is: 0 for clear, 255 for opaque. */
  fun alphaAt(
    x: Int,
    y: Int,
  ): Int {
    require(x in 0 until width && y in 0 until height) { "($x, $y) is not in a $width by $height atlas" }
    return pixels[(y * width + x) * CHANNELS + ALPHA].toInt() and BYTE
  }

  /**
   * Whether the cell face [index] of a die with [faces] faces would sample is
   * empty — every pixel of it clear.
   *
   * "Clear" is a threshold rather than exactly nought, because a drawing
   * program that has been asked for a transparent region will happily leave a
   * pixel at an alpha of one, and a cell nobody drew in is a cell nobody drew
   * in (`docs/dice-sets.md`, "Textures").
   */
  fun cellIsEmpty(
    faces: Int,
    index: Int,
  ): Boolean {
    require(index in 0 until faces) { "a die with $faces faces has no face $index" }
    val grid = ShapeAtlas.gridFor(faces)
    val column = index % grid.columns
    val row = index / grid.columns
    val fromX = column * width / grid.columns
    val toX = (column + 1) * width / grid.columns
    val fromY = row * height / grid.rows
    val toY = (row + 1) * height / grid.rows
    // A cell with no pixels in it at all — an atlas narrower than its grid —
    // has nothing drawn in it, which is exactly what empty means here.
    for (y in fromY until toY) {
      for (x in fromX until toX) {
        if (alphaAt(x, y) > CLEAR_ALPHA) return false
      }
    }
    return true
  }

  /**
   * Which faces of a die with [faces] faces this atlas leaves undrawn, in
   * order.
   *
   * Only the cells a *face* sits in are counted. The spare cells a grid has
   * left over ([ShapeAtlas.Grid.spareCells]) belong to no face, are sampled by
   * nothing, and are supposed to be empty.
   */
  fun emptyCells(faces: Int): List<Int> = (0 until faces).filter { cellIsEmpty(faces, it) }

  companion object {
    /** Red, green, blue and alpha, a byte each. */
    const val CHANNELS: Int = 4

    /**
     * At or below this, a pixel counts as not drawn in.
     *
     * Three parts in eight hundred of coverage is not a picture of anything;
     * it is what an eraser and an 8-bit alpha channel leave behind.
     */
    const val CLEAR_ALPHA: Int = 1

    private const val BYTE = 0xFF
    private const val ALPHA = 3
  }
}
