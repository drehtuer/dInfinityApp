package de.drehtuer.dinfinity.feature.graph

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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * The parts of the Modernist system the outcome graph draws with
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
 * Everything this screen measures with comes from here. A number written
 * straight into a layout is what this file exists to stop.
 */
internal object Modernist {
  /** `--space-1` … `--space-8`. Nothing in the module is off this scale. */
  val x1: Dp = 4.dp
  val x2: Dp = 8.dp
  val x3: Dp = 12.dp
  val x4: Dp = 16.dp

  /**
   * `.hr`, `.nav`'s underline and every section rule: 2 dp. The system draws
   * rules, not hairlines — a 1 px line is what it uses *inside* one block
   * ([hairline]), to separate rows of the same thing.
   */
  val rule: Dp = 2.dp

  /** `.table td`'s `border-bottom: 1px` — the line between two rows of one list. */
  val hairline: Dp = 1.dp

  /**
   * The line at the total that actually came up: 3 px, where the mean's is 2
   * (`design/dInfinityPhone.dc.html`, the graph's `border-left:3px solid
   * var(--color-accent)`). It is the one thing on the chart that is about this
   * player rather than about the formula, so it is drawn heavier than the
   * statistic behind it.
   */
  val mark: Dp = 3.dp

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

  /**
   * `--color-neutral-500` as a share of the ink.
   *
   * The grey the chart draws a bar outside ±1σ in. The ramp step itself is
   * `#9b9797` on both grounds — the design system does not flip it for dark
   * mode — and the ink at 45 % over the ground lands on it in light and near
   * enough in dark, which is a colour that follows the theme rather than one
   * written twice.
   */
  const val FAINT: Float = 0.45f

  /** A kicker's `letter-spacing:.08em` — the labels under the chart. */
  val kickerTracking: TextUnit = 0.08.em
}

/**
 * Copy that is deliberately quiet: a caption, a unit, a column heading.
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
 * `.seg-opt`: one option of a segmented control, filled with the accent while
 * it is the one chosen and printing its label in the ground colour.
 *
 * Material's own filter chip fills with `secondaryContainer`, a role the app's
 * theme does not set — so until now the question the chart answers was asked
 * in Material's baseline lavender, a colour the Modernist palette has no step
 * of.
 */
@Composable
internal fun segColours(): SelectableChipColors =
  FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = MaterialTheme.colorScheme.onBackground,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.background,
  )

/** `.seg`'s own `1px solid var(--color-divider)`, around every option of it. */
@Composable
@ReadOnlyComposable
internal fun segBorder(): BorderStroke = BorderStroke(width = Modernist.hairline, color = divider)

/**
 * `--color-neutral-500`: the grey a bar outside ±1σ is drawn in.
 *
 * See [Modernist.FAINT] for why it is the ink thinned rather than the ramp
 * step written out.
 */
internal val faint: Color
  @Composable @ReadOnlyComposable
  get() = MaterialTheme.colorScheme.onBackground.copy(alpha = Modernist.FAINT)
