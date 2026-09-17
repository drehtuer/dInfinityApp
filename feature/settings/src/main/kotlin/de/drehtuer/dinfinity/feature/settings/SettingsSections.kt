package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.SegmentedControl

/*
 * The rows of the Settings screen, one composable each.
 *
 * Apart from `SettingsScreen` because the list only grows, and a screen that
 * is one function of four hundred lines is a screen nobody reads before adding
 * the four hundred and first (`docs/TODO.md`, Step 4.10).
 *
 * Every one of them is stateless: the caller owns `AppSettings` and persists
 * the change, so each renders the same driven by the real repository or by a
 * value in a test.
 */

/**
 * A heading, its explanation, and whatever the setting is.
 *
 * Set the way the prototype sets a settings row: the name in body copy at
 * semibold, the sentence under it small and dimmed
 * (`design/dInfinity.dc.html`, option 1y). The two were the same weight of
 * ink before, so every explanation shouted as loudly as the thing it
 * explained.
 */
@Composable
internal fun Section(
  heading: String,
  explanation: String,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = heading,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = explanation,
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
    )
    content()
  }
}

/**
 * Light, dark, or whatever the phone is doing
 * (`design/dInfinity.dc.html`, option 1q).
 *
 * Three choices and no fourth. "Automatic at sunset" is a fourth, and an app
 * that changed colour halfway through an evening's game would be doing
 * something nobody asked it to.
 */
@Composable
internal fun AppearanceSection(
  chosen: Appearance,
  onChosen: (Appearance) -> Unit,
) {
  Section(
    heading = stringResource(R.string.settings_appearance_heading),
    explanation = stringResource(R.string.settings_appearance_explanation),
  ) {
    SegmentedControl(
      options = Appearance.entries,
      selected = chosen,
      label = { stringResource(it.labelRes()) },
      onSelect = onChosen,
      tagOf = SettingsTestTags::appearanceOf,
    )
  }
}

/**
 * Whether shaking the phone throws the dice
 * (`docs/physics-and-rendering.md`, "Shake input").
 *
 * The only setting on this screen that saves any power: off means the
 * accelerometer and the gyroscope are never registered at all, rather than
 * registered and ignored.
 */
@Composable
internal fun ShakeSection(
  on: Boolean,
  onChanged: (Boolean) -> Unit,
) {
  Section(
    heading = stringResource(R.string.settings_shake_heading),
    explanation = stringResource(R.string.settings_shake_explanation),
  ) {
    SwitchRow(
      label = stringResource(R.string.settings_shake_label),
      on = on,
      onChanged = onChanged,
      tag = SettingsTestTags.SHAKE,
    )
  }
}

/**
 * Whether a die landing is felt and heard
 * (`docs/physics-and-rendering.md`, "Haptics and sound").
 *
 * One section with two switches rather than two sections, because they are one
 * answer to one question — *should the dice make themselves felt* — and a
 * player who wants neither turns both off in one place. It matters underneath
 * too: with both off a roll records no impacts at all, so the pair is what the
 * saving is measured against rather than either switch on its own.
 *
 * Both take effect the next time the roll screen opens, like power saving and
 * the shake, and for the same reason (`docs/architecture.md`, decision 16).
 */
@Composable
internal fun FeelSection(
  haptics: Boolean,
  sound: Boolean,
  onHapticsChanged: (Boolean) -> Unit,
  onSoundChanged: (Boolean) -> Unit,
) {
  Section(
    heading = stringResource(R.string.settings_feel_heading),
    explanation = stringResource(R.string.settings_feel_explanation),
  ) {
    SwitchRow(
      label = stringResource(R.string.settings_haptics_label),
      on = haptics,
      onChanged = onHapticsChanged,
      tag = SettingsTestTags.HAPTICS,
    )
    SwitchRow(
      label = stringResource(R.string.settings_sound_label),
      on = sound,
      onChanged = onSoundChanged,
      tag = SettingsTestTags.SOUND,
    )
  }
}

/**
 * Which way division rounds unless a throw says otherwise
 * (`docs/dice-notation.md`, "Division rounding").
 *
 * The per-throw override on the result sheet is not remembered; this is what
 * the next roll — and every outcome graph, which is computed before any throw
 * exists — uses.
 */
@Composable
internal fun RoundingSection(
  chosen: Rounding,
  onChosen: (Rounding) -> Unit,
) {
  Section(
    heading = stringResource(R.string.settings_rounding_heading),
    explanation = stringResource(R.string.settings_rounding_explanation),
  ) {
    SegmentedControl(
      options = Rounding.entries,
      selected = chosen,
      label = { stringResource(it.labelRes()) },
      onSelect = onChosen,
      tagOf = SettingsTestTags::roundingOf,
    )
  }
}

/**
 * The debugging tools (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * Off on every install, and last on the screen, because it is not a feature: a
 * player has no use for a collision overlay and no use for a log of things
 * that are supposed to be impossible.
 *
 * What it turns on is a **separate surface** — an overlay on the tray, and a
 * screen in the menu — and never a field on a screen a player uses. The
 * history still has no replay and still never shows a seed with this on,
 * because `HistoryEntry` has no seed on it and the exports have no column for
 * one (`docs/architecture.md`, decisions 13 and 56).
 *
 * The overlay takes effect the next time the roll screen opens, like power
 * saving, the shake, the haptics and the sound, and for the same reason
 * (decision 16). The menu row appears at once, because a menu is not a roll.
 */
@Composable
internal fun DeveloperSection(
  on: Boolean,
  onChanged: (Boolean) -> Unit,
) {
  Section(
    heading = stringResource(R.string.settings_developer_heading),
    explanation = stringResource(R.string.settings_developer_explanation),
  ) {
    SwitchRow(
      label = stringResource(R.string.settings_developer_label),
      on = on,
      onChanged = onChanged,
      tag = SettingsTestTags.DEVELOPER,
    )
  }
}

/**
 * What this is and where it came from
 * (`design/dInfinity.dc.html`, option 2d).
 *
 * The version is read from the installed package rather than from a generated
 * constant, so it is what was actually installed rather than what some build
 * thought it was compiling.
 */
@Composable
internal fun AboutSection(
  version: String,
  onRepository: () -> Unit,
) {
  Section(
    heading = stringResource(R.string.settings_about_heading),
    explanation = stringResource(R.string.settings_about_licence),
  ) {
    Text(
      text = stringResource(R.string.settings_about_version, version),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.testTag(SettingsTestTags.VERSION),
    )
    ModernistButton(
      text = stringResource(R.string.settings_about_repository),
      onClick = onRepository,
      kind = ModernistButtonKind.Ghost,
      modifier = Modifier.testTag(SettingsTestTags.REPOSITORY),
    )
  }
}

/**
 * A setting that is on or off, drawn the way the prototype draws one: the name
 * on the left, an Off / On segmented control on the right
 * (`design/dInfinity.dc.html`, option 1y).
 *
 * The control is a **read-out**, not two buttons — the row is what carries the
 * tap, exactly as it did when a Material `Switch` sat there, so a tap anywhere
 * along it still flips the setting and TalkBack still reads it as a switch.
 * What changed is only what it looks like: `Switch` is a fully round pill with
 * a circular thumb, and this system has no rounded corner anywhere.
 */
@Composable
internal fun SwitchRow(
  label: String,
  on: Boolean,
  onChanged: (Boolean) -> Unit,
  tag: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier =
      Modifier
        .fillMaxWidth()
        .toggleable(value = on, role = Role.Switch, onValueChange = onChanged)
        .testTag(tag),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.weight(1f),
    )
    SegmentedControl(
      options = OFF_THEN_ON,
      selected = on,
      label = { stringResource(if (it) R.string.settings_on else R.string.settings_off) },
    )
  }
}

/** Off first, then On — the order the prototype's own Off / On control uses. */
private val OFF_THEN_ON = listOf(false, true)
