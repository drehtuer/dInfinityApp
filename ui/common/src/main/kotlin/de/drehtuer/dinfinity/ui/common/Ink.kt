package de.drehtuer.dinfinity.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

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
}
