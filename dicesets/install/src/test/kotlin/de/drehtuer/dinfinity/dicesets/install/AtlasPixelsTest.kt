package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

/**
 * The half of the decoder that needs Android: real bytes through a real
 * `BitmapFactory`.
 *
 * Robolectric rather than a device, because nothing here needs a GPU — a PNG
 * is decoded on the CPU, and what is being asked is whether the platform
 * decoder gives back the pixels the app then reasons about. What it *decides*
 * about them is `AtlasDecoderTest`'s and runs on a plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
class AtlasPixelsTest {
  @Test
  fun `a real png comes back as straight-alpha pixels, rows from the top`() {
    val image = requireNotNull(AtlasDecoder.pixelsOf(bytes(ATLAS))) { "a real png did not decode" }

    assertEquals(6, image.width)
    assertEquals(4, image.height)
    assertEquals("the drawn column lost its alpha", 0xFF, image.alphaAt(0, 0))
    assertEquals("a cleared pixel came back opaque", 0x00, image.alphaAt(5, 3))
  }

  @Test
  fun `the size is read without decoding anything`() {
    assertEquals(6 to 4, AtlasDecoder.boundsOf(bytes(ATLAS)))
  }

  @Test
  fun `bytes that are not a picture do not take the decoder with them`() {
    // Robolectric hands back a fake bitmap for anything it cannot identify
    // rather than the null a real decoder gives, so what can be asked here is
    // that nothing throws. That the refusal is a refusal is asked on the
    // device, in `AtlasDecoderDeviceTest`.
    val rubbish = "not a png at all, not even a little".toByteArray()

    AtlasDecoder.boundsOf(rubbish)
    AtlasDecoder.platform.decode(rubbish, "brass/textures/d6.png", faces = 6)
  }

  @Test
  fun `an atlas drawn in one column of three leaves the other two to be printed`() {
    // Six by four cut into a d6's 3x2 grid: the left column is drawn, so faces
    // 0 and 3 carry artwork and the other four carry their labels.
    val decoded = AtlasDecoder.platform.decode(bytes(ATLAS), "brass/textures/d6.png", faces = 6)

    val image = requireNotNull(decoded.drawn) { "a real png did not decode" }
    assertEquals(listOf(1, 2, 4, 5), image.emptyCells(faces = 6))
    assertEquals(listOf(ValidationCode.AtlasCellsEmpty), decoded.messages.map { it.code })
  }

  @Test
  fun `a truncated png is refused rather than drawn`() {
    // A sound IHDR with the image behind it cut off: the header check in
    // `dicesets/format` passes it and only a decoder finds out
    // (`docs/dice-sets.md`, "Validation").
    val decoded = AtlasDecoder.platform.decode(bytes(TRUNCATED), "brass/textures/d6.png", faces = 6)

    assertNull(decoded.drawn)
    assertTrue(decoded.messages.single().code in REFUSALS)
  }

  private fun bytes(base64: String): ByteArray = Base64.getDecoder().decode(base64)

  private companion object {
    /** Six by four, RGBA: the left two pixels of every row drawn, the rest clear. */
    const val ATLAS =
      "iVBORw0KGgoAAAANSUhEUgAAAAYAAAAECAYAAACtBE5DAAAAE0lEQVR42mM4oCDwH4QZ0AH1JAB/DQ955dj7TAAAAABJRU5ErkJggg=="

    /** The same file with its image data cut in half. */
    const val TRUNCATED = "iVBORw0KGgoAAAANSUhEUgAAAAYAAAAECAYAAACtBE5DAAAAE0k="

    /**
     * Either refusal is honest for a truncated file: a decoder that gives up
     * on the bounds and one that gives up on the pixels have found the same
     * thing out.
     */
    val REFUSALS = setOf(ValidationCode.TextureWillNotDecode, ValidationCode.TextureTooLarge)
  }
}
