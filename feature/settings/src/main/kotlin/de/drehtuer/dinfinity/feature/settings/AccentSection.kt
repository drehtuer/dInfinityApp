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
import androidx.compose.material3.Slider
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
import de.drehtuer.dinfinity.designer.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Sheet
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
    AccentPicker(
      start = selected.argb,
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

/**
 * A colour of the player's own.
 *
 * Android has no colour-picker intent to send them to — the prototype's
 * `<input type="color">` is the browser's, and there is no Android equivalent
 * to borrow — so the app draws the one it already has: hue, depth and
 * brightness over `designer/Ink`, which is the picker the face designer offers
 * and the arithmetic a JVM test already holds (`docs/face-designer.md`, "A
 * colour beyond the twelve"). Two pickers in one app that disagreed about what
 * a hue is would be one too many.
 *
 * The patch shows the colour as chosen. What the app will actually paint may
 * be deeper, and the sentence above the grid says so; showing the clamped
 * colour here would make the slider look broken.
 */
@Composable
private fun AccentPicker(
  start: Int,
  onDismiss: () -> Unit,
  onChosen: (Int) -> Unit,
) {
  var hsv by remember { mutableStateOf(Ink.hsv(start)) }
  Sheet(
    title = stringResource(R.string.settings_accent_picker_title),
    onDismiss = onDismiss,
    modifier = Modifier.testTag(SettingsTestTags.ACCENT_PICKER),
    actions = {
      ModernistButton(
        text = stringResource(R.string.settings_accent_picker_use),
        onClick = { onChosen(hsv.argb) },
        kind = ModernistButtonKind.Primary,
        modifier = Modifier.testTag(SettingsTestTags.ACCENT_PICKER_USE),
      )
      ModernistButton(
        text = stringResource(R.string.settings_accent_picker_cancel),
        onClick = onDismiss,
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(SettingsTestTags.ACCENT_PICKER_CANCEL),
      )
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(Modernist.x1)) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(TOUCH_TARGET)
            .background(Color(hsv.argb))
            .border(Modernist.rule, MaterialTheme.colorScheme.outline)
            .semantics { contentDescription = Ink.hex(hsv.argb) }
            .testTag(SettingsTestTags.ACCENT_PICKER_PATCH),
      )
      Channel(R.string.settings_accent_hue, hsv.hue, HUE_ROUND, SettingsTestTags.ACCENT_HUE) {
        hsv = hsv.copy(hue = it)
      }
      Channel(R.string.settings_accent_depth, hsv.saturation, 1f, SettingsTestTags.ACCENT_DEPTH) {
        hsv = hsv.copy(saturation = it)
      }
      Channel(R.string.settings_accent_brightness, hsv.value, 1f, SettingsTestTags.ACCENT_BRIGHTNESS) {
        hsv = hsv.copy(value = it)
      }
    }
  }
}

/** One of the picker's three sliders, named so a screen reader can say which. */
@Composable
private fun Channel(
  label: Int,
  value: Float,
  most: Float,
  tag: String,
  onChange: (Float) -> Unit,
) {
  val name = stringResource(label)
  Text(text = name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  Slider(
    value = value,
    onValueChange = onChange,
    valueRange = 0f..most,
    modifier =
      Modifier
        .semantics { contentDescription = name }
        .testTag(tag),
  )
}

/** Four across (`docs/design-handover.md`). */
private const val COLUMNS = 4

/** The prototype's `height: 44px`. */
private val SWATCH = 44.dp

/** Degrees round the wheel — the hue slider's range, not a colour of its own. */
private const val HUE_ROUND = 360f
