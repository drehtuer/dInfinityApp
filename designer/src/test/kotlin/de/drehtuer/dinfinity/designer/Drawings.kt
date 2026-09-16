package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape

/**
 * Drawings, and a painter that is not a device, for the export tests.
 *
 * The painter answers with a PNG **header** and nothing else — the eight
 * signature bytes, the length, `IHDR` and the two dimensions. That is exactly
 * what the validator reads (`dicesets/format`'s `ImageHeader`: dimensions
 * before decoding), so a package built with it goes through the real validator
 * and is really checked. What it cannot say anything about is the pixels, and
 * those are `BitmapAtlasTest`'s, on a device Robolectric provides.
 */
internal object Drawings {
  /** A stroke across the middle of a cell. */
  fun line(colorArgb: Int = INK): Stroke =
    Stroke(dots = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)), colorArgb = colorArgb, width = 0.02f)

  /** A die of [shape], with the ordinary `1..n` faces. */
  fun die(
    shape: DieShape,
    id: String = shape.id,
  ): Die = Die.standard(id = id, shape = shape)

  /** [die] with something on each of [cells]. */
  fun drawn(
    die: Die,
    vararg cells: Int,
  ): Draft = cells.fold(Draft(die = die)) { draft, cell -> draft.onFace(cell) { it.draw(line()) } }

  /** A painter that writes a PNG header of the right size and no pixels. */
  fun headers(): AtlasPainter = AtlasPainter { plan -> png(plan.width, plan.height) }

  /** The first 24 bytes of a PNG that is [width] by [height]. */
  fun png(
    width: Int,
    height: Int,
  ): ByteArray =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
      byteArrayOf(0, 0, 0, 0x0D) +
      "IHDR".toByteArray(Charsets.US_ASCII) +
      bigEndian(width) +
      bigEndian(height)

  /**
   * The first 25 bytes of a lossless WebP that is [width] by [height].
   *
   * The same trick [png] plays, for the other format the validator reads: a
   * `RIFF`/`WEBP`/`VP8L` container with the two fourteen-bit dimensions packed
   * where `ImageHeader` looks for them. It is what a photo table's texture is
   * checked as, so a package built with one goes through the real validator.
   */
  fun webp(
    width: Int,
    height: Int,
  ): ByteArray =
    "RIFF".toByteArray(Charsets.US_ASCII) +
      littleEndian(0) +
      "WEBP".toByteArray(Charsets.US_ASCII) +
      "VP8L".toByteArray(Charsets.US_ASCII) +
      littleEndian(0) +
      byteArrayOf(0x2F) +
      littleEndian((width - 1) or ((height - 1) shl 14))

  private fun littleEndian(value: Int): ByteArray =
    byteArrayOf(
      value.toByte(),
      (value ushr 8).toByte(),
      (value ushr 16).toByte(),
      (value ushr 24).toByte(),
    )

  private fun bigEndian(value: Int): ByteArray =
    byteArrayOf(
      (value ushr 24).toByte(),
      (value ushr 16).toByte(),
      (value ushr 8).toByte(),
      value.toByte(),
    )

  const val INK: Int = 0xFF000000.toInt()
  const val RED: Int = 0xFFCC2222.toInt()
}
