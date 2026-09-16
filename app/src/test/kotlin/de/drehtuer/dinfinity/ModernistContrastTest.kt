package de.drehtuer.dinfinity

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.Contrast
import de.drehtuer.dinfinity.theme.ModernistTokens
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette, measured rather than looked at
 * (`docs/architecture.md`, "Accessibility").
 *
 * WCAG 2.2 AA asks 4.5:1 of body copy and 3:1 of large text and of a control's
 * own boundary. Every pair the interface actually puts on screen is checked
 * here, on both grounds, so that editing a hex value fails a test instead of
 * quietly making a screen unreadable.
 *
 * Two pairs are **known to fall short** and are held at their measured value
 * rather than asserted to pass. Both would need the palette itself to change,
 * which is a design decision and not a test's to make; they are written down
 * in `docs/TODO.md` under "Open questions" with these numbers.
 */
class ModernistContrastTest {
  @Test
  fun `body copy clears 4 point 5 to 1 on both grounds`() {
    passes("light text on background", ModernistTokens.Light.text, ModernistTokens.Light.background, Contrast.BODY_TEXT)
    passes("light text on surface", ModernistTokens.Light.text, ModernistTokens.Light.surface, Contrast.BODY_TEXT)
    passes("dark text on background", ModernistTokens.Dark.text, ModernistTokens.Dark.background, Contrast.BODY_TEXT)
    passes("dark text on surface", ModernistTokens.Dark.text, ModernistTokens.Dark.surface, Contrast.BODY_TEXT)
  }

  /**
   * The accent is spent on chrome and on large text, which is the 3:1 bar.
   * Body copy in the accent uses the deep ramp step instead, which is the 4.5:1
   * one — that is what `accentOnLightText` is for.
   */
  @Test
  fun `the accent clears the bar for what it is used for`() {
    passes("accent on light background", ModernistTokens.accent, ModernistTokens.Light.background, Contrast.LARGE_TEXT)
    passes("accent on light surface", ModernistTokens.accent, ModernistTokens.Light.surface, Contrast.LARGE_TEXT)
    passes("accent on dark background", ModernistTokens.accent, ModernistTokens.Dark.background, Contrast.LARGE_TEXT)
    passes("accent on dark surface", ModernistTokens.accent, ModernistTokens.Dark.surface, Contrast.LARGE_TEXT)
    passes(
      "accent body copy on light",
      ModernistTokens.accentOnLightText,
      ModernistTokens.Light.background,
      Contrast.BODY_TEXT,
    )
  }

  /**
   * The rule under a heading and between two rows, at the design system's 40 %
   * of the text colour.
   *
   * It passes on the dark ground and not on the light one, and the same token
   * is Material's `outline` — which is an outlined button's border as well as a
   * divider. A divider is decoration; a border that says where a control is is
   * not, and 2.41:1 is under the 3:1 that asks for.
   */
  @Test
  fun `the divider is short of 3 to 1 on the light ground`() {
    val onLight =
      Contrast.ratio(
        Contrast.over(ModernistTokens.Light.text.toArgb(), DIVIDER_ALPHA, ModernistTokens.Light.background.toArgb()),
        ModernistTokens.Light.background.toArgb(),
      )
    val onDark =
      Contrast.ratio(
        Contrast.over(ModernistTokens.Dark.text.toArgb(), DIVIDER_ALPHA, ModernistTokens.Dark.background.toArgb()),
        ModernistTokens.Dark.background.toArgb(),
      )
    noWorseThan("divider on the light ground", onLight, DIVIDER_ON_LIGHT)
    assertTrue(
      "the divider is ${said(onDark)} on the dark ground, which is under ${Contrast.COMPONENT}",
      onDark >= Contrast.COMPONENT,
    )
  }

  /**
   * The label on a filled button, which is the app's loudest control: `Roll`,
   * `Save group`, the confirmation of a reset.
   *
   * `onPrimary` is the ground colour by design, so the label is the pale ink on
   * the accent — and no accent in the palette reaches 4.5:1 that way on the
   * light ground. The fix is a palette change either way (a deeper fill, or the
   * ramp's 700 step as the fill), so this holds the floor rather than asserting
   * a pass.
   */
  @Test
  fun `a filled button's label is short of 4 point 5 to 1`() {
    AccentColor.entries.forEach { accent ->
      val onLight = Contrast.ratio(ModernistTokens.Light.background.toArgb(), accent.argb)
      val onDark = Contrast.ratio(ModernistTokens.Dark.background.toArgb(), accent.argb)
      // Still legible as large text on both grounds, which is what keeps this
      // an open question rather than a bug to stop the release.
      assertTrue(
        "${accent.id} button label is ${said(onLight)} on the light ground",
        onLight >= Contrast.LARGE_TEXT,
      )
      assertTrue(
        "${accent.id} button label is ${said(onDark)} on the dark ground",
        onDark >= Contrast.LARGE_TEXT,
      )
    }
    noWorseThan(
      "the default accent's button label on the light ground",
      Contrast.ratio(ModernistTokens.Light.background.toArgb(), AccentColor.Default.argb),
      DEFAULT_BUTTON_ON_LIGHT,
    )
  }

  private fun passes(
    what: String,
    ink: Color,
    ground: Color,
    bar: Double,
  ) {
    val measured = Contrast.ratio(ink.toArgb(), ground.toArgb())
    assertTrue("$what is ${said(measured)}, which is under $bar", measured >= bar)
  }

  /**
   * A ratchet: a known shortfall may be repaired, never deepened.
   *
   * [floor] is the measured ratio written down in `docs/TODO.md`, and the
   * comparison allows the last printed decimal: this is a guard against a
   * palette edit, not against the last bit of a double.
   */
  private fun noWorseThan(
    what: String,
    measured: Double,
    floor: Double,
  ) {
    assertTrue("$what has sunk to ${said(measured)}, below its recorded $floor", measured >= floor - ROUNDING)
  }

  private fun said(ratio: Double): String = "%.2f:1".format(ratio)

  private companion object {
    /** `--color-divider`: the text colour at 40 % (`ModernistTokens.divider`). */
    const val DIVIDER_ALPHA = 0.4

    /** Measured, and written down in `docs/TODO.md`. */
    const val DIVIDER_ON_LIGHT = 2.41
    const val DEFAULT_BUTTON_ON_LIGHT = 3.76

    /** The floors above are the ratios as `docs/TODO.md` prints them. */
    const val ROUNDING = 0.005
  }
}
