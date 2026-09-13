package de.drehtuer.dinfinity.dicesets.format

/** One byte's worth of bits, as an `Int` mask: a `Byte` in Kotlin is signed. */
private const val BYTE = 0xFF

private const val BITS_PER_BYTE = 8

/** Both formats label their parts with a four-character name. */
private const val TAG_BYTES = 4

/** The four-character chunk name at [at]. */
private fun tag(
  bytes: ByteArray,
  at: Int,
): String = String(bytes, at, TAG_BYTES, Charsets.US_ASCII)

/** [count] bytes at [at], most significant first — how PNG writes a number. */
private fun bigEndian(
  bytes: ByteArray,
  at: Int,
  count: Int,
): Int {
  var value = 0
  for (index in 0 until count) value = (value shl BITS_PER_BYTE) or (bytes[at + index].toInt() and BYTE)
  return value
}

/** [count] bytes at [at], least significant first — how WebP writes one. */
private fun littleEndian(
  bytes: ByteArray,
  at: Int,
  count: Int,
): Int {
  var value = 0
  for (index in 0 until count) {
    value = value or ((bytes[at + index].toInt() and BYTE) shl (index * BITS_PER_BYTE))
  }
  return value
}

/**
 * The size of a picture, read out of the first few bytes of it.
 *
 * `docs/dice-sets.md` requires the dimensions to be known *before* anything is
 * decoded, and this is that: a PNG's `IHDR` or a WebP's header, read straight
 * out of the array. An image claiming to be thirty thousand pixels square is
 * refused without a decoder ever seeing it, which is the only way to refuse it
 * safely — the decoder is the part with the attack surface.
 *
 * It also checks the file is the kind of thing its name claims, which catches
 * the ordinary cases of a truncated download and a `.png` that is not one. It
 * is not a decoder and does not pretend to be: the real decode happens on the
 * IO dispatcher at load time, inside these same caps.
 */
internal object ImageHeader {
  /** What a picture's header said about it. */
  data class Size(
    val width: Int,
    val height: Int,
  )

  /** The size of [bytes], or `null` when it is not a picture of a kind the app reads. */
  fun sizeOf(bytes: ByteArray): Size? =
    when {
      isPng(bytes) -> pngSize(bytes)
      isWebp(bytes) -> webpSize(bytes)
      else -> null
    }

  private fun isPng(bytes: ByteArray): Boolean =
    bytes.size >= PNG_HEADER_BYTES && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }

  /** `IHDR` is required by the format to be the first chunk, and it carries the size. */
  private fun pngSize(bytes: ByteArray): Size? {
    if (tag(bytes, PNG_IHDR_TAG_AT) != "IHDR") return null
    return Size(
      width = bigEndian(bytes, PNG_WIDTH_AT, INT32),
      height = bigEndian(bytes, PNG_HEIGHT_AT, INT32),
    )
  }

  private fun isWebp(bytes: ByteArray): Boolean =
    bytes.size >= WEBP_HEADER_BYTES && tag(bytes, RIFF_TAG_AT) == "RIFF" && tag(bytes, WEBP_TAG_AT) == "WEBP"

  /**
   * WebP keeps its size in a different place in each of its three flavours, so
   * each is read where the container says it is.
   */
  private fun webpSize(bytes: ByteArray): Size? =
    when (tag(bytes, WEBP_CHUNK_TAG_AT)) {
      "VP8X" -> extended(bytes)
      "VP8L" -> lossless(bytes)
      "VP8 " -> lossy(bytes)
      else -> null
    }

  /** The extended container writes both dimensions as three bytes, less one. */
  private fun extended(bytes: ByteArray): Size? {
    if (bytes.size < VP8X_HEADER_BYTES) return null
    return Size(
      width = littleEndian(bytes, VP8X_WIDTH_AT, INT24) + 1,
      height = littleEndian(bytes, VP8X_HEIGHT_AT, INT24) + 1,
    )
  }

  /** A lossy WebP's key frame carries two fourteen-bit dimensions. */
  private fun lossy(bytes: ByteArray): Size? {
    if (bytes.size < VP8_HEADER_BYTES) return null
    return Size(
      width = littleEndian(bytes, VP8_WIDTH_AT, INT16) and FOURTEEN_BITS,
      height = littleEndian(bytes, VP8_HEIGHT_AT, INT16) and FOURTEEN_BITS,
    )
  }

  /** A lossless WebP packs both dimensions into the four bytes after its signature. */
  private fun lossless(bytes: ByteArray): Size? {
    if (bytes.size < LOSSLESS_HEADER_BYTES || bytes[LOSSLESS_SIGNATURE_AT] != LOSSLESS_SIGNATURE) return null
    val packed = littleEndian(bytes, LOSSLESS_BITS_AT, INT32)
    return Size(
      width = (packed and FOURTEEN_BITS) + 1,
      height = ((packed shr FOURTEEN) and FOURTEEN_BITS) + 1,
    )
  }

  private const val FOURTEEN = 14
  private const val FOURTEEN_BITS = 0x3FFF
  private const val INT32 = 4
  private const val INT24 = 3
  private const val INT16 = 2

  private const val PNG_HEADER_BYTES = 24
  private const val PNG_IHDR_TAG_AT = 12
  private const val PNG_WIDTH_AT = 16
  private const val PNG_HEIGHT_AT = 20

  private const val RIFF_TAG_AT = 0
  private const val WEBP_TAG_AT = 8
  private const val WEBP_CHUNK_TAG_AT = 12
  private const val WEBP_HEADER_BYTES = 16

  private const val VP8X_HEADER_BYTES = 30
  private const val VP8X_WIDTH_AT = 24
  private const val VP8X_HEIGHT_AT = 27

  private const val VP8_HEADER_BYTES = 30
  private const val VP8_WIDTH_AT = 26
  private const val VP8_HEIGHT_AT = 28

  private const val LOSSLESS_HEADER_BYTES = 25
  private const val LOSSLESS_SIGNATURE_AT = 20
  private const val LOSSLESS_BITS_AT = 21
  private const val LOSSLESS_SIGNATURE: Byte = 0x2F

  private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
