package de.drehtuer.dinfinity.core.model

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccentColorTest {
  @Test
  fun `ids are unique and stable`() {
    val ids = AccentColor.entries.map(AccentColor::id)
    assertEquals(ids.size, ids.toSet().size, "two accents share an id")
    // Spelled out rather than derived from the enum: this is the set written
    // to storage, so a rename has to fail here before it resets everybody.
    assertEquals(
      listOf("vermilion", "coral", "sky", "moss", "amber", "violet"),
      ids,
    )
  }

  @Test
  fun `an unknown or missing id falls back to the default`() {
    assertEquals(AccentColor.Default, AccentColor.ofId(null))
    assertEquals(AccentColor.Default, AccentColor.ofId(""))
    assertEquals(AccentColor.Default, AccentColor.ofId("chartreuse"))
    assertEquals(AccentColor.Vermilion, AccentColor.Default)
  }

  @Test
  fun `every id round-trips`() {
    AccentColor.entries.forEach { accent ->
      assertEquals(accent, AccentColor.ofId(accent.id))
    }
  }

  /**
   * The accent carries meaning on its own, so it has to be legible on both
   * grounds. 3:1 is the WCAG bar for large text and user-interface components,
   * which is what the accent is used for — body copy in the accent uses the
   * deeper pressed step instead.
   */
  @Test
  fun `every accent reaches 3 to 1 against both grounds`() {
    AccentColor.entries.forEach { accent ->
      val onLight = contrast(accent.argb, LIGHT_GROUND)
      val onDark = contrast(accent.argb, DARK_GROUND)
      assertTrue(onLight >= MIN_CONTRAST, "${accent.id} is ${"%.2f".format(onLight)}:1 on the light ground")
      assertTrue(onDark >= MIN_CONTRAST, "${accent.id} is ${"%.2f".format(onDark)}:1 on the dark ground")
    }
  }

  /**
   * The pressed step has to be a different colour from the accent — otherwise
   * pressing shows nothing — and has to stay readable on its own ground. On a
   * light ground it also carries body copy, which is the 4.5:1 bar rather than
   * 3:1.
   */
  @Test
  fun `pressed steps are distinct from the accent and legible on their ground`() {
    AccentColor.entries.forEach { accent ->
      assertTrue(
        accent.pressedOnLightArgb != accent.argb && accent.pressedOnDarkArgb != accent.argb,
        "${accent.id} has a pressed step identical to the accent",
      )
      val body = contrast(accent.pressedOnLightArgb, LIGHT_GROUND)
      assertTrue(body >= BODY_CONTRAST, "${accent.id} pressed is ${"%.2f".format(body)}:1 on the light ground")
      val onDark = contrast(accent.pressedOnDarkArgb, DARK_GROUND)
      assertTrue(onDark >= MIN_CONTRAST, "${accent.id} pressed is ${"%.2f".format(onDark)}:1 on the dark ground")
    }
  }

  /**
   * The four accents the design system has no ramp for are derived with the
   * rule the design itself uses for an ad-hoc accent,
   * `color-mix(in srgb, accent 58%, text)` (`design/Logo.dc.html`). Recomputing
   * it here stops a hand-edited constant drifting away from the system.
   */
  @Test
  fun `derived accents match the design's colour-mix rule`() {
    listOf(AccentColor.Sky, AccentColor.Moss, AccentColor.Amber, AccentColor.Violet).forEach { accent ->
      assertEquals(
        mix(accent.argb, TEXT_ON_LIGHT, ACCENT_SHARE),
        accent.pressedOnLightArgb,
        "${accent.id} light pressed step is not mix(accent 58%, text)",
      )
      assertEquals(
        mix(accent.argb, TEXT_ON_DARK, ACCENT_SHARE),
        accent.pressedOnDarkArgb,
        "${accent.id} dark pressed step is not mix(accent 58%, text)",
      )
    }
  }

  /** The two the system does define keep the CSS ramp's exact values. */
  @Test
  fun `the design system's own accents keep their ramp steps`() {
    assertEquals(0xFFEC3013.toInt(), AccentColor.Vermilion.argb)
    assertEquals(0xFFAE1800.toInt(), AccentColor.Vermilion.pressedOnLightArgb)
    assertEquals(0xFFDD2B0F.toInt(), AccentColor.Vermilion.pressedOnDarkArgb)
    assertEquals(0xFFE15B47.toInt(), AccentColor.Coral.argb)
    assertEquals(0xFF9E3526.toInt(), AccentColor.Coral.pressedOnLightArgb)
    assertEquals(0xFFC94B39.toInt(), AccentColor.Coral.pressedOnDarkArgb)
  }

  private companion object {
    const val MIN_CONTRAST = 3.0
    const val BODY_CONTRAST = 4.5
    const val ACCENT_SHARE = 0.58
    const val LIGHT_GROUND = 0xFFF3F2F2.toInt()
    const val DARK_GROUND = 0xFF201E1D.toInt()
    const val TEXT_ON_LIGHT = 0xFF201E1D.toInt()
    const val TEXT_ON_DARK = 0xFFF3F2F2.toInt()

    fun channel(
      argb: Int,
      shift: Int,
    ) = (argb shr shift) and 0xFF

    fun mix(
      a: Int,
      b: Int,
      shareOfA: Double,
    ): Int {
      fun blend(shift: Int): Int {
        val value = shareOfA * channel(a, shift) + (1 - shareOfA) * channel(b, shift)
        return Math.round(value).toInt()
      }
      return (0xFF shl 24) or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
    }

    fun relativeLuminance(argb: Int): Double {
      fun linear(shift: Int): Double {
        val c = channel(argb, shift) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
      }
      return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }

    fun contrast(
      a: Int,
      b: Int,
    ): Double {
      val la = relativeLuminance(a)
      val lb = relativeLuminance(b)
      return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
  }
}
