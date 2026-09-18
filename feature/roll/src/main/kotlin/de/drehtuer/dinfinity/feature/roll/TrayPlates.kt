package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.notation.RollRange
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Plate
import de.drehtuer.dinfinity.ui.common.SectionKicker

/*
 * The four things that can sit across the bottom of the tray
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * One plate, four states, and they are mutually exclusive because they are
 * four answers to the same question — what is the roll doing and what, if
 * anything, does it want: `ReadyPlate` before a hand has touched the phone,
 * `CountingPlate` while the dice are still being read, `EarnedPlate` when a
 * chain has stopped one throw short, and `StalledPlate` when dice never came
 * to rest and there is no total.
 *
 * None of them invents a state. `RollState.Ready`, `RollState.Rolling` with a
 * `RollProgress`, `RollState.ShakeAgain` and `RollState.Stalled` are what the
 * machine already reaches; this is the drawing.
 *
 * Only one of the four carries a button, and it is `Cancel the roll`. Throwing
 * is a shake — there is no Roll button, no `Throw it` and no `Throw those
 * again` (`docs/physics-and-rendering.md`, "Starting a roll").
 */

/**
 * How far through the reading a roll is
 * (`design/dInfinityPhone.dc.html`, the `counting` block over the tray).
 *
 * **This is the home the running readout did not have.** It was one line of
 * unstyled text sitting where "Rolling…" used to be, and it is the most
 * important thing on the screen for the second or two it is up: the dice are
 * read and taken off the table as they settle, so by the time the last one
 * lands there is nothing left to watch but this.
 *
 * In order: the kicker, the count in tabular figures with `of 20 read` beside
 * it, the range the finished roll can still come out in — right-aligned, low
 * to high, and carrying whatever the formula adds, because [RollProgress] gets
 * it from the real evaluator rather than from a sum of faces — and a 3 dp
 * rule. An open exploding chain puts a `+` after the top of the range, in the
 * accent's 700 step, which is the size accent-coloured text is legible at.
 *
 * It says the whole thing once to a screen reader rather than four times: a
 * row of figures an eye takes in at a glance is four disconnected fragments
 * read aloud (`docs/architecture.md`, "Accessibility").
 */
@Composable
internal fun CountingPlate(
  progress: RollProgress,
  modifier: Modifier = Modifier,
) {
  val ceiling =
    if (progress.range.more) {
      stringResource(R.string.roll_ceiling_more, progress.range.highest)
    } else {
      progress.range.highest.toString()
    }
  val spoken =
    pluralStringResource(
      R.plurals.roll_counting,
      progress.of,
      progress.read,
      progress.of,
      progress.range.lowest,
      ceiling,
    )
  Plate(
    modifier =
      modifier
        .fillMaxWidth()
        // Merged and named rather than cleared. A listener hears the sentence
        // once instead of four loose figures, and the pieces stay in the
        // unmerged tree where a test can still measure them — clearing the
        // subtree would take the rule and the `+` out of reach with them.
        .semantics(mergeDescendants = true) { contentDescription = spoken }
        .testTag(RollTestTags.COUNTING),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
      SectionKicker(text = stringResource(R.string.roll_counting_kicker), color = Ink.muted)
      Row(
        horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
        verticalAlignment = Alignment.Bottom,
      ) {
        Text(
          text = progress.read.toString(),
          // `.card-title`: the heading face at 17 sp and 800, which is the one
          // step of the scale between the body and a title.
          style = MaterialTheme.typography.titleMedium.tabular(),
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = pluralStringResource(R.plurals.roll_counting_read, progress.of, progress.of),
          style = MaterialTheme.typography.bodyMedium,
          color = Ink.muted,
        )
        Box(modifier = Modifier.weight(1f))
        Range(range = progress.range)
      }
      ProgressRule(filled = progress.filled)
    }
  }
}

/**
 * The range the finished roll can still come out in, low to high.
 *
 * The `+` is a separate run because it is the one part of this plate in the
 * accent, and it is [Ink.accentDeep] rather than the accent itself: it is a
 * 13 dp glyph, which is small text, and the accent as chosen only clears the
 * contrast bar for large. It says the chain is not done — the ceiling is what
 * the throw can reach *without* earning another die (`RollRange.more`).
 */
@Composable
private fun Range(range: RollRange) {
  Row(verticalAlignment = Alignment.Bottom) {
    Text(
      text = stringResource(R.string.roll_counting_range, range.lowest, range.highest),
      style = MaterialTheme.typography.bodyMedium.tabular(),
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (range.more) {
      Text(
        text = stringResource(R.string.roll_counting_more),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.ExtraBold,
        color = Ink.accentDeep,
        modifier = Modifier.testTag(RollTestTags.COUNTING_MORE),
      )
    }
  }
}

/**
 * The 3 dp rule under the count: `--color-neutral-200` track, ink fill.
 *
 * The track is the grey ramp's pale end, **mirrored on a dark ground** the way
 * `.dz-dark` mirrors the other eight steps — a near-white bar under a dark
 * plate would be the brightest thing on the screen and would read as the fill
 * rather than as what is left to do (`Modernist.Neutral`).
 *
 * Laid out rather than `fillMaxWidth(fraction)`, so a roll that has read
 * nothing draws a track with no fill at all instead of a sliver.
 */
@Composable
private fun ProgressRule(filled: Float) {
  val dark = MaterialTheme.colorScheme.background.luminance() < HALF
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(RULE)
        .background(if (dark) Modernist.Neutral.v800 else Modernist.Neutral.v200),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxHeight()
          .layout { measurable, constraints ->
            val width = (constraints.maxWidth * filled).toInt()
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(width, placeable.height) { placeable.place(0, 0) }
          }.background(MaterialTheme.colorScheme.onBackground)
          .testTag(RollTestTags.COUNTING_RULE),
    )
  }
}

/**
 * Waiting to be shaken, and what a shake is worth
 * (`design/dInfinityPhone.dc.html`, the hint block over the tray).
 *
 * **This is what stands where the Roll button stood.** Shaking is the only way
 * to throw (`docs/physics-and-rendering.md`, "Starting a roll"), so the one
 * thing the screen owes a player before they shake is the shape of the throw:
 * the lowest it can come to, the highest, and the average in between. A button
 * said "Roll"; this says what rolling would get them.
 *
 * The range is drawn by the same [Range] the counting plate uses mid-roll, so
 * the figures a player reads before the throw and the figures they watch close
 * during it are one calculation seen twice ([Expectation]).
 *
 * @param expected null for a formula that does not read, which is when there
 *   is a hint to give and nothing to expect.
 */
@Composable
internal fun ReadyPlate(
  expected: Expectation?,
  modifier: Modifier = Modifier,
) {
  Plate(modifier = modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
      Text(
        text = stringResource(R.string.roll_hint_ready),
        style = MaterialTheme.typography.bodyLarge,
        color = Ink.muted,
        modifier = Modifier.testTag(RollTestTags.HINT),
      )
      if (expected != null) Expected(expected)
    }
  }
}

/**
 * The lowest, the highest and the average, on one line.
 *
 * Said once to a screen reader rather than three times, for the reason the
 * counting plate is: a row of figures an eye takes in at a glance is a handful
 * of loose numbers read aloud (`docs/architecture.md`, "Accessibility").
 *
 * The average is missing for a formula past what the outcome graph computes
 * exactly. The range is still drawn — a throw whose ends are known and whose
 * middle is not is better described by the ends than by nothing
 * (`docs/probability.md`, limits).
 */
@Composable
internal fun Expected(
  expected: Expectation,
  modifier: Modifier = Modifier,
) {
  val range = expected.range
  val ceiling =
    if (range.more) stringResource(R.string.roll_ceiling_more, range.highest) else range.highest.toString()
  val average = expected.mean?.let { stringResource(R.string.roll_expected_average, it) }
  val spoken =
    if (average == null) {
      stringResource(R.string.roll_expected_spoken, range.lowest, ceiling)
    } else {
      stringResource(R.string.roll_expected_spoken_average, range.lowest, ceiling, average)
    }
  Row(
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
    verticalAlignment = Alignment.Bottom,
    modifier =
      modifier
        .semantics(mergeDescendants = true) { contentDescription = spoken }
        .testTag(RollTestTags.EXPECTED),
  ) {
    SectionKicker(text = stringResource(R.string.roll_expected_kicker), color = Ink.muted)
    Range(range = range)
    if (average != null) {
      Text(
        text = average,
        style = MaterialTheme.typography.bodyMedium.tabular(),
        color = Ink.muted,
        modifier = Modifier.testTag(RollTestTags.EXPECTED_AVERAGE),
      )
    }
  }
}

/**
 * A chain stopped one throw short (`design/dInfinityPhone.dc.html`, the
 * `earned` block).
 *
 * **The app does not throw the die an explosion earned.** A six earns another
 * throw and a throw is something a hand does, so the dice that are down stay
 * down and this says so (`docs/dice-notation.md`, "Evaluation").
 *
 * No buttons at all. It had two — one to throw the earned die and one to stop
 * the chain — and both are gone: throwing is a shake now, and `Stop the chain`
 * put the roll away with no total, which is a roll thrown away rather than a
 * roll finished. What is left is a sentence, which is all this ever was
 * (`docs/physics-and-rendering.md`, "Starting a roll").
 */
@Composable
internal fun EarnedPlate(
  waiting: Int,
  modifier: Modifier = Modifier,
) {
  Plate(modifier = modifier.fillMaxWidth().testTag(RollTestTags.SHAKE_AGAIN)) {
    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
      SectionKicker(text = stringResource(R.string.roll_earned_kicker), color = Ink.accentDeep)
      Text(
        text = pluralStringResource(R.plurals.roll_earned_body, waiting, waiting),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }
  }
}

/**
 * A roll that could not finish, and the offer of its dice back
 * (`design/dInfinityPhone.dc.html`, the `stuck` block).
 *
 * There is no total and there is not going to be one for this throw: some of
 * its dice never stopped. Reading them off whatever face they were nearest is
 * the one thing this app may not do, so it says what happened and hands them
 * back (`docs/physics-and-rendering.md`).
 *
 * Handing them back is a shake, not a button. `Throw those N again` was the
 * last throw in the app that a hand could not make, and it is gone; `Cancel
 * the roll` stays, because giving up is not throwing.
 */
@Composable
internal fun StalledPlate(
  unsettled: Int,
  read: Int,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Plate(modifier = modifier.fillMaxWidth().testTag(RollTestTags.STALLED)) {
    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AlertMark()
        SectionKicker(text = stringResource(R.string.roll_stalled_kicker), color = Ink.accentDeep)
      }
      Text(
        text = pluralStringResource(R.plurals.roll_stalled, unsettled, unsettled, unsettled + read, read),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      ModernistButton(
        text = stringResource(R.string.roll_stalled_cancel),
        onClick = onCancel,
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(RollTestTags.STALLED_CANCEL),
      )
    }
  }
}

/**
 * The refusal's `ic-alert`: a triangle with a bar and a dot in it.
 *
 * Drawn rather than brought in, because this app ships no icon set and one
 * glyph is not a reason to start one. It is named for a screen reader even
 * though the copy beside it says the same thing — the kicker it sits against
 * is `COULD NOT SETTLE`, and a listener who hears only that has been told the
 * state without being told it is a warning.
 */
@Composable
private fun AlertMark() {
  val ink = Ink.accentDeep
  val alert = stringResource(R.string.roll_stalled_alert)
  Box(
    modifier =
      Modifier
        .size(ALERT)
        .clearAndSetSemantics { contentDescription = alert }
        .drawBehind { alertMark(ink) },
  )
}

/** The triangle, its bar and its dot, at the stroke the prototype draws it in. */
private fun DrawScope.alertMark(ink: Color) {
  val stroke = ALERT_STROKE.toPx()
  val middle = size.width / 2f
  val triangle =
    Path().apply {
      moveTo(middle, stroke)
      lineTo(size.width - stroke, size.height - stroke)
      lineTo(stroke, size.height - stroke)
      close()
    }
  drawPath(path = triangle, color = ink, style = Stroke(width = stroke))
  drawLine(
    color = ink,
    start = Offset(middle, size.height * BAR_TOP),
    end = Offset(middle, size.height * BAR_BOTTOM),
    strokeWidth = stroke,
  )
  drawLine(
    color = ink,
    start = Offset(middle, size.height * DOT),
    end = Offset(middle, size.height * DOT + stroke / 2f),
    strokeWidth = stroke,
  )
}

/** `gap: 5px` inside the counting plate — a gap, not a step of the scale. */
private val ROW_GAP = 5.dp

/** `display:flex;gap:6px` between the two buttons of a plate that asks. */
private val BUTTON_GAP = 6.dp

/** `height:3px` — the progress rule, which is neither a hairline nor a `.hr`. */
private val RULE = 3.dp

/** `width="14" height="14"` on the prototype's `ic-alert`. */
private val ALERT = 14.dp
private val ALERT_STROKE = 2.dp

private const val BAR_TOP = 0.42f
private const val BAR_BOTTOM = 0.66f
private const val DOT = 0.78f

/** Halfway up the luminance range: what divides a dark page from a light one. */
private const val HALF = 0.5f
