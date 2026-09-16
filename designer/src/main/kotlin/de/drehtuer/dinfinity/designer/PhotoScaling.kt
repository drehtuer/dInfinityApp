package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits

/**
 * How big a picture is, in pixels.
 *
 * Its own type rather than a pair, because every function here takes one and
 * returns one, and a pair of `Int`s is two chances to get the order wrong.
 */
data class PhotoSize(
  val width: Int,
  val height: Int,
) {
  /** True for a size a decoder could actually have produced. */
  val real: Boolean get() = width > 0 && height > 0

  /** The side the texture limit is about. */
  val longestSide: Int get() = maxOf(width, height)
}

/**
 * What a photograph has to be cut down to before it may be a table
 * (`docs/tables.md`, "Your own photo").
 *
 * **This is the security-relevant half of "use a photo", and it is all
 * arithmetic.** A phone camera produces something like 4080×3072 — twelve
 * megapixels, forty-eight megabytes decoded — and a hostile file may claim
 * very much more than that. Everything here decides *how small* before a
 * decoder is handed anything, so the Android side ([BitmapPhoto]) is left with
 * no judgement to make at all.
 *
 * **Nothing here is a new limit.** Both numbers that matter already exist and
 * are already published:
 *
 * - [DiceSetLimits.MAX_TEXTURE_PIXELS] (2048) is the longest side a texture in
 *   any package may have, so it is the longest side a photo may be scaled to.
 *   The aspect ratio is kept, because a photo squashed to a square is not the
 *   picture somebody chose.
 * - [DiceSetLimits.MAX_TEXTURE_BYTES] (4 MiB) is what one texture file may
 *   weigh. A 2048-pixel photo is not *guaranteed* to encode under it, so the
 *   size is a ladder rather than a single answer: each rung halves the one
 *   before, and the first rung whose encoded bytes fit is the one that is
 *   written ([steps]). In practice the first rung always wins — a 2048×1536
 *   WebP of a photograph is a few hundred kilobytes — and the rest of the
 *   ladder exists so that "it did not fit" cannot become "it was written
 *   anyway".
 *
 * The package-wide cap, [DiceSetLimits.MAX_PACKAGE_TEXTURE_BYTES], is not
 * checked here at all. It is the validator's, and a photo goes through the
 * validator like anything else ([MineSets.addPhoto]).
 */
object PhotoScaling {
  /**
   * The longest side a photo may end up with: the texture limit, and nothing
   * of this file's own invention.
   */
  const val LONGEST_SIDE: Int = DiceSetLimits.MAX_TEXTURE_PIXELS

  /** What one written photo may weigh, which is what any texture may weigh. */
  const val MAX_BYTES: Long = DiceSetLimits.MAX_TEXTURE_BYTES

  /**
   * How far the ladder goes down before the photo is refused.
   *
   * Two hundred and fifty-six pixels across a 240 mm tray is roughly a pixel
   * per millimetre: visibly soft, still a picture. Below that it is a colour
   * with a suggestion in it, and a table look that is a blur is worse than
   * being told the photo could not be used.
   */
  const val SMALLEST_SIDE: Int = 256

  /**
   * The quality a photo is encoded at.
   *
   * Lossy, and deliberately: a photograph in PNG is several times the size for
   * a difference nobody can see on a tray under tumbling dice, and the size is
   * the thing being defended here. WebP because it is one of the two formats
   * the validator's own header reader understands
   * (`DiceSetLimits.IMAGE_EXTENSIONS`), so nothing new has to learn to read a
   * photo table.
   */
  const val QUALITY: Int = 80

  /**
   * [source] scaled to fit inside a [longest]-pixel box, keeping its shape.
   *
   * A picture already smaller than the box comes back untouched: upscaling a
   * photo makes a bigger file out of no more detail.
   */
  fun fitWithin(
    source: PhotoSize,
    longest: Int,
  ): PhotoSize {
    if (!source.real) return source
    if (source.longestSide <= longest) return source
    val scale = longest.toDouble() / source.longestSide
    return PhotoSize(
      width = scaled(source.width, scale),
      height = scaled(source.height, scale),
    )
  }

  /** The size a photo is aimed at: as large as a texture may be, and no larger. */
  fun target(source: PhotoSize): PhotoSize = fitWithin(source, LONGEST_SIDE)

  /**
   * The sizes to try, largest first, until one encodes small enough.
   *
   * Halving rather than stepping by a few per cent, for two reasons. A decoder
   * subsamples by powers of two ([sampleSize]), so a halved target is a target
   * the decode can reach without ever holding the full picture; and a ladder
   * that creeps down would encode the same photo twenty times to save a
   * kilobyte.
   */
  fun steps(source: PhotoSize): List<PhotoSize> {
    val first = target(source)
    if (!first.real) return emptyList()
    val ladder =
      generateSequence(first, ::half)
        .takeWhile { it.longestSide >= SMALLEST_SIDE }
        .toList()
    // A photo that is already smaller than the floor is not refused for it:
    // the floor is where *halving* stops, not a size a picture must reach.
    return ladder.ifEmpty { listOf(first) }
  }

  /**
   * What to hand `BitmapFactory`'s `inSampleSize` for [target].
   *
   * The largest power of two that still leaves both sides at least as big as
   * they need to be, so the decoder never allocates more than about four times
   * the pixels that are wanted and the remaining shrink is exact. Always at
   * least 1, which is what "do not subsample" means.
   */
  fun sampleSize(
    source: PhotoSize,
    target: PhotoSize,
  ): Int {
    if (!source.real || !target.real) return 1
    var sample = 1
    while (fitsAt(source, target, sample * 2)) sample *= 2
    return sample
  }

  /** Whether an encoded photo is small enough to be written as a texture. */
  fun fits(bytes: Int): Boolean = bytes in 1..MAX_BYTES.toInt()

  private fun fitsAt(
    source: PhotoSize,
    target: PhotoSize,
    sample: Int,
  ): Boolean = source.width / sample >= target.width && source.height / sample >= target.height

  private fun half(size: PhotoSize): PhotoSize =
    PhotoSize(width = (size.width / 2).coerceAtLeast(1), height = (size.height / 2).coerceAtLeast(1))

  /** Rounded, and never to nothing: a one-pixel side is still a picture. */
  private fun scaled(
    side: Int,
    scale: Double,
  ): Int = Math.round(side * scale).toInt().coerceAtLeast(1)
}
