package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * A horizontal rule, in the design system's ink and at one of its two weights
 * (`.hr` in `design/_ds/modernist-…/styles.css`).
 *
 * The Modernist system separates with lines rather than with shadows or
 * rounded cards, and it uses exactly two: a **2 dp rule between blocks** — the
 * `.hr`, and the `border-bottom` under every section of the prototype's menu
 * and settings screens — and a **1 dp hairline between the rows inside one
 * block**. Both print `--color-divider`, which is the text colour at 40 % and
 * which Material knows as `outline`.
 *
 * It exists because `HorizontalDivider()` is only ever one of the two: it
 * draws a 1 dp line of `outlineVariant` and takes no position on the heavier
 * rule, so the 2 dp weight — the one that does the grouping — had no way of
 * being drawn and every line on a screen came out the same.
 *
 * Its contrast is the divider token's own: 2.41:1 on the light ground, which
 * is under the 3:1 a control boundary wants and is written down as an open
 * question in `docs/TODO.md`. A rule between rows is decoration, so this is
 * the use the shortfall is tolerable in; a border that says where a control is
 * is not, and `SegmentedControl` says so where it draws one.
 */
@Composable
fun Rule(
  modifier: Modifier = Modifier,
  weight: RuleWeight = RuleWeight.Block,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(weight.thickness)
        .background(Ink.divider),
  )
}

/** The two weights the design system draws a line at. */
enum class RuleWeight(
  val thickness: Dp,
) {
  /** Between the rows of one block. */
  Hairline(Modernist.hairline),

  /** Between blocks, and under a heading. The system's `.hr`. */
  Block(Modernist.rule),
}
