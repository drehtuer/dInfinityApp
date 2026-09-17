package de.drehtuer.dinfinity.feature.sets

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * The parts of the Modernist system these screens draw with that Material's
 * colour roles and defaults do not give them
 * (`design/_ds/modernist-f7022762-4cb9-409e-a6ce-7116795bae5b/styles.css`).
 *
 * The app's theme fills in `primary`, `background`, `surface`, their `on*`
 * partners and `outline` from the design tokens and leaves the rest of
 * Material's scheme at its own defaults (`theme/Theme.kt`). Anything drawn in
 * one of the leftovers — `onSurfaceVariant`, `surfaceVariant`, `error` — is
 * drawn in Material's baseline lavender-grey or Material's own red, and
 * neither is a colour this design system contains.
 *
 * The same module-local copy `feature/roll` and `feature/stats` keep, and for
 * the same reason: the tokens themselves live in `:app`, which depends on this
 * module, so the arrow cannot be turned round without moving them somewhere
 * both can see.
 */
internal object Modernist {
  /**
   * `--radius-sm`, `--radius-md` and `--radius-lg` are all `0px`. Nothing in
   * this system has a rounded corner, and a Material button is a pill unless
   * it is told otherwise: `ButtonDefaults.shape` reads `CornerFull`, which is
   * a corner no `Shapes` override in the theme reaches.
   */
  val square: Shape = RectangleShape

  /**
   * `.btn-secondary`'s and `.input`'s `border: 1px` — the thinnest line the
   * system draws, and what edges a control rather than divides a page.
   */
  val hairline: Dp = 1.dp

  /** A kicker's `letter-spacing: .1em` (`h6`, and every section heading). */
  val kickerTracking: TextUnit = 0.1.em

  /**
   * The prototype's `opacity:.65` — what it dims a caption, a version, a hint
   * and a row's second line to. `feature/stats` keeps the same number.
   */
  const val MUTED: Float = 0.65f
}

/**
 * Copy that is deliberately quiet: a caption, a version, a licence.
 *
 * The system has no second text colour. Secondary copy is the same ink,
 * thinned — which is why it stays right when the ground flips to dark and why
 * it cannot drift away from the text it sits under.
 */
internal val muted: Color
  @Composable @ReadOnlyComposable
  get() = MaterialTheme.colorScheme.onBackground.copy(alpha = Modernist.MUTED)

/**
 * What is wrong, in the system's **one** red.
 *
 * `--color-accent` is the only red in the palette: there is no separate error
 * colour, and the prototype paints every refusal in the accent. Named here
 * rather than reached for through Material's `error` so that the call site
 * says which red it means — the theme maps `error` onto the same accent, and
 * the two must not be able to drift apart.
 */
internal val wrong: Color
  @Composable @ReadOnlyComposable
  get() = MaterialTheme.colorScheme.primary
