package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieMaterial
import org.junit.Assert.assertEquals
import org.junit.Test

/** The ink that reads on a paper (`docs/face-designer.md`, "The guide"). */
class PaperInkTest {
  @Test
  fun `black on light paper and white on dark`() {
    assertEquals(PaperInk.BLACK, PaperInk.on(PaperInk.WHITE))
    assertEquals(PaperInk.WHITE, PaperInk.on(PaperInk.BLACK))
    assertEquals("the built-in bone die", PaperInk.BLACK, PaperInk.on(DieMaterial.DEFAULT_COLOR_ARGB))
    assertEquals("a deep blue", PaperInk.WHITE, PaperInk.on(0xFF1A237E.toInt()))
  }

  @Test
  fun `a saturated yellow is light paper and a saturated red is dark`() {
    // Luminance, not lightness: the eye takes yellow for bright and red for
    // dark, and so does WCAG's arithmetic.
    assertEquals(PaperInk.BLACK, PaperInk.on(0xFFF8E71C.toInt()))
    assertEquals(PaperInk.WHITE, PaperInk.on(0xFF9013FE.toInt()))
  }
}
