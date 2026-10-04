package de.drehtuer.dinfinity.render.filament

import kotlin.math.pow

/**
 * A Radiance `.hdr` panorama, decoded into linear floats
 * (`docs/architecture.md`, decision 89).
 *
 * The format is small enough to read by hand and that is the point of doing
 * so: Filament's own decoder lives in `filament-utils-android`, whose native
 * library links against gltfio's, and the two cost about 17.5 MB of native
 * code over the four ABIs the APK carries — for a header, a run-length code
 * and an exponent. What this reads is the whole of what Poly Haven writes and
 * what Greg Ward's `RGBE` code reads:
 *
 * - a header of `KEY=value` lines starting `#?RADIANCE` (or `#?RGBE`), whose
 *   only line that matters is `FORMAT=32-bit_rle_rgbe`, ended by an empty line;
 * - a resolution line, `-Y height +X width` — rows top first, columns left to
 *   right, which is how every panorama is stored and the only orientation
 *   this accepts;
 * - each row either flat (four bytes a pixel) or, the usual case, the
 *   "new" run-length code: `2 2 hi lo`, then each of the four channels in
 *   runs — a count above 128 repeats the next byte, a count up to 128 copies
 *   that many.
 *
 * A pixel is a shared exponent and three mantissas: `m / 256 · 2^(e − 128)`,
 * with an exponent of nought meaning black.
 *
 * **The file is shipped, but it is read as if it were not.** Every count is
 * checked against what is left of the row and of the file, the size is
 * bounded, and anything that does not add up is null — which lights the tray
 * with the gradient ([RoomLight]) rather than crashing the roll thread's first
 * draw.
 */
object Radiance {
  /** The widest panorama read. A 4k one is 4,096; this is twice that. */
  const val MAX_WIDTH: Int = 8_192

  /** And the tallest: an equirectangular panorama is half as tall as it is wide. */
  const val MAX_HEIGHT: Int = 4_096

  /** Red, green and blue — the exponent is folded into them. */
  const val CHANNELS: Int = 3

  /**
   * A decoded panorama: [width] × [height] pixels of linear RGB radiance,
   * three floats each, the top row first.
   */
  class Image(
    val width: Int,
    val height: Int,
    val pixels: FloatArray,
  ) {
    init {
      require(pixels.size == width * height * CHANNELS) {
        "a $width × $height image is ${width * height * CHANNELS} floats, not ${pixels.size}"
      }
    }

    /** Channel [channel] of pixel ([x], [y]). */
    fun at(
      x: Int,
      y: Int,
      channel: Int,
    ): Float = pixels[(y * width + x) * CHANNELS + channel]
  }

  /** [bytes] as a panorama, or null if they are not one this can read. */
  fun decode(bytes: ByteArray): Image? = Reader(bytes).read()

  /** One row of RGBE quads as linear floats, into [into] from [start]. */
  private fun expand(
    row: ByteArray,
    into: FloatArray,
    start: Int,
  ) {
    for (x in 0 until row.size / RGBE) {
      val exponent = row[x * RGBE + EXPONENT].toInt() and BYTE
      // An exponent of nought is black, whatever the mantissas say.
      if (exponent != 0) {
        val scale = 2.0.pow(exponent - EXPONENT_BIAS - MANTISSA_BITS).toFloat()
        repeat(CHANNELS) { into[start + x * CHANNELS + it] = ((row[x * RGBE + it].toInt() and BYTE) + HALF) * scale }
      }
    }
  }

  /** Whether the row at [at] starts with the run-length code's `2 2 hi lo`. */
  private fun runLength(
    bytes: ByteArray,
    at: Int,
    width: Int,
  ): Boolean =
    width in MIN_RLE_WIDTH..MAX_RLE_WIDTH &&
      bytes[at].toInt() == 2 &&
      bytes[at + 1].toInt() == 2 &&
      (bytes[at + WIDTH_HIGH].toInt() and HIGH_BIT) == 0

  /** One pass over a file, front to back. */
  private class Reader(
    private val bytes: ByteArray,
  ) {
    private var at = 0

    fun read(): Image? {
      val size = if (header()) resolution() else null
      return size?.let { (width, height) -> pixels(width, height) }
    }

    /** Every row, top first; null if any of them is not there or does not add up. */
    private fun pixels(
      width: Int,
      height: Int,
    ): Image? {
      val pixels = FloatArray(width * height * CHANNELS)
      val row = ByteArray(width * RGBE)
      for (y in 0 until height) {
        if (!row(row, width)) return null
        expand(row, pixels, y * width * CHANNELS)
      }
      return Image(width, height, pixels)
    }

    /** The header, up to and including its empty line; false if it is not a Radiance one. */
    private fun header(): Boolean {
      val magic = line()
      if (magic != "#?RADIANCE" && magic != "#?RGBE") return false
      var format: String? = null
      var line = line()
      while (line != null && line.isNotEmpty()) {
        if (line.startsWith(FORMAT)) format = line.removePrefix(FORMAT)
        line = line()
      }
      return line != null && (format == null || format == RLE_RGBE)
    }

    /** `-Y height +X width`, as (width, height); null for any other orientation or size. */
    private fun resolution(): Pair<Int, Int>? {
      val parts = line()?.split(' ')?.takeIf { it.size == RESOLUTION_PARTS && it[ROWS] == "-Y" && it[COLUMNS] == "+X" }
      val height = parts?.get(HEIGHT)?.toIntOrNull()?.takeIf { it in 1..MAX_HEIGHT }
      val width = parts?.get(WIDTH)?.toIntOrNull()?.takeIf { it in 1..MAX_WIDTH }
      return if (width != null && height != null) width to height else null
    }

    /** The next line, without its newline; null at the end of the file or past a sane length. */
    private fun line(): String? {
      val start = at
      while (at < bytes.size && bytes[at] != NEWLINE) {
        if (at - start > MAX_LINE) return null
        at++
      }
      if (at >= bytes.size) return null
      return String(bytes, start, at++ - start, Charsets.US_ASCII)
    }

    /** One row of [width] pixels into [row] as RGBE quads; false if the file ends or lies. */
    private fun row(
      row: ByteArray,
      width: Int,
    ): Boolean =
      when {
        at + RGBE > bytes.size -> false
        runLength(bytes, at, width) -> runs(row, width)
        else -> flat(row, width)
      }

    /**
     * A flat row: four bytes a pixel, as written. (The old run-length code
     * that repeats the previous pixel is not read: nothing has written it
     * since 1991, and a file that uses it is not a panorama worth shipping.)
     */
    private fun flat(
      row: ByteArray,
      width: Int,
    ): Boolean {
      val end = at + width * RGBE
      if (end > bytes.size) return false
      bytes.copyInto(row, 0, at, end)
      at = end
      return true
    }

    /** A run-length row: its width again, then each channel in runs. */
    private fun runs(
      row: ByteArray,
      width: Int,
    ): Boolean {
      val high = bytes[at + WIDTH_HIGH].toInt() and BYTE
      val encoded = (high shl BITS_PER_BYTE) or (bytes[at + WIDTH_LOW].toInt() and BYTE)
      if (encoded != width) return false
      at += RGBE
      return (0 until RGBE).all { channel(row, width, it) }
    }

    /** Every run of one channel of a row; false if they do not fill it exactly. */
    private fun channel(
      row: ByteArray,
      width: Int,
      channel: Int,
    ): Boolean {
      var x = 0
      while (x in 0 until width) x = run(row, x, width, channel)
      return x == width
    }

    /**
     * One run of [channel] from pixel [x]: a count above 128 repeats the next
     * byte, a count up to 128 copies that many. Where the next run starts, or
     * [FAILED] if the file ends or the run overruns the row.
     */
    private fun run(
      row: ByteArray,
      x: Int,
      width: Int,
      channel: Int,
    ): Int {
      if (at >= bytes.size) return FAILED
      val count = bytes[at++].toInt() and BYTE
      val repeated = count > RUN
      val length = if (repeated) count - RUN else count
      val reads = if (repeated) 1 else length
      if (length == 0 || x + length > width || at + reads > bytes.size) return FAILED
      for (step in 0 until length) row[(x + step) * RGBE + channel] = bytes[if (repeated) at else at + step]
      at += reads
      return x + length
    }
  }

  private const val FORMAT = "FORMAT="
  private const val RLE_RGBE = "32-bit_rle_rgbe"
  private const val NEWLINE = '\n'.code.toByte()
  private const val MAX_LINE = 1_024
  private const val RESOLUTION_PARTS = 4

  /** Where, in `-Y height +X width`, each part is. */
  private const val ROWS = 0
  private const val HEIGHT = 1
  private const val COLUMNS = 2
  private const val WIDTH = 3

  /** Four bytes a pixel: three mantissas and the exponent they share. */
  private const val RGBE = 4
  private const val EXPONENT = 3
  private const val EXPONENT_BIAS = 128
  private const val MANTISSA_BITS = 8

  /** The middle of a mantissa's step, as Ward's own decoder reads it. */
  private const val HALF = 0.5f

  /** Where a run-length row's header keeps its width, high byte first. */
  private const val WIDTH_HIGH = 2
  private const val WIDTH_LOW = 3

  /** What [Reader.run] returns for a run that cannot be. */
  private const val FAILED = -1

  /** The run-length code only exists for rows this wide. */
  private const val MIN_RLE_WIDTH = 8
  private const val MAX_RLE_WIDTH = 0x7FFF

  /** A count above this is a run of one byte; up to it, that many literal bytes. */
  private const val RUN = 128
  private const val HIGH_BIT = 0x80
  private const val BYTE = 0xFF
  private const val BITS_PER_BYTE = 8
}
