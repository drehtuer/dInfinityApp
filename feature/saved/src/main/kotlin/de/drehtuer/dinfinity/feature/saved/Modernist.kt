package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The parts of the Modernist system the saved-roll screens draw with
 * (`design/_ds/modernist-f7022762-4cb9-409e-a6ce-7116795bae5b/styles.css`).
 *
 * Transcribed rather than imported. The app's own copy of these tokens is
 * `de.drehtuer.dinfinity.theme.ModernistTokens`, which lives in `:app` — and
 * `:app` depends on this module, so the arrow cannot be turned round without
 * moving the tokens to a module both can see. That move is its own change;
 * until it happens the values here are checked against the CSS by
 * [ModernistTest], so a drift is a failing test rather than a screen nobody
 * looked at.
 *
 * Everything the screens in this module measure with comes from here. A number
 * written straight into a layout is what this file exists to stop.
 */
internal object Modernist {
  /** `--space-1` … `--space-8`. Nothing in the module is off this scale. */
  val x1: Dp = 4.dp
  val x2: Dp = 8.dp
  val x3: Dp = 12.dp
  val x4: Dp = 16.dp
  val x6: Dp = 24.dp

  /**
   * `.hr`, `.nav`'s underline and every section rule: 2 dp. The system draws
   * rules, not hairlines — a 1 px line is what it uses *inside* one list
   * ([hairline]), to separate rows of the same thing.
   */
  val rule: Dp = 2.dp

  /** `.table td`'s `border-bottom: 1px` — the line between two rows of one list. */
  val hairline: Dp = 1.dp

  /**
   * `--radius-sm`, `--radius-md` and `--radius-lg` are all `0px`. Nothing in
   * this system has a rounded corner, and Material's buttons are pills unless
   * they are told otherwise: `ButtonDefaults.shape` reads `CornerFull`, which
   * is a circle no `Shapes` override reaches.
   */
  val radius: Dp = 0.dp

  /** [radius], as the shape a Material button has to be handed. */
  val square: Shape = RectangleShape

  /**
   * The prototype's `opacity:.65` — what it dims a row's second line, a
   * caption and a column heading to.
   */
  const val MUTED: Float = 0.65f
}

/**
 * Copy that is deliberately quiet: a caption, a count, a formula under a name.
 *
 * The ink at 65 %, which is what the prototype writes as `opacity:.65`. Not
 * `onSurfaceVariant`: that role is not one the app's theme fills in, so it
 * falls back to Material's baseline lavender-grey — a colour the Modernist
 * palette does not contain.
 */
internal val muted: Color
  @Composable @ReadOnlyComposable
  get() = MaterialTheme.colorScheme.onBackground.copy(alpha = Modernist.MUTED)

/**
 * The line between two rows, and the rule under a heading.
 *
 * `--color-divider`, which the theme puts on `outline`. Material's dividers
 * default to `outlineVariant` instead, which the theme does not fill in.
 */
internal val divider: Color
  @Composable @ReadOnlyComposable
  get() = MaterialTheme.colorScheme.outline

/**
 * What is wrong, and what a favourite is starred in: the system's **one** red.
 *
 * `--color-accent` is the only red in the palette. There is no separate error
 * colour, and the prototype paints a refusal, a bad formula and a star in the
 * accent. Material's `error` is a different red, so using it put two reds on
 * one screen — and three, once the player picks their own accent in Settings.
 */
internal val accent: Color
  @Composable @ReadOnlyComposable
  get() = MaterialTheme.colorScheme.primary

/**
 * `.seg-opt`: one option of a segmented control, filled with the accent while
 * it is the one chosen and printing its label in the ground colour.
 *
 * Material's own filter chip fills with `secondaryContainer`, a role the app's
 * theme does not set — so until now a chosen group, table or question came out
 * in Material's baseline lavender, a colour the Modernist palette has no
 * step of.
 */
@Composable
internal fun segColours(): SelectableChipColors =
  FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = MaterialTheme.colorScheme.onBackground,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.background,
  )

/**
 * The editor's icon buttons, which invert to the **ink** rather than to the
 * accent when they are chosen (`design/dInfinityPhone.dc.html`,
 * `iconOptions`: `bg:'var(--color-text)'`, `color:'var(--color-bg)'`).
 *
 * The one mark already prints in the roll's own colour, and a red square
 * behind a red mark would be the two saying the same thing over each other.
 */
@Composable
internal fun inkColours(): SelectableChipColors =
  FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = MaterialTheme.colorScheme.onBackground,
    selectedContainerColor = MaterialTheme.colorScheme.onBackground,
    selectedLabelColor = MaterialTheme.colorScheme.background,
  )

/** `.seg`'s own `1px solid var(--color-divider)`, around every option of it. */
@Composable
@ReadOnlyComposable
internal fun segBorder(): BorderStroke = BorderStroke(width = Modernist.hairline, color = divider)
