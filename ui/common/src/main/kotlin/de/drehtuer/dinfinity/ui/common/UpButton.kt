package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The chevron a screen's header draws to climb out of it
 * (`design/dInfinityPhone.dc.html`, `#ic-back`).
 *
 * **It goes up, never back.** It takes the player to the screen this one hangs
 * off — whatever path they took to get here — and it is not a second spelling
 * of the system's back button: one of them retraces steps and the other
 * climbs, and a control that sometimes does each is a control nobody can
 * predict (`docs/architecture.md`, "Navigation"). What "up" *is* for a given
 * screen is the navigation graph's to say, which is why this takes a lambda
 * and knows nothing about destinations.
 *
 * Drawn rather than fetched, like [MenuButton]'s three bars: an arrow is four
 * strokes and an icon pack is a dependency. It is labelled for TalkBack,
 * because a drawing is not a word.
 */
@Composable
fun UpButton(
  onUp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val ink = MaterialTheme.colorScheme.onBackground
  ModernistIconButton(
    contentDescription = stringResource(R.string.up),
    onClick = onUp,
    modifier = modifier.testTag(UpTestTags.UP),
  ) {
    Canvas(modifier = Modifier.size(GLYPH)) {
      // The prototype's `m12 19-7-7 7-7M19 12H5`, as a share of the box rather
      // than as the 24-unit viewBox it is written in: the same drawing at any
      // size, and no number here that is a pixel.
      val x = { share: Float -> size.width * share }
      val y = { share: Float -> size.height * share }
      val arrow =
        Path().apply {
          moveTo(x(POINT), y(BOTTOM))
          lineTo(x(TIP), y(MIDDLE))
          lineTo(x(POINT), y(TOP))
          moveTo(x(TAIL), y(MIDDLE))
          lineTo(x(TIP), y(MIDDLE))
        }
      drawPath(
        path = arrow,
        color = ink,
        style = Stroke(width = INK.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
    }
  }
}

/** How a test finds the way up without knowing what it is drawn with. */
object UpTestTags {
  const val UP: String = "up"
}

/** The prototype draws it at 20 px in a 36 px button. */
private val GLYPH = 20.dp

/** `stroke-width: 2`, which is the system's rule weight. */
private val INK = Modernist.rule

// The four corners of `#ic-back`, as shares of its 24-unit box: the tip at
// x=5, the shaft's tail at x=19, and the head's two ends at x=12, y=5 and 19.
private const val TIP = 5f / 24f
private const val POINT = 12f / 24f
private const val TAIL = 19f / 24f
private const val TOP = 5f / 24f
private const val MIDDLE = 12f / 24f
private const val BOTTOM = 19f / 24f
