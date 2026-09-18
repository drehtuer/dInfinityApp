package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.ui.common.Ink

/**
 * The formula on the tray, waiting to be typed into
 * (`design/dInfinity.dc.html`, option 2a).
 *
 * **A dashed underline is the whole affordance.** The design asks for the
 * formula to sit on the tray as text and for a tap on it to bring the keyboard
 * up — not for a text field to stand under the dice all the time. A field is a
 * thing to fill in; this is a thing somebody has already written, which they
 * may change.
 *
 * The hint stands in when nothing has been typed, so there is always something
 * to tap. Without it a fresh install would show a tray, a row of dice and a
 * blank space where the formula goes.
 *
 * Red when the formula does not read, which is the badge `9c` asks for — what
 * exactly is wrong is said in the editor, under the squiggle, because that is
 * where somebody can do anything about it.
 *
 * **It hugs its words, and the rule hugs them with it.** It used to fill the
 * width of the screen with the text centred in it, so the dashed rule ran the
 * whole width and crossed the formula rather than sitting under it — the first
 * device session read `3d6 + 4` as struck through. The line is what the plate
 * under it is for: a block of type over the table, as wide as the type
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 */
@Composable
internal fun FormulaLine(
  text: String,
  onEdit: () -> Unit,
  modifier: Modifier = Modifier,
  wrong: Boolean = false,
) {
  // The design system has one red and the theme maps `error` onto it, so a
  // formula that does not read is printed in the accent (`theme/Theme.kt`).
  val ink = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
  Text(
    text = text.ifBlank { stringResource(R.string.roll_formula_hint) },
    // The heading face at 800, 20 sp: the formula is the title of the tray it
    // sits on, not a caption under it (`--font-heading`, `titleLarge`).
    style = MaterialTheme.typography.titleLarge,
    color = if (text.isBlank()) Ink.muted else ink,
    textAlign = TextAlign.Start,
    modifier =
      modifier
        .clickable(onClick = onEdit)
        .padding(vertical = 8.dp)
        .drawBehind { underline(ink) }
        .testTag(RollTestTags.FORMULA_LINE),
  )
}

/**
 * The dashed rule under it.
 *
 * Drawn rather than a `TextDecoration`, because the design's is a rule the
 * width of the line rather than an underline under the words — and because a
 * dashed decoration is not something Compose's text draws.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.underline(ink: androidx.compose.ui.graphics.Color) {
  val y = size.height - UNDERLINE_INSET.toPx()
  drawLine(
    color = ink.copy(alpha = UNDERLINE_ALPHA),
    start = Offset(0f, y),
    end = Offset(size.width, y),
    strokeWidth = UNDERLINE_WIDTH.toPx(),
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), GAP.toPx())),
    cap = Stroke.DefaultCap,
  )
}

private val UNDERLINE_INSET = 4.dp

/** `border-bottom:2px dashed` — rules in this system are 2 dp, not hairlines. */
private val UNDERLINE_WIDTH = 2.dp
private val DASH = 4.dp
private val GAP = 4.dp

/** `color-mix(in srgb, currentColor 45%, transparent)`. */
private const val UNDERLINE_ALPHA = 0.45f
