package de.drehtuer.dinfinity.dicesets.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading a picture's size out of its first few bytes, which is how a texture
 * too large to decode is refused without decoding it
 * (`docs/dice-sets.md`, "Textures").
 */
class ImageHeaderTest {
  @Test
  fun `a PNG reports the size in its IHDR`() {
    assertEquals(ImageHeader.Size(1024, 512), ImageHeader.sizeOf(png(1024, 512)))
  }

  @Test
  fun `a PNG claiming an absurd size still only costs a header read`() {
    assertEquals(ImageHeader.Size(30_000, 30_000), ImageHeader.sizeOf(png(30_000, 30_000)))
  }

  @Test
  fun `a file that is not a PNG at all is not a picture`() {
    assertNull(ImageHeader.sizeOf("not a png".encodeToByteArray()))
    assertNull(ImageHeader.sizeOf(ByteArray(0)))
  }

  @Test
  fun `a PNG cut off before its header is not a picture`() {
    assertNull(ImageHeader.sizeOf(png(64, 64).copyOf(12)))
  }

  @Test
  fun `a PNG whose first chunk is not IHDR is not one this reads`() {
    val broken = png(64, 64).copyOf()
    broken[12] = 'X'.code.toByte()
    assertNull(ImageHeader.sizeOf(broken))
  }

  @Test
  fun `an extended WebP reports its size`() {
    assertEquals(ImageHeader.Size(800, 600), ImageHeader.sizeOf(webpExtended(800, 600)))
  }

  @Test
  fun `a lossy WebP reports its size`() {
    assertEquals(ImageHeader.Size(320, 240), ImageHeader.sizeOf(webpLossy(320, 240)))
  }

  @Test
  fun `a lossless WebP reports its size`() {
    assertEquals(ImageHeader.Size(256, 128), ImageHeader.sizeOf(webpLossless(256, 128)))
  }

  @Test
  fun `a RIFF file that is not a WebP is not a picture`() {
    val bytes = webpLossy(16, 16).copyOf()
    "AVI ".forEachIndexed { index, char -> bytes[8 + index] = char.code.toByte() }
    assertNull(ImageHeader.sizeOf(bytes))
  }

  @Test
  fun `a WebP with a chunk this app does not read is not a picture`() {
    val bytes = webpLossy(16, 16).copyOf()
    "XXXX".forEachIndexed { index, char -> bytes[12 + index] = char.code.toByte() }
    assertNull(ImageHeader.sizeOf(bytes))
  }

  @Test
  fun `a WebP cut off before its dimensions is not a picture`() {
    assertNull(ImageHeader.sizeOf(webpLossy(16, 16).copyOf(20)))
    assertNull(ImageHeader.sizeOf(webpExtended(16, 16).copyOf(20)))
    assertNull(ImageHeader.sizeOf(webpLossless(16, 16).copyOf(22)))
  }

  private fun riff(chunk: String): ByteArray =
    "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() + chunk.toByteArray()

  private fun webpExtended(
    width: Int,
    height: Int,
  ): ByteArray = riff("VP8X") + ByteArray(8) + little24(width - 1) + little24(height - 1) + ByteArray(3)

  private fun webpLossy(
    width: Int,
    height: Int,
  ): ByteArray = riff("VP8 ") + ByteArray(10) + little16(width) + little16(height) + ByteArray(2)

  private fun webpLossless(
    width: Int,
    height: Int,
  ): ByteArray {
    val packed = (width - 1) or ((height - 1) shl 14)
    return riff("VP8L") + ByteArray(4) + byteArrayOf(0x2F) + little32(packed) + ByteArray(4)
  }

  private fun little16(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte())

  private fun little24(value: Int) = little16(value) + byteArrayOf((value ushr 16).toByte())

  private fun little32(value: Int) = little24(value) + byteArrayOf((value ushr 24).toByte())
}
