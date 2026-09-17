package de.drehtuer.dinfinity.ui.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The smallest thing worth pressing (`docs/architecture.md`, "Touch targets").
 *
 * 48 dp is Android's own figure and WCAG 2.2's success criterion 2.5.8 at level
 * AA. Every screen in the app has controls whose *label* is one character — a
 * back arrow, a cut of a list, an option of a segmented control — and a button
 * sized to its text is a button the size of its text.
 *
 * It is **not** in [Modernist], deliberately. That object is a transcription of
 * `design/_ds/modernist-.../styles.css` and a test holds it to the stylesheet;
 * this number is not in the stylesheet and never will be, because it is a rule
 * about fingers on a phone rather than a decision about how the app looks. Five
 * modules each wrote it down before this file existed, which is four too many
 * for a figure a standard fixes.
 */
val TOUCH_TARGET: Dp = 48.dp
