package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
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
 * One setting, on **one row**: what it is called and what it does on the left,
 * the control that changes it on the right, centred against the text
 * (`design/dInfinityPhone.dc.html`, the Settings screen — `settingRows`, a
 * flex row of a `min-width: 0` text column and a `flex: none` segmented
 * control).
 *
 * The app used to stack the three — name, sentence, control — which made every
 * setting three blocks tall and the screen twice the length the design draws.
 * The design wins (`docs/architecture.md`, "Settings").
 *
 * **When the control cannot fit beside the text, it goes under it**, at the
 * left edge, and the row grows instead of either half shrinking. A browser can
 * let `flex: none` overflow the viewport; a phone cannot, and the two other
 * answers are worse: squeezing the text column to nothing turns a sentence
 * into a column of single words, and squeezing the control either clips an
 * option's label or drops it below Android's 48 dp target. The threshold is
 * [TEXT_LEAST] — the narrowest a description may be set before that happens —
 * and it is measured against the room actually offered, so the same row is
 * beside its text on a phone and under it in a narrow pane.
 *
 * @param modifier what carries the tap and the test handle for a setting whose
 *   whole row is the control, which is how a boolean one is drawn ([SwitchRow]).
 * @param explanation the one or two lines under the name, or `null` for a row
 *   inside a section that has already explained itself.
 * @param readAsOne whether the name and the sentence are merged into one stop
 *   for a screen reader. **Off when [modifier] already merges the whole row**,
 *   as a boolean setting's does: a merging node inside a merging node is not
 *   joined to it but *withheld* from it, and the switch would announce its
 *   state to TalkBack without ever saying which setting it is.
 */
@Composable
internal fun SettingRow(
  heading: String,
  modifier: Modifier = Modifier,
  explanation: String? = null,
  readAsOne: Boolean = true,
  control: @Composable () -> Unit,
) {
  Layout(
    content = {
      SettingText(heading = heading, explanation = explanation, readAsOne = readAsOne)
      Box { control() }
    },
    modifier = modifier.fillMaxWidth(),
  ) { measurables, constraints ->
    // `fillMaxWidth` above is what makes this a number rather than an infinity:
    // a row is always given a width to fill, even by a parent that offers none,
    // so there is no unbounded case to answer for here.
    val room = constraints.maxWidth
    val gap = Modernist.x3.roundToPx()
    // **Asked how wide it wants to be, not how wide it can be.** A segmented
    // control fills the width it is offered — that is what makes its options
    // equal — so measuring it against the row's own constraints hands it the
    // whole row and leaves nothing for the words. Every row with one would
    // then stack, which is exactly what a phone showed. The intrinsic width is
    // the content's: three options plus their padding, and nothing else.
    val wanted = measurables[1].maxIntrinsicWidth(constraints.maxHeight).coerceAtMost(room)
    val beside = room - wanted - gap >= TEXT_LEAST.roundToPx()
    // Fixed at what it wanted, so that being placed beside the words does not
    // let it take them back.
    val dial =
      measurables[1].measure(
        if (beside) {
          Constraints.fixedWidth(wanted)
        } else {
          constraints.copy(minWidth = 0, minHeight = 0)
        },
      )
    val words =
      measurables[0].measure(
        constraints.copy(
          minWidth = 0,
          minHeight = 0,
          maxWidth = if (beside) room - dial.width - gap else room,
        ),
      )
    val height = if (beside) maxOf(words.height, dial.height) else words.height + gap + dial.height
    layout(room, height) {
      if (beside) {
        words.placeRelative(0, (height - words.height) / 2)
        dial.placeRelative(room - dial.width, (height - dial.height) / 2)
      } else {
        words.placeRelative(0, 0)
        dial.placeRelative(0, words.height + gap)
      }
    }
  }
}

/**
 * A heading and its explanation, read as **one thing**.
 *
 * Set the way the prototype sets a settings row: the name in body copy at
 * semibold, the sentence under it small and dimmed
 * (`design/dInfinity.dc.html`, option 1y). The two were the same weight of
 * ink before, so every explanation shouted as loudly as the thing it
 * explained.
 *
 * The semantics are merged because a screen reader announcing "Appearance" and
 * then, as a separate stop, "Light, dark, or whatever the phone is doing" has
 * split one sentence into two — and on a row whose control sits *beside* the
 * text, the swipe order between them is no longer even top to bottom. Not when
 * something above has already merged the whole row, which is the [readAsOne]
 * parameter of [SettingRow] and the reason it exists.
 */
@Composable
private fun SettingText(
  heading: String,
  explanation: String?,
  modifier: Modifier = Modifier,
  readAsOne: Boolean = true,
) {
  Column(
    modifier = modifier.then(if (readAsOne) Modifier.semantics(mergeDescendants = true) {} else Modifier),
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    Text(
      text = heading,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (explanation != null) {
      Text(
        text = explanation,
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
      )
    }
  }
}

/**
 * A heading, its explanation, and a block that belongs **under** them rather
 * than beside them.
 *
 * The exception to [SettingRow], and there are two of it: the accent grid,
 * which is four columns of swatches and would have nothing left of itself in
 * half a row, and About, which is not a control at all. Anything that is one
 * setting with one control is a row.
 */
@Composable
internal fun Section(
  heading: String,
  explanation: String,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Modernist.x2)) {
    SettingText(heading = heading, explanation = explanation)
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
  SettingRow(
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
 * How far the camera leans over the table
 * (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
 *
 * Two positions and no slider: the shot either leans or it does not, and a
 * dial of degrees would be asking a player to art-direct a camera. Straight
 * down is the default, because a leaning shot on a tall phone spends a large
 * share of the frame on the wooden rim.
 *
 * It takes effect the next time the roll screen opens, like power saving, the
 * shake, the haptics and the sound, and for the same reason
 * (`docs/architecture.md`, decision 16).
 */
@Composable
internal fun TableViewSection(
  chosen: TableView,
  onChosen: (TableView) -> Unit,
) {
  SettingRow(
    heading = stringResource(R.string.settings_table_view_heading),
    explanation = stringResource(R.string.settings_table_view_explanation),
  ) {
    SegmentedControl(
      options = TableView.entries,
      selected = chosen,
      label = { stringResource(it.labelRes()) },
      onSelect = onChosen,
      tagOf = SettingsTestTags::tableViewOf,
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
  SwitchRow(
    label = stringResource(R.string.settings_shake_heading),
    explanation = stringResource(R.string.settings_shake_explanation),
    on = on,
    onChanged = onChanged,
    tag = SettingsTestTags.SHAKE,
  )
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
 * It is therefore the one setting that is a **section** of rows rather than a
 * row: the sentence is about both switches, so it sits above both rather than
 * beside either. The design has no sound switch at all, which is the open
 * question `docs/TODO.md` records under "Does the sound go?".
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
  SettingRow(
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
  SwitchRow(
    label = stringResource(R.string.settings_developer_heading),
    explanation = stringResource(R.string.settings_developer_explanation),
    on = on,
    onChanged = onChanged,
    tag = SettingsTestTags.DEVELOPER,
  )
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
 * A setting that is on or off: its name and sentence on the left, an Off / On
 * segmented control on the right, exactly like every other row
 * (`design/dInfinity.dc.html`, option 1y; the prototype's own
 * *Power-saving mode* and *Haptics* rows).
 *
 * The control is a **read-out**, not two buttons — the row is what carries the
 * tap, exactly as it did when a Material `Switch` sat there, so a tap anywhere
 * along it still flips the setting and TalkBack still reads it as a switch.
 * What changed is only what it looks like: `Switch` is a fully round pill with
 * a circular thumb, and this system has no rounded corner anywhere.
 *
 * Its name *is* the setting's heading now, which is why the three switches
 * that stood alone lost the second label they used to carry underneath one:
 * "Power saving" above "Do not draw the dice" was one row saying the same
 * thing twice, and a screen reader read both.
 */
@Composable
internal fun SwitchRow(
  label: String,
  on: Boolean,
  onChanged: (Boolean) -> Unit,
  tag: String,
  explanation: String? = null,
) {
  SettingRow(
    heading = label,
    modifier =
      Modifier
        .toggleable(value = on, role = Role.Switch, onValueChange = onChanged)
        .testTag(tag),
    explanation = explanation,
    // `toggleable` has already merged the row, and a second boundary inside it
    // would withhold the name from that merge rather than join it.
    readAsOne = false,
  ) {
    SegmentedControl(
      options = OFF_THEN_ON,
      selected = on,
      label = { stringResource(if (it) R.string.settings_on else R.string.settings_off) },
    )
  }
}

/** Off first, then On — the order the prototype's own Off / On control uses. */
private val OFF_THEN_ON = listOf(false, true)

/**
 * The narrowest a setting's text column may be squeezed before the control
 * goes under it instead: about a dozen characters of body copy.
 *
 * Not on the 4/8/12 scale on purpose — it is a legibility floor rather than a
 * measurement of the drawing, the same kind of number as the 48 dp touch
 * target, and the design has none of its own because a browser is allowed to
 * overflow sideways.
 */
private val TEXT_LEAST: Dp = 120.dp
