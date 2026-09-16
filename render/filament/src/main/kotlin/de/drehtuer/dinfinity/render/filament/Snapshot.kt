package de.drehtuer.dinfinity.render.filament

/**
 * A frame that was drawn, read back off the GPU.
 *
 * The far side of [Stage] can only say *a frame was drawn*; this is the one
 * thing it can hand back that something on this side can ask questions of, and
 * it is what a table thumbnail is made of (`docs/tables.md`, "Thumbnails").
 *
 * Reading pixels back means waiting for the GPU, which no frame of a roll ever
 * does. A still picture drawn once, off screen, can afford it.
 *
 * Not a `data class`: it holds an array, and two arrays with the same contents
 * are not equal — the same reason
 * [de.drehtuer.dinfinity.core.model.AtlasImage] is not one.
 *
 * @param pixels red, green, blue and alpha per pixel, a byte each, **rows from
 *   the top**. Which is not the order a graphics driver reads them in, and
 *   [fromBottomUp] is where that is put right.
 */
class Snapshot(
  val width: Int,
  val height: Int,
  val pixels: ByteArray,
) {
  init {
    require(width > 0 && height > 0) { "a frame of $width by $height has no pixels" }
    require(pixels.size == width * height * CHANNELS) {
      "a frame of $width by $height is ${width * height * CHANNELS} bytes, not ${pixels.size}"
    }
  }

  /**
   * True when every pixel of this frame is the same colour.
   *
   * The cheapest thing that notices a frame nothing arrived in — a camera
   * pointing at nothing, a material that compiled to black, or a driver that
   * renders correctly to a screen and hands back an empty buffer when asked
   * to read one. It is the same question the device suite asks, asked by the
   * code that ships, so a phone whose driver will not read a frame back shows
   * the swatch rather than a black rectangle (`docs/tables.md`).
   */
  val uniform: Boolean
    get() {
      for (byte in CHANNELS until pixels.size) {
        if (pixels[byte] != pixels[byte % CHANNELS]) return false
      }
      return true
    }

  /**
   * The same pixels, packed one to an `Int` as alpha, red, green and blue.
   *
   * Which is what an Android bitmap is made from, and the one unambiguous way
   * to hand it these bytes: a bitmap's own channel order is a property of the
   * platform and of the machine's endianness, and copying a buffer into one
   * relies on both being what this file assumed. The packing is arithmetic, so
   * it is here and has a test, rather than being a channel order somebody
   * notices because a green table came out purple.
   */
  fun argb(): IntArray =
    IntArray(width * height) { pixel ->
      val at = pixel * CHANNELS
      (byteAt(at + ALPHA) shl ALPHA_SHIFT) or
        (byteAt(at) shl RED_SHIFT) or
        (byteAt(at + GREEN) shl GREEN_SHIFT) or
        byteAt(at + BLUE)
    }

  private fun byteAt(index: Int): Int = pixels[index].toInt() and BYTE

  companion object {
    /** Red, green, blue and alpha, a byte each. */
    const val CHANNELS: Int = 4

    private const val BYTE = 0xFF
    private const val GREEN = 1
    private const val BLUE = 2
    private const val ALPHA = 3
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /**
     * The same frame, with its rows turned the right way up.
     *
     * A graphics driver's origin is the bottom left and every image in this
     * app counts rows from the top — the atlas grid, a die's printed numbers,
     * and what an Android `Bitmap` expects to be handed. So the flip belongs
     * here, in plain Kotlin where a test can hold it still, rather than in the
     * one file that talks to Filament where it could only be checked by
     * looking at a phone and deciding whether the picture was upside down.
     */
    fun fromBottomUp(
      width: Int,
      height: Int,
      rows: ByteArray,
    ): Snapshot {
      require(rows.size == width * height * CHANNELS) {
        "a frame of $width by $height is ${width * height * CHANNELS} bytes, not ${rows.size}"
      }
      val stride = width * CHANNELS
      val turned = ByteArray(rows.size)
      for (row in 0 until height) {
        rows.copyInto(
          destination = turned,
          destinationOffset = row * stride,
          startIndex = (height - 1 - row) * stride,
          endIndex = (height - row) * stride,
        )
      }
      return Snapshot(width, height, turned)
    }
  }
}
