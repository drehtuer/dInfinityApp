package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The contrast arithmetic, against the values WCAG 2.2 itself publishes.
 *
 * Worth pinning rather than trusting: every accessibility claim the palette
 * makes is a number this file produces, and a gamma curve transcribed slightly
 * wrong is a claim that is quietly false.
 */
class ContrastTest {
  @Test
  fun `black on white is the widest there is`() {
    assertEquals(21.0, Contrast.ratio(BLACK, WHITE), TOLERANCE)
  }

  @Test
  fun `a colour against itself is one to one`() {
    listOf(BLACK, WHITE, ACCENT, 0xFF808080.toInt()).forEach { colour ->
      assertEquals(1.0, Contrast.ratio(colour, colour), TOLERANCE)
    }
  }

  @Test
  fun `which one is the ink does not change the answer`() {
    assertEquals(Contrast.ratio(ACCENT, WHITE), Contrast.ratio(WHITE, ACCENT), TOLERANCE)
  }

  @Test
  fun `luminance runs from black to white`() {
    assertEquals(0.0, Contrast.relativeLuminance(BLACK), TOLERANCE)
    assertEquals(1.0, Contrast.relativeLuminance(WHITE), TOLERANCE)
  }

  @Test
  fun `the alpha byte is ignored`() {
    // A ratio is between two colours already on screen. A caller that passes a
    // colour with no alpha byte set must get the same answer as one that does.
    assertEquals(Contrast.ratio(0xFFEC3013.toInt(), WHITE), Contrast.ratio(0x00EC3013, WHITE), TOLERANCE)
  }

  /**
   * Green carries most of what the eye reads as brightness and blue almost
   * none, which is why an equal-channel check would be wrong in both
   * directions at once.
   */
  @Test
  fun `the channels are not weighted equally`() {
    val green = Contrast.relativeLuminance(0xFF00FF00.toInt())
    val red = Contrast.relativeLuminance(0xFFFF0000.toInt())
    val blue = Contrast.relativeLuminance(0xFF0000FF.toInt())
    assertTrue(green > red, "green should read brighter than red")
    assertTrue(red > blue, "red should read brighter than blue")
    assertEquals(1.0, green + red + blue, TOLERANCE)
  }

  @Test
  fun `meets is the bar, not near it`() {
    assertTrue(Contrast.meets(BLACK, WHITE, Contrast.BODY_TEXT))
    assertFalse(Contrast.meets(ACCENT, WHITE, Contrast.BODY_TEXT))
    assertTrue(Contrast.meets(ACCENT, WHITE, Contrast.LARGE_TEXT))
    assertEquals(Contrast.LARGE_TEXT, Contrast.COMPONENT)
  }

  @Test
  fun `laying a colour over another mixes it by share`() {
    assertEquals(WHITE, Contrast.over(WHITE, 1.0, BLACK))
    assertEquals(BLACK, Contrast.over(WHITE, 0.0, BLACK))
    // Half of full white over black is the midpoint of every channel.
    assertEquals(0xFF808080.toInt(), Contrast.over(WHITE, 0.5, BLACK))
  }

  @Test
  fun `an impossible alpha is clamped rather than producing a colour that is not one`() {
    assertEquals(WHITE, Contrast.over(WHITE, 2.0, BLACK))
    assertEquals(BLACK, Contrast.over(WHITE, -1.0, BLACK))
  }

  @Test
  fun `what is laid over a ground is always opaque`() {
    // The result is a colour that is on screen, so it has no transparency left
    // to carry — a caller feeding it back into `ratio` must not be surprised.
    assertEquals(0xFF, (Contrast.over(0x40FFFFFF, 0.4, BLACK) ushr 24) and 0xFF)
  }

  private companion object {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val ACCENT = 0xFFEC3013.toInt()
    const val TOLERANCE = 0.01
  }
}
