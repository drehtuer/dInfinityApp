package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.notation.NotationError

/**
 * What is wrong with the formula, under the part that is wrong
 * (`design/dInfinity.dc.html`, options 6f and 9c).
 *
 * The formula is printed again with a squiggle under the offending characters
 * and the message after it. Printing it again rather than marking up the field
 * is what the prototype does and is the right way round: the field is where
 * somebody is typing, and a squiggle that moves under the cursor while they
 * type is a squiggle that fights them.
 *
 * A squiggle rather than a straight underline, because a straight underline
 * under text reads as a link. The wave is the mark everybody already knows.
 *
 * Where a mistake has an obvious reading — `3 d 6` meant `3d6`, `d7` meant the
 * nearest die the set actually has — the parser says so, and the fix is one
 * tap (`docs/dice-notation.md`, "Error messages").
 */
@Composable
internal fun FormulaError(
  formula: String,
  error: NotationError,
  modifier: Modifier = Modifier,
  onSuggestion: (String) -> Unit = {},
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.errorContainer)
        .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Squiggled(
      text = said(formula, error.message),
      under = squiggleOver(formula, error.range),
      colour = MaterialTheme.colorScheme.error,
      modifier = Modifier.testTag(RollTestTags.INVALID),
    )

    val suggestion = error.suggestion
    if (suggestion != null) {
      TextButton(
        onClick = { onSuggestion(suggestion) },
        modifier = Modifier.testTag(RollTestTags.SUGGESTION),
      ) {
        Text(
          text = stringResource(R.string.roll_formula_suggestion, suggestion),
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }
  }
}

/** The formula, then the complaint: `2d6 + 1d7 - 4 — this set has no d7`. */
@Composable
private fun said(
  formula: String,
  message: String,
): AnnotatedString =
  buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(formula) }
    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onErrorContainer)) { append(" — $message") }
  }

/**
 * [text] with a wave drawn under the characters in [under].
 *
 * Drawn from the laid-out text rather than from character counts, so it lands
 * under the right glyphs at any font size and follows them if the line wraps.
 */
@Composable
private fun Squiggled(
  text: AnnotatedString,
  under: IntRange?,
  colour: Color,
  modifier: Modifier = Modifier,
) {
  var laidOut by remember { mutableStateOf<TextLayoutResult?>(null) }
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onErrorContainer,
    onTextLayout = { laidOut = it },
    modifier =
      modifier.drawBehind {
        val layout = laidOut
        if (layout == null || under == null) return@drawBehind
        val ink = Stroke(width = INK.toPx(), cap = StrokeCap.Round)
        underlinesOf(layout, under, CLEARANCE.toPx()).forEach { run ->
          drawPath(crestPath(run, WAVELENGTH.toPx(), AMPLITUDE.toPx()), colour, style = ink)
        }
      },
  )
}

/**
 * Where the wave goes: one run per line the range covers.
 *
 * Read off the laid-out text rather than counted from characters, so it lands
 * under the right glyphs at any font size and splits across a line break
 * instead of striking through the middle of the line.
 *
 * Separate from the drawing so the part that can be wrong can be asserted.
 * Whether a wave *looks* like a wave is the device suite's to say.
 */
internal fun underlinesOf(
  layout: TextLayoutResult,
  under: IntRange,
  clearance: Float,
): List<Underline> {
  val first = layout.getLineForOffset(under.first)
  val last = layout.getLineForOffset(under.last)
  val runs = mutableListOf<Underline>()
  for (line in first..last) {
    val left = if (line == first) layout.getHorizontalPosition(under.first, true) else layout.getLineLeft(line)
    val right = if (line == last) layout.getHorizontalPosition(under.last + 1, true) else layout.getLineRight(line)
    runs += Underline(left = left, right = right, bottom = layout.getLineBottom(line) - clearance)
  }
  return runs
}

/** One run of squiggle, in pixels. */
internal data class Underline(
  val left: Float,
  val right: Float,
  val bottom: Float,
)

/**
 * A wave along [run]: half a period up, half a period down, repeating.
 *
 * The last period is cut short rather than overrun, so the squiggle stops
 * where the offending characters do and not a few pixels into the next one.
 */
internal fun crestPath(
  run: Underline,
  wavelength: Float,
  amplitude: Float,
): Path {
  val path = Path().apply { moveTo(run.left, run.bottom) }
  var at = run.left
  var up = true
  while (at < run.right) {
    val next = minOf(at + wavelength, run.right)
    path.quadraticTo((at + next) / 2f, run.bottom + if (up) -amplitude else amplitude, next, run.bottom)
    up = !up
    at = next
  }
  return path
}

/**
 * The characters of [formula] to draw the squiggle under, or `null` when there
 * is nothing sensible to point at.
 *
 * An error's range is an offset into the formula as typed, and the formula is
 * the start of the line it is printed in, so the two line up with nothing to
 * add. What does have to happen is clamping: a formula that stops in the
 * middle of something is blamed on a position one past its end, and a squiggle
 * has to be drawn under a character that exists.
 */
internal fun squiggleOver(
  formula: String,
  range: IntRange,
): IntRange? {
  if (formula.isEmpty()) return null
  val last = formula.length - 1
  val from = range.first.coerceIn(0, last)
  val to = range.last.coerceIn(from, last)
  return from..to
}

/** How far above the baseline's bottom the wave sits, and how big it is. */
private val CLEARANCE = 1.dp
private val WAVELENGTH = 4.dp
private val AMPLITUDE = 1.5.dp
private val INK = 1.5.dp
