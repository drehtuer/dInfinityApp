package de.drehtuer.dinfinity.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import de.drehtuer.dinfinity.core.model.AccentRamp
import de.drehtuer.dinfinity.core.model.Ground

/**
 * The colours a screen reaches for that Material has no role of its own for.
 *
 * The design system has **one** text colour and **one** red. It dims the ink
 * where the text is not the point — `.text-muted` is
 * `color-mix(in srgb, var(--color-text) 55%, transparent)` and the prototype's
 * row descriptions sit at `opacity: .65` — and it paints every refusal, every
 * star and every emphasis in the single accent
 * (`design/_ds/modernist-…/styles.css`, `design/dInfinityPhone.dc.html`).
 *
 * It is here rather than left to each screen because Material has no role for
 * *the text colour, dimmed*. `onSurfaceVariant` is the nearest thing and is a
 * colour of its own — whatever a theme maps it to, it is not the ink at 65 %,
 * and four screens reaching for it is four screens agreeing to whatever it
 * happens to be. Dimming the mapped ink instead keeps secondary copy in the
 * same pigment as the rest of the screen, on both grounds and behind whichever
 * accent the player chose.
 */
object Ink {
  /** A row's description: present, but not the thing being read. */
  const val MUTED: Float = Modernist.MUTED

  /** A footnote, and the design system's own `.text-muted`. */
  const val FAINT: Float = Modernist.FAINT

  /** The text colour at [MUTED]. */
  val muted: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED)

  /** The text colour at [FAINT]. */
  val faint: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onBackground.copy(alpha = FAINT)

  /**
   * The line between two rows, and the rule under a heading.
   *
   * `--color-divider`, which the theme puts on `outline`. Material's own
   * dividers default to `outlineVariant` instead — the theme fills that in
   * too, but a screen should say which of the two it means.
   */
  val divider: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline

  /**
   * The system's **one** red: what is wrong, what is chosen, what is starred.
   *
   * `--color-accent` is the only red in the palette. There is no separate
   * error colour, and the prototype paints a refusal, a bad formula and a
   * favourite in the same accent. Named here rather than reached for through
   * Material's `error` so that the call site says which red it means — the
   * theme maps `error` onto this same accent, and the two must not be able to
   * drift apart. It follows the accent the player chose in Settings.
   */
  val accent: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

  /**
   * `--color-accent-700`: the accent deep enough to print small words in.
   *
   * [accent] itself reaches only 3:1 against the ground, which is enough for
   * chrome and for large text and not enough for a kicker at 10 dp or a `+`
   * at 13. The design system's answer is the ramp's 700 step, and the app's
   * answer to a *freely chosen* accent is to mix that step rather than look it
   * up — which is what [AccentRamp] is, and what [Tag] already does for the
   * ramp's other two ends.
   *
   * Mixed from the theme's `primary`, which is the player's colour **after**
   * the clamp, against the ground it will be read on. Clamping an already
   * clamped colour returns it untouched, so going through [AccentRamp.of]
   * again costs nothing and keeps one rule rather than two.
   */
  val accentDeep: Color
    @Composable
    get() {
      val accent = MaterialTheme.colorScheme.primary
      val dark = MaterialTheme.colorScheme.background.luminance() < HALF
      return remember(accent, dark) {
        Color(AccentRamp.of(accent.toArgb(), if (dark) Ground.Dark else Ground.Light).v700)
      }
    }
}

/** Halfway up the luminance range: what divides a dark page from a light one. */
private const val HALF = 0.5f
