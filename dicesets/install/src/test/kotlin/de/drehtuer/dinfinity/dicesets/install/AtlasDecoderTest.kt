package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits
import de.drehtuer.dinfinity.dicesets.format.Severity
import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the decoder decides, with the platform's decoder replaced.
 *
 * The two halves are tested apart on purpose (`docs/architecture.md`,
 * decision 40): every refusal, its order and its wording is plain Kotlin and is
 * driven from here, and whether a real PNG comes back as pixels is a question
 * for a device, where `AtlasPixelsTest` asks it over Robolectric.
 */
class AtlasDecoderTest {
  @Test
  fun `a file whose size cannot even be read is refused before anything is decoded`() {
    var decoded = false
    val result =
      decoder(bounds = { null }, pixels = {
        decoded = true
        null
      }).decode(SOME_BYTES, "brass/textures/d6.png")

    assertEquals(listOf(ValidationCode.TextureWillNotDecode), codesOf(result))
    assertTrue("a file with no size was handed to a decoder anyway", !decoded)
    assertNull(result.drawn)
  }

  @Test
  fun `a file that passes the header check and will not decode is reported, not thrown`() {
    // The check `dicesets/format` cannot make from bytes alone: a sound IHDR
    // with a truncated image behind it gives up its size and has no pixels.
    val result = decoder(bounds = { 64 to 64 }, pixels = { null }).decode(SOME_BYTES, "brass/textures/d6.png")

    assertEquals(listOf(ValidationCode.TextureWillNotDecode), codesOf(result))
    assertEquals(Severity.Error, result.messages.single().severity)
    assertTrue(
      result.messages
        .single()
        .text
        .contains("brass/textures/d6.png"),
    )
    assertEquals("brass/textures/d6.png", result.messages.single().file)
  }

  @Test
  fun `an enormous image is refused from its bounds, with nothing decoded`() {
    var decoded = false
    val huge = DiceSetLimits.MAX_TEXTURE_PIXELS + 1
    val result =
      decoder(bounds = { huge to 16 }, pixels = {
        decoded = true
        null
      }).decode(SOME_BYTES, "brass/textures/d6.png")

    assertEquals(listOf(ValidationCode.TextureTooLarge), codesOf(result))
    assertTrue("thirty thousand pixels reached a decoder", !decoded)
    assertTrue(
      result.messages
        .single()
        .text
        .contains("$huge×16"),
    )
  }

  @Test
  fun `an image at exactly the limit is decoded`() {
    val edge = DiceSetLimits.MAX_TEXTURE_PIXELS
    val result = decoder(bounds = { edge to edge }, pixels = { drawn() }).decode(SOME_BYTES, "brass/d6.png", faces = 6)

    assertTrue(result is AtlasDecode.Drawn)
    assertEquals(emptyList<ValidationCode>(), codesOf(result))
  }

  @Test
  fun `an image too tall is refused as well as one too wide`() {
    val huge = DiceSetLimits.MAX_TEXTURE_PIXELS + 1
    val result = decoder(bounds = { 16 to huge }, pixels = { drawn() }).decode(SOME_BYTES, "brass/d6.png")

    assertEquals(listOf(ValidationCode.TextureTooLarge), codesOf(result))
  }

  @Test
  fun `an atlas with empty cells installs, and says which faces it left`() {
    val result = decoder(pixels = { blank() }).decode(SOME_BYTES, "brass/d6.png", faces = 6)

    assertEquals(listOf(ValidationCode.AtlasCellsEmpty), codesOf(result))
    assertEquals(Severity.Warning, result.messages.single().severity)
    assertTrue(
      result.messages
        .single()
        .text
        .contains("6 of 6 cells"),
    )
    assertTrue("a warning is not a refusal", result is AtlasDecode.Drawn)
  }

  @Test
  fun `an atlas with every cell drawn says nothing`() {
    val result = decoder(pixels = { drawn() }).decode(SOME_BYTES, "brass/d6.png", faces = 6)

    assertEquals(emptyList<ValidationCode>(), codesOf(result))
    assertEquals(30, result.drawn!!.width)
  }

  @Test
  fun `an atlas no die wears has no grid, so its cells are not checked`() {
    val result = decoder(pixels = { blank() }).decode(SOME_BYTES, "brass/spare.png", faces = null)

    assertEquals(emptyList<ValidationCode>(), codesOf(result))
  }

  @Test
  fun `a die with no faces at all has no cells to be empty`() {
    val result = decoder(pixels = { blank() }).decode(SOME_BYTES, "brass/spare.png", faces = 0)

    assertEquals(emptyList<ValidationCode>(), codesOf(result))
  }

  private fun decoder(
    bounds: (ByteArray) -> Pair<Int, Int>? = { 30 to 20 },
    pixels: (ByteArray) -> AtlasImage?,
  ): AtlasDecoder = AtlasDecoder(bounds = bounds, pixels = pixels)

  private fun codesOf(result: AtlasDecode): List<ValidationCode> = result.messages.map { it.code }

  /** A d6's atlas with nothing in any cell. */
  private fun blank(): AtlasImage = AtlasImage(30, 20, ByteArray(30 * 20 * AtlasImage.CHANNELS))

  /** The same, opaque everywhere. */
  private fun drawn(): AtlasImage {
    val pixels = ByteArray(30 * 20 * AtlasImage.CHANNELS)
    pixels.fill(0xFF.toByte())
    return AtlasImage(30, 20, pixels)
  }

  private companion object {
    val SOME_BYTES = ByteArray(8)
  }
}
