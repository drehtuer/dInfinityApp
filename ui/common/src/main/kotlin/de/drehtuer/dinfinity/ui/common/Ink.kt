package de.drehtuer.dinfinity.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The two weaker inks the Modernist system prints secondary copy in.
 *
 * The design system has one text colour and dims it where the text is not the
 * point: `.text-muted` is `color-mix(in srgb, var(--color-text) 55%, transparent)`
 * and the prototype's row descriptions sit at `opacity: .65`
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
  const val MUTED: Float = 0.65f

  /** A footnote, and the design system's own `.text-muted`. */
  const val FAINT: Float = 0.55f

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
}
