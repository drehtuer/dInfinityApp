package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one way a colour is written down.
 *
 * Three places wrote these six digits before this object existed — the
 * accent's label, the designer's ink readout and a saved roll's colour tag —
 * and the test that matters is that all three now say the same thing about the
 * same colour.
 */
class HexTest {
  @Test
  fun `a colour is written down without its alpha`() {
    assertEquals("#EC3013", Hex.of(0xFFEC3013.toInt()))
    assertEquals("#000000", Hex.of(0xFF000000.toInt()))
    assertEquals("#FFFFFF", Hex.of(0xFFFFFFFF.toInt()))
  }

  @Test
  fun `a colour with no alpha byte at all is written the same way`() {
    // What arrives from a file, a draft or a slider is not always opaque yet.
    assertEquals("#2B5AA8", Hex.of(0x002B5AA8))
    assertEquals("#2B5AA8", Hex.of(0xFF2B5AA8.toInt()))
  }

  @Test
  fun `a digit that would be dropped is padded rather than lost`() {
    assertEquals("#00000F", Hex.of(0xFF00000F.toInt()))
  }

  @Test
  fun `what the accent prints above its grid is what this writes`() {
    AccentColor.entries.forEach { accent ->
      assertEquals(accent.hex, Hex.of(accent.argb), accent.name)
    }
  }
}
