package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.AccentChoice
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.ui.common.ColourPicker
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET

/**
 * The one colour the interface spends (`design/dInfinity.dc.html`, option 1q;
 * `design/dInfinityPhone.dc.html`, the Settings screen).
 *
 * **Six presets and a colour of the player's own**, in a grid four across.
 * Four rather than the five that fitted before, because six swatches wrapping
 * five and one left an orphan on the second row, and because six presets plus
 * a custom swatch is seven — which comes out 4 + 3 with nothing left over
 * (`docs/architecture.md`, "Settings").
 *
 * **The hex above the grid is the colour that was chosen, not the colour that
 * is painted.** Every accent goes through a contrast clamp on its way to the
 * theme, and on a light page that deepens the paler ones — but a picker that
 * silently shows something other than what the finger landed on is a picker
 * nobody believes. So the clamp is explained in the sentence and is never
 * allowed to rewrite the swatch (`AccentRamp.clamp`).
 *
 * The swatches say their names to a screen reader rather than printing them,
 * which is what the prototype does: four columns of a phone's width is not
 * room for "Modernist red" under a chip, and the name of the chosen one is
 * already on the screen.
 */
@Composable
internal fun AccentSection(
  selected: AccentChoice,
  onAccentSelected: (AccentChoice) -> Unit,
) {
  // The only state on this screen, and it is not a setting: a sheet that is
  // open is not something the app should remember having been opened.
  var picking by remember { mutableStateOf(false) }
  Section(
    heading = stringResource(R.string.settings_accent_heading),
    explanation = stringResource(R.string.settings_accent_explanation),
  ) {
    Text(
      text = selected.hex,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.testTag(SettingsTestTags.ACCENT_HEX),
    )
    Column(
      verticalArrangement = Arrangement.spacedBy(Modernist.x2),
      modifier = Modifier.selectableGroup(),
    ) {
      // `null` is the custom swatch, last, where the prototype puts it.
      (AccentColor.entries + null).chunked(COLUMNS).forEach { cells ->
        Row(
          horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
          modifier = Modifier.fillMaxWidth(),
        ) {
          cells.forEach { preset ->
            if (preset == null) {
              CustomSwatch(selected = selected, onClick = { picking = true })
            } else {
              PresetSwatch(
                accent = preset,
                isSelected = preset == selected,
                onClick = { onAccentSelected(preset) },
              )
            }
          }
          // So a short last row keeps the columns of the one above it rather
          // than spreading three swatches across the width of four.
          repeat(COLUMNS - cells.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
      }
    }
  }
  if (picking) {
    // `ui/common`'s picker, which is the one the face designer and the
    // saved-roll editor open too. **The patch shows the colour as chosen**:
    // what the app paints may be deeper, the sentence above the grid says so,
    // and a patch showing the clamped colour would make the slider look
    // broken (`ColourPicker`).
    ColourPicker(
      start = selected.argb,
      title = stringResource(R.string.settings_accent_picker_title),
      tags = SettingsTestTags.ACCENT_PICKER,
      onDismiss = { picking = false },
      onChosen = {
        picking = false
        onAccentSelected(AccentChoice.Custom(it))
      },
    )
  }
}

/** One of the six, drawn in itself. */
@Composable
private fun RowScope.PresetSwatch(
  accent: AccentColor,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val label = stringResource(accent.labelRes())
  Swatch(
    colour = Color(accent.argb),
    isSelected = isSelected,
    label = label,
    tag = SettingsTestTags.accentSwatch(accent),
    onClick = onClick,
  )
}

/**
 * The seventh swatch: whatever the player picked, or an empty cell inviting
 * them to.
 *
 * Empty rather than a plausible colour waiting to be confirmed — the prototype
 * fills it with a placeholder blue, and a swatch that shows a colour nobody
 * chose is a swatch that will be tapped by somebody expecting to get *that*.
 */
@Composable
private fun RowScope.CustomSwatch(
  selected: AccentChoice,
  onClick: () -> Unit,
) {
  val custom = selected as? AccentChoice.Custom
  Swatch(
    colour = custom?.let { Color(it.argb) } ?: MaterialTheme.colorScheme.surface,
    isSelected = custom != null,
    label = stringResource(R.string.accent_custom),
    tag = SettingsTestTags.ACCENT_CUSTOM,
    onClick = onClick,
  )
}

/**
 * A 44 dp chip in a 48 dp target.
 *
 * The chip is the height the design draws it and the thing a finger hits is
 * not: 44 dp is the prototype's swatch and 48 dp is Android's floor for
 * anything pressable, so the target is grown around the chip rather than the
 * chip grown past its drawing (`ui/common/TOUCH_TARGET`).
 *
 * Which one is chosen is said by the **edge**, in the text colour rather than
 * in the accent: on a swatch whose whole point is its colour, a mark drawn in
 * another colour is the only one guaranteed to be visible on all seven.
 */
@Composable
private fun RowScope.Swatch(
  colour: Color,
  isSelected: Boolean,
  label: String,
  tag: String,
  onClick: () -> Unit,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .weight(1f)
        .heightIn(min = TOUCH_TARGET)
        .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = label }
        .testTag(tag),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(SWATCH)
          .background(colour)
          .border(
            width = if (isSelected) Modernist.rule else Modernist.hairline,
            color =
              if (isSelected) {
                MaterialTheme.colorScheme.onBackground
              } else {
                MaterialTheme.colorScheme.outline
              },
          ),
    )
  }
}

/** Four across (`docs/design-handover.md`). */
private const val COLUMNS = 4

/** The prototype's `height: 44px`. */
private val SWATCH = 44.dp
