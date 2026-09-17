package de.drehtuer.dinfinity.feature.designer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The parts of the Modernist system this screen draws with that Material's
 * colour roles and defaults do not give it
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
 * both can see. It is named for the system rather than for ink because
 * `designer/`'s own `Ink` — the hex and HSV arithmetic — is already imported
 * here under that name.
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
   * `.hr` and every section rule: 2 dp. The system draws rules, not hairlines
   * — which is what the prototype outlines the drawing square and each colour
   * swatch with (`outline:2px`, `border:2px`).
   */
  val rule: Dp = 2.dp

  /** How big a colour swatch is drawn (`width:26px;height:26px`). */
  val swatch: Dp = 26.dp

  /**
   * The prototype's `opacity:.65` — what it dims a caption, a version, a hint
   * and a row's second line to. `feature/stats` keeps the same number.
   */
  const val MUTED: Float = 0.65f
}

/**
 * Copy that is deliberately quiet: a hint, a hex, the name of a tool that is
 * not in hand.
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
