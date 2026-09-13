package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The way to the menu, on every screen that has one
 * (`design/dInfinity.dc.html`, option 1q).
 *
 * Three lines, drawn rather than fetched: it is the one glyph every phone user
 * already knows, and a `Canvas` of three strokes costs nothing next to a
 * dependency on an icon pack for it. It is labelled for TalkBack, because a
 * drawing is not a word.
 */
@Composable
fun MenuButton(
  onOpen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val label = stringResource(R.string.menu_open)
  val ink = MaterialTheme.colorScheme.onBackground
  IconButton(
    onClick = onOpen,
    modifier =
      modifier
        .semantics { contentDescription = label }
        .testTag(MenuTestTags.BUTTON),
  ) {
    Canvas(modifier = Modifier.size(BARS_SIZE)) {
      val gap = size.height / (BARS + 1)
      repeat(BARS) { bar ->
        val y = gap * (bar + 1)
        drawLine(
          color = ink,
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = INK.toPx(),
          cap = StrokeCap.Round,
        )
      }
    }
  }
}

private const val BARS = 3
private val BARS_SIZE = 22.dp
private val INK = 2.dp
