package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
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
 */
@Composable
internal fun FormulaLine(
  text: String,
  onEdit: () -> Unit,
  modifier: Modifier = Modifier,
  wrong: Boolean = false,
) {
  val ink =
    if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
  Text(
    text = text.ifBlank { stringResource(R.string.roll_formula_hint) },
    style = MaterialTheme.typography.titleMedium,
    color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else ink,
    textAlign = TextAlign.Center,
    modifier =
      modifier
        .fillMaxWidth()
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

private val UNDERLINE_INSET = 2.dp
private val UNDERLINE_WIDTH = 1.dp
private val DASH = 4.dp
private val GAP = 3.dp
private const val UNDERLINE_ALPHA = 0.6f
