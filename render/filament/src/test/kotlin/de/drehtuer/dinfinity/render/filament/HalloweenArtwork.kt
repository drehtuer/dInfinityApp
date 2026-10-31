package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The artwork of `sample-sets/halloween`'s pumpkin die: three jack-o'-lantern
 * faces in black ink, after a set of real glow-in-the-dark dice, and three
 * faces left blank.
 *
 * The faces are on cube faces 0, 2 and 4 — `+x`, `+y` and `+z`, which meet
 * at one corner — so each sits opposite a blank one, as on the real die. A
 * blank face's cell is left wholly clear and its label is empty, so the app
 * prints nothing there and the glowing body is all it shows
 * (`docs/dice-sets.md`, "Labels, and the artwork over them"). Around the ink
 * a drawn cell is clear too, so the body glows between the marks.
 *
 * Each face is a region in *face units*: `u` across and `v` down the square
 * face, −1 to 1 edge to edge. A cube's square is the one inscribed in its
 * cell's circle, so it covers the middle [FACE_IN_CELL] of the cell, and that
 * is where face units are mapped to. Plain arithmetic, nothing random:
 * [HalloweenArtworkTest] holds the committed PNG to this code.
 */
object HalloweenArtwork {
  /** Pixels a face's cell is drawn at: the ink's curves stay smooth when pinched in. */
  const val CELL_PIXELS: Int = 256

  /** Which of the cube's faces carry a pumpkin, by catalogue index; the rest are blank. */
  val PUMPKIN_FACES: List<Int> = listOf(0, 2, 4)

  /** The ink, a near-black: the real dice's faces are printed black. */
  const val INK: Int = 0x141414

  /** How much of a cell the cube's square face covers, edge to edge: one over √2. */
  private val FACE_IN_CELL = 1.0 / sqrt(2.0)

  /** The three faces, in the order of [PUMPKIN_FACES]. */
  private val FACES: List<(Double, Double) -> Boolean> = listOf(::grin, ::fangs, ::scared)

  /**
   * The pumpkin die's atlas as ARGB pixels, rows from the top, in the cube's
   * grid at [CELL_PIXELS] a cell: ink where a face is drawn, clear elsewhere.
   */
  fun atlas(): MarbleArtwork.Atlas {
    val grid = ShapeAtlas.gridFor(DieShape.Cube)
    val width = grid.columns * CELL_PIXELS
    val height = grid.rows * CELL_PIXELS
    val pixels =
      IntArray(width * height) { at ->
        val x = at % width
        val y = at / width
        val cell = (y / CELL_PIXELS) * grid.columns + x / CELL_PIXELS
        val face = PUMPKIN_FACES.indexOf(cell)
        if (face < 0) 0 else inked(coverage(FACES[face], x % CELL_PIXELS, y % CELL_PIXELS))
      }
    return MarbleArtwork.Atlas(width, height, pixels)
  }

  /** The ink at [coverage], as straight alpha: the colour is the ink's whatever the alpha. */
  private fun inked(coverage: Double): Int {
    val alpha = (coverage * BYTE).roundToInt()
    return if (alpha == 0) 0 else (alpha shl ALPHA_SHIFT) or INK
  }

  /**
   * How much of the pixel at ([x], [y]) in a cell [face] covers, from a grid
   * of [SAMPLES] × [SAMPLES] points inside it: the edges antialiased, so a
   * curve does not stair-step on the tray.
   */
  internal fun coverage(
    face: (Double, Double) -> Boolean,
    x: Int,
    y: Int,
  ): Double {
    var inside = 0
    for (sy in 0 until SAMPLES) {
      for (sx in 0 until SAMPLES) {
        val cellX = (x + (sx + HALF) / SAMPLES) / CELL_PIXELS
        val cellY = (y + (sy + HALF) / SAMPLES) / CELL_PIXELS
        if (face(faceUnit(cellX), faceUnit(cellY))) inside++
      }
    }
    return inside.toDouble() / (SAMPLES * SAMPLES)
  }

  /** A position across the cell, nought to one, in face units. */
  private fun faceUnit(cell: Double): Double = (cell - HALF) / (FACE_IN_CELL / 2)

  /**
   * The grinning one: slanted crescent eyes, a dot of a nose, and a wide smile
   * whose top edge is a row of teeth.
   */
  internal fun grin(
    u: Double,
    v: Double,
  ): Boolean {
    val eye = GRIN_EYE.covers(abs(u), v)
    val nose = hypot(u, v + 0.1) < 0.075
    val mouth =
      within(u, GRIN_HALF_WIDTH) { across ->
        val bottom = 0.1 + 0.62 * across
        val top = 0.06 + 0.14 * across + teeth(u, GRIN_HALF_WIDTH, count = 6) * 0.22 * sqrt(across)
        v > top && v < bottom
      }
    return eye || nose || mouth
  }

  /**
   * The snarling one: angry crescent eyes and no nose, over a mouth of
   * pointed teeth from above and below that interlock — a bold zigzag
   * curved like a smile.
   */
  internal fun fangs(
    u: Double,
    v: Double,
  ): Boolean {
    val eye = FANGS_EYE.covers(abs(u), v)
    val mouth =
      within(u, FANGS_HALF_WIDTH) { across ->
        val round = sqrt(across)
        val middle = 0.12 + 0.32 * across
        val half = 0.06 + 0.24 * round
        val bite = 0.3 * round
        val top = middle - half + teeth(u, FANGS_HALF_WIDTH, count = 5) * bite
        val bottom = middle + half - teeth(u + FANGS_HALF_WIDTH / 5, FANGS_HALF_WIDTH, count = 5) * bite
        v > top && v < bottom
      }
    return eye || mouth
  }

  /**
   * The frightened one: two bean-shaped eyes and a mouth wide open, a blob
   * with a wobbling edge.
   */
  internal fun scared(
    u: Double,
    v: Double,
  ): Boolean {
    val eye = SCARED_EYE.covers(abs(u), v)
    val du = u + 0.04
    val dv = (v - 0.26) / 1.14
    val angle = atan2(dv, du)
    val edge = 0.43 + 0.06 * sin(3 * angle + 0.6) + 0.035 * sin(5 * angle + 1.9)
    val mouth = hypot(du, dv) < edge
    return eye || mouth
  }

  /**
   * A disc of [radius] at ([centreU], [centreV]) with another disc the same
   * size, moved by [offsetU] and [offsetV], taken out of it: an eye.
   */
  private class Crescent(
    val centreU: Double,
    val centreV: Double,
    val radius: Double,
    val offsetU: Double,
    val offsetV: Double,
  ) {
    fun covers(
      u: Double,
      v: Double,
    ): Boolean =
      hypot(u - centreU, v - centreV) < radius &&
        hypot(u - centreU - offsetU, v - centreV - offsetV) >= radius
  }

  /**
   * [inside] at how far across a mouth [halfWidth] wide [u] is, as one at the
   * middle falling to nought at the corners (`1 − (u / halfWidth)²`), or false
   * past the corners.
   */
  private inline fun within(
    u: Double,
    halfWidth: Double,
    inside: (Double) -> Boolean,
  ): Boolean {
    val across = 1.0 - (u / halfWidth) * (u / halfWidth)
    return across > 0.0 && inside(across)
  }

  /**
   * A row of [count] pointed teeth across a mouth [halfWidth] wide: nought
   * between two teeth, one at a tooth's point, in straight lines.
   */
  internal fun teeth(
    u: Double,
    halfWidth: Double,
    count: Int,
  ): Double {
    val along = (u + halfWidth) / (2 * halfWidth) * count
    val within = along - floor(along)
    return 1.0 - abs(2 * within - 1.0)
  }

  /** The eyes of each face, the left one mirrored onto the right. */
  private val GRIN_EYE = Crescent(centreU = 0.4, centreV = -0.44, radius = 0.25, offsetU = 0.08, offsetV = -0.14)
  private val FANGS_EYE = Crescent(centreU = 0.42, centreV = -0.46, radius = 0.26, offsetU = -0.12, offsetV = -0.15)
  private val SCARED_EYE = Crescent(centreU = 0.4, centreV = -0.48, radius = 0.17, offsetU = 0.17, offsetV = 0.1)

  /** Halfway across the face, the grin's corners. */
  private const val GRIN_HALF_WIDTH = 0.8

  /** And the snarl's, a little narrower. */
  private const val FANGS_HALF_WIDTH = 0.78

  private const val SAMPLES = 4
  private const val HALF = 0.5
  private const val BYTE = 0xFF
  private const val ALPHA_SHIFT = 24
}
