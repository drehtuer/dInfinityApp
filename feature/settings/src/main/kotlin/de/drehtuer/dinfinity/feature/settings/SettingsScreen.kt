package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.drehtuer.dinfinity.core.model.AccentChoice
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.ui.common.ColourPickerTags
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.RuleWeight

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
  onAccentSelected: (AccentChoice) -> Unit,
  modifier: Modifier = Modifier,
  onAppearanceSelected: (Appearance) -> Unit = {},
  onPowerSavingChanged: (Boolean) -> Unit = {},
  onShakeChanged: (Boolean) -> Unit = {},
  onHapticsChanged: (Boolean) -> Unit = {},
  onSoundChanged: (Boolean) -> Unit = {},
  onRoundingSelected: (Rounding) -> Unit = {},
  onTableViewSelected: (TableView) -> Unit = {},
  onDeveloperToolsChanged: (Boolean) -> Unit = {},
  onRepository: () -> Unit = {},
  version: String = "",
  menu: @Composable () -> Unit = {},
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        // Scrolls, because the list only grows, and a setting below the fold
        // on a short phone is a setting nobody can reach.
        .verticalScroll(rememberScrollState())
        // The prototype's own `padding: 12px 16px` on a settings row, hoisted
        // to the screen because every rule on it runs the full width.
        .padding(horizontal = Modernist.x4, vertical = Modernist.x3)
        .testTag(SettingsTestTags.SCREEN),
    // 12 dp above and below each rule is the 24 dp the design leaves between
    // one row's words and the next. It was 24 — twice the drawing — which is
    // most of why the screen ran to twice the length.
    verticalArrangement = Arrangement.spacedBy(Modernist.x3),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      menu()
    }
    // A rule under the title and between every setting, which is how this
    // system separates one thing from the next — it has no cards to put them
    // in and no shadows to lift them with (`design/dInfinity.dc.html`,
    // option 1y).
    Rule()
    AppearanceSection(chosen = settings.appearance, onChosen = onAppearanceSelected)
    Rule(weight = RuleWeight.Hairline)
    // Second, which is where the design's own list of rows puts it — right
    // under Appearance, above everything about what the app *does*.
    TableViewSection(chosen = settings.tableView, onChosen = onTableViewSelected)
    Rule(weight = RuleWeight.Hairline)
    AccentSection(selected = settings.accentColor, onAccentSelected = onAccentSelected)
    Rule(weight = RuleWeight.Hairline)
    ShakeSection(on = settings.shakeToRoll, onChanged = onShakeChanged)
    Rule(weight = RuleWeight.Hairline)
    FeelSection(
      haptics = settings.haptics,
      sound = settings.sound,
      onHapticsChanged = onHapticsChanged,
      onSoundChanged = onSoundChanged,
    )
    Rule(weight = RuleWeight.Hairline)
    RoundingSection(chosen = settings.rounding, onChosen = onRoundingSelected)
    Rule(weight = RuleWeight.Hairline)
    PowerSection(on = settings.powerSaving, onChanged = onPowerSavingChanged)
    // The block the prototype rules off from the settings above it: what the
    // app is, and the tool that is not a setting.
    Rule()
    AboutSection(version = version, onRepository = onRepository)
    Rule(weight = RuleWeight.Hairline)
    // Last, and off on every install: it is a debugging tool rather than a
    // feature, and it belongs after the thing that says what the app is.
    DeveloperSection(on = settings.developerTools, onChanged = onDeveloperToolsChanged)
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
  SwitchRow(
    label = stringResource(R.string.settings_power_heading),
    explanation = stringResource(R.string.settings_power_explanation),
    on = on,
    onChanged = onChanged,
    tag = SettingsTestTags.POWER_SAVING,
  )
}

/** Stable handles for tests, so a wording change does not break them. */
object SettingsTestTags {
  const val SCREEN: String = "settings:screen"

  /** The power-saving switch (design option 1z). */
  const val POWER_SAVING: String = "settings:power-saving"

  /** Whether shaking the phone throws the dice. */
  const val SHAKE: String = "settings:shake"

  /** Whether a die landing is felt, and whether it is heard. */
  const val HAPTICS: String = "settings:haptics"

  /** The debugging tools, off on every install (design: none — it is a tool). */
  const val DEVELOPER: String = "settings:developer"

  const val SOUND: String = "settings:sound"

  /** The seventh swatch: a colour of the player's own. */
  const val ACCENT_CUSTOM: String = "settings:accent:custom"

  /** The chosen colour written out — what was picked, not what is painted. */
  const val ACCENT_HEX: String = "settings:accent:hex"

  /**
   * The sheet behind the custom swatch, and the six controls on it.
   *
   * One tag rather than seven: the sheet is `ui/common`'s now — the same one
   * the face designer and the saved-roll editor open — and it derives its
   * children's tags from this one, so there is nowhere for a suffix to be
   * spelled two ways (`ColourPickerTags`). The three sliders moved from
   * `settings:accent:hue` to `settings:accent:picker:hue` with it, which is
   * where they always belonged: they are the sheet's, not the section's.
   */
  val ACCENT_PICKER: ColourPickerTags = ColourPickerTags("settings:accent:picker")

  /** What this is and where it came from (design option 2d). */
  const val VERSION: String = "settings:version"
  const val REPOSITORY: String = "settings:repository"

  fun appearanceOf(appearance: Appearance): String = "settings:appearance:${appearance.id}"

  fun roundingOf(rounding: Rounding): String = "settings:rounding:${rounding.id}"

  /** One position of the camera's lean (design: the Table view row). */
  fun tableViewOf(view: TableView): String = "settings:table-view:${view.id}"

  /** One of the six presets. The custom swatch is [ACCENT_CUSTOM]. */
  fun accentSwatch(accent: AccentColor): String = "settings:accent:${accent.id}"
}
