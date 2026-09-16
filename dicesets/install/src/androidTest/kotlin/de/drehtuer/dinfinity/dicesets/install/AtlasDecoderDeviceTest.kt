package de.drehtuer.dinfinity.dicesets.install

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/**
 * The decoder against the platform's own, on a device
 * (`docs/dice-sets.md`, "Textures").
 *
 * Everything the decoder *decides* is tested on a JVM and most of what the
 * platform does is tested under Robolectric. What is left for here is the one
 * thing neither can answer honestly: Robolectric hands back a fake bitmap for
 * bytes it cannot identify, so "a file that will not decode is refused" —
 * which is the whole of check (c) in `docs/TODO.md`, Step 3 — can only be
 * proven where a real decoder runs.
 */
@RunWith(AndroidJUnit4::class)
class AtlasDecoderDeviceTest {
  @Test
  fun bytesThatAreNotAPictureAreRefused() {
    val rubbish = "not a png at all, not even a little".toByteArray()

    val decoded = AtlasDecoder.platform.decode(rubbish, "brass/textures/d6.png", faces = 6)

    assertNull("a stranger's rubbish was drawn on a die", decoded.drawn)
    assertEquals(ValidationCode.TextureWillNotDecode, decoded.messages.single().code)
  }

  @Test
  fun aPngThatIsTruncatedAfterItsHeaderIsRefused() {
    // The check `dicesets/format` cannot make from bytes alone: the `IHDR` is
    // sound, so the header check passes it, and there is no picture behind it.
    val decoded = AtlasDecoder.platform.decode(bytes(TRUNCATED), "brass/textures/d6.png", faces = 6)

    assertNull(decoded.drawn)
    assertEquals(ValidationCode.TextureWillNotDecode, decoded.messages.single().code)
  }

  @Test
  fun anEmptyFileIsRefusedRatherThanThrown() {
    val decoded = AtlasDecoder.platform.decode(ByteArray(0), "brass/textures/d6.png", faces = 6)

    assertNull(decoded.drawn)
    assertEquals(ValidationCode.TextureWillNotDecode, decoded.messages.single().code)
  }

  @Test
  fun aRealPngComesBackAsStraightAlphaPixels() {
    val decoded = AtlasDecoder.platform.decode(bytes(ATLAS), "brass/textures/d6.png", faces = 6)

    val image = requireNotNull(decoded.drawn) { "a real png did not decode on this device" }
    assertEquals(6, image.width)
    assertEquals(4, image.height)
    // Premultiplied pixels would have dragged the cleared half towards black
    // and kept an alpha the material could not tell from coverage.
    assertEquals(0xFF, image.alphaAt(0, 0))
    assertEquals(0x00, image.alphaAt(5, 3))
    assertEquals(listOf(1, 2, 4, 5), image.emptyCells(faces = 6))
    assertEquals(ValidationCode.AtlasCellsEmpty, decoded.messages.single().code)
  }

  private fun bytes(base64: String): ByteArray = Base64.getDecoder().decode(base64)

  private companion object {
    /** Six by four, RGBA: the left two pixels of every row drawn, the rest clear. */
    const val ATLAS =
      "iVBORw0KGgoAAAANSUhEUgAAAAYAAAAECAYAAACtBE5DAAAAE0lEQVR42mM4oCDwH4QZ0AH1JAB/DQ955dj7TAAAAABJRU5ErkJggg=="

    /** The same file with its image data cut in half. */
    const val TRUNCATED = "iVBORw0KGgoAAAANSUhEUgAAAAYAAAAECAYAAACtBE5DAAAAE0k="
  }
}
