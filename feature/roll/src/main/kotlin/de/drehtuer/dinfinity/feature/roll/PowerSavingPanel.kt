package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.SectionKicker

/**
 * What stands where the tray would be when the player has turned the pictures
 * off (`design/dInfinityPhone.dc.html`, option `1z`).
 *
 * Power-saving mode puts **no surface on the screen at all** — not a surface
 * nothing draws to, which would be a buffer the compositor keeps for nothing
 * (`docs/physics-and-rendering.md`, "Power-saving mode"). What a player saw
 * was therefore an empty screen and a total arriving from nowhere, which is
 * indistinguishable from a renderer that has failed: the first session on a
 * phone lost twenty minutes to exactly that, convinced Filament was broken.
 *
 * So the mode says so. A flat grey panel, a kicker and one sentence, in the
 * prototype's own words — because the sentence that matters is the promise
 * that **the number is the same**. A player who thinks this mode is a cheaper
 * kind of roll is a player who will not use it.
 *
 * It is not a plate. A plate is a ground for a control that stands *over* the
 * table; this stands *instead of* it, which is why it fills the same space the
 * tray would and carries its own colour rather than the page's.
 */
@Composable
fun PowerSavingPanel(modifier: Modifier = Modifier) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Modernist.x2, Alignment.CenterVertically),
    modifier =
      modifier
        .fillMaxSize()
        .background(PANEL)
        // One thing to a screen reader rather than two: it is a notice, and a
        // notice read as a heading and then a sentence is a notice read twice
        // (`docs/architecture.md`, "Accessibility").
        .semantics(mergeDescendants = true) {}
        .testTag(RollTestTags.POWER_SAVING)
        .padding(Modernist.x6),
  ) {
    SectionKicker(text = stringResource(R.string.roll_power_saving), color = INK)
    Text(
      text = stringResource(R.string.roll_power_saving_same),
      style = MaterialTheme.typography.bodyMedium,
      color = INK,
    )
  }
}

/**
 * The prototype's `#9b9797`, which is a literal there and a literal here.
 *
 * It is not a palette token and should not be: the panel stands where a
 * *table* would, so it follows the tray's greys rather than the page's, and
 * the design system has no token for "the colour of a table that is not
 * there".
 */
private val PANEL = Color(0xFF9B9797) // design-system-exception: see above.

/**
 * Ink on it, because the panel is a light grey whichever theme is on.
 *
 * design-system-exception: the theme's ink follows the page and this does not
 * — on a dark page `onBackground` is nearly white, and nearly white on
 * `#9b9797` is a notice nobody can read. It is the palette's own `--color-text`
 * either way, pinned rather than read, because what it has to stand on is
 * pinned.
 */
private val INK = Color(0xFF201E1D)
