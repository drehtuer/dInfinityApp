package de.drehtuer.dinfinity.feature.stats

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The smallest thing worth pressing (`docs/architecture.md`, "Accessibility").
 *
 * 48 dp is Android's own figure and WCAG 2.2's 2.5.8 at level AA. It is here
 * rather than in each file because the statistics screens are full of controls
 * whose *label* is one character — a back arrow, a cut of the list — and a
 * button sized to its text is a button the size of its text.
 */
internal val TOUCH_TARGET: Dp = 48.dp
