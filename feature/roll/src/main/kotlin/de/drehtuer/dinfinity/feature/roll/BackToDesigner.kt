package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Plate

/**
 * The way back to the face designer, for a throw that came from it
 * (`design/dInfinityPhone.dc.html`, the `fromDesigner` banner over the roll
 * screen; `docs/face-designer.md`, "Flow", step 4).
 *
 * **Roll it is a round trip, and until now it was a one-way street.** The
 * button hands the tray `mine:1d20` and leaves the player on the tray with no
 * control that says "designer" anywhere on it: the menu lists the face
 * designer, but it opens on whichever die the designer last opened on rather
 * than on the one being tested, and finding it means knowing it is under
 * *Customise*. The device session's words were "testing a roll from the face
 * designer offers no way back to the face designer".
 *
 * **It is a banner rather than a chevron**, and the reason is this app's own
 * navigation rule. A header chevron *climbs* — the same control lands on the
 * same screen every time, whatever path was taken to it
 * (`docs/architecture.md`, "Navigation") — and the tray is home, so it has no
 * up and can never grow one. A banner is the other kind of control: it is
 * about *this* visit, it is only here because this visit came from the
 * designer, and it goes away the moment the formula is touched. Putting that
 * on the chevron would be the one control in the app that sometimes climbs
 * and sometimes retraces.
 *
 * It is a [Plate] and not the prototype's accent-tinted strip for the reason
 * every other run of words on this screen is a plate: the tray is a lit table
 * whose colour the player chose, and the accent is never drawn on it
 * (`docs/physics-and-rendering.md`, "What is drawn over the table"). What the
 * prototype's colour was carrying — "this is not an ordinary tray" — the
 * kicker line carries instead.
 *
 * It takes no `modifier`. It is drawn in exactly one place — the bottom of
 * the tray's column of controls — and a slot for the caller to place it with
 * would be a slot with one caller and one value in it.
 *
 * @param onBack where it goes: the designer, on the die being tested.
 */
@Composable
internal fun BackToDesigner(onBack: () -> Unit) {
  val said = stringResource(R.string.roll_back_to_designer)
  Plate(
    modifier =
      Modifier
        .clickable(onClick = onBack)
        .semantics { contentDescription = said }
        .testTag(RollTestTags.BACK_TO_DESIGNER),
  ) {
    Column {
      Text(
        text = stringResource(R.string.roll_testing_your_die),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text = stringResource(R.string.roll_back_to_designer),
        style = MaterialTheme.typography.bodySmall,
        color = Ink.muted,
      )
    }
  }
}
