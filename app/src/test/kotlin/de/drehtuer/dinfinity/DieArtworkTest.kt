package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.dicesets.format.Severity
import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.dicesets.install.AtlasDecode
import de.drehtuer.dinfinity.render.filament.AtlasKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DieArtworkTest {
  @Test
  fun `a key is taken apart into the package and the path inside it`() {
    var asked: Pair<String, String>? = null
    val artwork =
      DieArtwork { setId, path ->
        asked = setId to path
        AtlasDecode.Drawn(image())
      }

    val found = artwork(AtlasKey.of("mine", "textures/d20.png"))

    assertEquals(30, found?.width)
    assertEquals("mine" to "textures/d20.png", asked)
  }

  @Test
  fun `a path with no package is never looked for`() {
    // A table look's floor texture: a path, and nothing saying whose
    // (`docs/TODO.md`, "Open questions").
    var asks = 0
    val artwork =
      DieArtwork { _, _ ->
        asks++
        AtlasDecode.Drawn(image())
      }

    assertNull(artwork("tables/felt.png"))
    assertEquals(0, asks)
  }

  @Test
  fun `a texture that will not decode leaves the die with no artwork`() {
    val artwork = DieArtwork { _, _ -> AtlasDecode.Unusable(listOf(refusal())) }

    assertNull(artwork(AtlasKey.of("mine", "textures/d20.png")))
  }

  private fun refusal() =
    ValidationMessage(
      severity = Severity.Error,
      code = ValidationCode.TextureWillNotDecode,
      text = "will not decode",
      file = "mine/textures/d20.png",
    )

  private fun image(): AtlasImage = AtlasImage(30, 20, ByteArray(30 * 20 * AtlasImage.CHANNELS))
}
