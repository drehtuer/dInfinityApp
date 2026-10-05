package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.glyphs.SignedDistanceField
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The artwork of `sample-sets/marble`: veined stone on every face, with the
 * die's own numbers cut into it.
 *
 * The numbers are not drawn by hand. A face with artwork on it is not printed
 * (`docs/architecture.md`, decision 96), so a marble die has to carry its
 * numbers in the picture — and they are taken from [DieNumbers], the field the
 * renderer prints a plain die with. A marble d4's corner numbers, a d20's
 * marked `6.` and `9.` and a d10's `0` therefore sit exactly where a plain die
 * of the same set would have them, and stay there if the printing ever moves.
 *
 * Plain arithmetic on purpose, with nothing random in it: the same die always
 * gives the same pixels, which is what lets [MarbleArtworkTest] hold the
 * committed PNGs to this code.
 */
object MarbleArtwork {
  /** Pixels a face's cell is drawn at: sharp at tray distance, small in git. */
  const val CELL_PIXELS: Int = 128

  /** The stone, a warm white. */
  private val STONE = Rgb(0xEC, 0xE8, 0xE1)

  /** The veins, a cool grey. */
  private val VEIN = Rgb(0x7E, 0x7A, 0x77)

  /** The cut numbers, the set's `number_color`. */
  private val INK = Rgb(0x2E, 0x2C, 0x2A)

  /**
   * The atlas for [die] as ARGB pixels, rows from the top, in the shape's grid
   * at [CELL_PIXELS] a cell. A spare cell of the grid is left clear.
   */
  fun atlasOf(die: Die): Atlas {
    val grid = ShapeAtlas.gridFor(die.shape)
    val width = grid.columns * CELL_PIXELS
    val height = grid.rows * CELL_PIXELS
    val field = DieNumbers.fieldOf(die, cellPixels = CELL_PIXELS)
    val seed = die.id.hashCode()
    val pixels =
      IntArray(width * height) { at ->
        val x = at % width
        val y = at / width
        val cell = (y / CELL_PIXELS) * grid.columns + x / CELL_PIXELS
        if (cell >= die.shape.faceCount) {
          0
        } else {
          val stone = stoneAt(x.toDouble() / CELL_PIXELS, y.toDouble() / CELL_PIXELS, seed)
          val ink = field?.let { inkOf(it.pixels[at].toInt() and BYTE) } ?: 0.0
          stone.mix(INK, ink).argb()
        }
      }
    return Atlas(width, height, pixels)
  }

  /** Pixels and their size. */
  class Atlas(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
  )

  /**
   * How much of a pixel is number, from the distance field: the shader's own
   * reading (an edge at [SignedDistanceField.EDGE], antialiased over a pixel).
   */
  internal fun inkOf(distance: Int): Double =
    ((distance - SignedDistanceField.EDGE) / unitsPerPixel() + HALF).coerceIn(0.0, 1.0)

  /** How far the field's byte moves across one pixel at [CELL_PIXELS]. */
  private fun unitsPerPixel(): Double = SignedDistanceField.EDGE / (SignedDistanceField.SPREAD * CELL_PIXELS)

  /**
   * The classic marble: a sine across the stone whose phase is pushed about by
   * turbulence, so straight bands become wandering veins, sharpened so only
   * the troughs show, over a faint cloud. In cell units, so a vein runs on
   * across a face at any atlas size.
   */
  internal fun stoneAt(
    x: Double,
    y: Double,
    seed: Int,
  ): Rgb {
    val turbulence = turbulence(x * VEIN_SCALE, y * VEIN_SCALE, seed)
    val band = abs(sin((x * BAND_X + y * BAND_Y) * BAND_FREQUENCY + turbulence * TWIST))
    val vein = (1.0 - band).pow(VEIN_SHARPNESS)
    // A second, fainter set of veins across the first, wider and softer: real
    // marble is seldom one family of lines.
    val drift = turbulence(x * VEIN_SCALE, y * VEIN_SCALE, seed + 2)
    val cross = abs(sin((x * BAND_Y - y * BAND_X) * CROSS_FREQUENCY + drift * TWIST))
    val haze = (1.0 - cross).pow(HAZE_SHARPNESS)
    val cloud = turbulence(x * CLOUD_SCALE, y * CLOUD_SCALE, seed + 1) * CLOUD_DEPTH
    return STONE.mix(VEIN, haze * HAZE_DEPTH).mix(VEIN, vein * VEIN_DEPTH).darker(cloud)
  }

  /** Summed octaves of [noise], each half the size and half the weight of the last. */
  private fun turbulence(
    x: Double,
    y: Double,
    seed: Int,
  ): Double {
    var sum = 0.0
    var weight = 1.0
    var scale = 1.0
    repeat(OCTAVES) {
      sum += weight * noise(x * scale, y * scale, seed + it)
      weight /= 2
      scale *= 2
    }
    return sum / (2.0 - 2.0 / (1 shl OCTAVES))
  }

  /** Value noise in 0..1: a hashed value at each lattice point, smoothly blended. */
  private fun noise(
    x: Double,
    y: Double,
    seed: Int,
  ): Double {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val tx = smooth(x - x0)
    val ty = smooth(y - y0)
    val top = lerp(lattice(x0, y0, seed), lattice(x0 + 1, y0, seed), tx)
    val bottom = lerp(lattice(x0, y0 + 1, seed), lattice(x0 + 1, y0 + 1, seed), tx)
    return lerp(top, bottom, ty)
  }

  /** A fixed pseudo-random value in 0..1 for one lattice point. */
  private fun lattice(
    x: Int,
    y: Int,
    seed: Int,
  ): Double {
    var h = x * HASH_X + y * HASH_Y + seed * HASH_SEED
    h = (h xor (h ushr HASH_SHIFT)) * HASH_MIX
    h = h xor (h ushr HASH_SHIFT)
    return (h and HASH_MASK).toDouble() / HASH_MASK
  }

  private fun smooth(t: Double): Double = t * t * (3 - 2 * t)

  private fun lerp(
    a: Double,
    b: Double,
    t: Double,
  ): Double = a + (b - a) * t

  /** A colour, a byte a channel. */
  data class Rgb(
    val r: Int,
    val g: Int,
    val b: Int,
  ) {
    fun mix(
      other: Rgb,
      amount: Double,
    ): Rgb =
      Rgb(
        (r + (other.r - r) * amount).roundToInt(),
        (g + (other.g - g) * amount).roundToInt(),
        (b + (other.b - b) * amount).roundToInt(),
      )

    fun darker(by: Double): Rgb = mix(Rgb(0, 0, 0), by)

    fun argb(): Int = (OPAQUE shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
  }

  private const val BYTE = 0xFF
  private const val HALF = 0.5
  private const val OCTAVES = 4
  private const val VEIN_SCALE = 0.9
  private const val BAND_X = 0.8
  private const val BAND_Y = 0.6
  private const val BAND_FREQUENCY = 1.7
  private const val TWIST = 2.6
  private const val VEIN_SHARPNESS = 14.0
  private const val VEIN_DEPTH = 0.75
  private const val CROSS_FREQUENCY = 1.1
  private const val HAZE_SHARPNESS = 5.0
  private const val HAZE_DEPTH = 0.22
  private const val CLOUD_SCALE = 0.7
  private const val CLOUD_DEPTH = 0.08
  private const val HASH_X = 374_761_393
  private const val HASH_Y = 668_265_263
  private const val HASH_SEED = 1_274_126_177
  private const val HASH_SHIFT = 13
  private const val HASH_MIX = 1_103_515_245
  private const val HASH_MASK = 0x7FFF_FFFF
  private const val OPAQUE = 0xFF
  private const val ALPHA_SHIFT = 24
  private const val RED_SHIFT = 16
  private const val GREEN_SHIFT = 8
}
