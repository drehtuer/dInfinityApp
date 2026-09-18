package de.drehtuer.dinfinity.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * The accent line that says what the block under it *is*
 * (`.card-kicker` in `design/_ds/modernist-…/styles.css`, and every section
 * heading in `design/dInfinityPhone.dc.html`).
 *
 * Ten sp, tracked wide, semibold, in the accent. It is the system's smallest
 * type and its most frequent use of colour: "Install from a URL or file",
 * "Installed", "Validation report", "Dice · 7 · rendered from the set". The
 * prototype separates a screen into blocks with these and a 2 dp [Rule];
 * nothing else does the grouping, because the system has no cards, no shadows
 * and no rounded containers to do it with.
 *
 * **It is a label, not a heading**, which is why it is smaller than anything
 * the app sets a sentence in and why it is the one place a whole line prints
 * in the accent. A block's *name* — the set's, the roll's — is a heading and
 * is set in the ink.
 *
 * **The case is the writer's, not this component's.** The prototype sets these
 * in `text-transform: uppercase`. Compose has no text transform, so applying
 * it would mean uppercasing the string itself — which changes what a screen
 * reader says, and is a decision recorded as an open question in
 * `docs/TODO.md` rather than taken here. The tracking, which is what makes
 * uppercase readable, is applied either way.
 *
 * @param color which ink it is set in. The accent by default, because that is
 *   what a kicker is nearly always for. The two exceptions are both on the
 *   roll screen's plates: `COUNTING` is the *label* of a readout rather than
 *   an accent line and is set in the ink at 65 %, and the two plates that do
 *   want the accent want its **700 step** ([Ink.accentDeep]) — a kicker is
 *   10 dp, which is small text, and the accent itself only clears the bar for
 *   large (`docs/physics-and-rendering.md`, "What is drawn over the table").
 */
@Composable
fun SectionKicker(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = Ink.accent,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    fontSize = Modernist.Type.kicker,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = Modernist.kickerTracking,
    color = color,
    modifier = modifier,
  )
}
