package de.drehtuer.dinfinity.feature.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET

/**
 * One cut of a list, chosen or not — a set, a session, a saved roll, an order.
 *
 * Statistics and History both narrow a long list the same way, with a
 * horizontally scrolling row of these above it, and they were drawing it twice
 * with the same nine lines. One of them is enough.
 *
 * **It is not a [de.drehtuer.dinfinity.ui.common.ModernistButton].** A cut is
 * chosen or it is not, and the mark that says which is the accent on its
 * label; `Ghost` is the accent *by definition*, so drawn as one every cut in
 * the row would read as the chosen one and the only visual state would be
 * gone. What is drawn here is what `Ghost` draws — no box at all — with the
 * label's colour and weight left conditional.
 *
 * **It is not `OptionBox` either, yet.** That is the design system's answer to
 * a wrapping set of options, and these rows very nearly are one: the prototype
 * draws both screens' filters as `.seg` segmented controls. Moving them there
 * is a visible redesign — a bordered box that inverts, in place of a bare
 * accent word — and a semantics change with it, so it is recorded in
 * `docs/TODO.md` rather than done in a pass about removing duplication.
 *
 * The accent and the bold are marks only an eye can read, which is what
 * `selected` in the semantics tree is for: TalkBack announces "selected"
 * instead of leaving the state of the whole row a guess
 * (`docs/architecture.md`, "Accessibility").
 */
@Composable
internal fun Cut(
  label: String,
  chosen: Boolean,
  tag: String,
  onChoose: () -> Unit,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .clickable(role = Role.Button, onClick = onChoose)
        .sizeIn(minWidth = TOUCH_TARGET, minHeight = TOUCH_TARGET)
        .semantics { selected = chosen }
        .testTag(tag)
        .padding(Modernist.x2),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = if (chosen) MaterialTheme.colorScheme.primary else Ink.muted,
      fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
    )
  }
}
