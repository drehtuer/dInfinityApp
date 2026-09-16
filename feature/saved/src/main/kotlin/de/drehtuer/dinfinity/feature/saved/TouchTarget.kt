package de.drehtuer.dinfinity.feature.saved

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The smallest thing worth pressing (`docs/architecture.md`, "Accessibility").
 *
 * 48 dp is Android's own figure and WCAG 2.2's success criterion 2.5.8 at level
 * AA. The controls that need saying so are the ones whose label is a single
 * character — the export mark, the group's **…** — because a button sized to
 * its text is a button the size of one glyph.
 */
internal val TOUCH_TARGET: Dp = 48.dp
