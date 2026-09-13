package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings

/**
 * Settings. Stateless: the caller owns [AppSettings] and persists the change,
 * so this renders the same whether it is driven by the real repository or by a
 * value in a test.
 *
 * It styles itself from Material's colour roles rather than the Modernist
 * tokens, which live in `:app`. `DInfinityTheme` maps one onto the other, so
 * the result is the same and a feature module does not have to reach upwards.
 */
@Composable
fun SettingsScreen(
  settings: AppSettings,
  onAccentSelected: (AccentColor) -> Unit,
  modifier: Modifier = Modifier,
  onPowerSavingChanged: (Boolean) -> Unit = {},
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        // Scrolls, because the list only grows: appearance, shake, haptics,
        // sound, rounding, the default set and the table are all still to come
        // (`docs/TODO.md`, Step 4.10), and a setting below the fold on a short
        // phone is a setting nobody can reach.
        .verticalScroll(rememberScrollState())
        .padding(24.dp)
        .testTag(SettingsTestTags.SCREEN),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text(
      text = stringResource(R.string.settings_title),
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
    AccentSection(selected = settings.accentColor, onAccentSelected = onAccentSelected)
    PowerSection(on = settings.powerSaving, onChanged = onPowerSavingChanged)
  }
}

/**
 * Roll without drawing the dice (`design/dInfinity.dc.html`, option 1z).
 *
 * On or off and nothing else — no "automatic", no battery threshold. A roll
 * that silently stopped rendering because the battery dipped would be a
 * surprise in the middle of a game (`docs/architecture.md`, decision 16).
 */
@Composable
private fun PowerSection(
  on: Boolean,
  onChanged: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = stringResource(R.string.settings_power_heading),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.settings_power_explanation),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
        Modifier
          .fillMaxWidth()
          .toggleable(
            value = on,
            role = Role.Switch,
            onValueChange = onChanged,
          ).testTag(SettingsTestTags.POWER_SAVING),
    ) {
      Text(
        text = stringResource(R.string.settings_power_label),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
      )
      Switch(checked = on, onCheckedChange = null)
    }
  }
}

@Composable
private fun AccentSection(
  selected: AccentColor,
  onAccentSelected: (AccentColor) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = stringResource(R.string.settings_accent_heading),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.settings_accent_explanation),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onBackground,
    )
    FlowRow(
      modifier = Modifier.selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AccentColor.entries.forEach { accent ->
        AccentSwatch(
          accent = accent,
          isSelected = accent == selected,
          onClick = { onAccentSelected(accent) },
        )
      }
    }
  }
}

@Composable
private fun AccentSwatch(
  accent: AccentColor,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val label = stringResource(accent.labelRes())
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier =
      Modifier
        .selectable(
          selected = isSelected,
          role = Role.RadioButton,
          onClick = onClick,
        ).testTag(SettingsTestTags.accentSwatch(accent)),
  ) {
    // Selection is a ring in the text colour, not a tick in the accent: on a
    // swatch whose whole point is its colour, a mark drawn in another colour
    // is the only one guaranteed to be visible on all six.
    Box(
      modifier =
        Modifier
          .size(SWATCH_SIZE)
          .background(Color(accent.argb))
          .then(
            if (isSelected) {
              Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground)
            } else {
              Modifier.border(1.dp, MaterialTheme.colorScheme.outline)
            },
          ),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
    )
  }
}

private val SWATCH_SIZE = 56.dp

/** Stable handles for tests, so a wording change does not break them. */
object SettingsTestTags {
  const val SCREEN: String = "settings:screen"

  /** The power-saving switch (design option 1z). */
  const val POWER_SAVING: String = "settings:power-saving"

  fun accentSwatch(accent: AccentColor): String = "settings:accent:${accent.id}"
}
