package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.render.filament.PickMark

/**
 * A ring round each die a finger has picked up (`docs/architecture.md`,
 * decision 76).
 *
 * Drawn **over** the picture, in Compose, rather than into it. The renderer
 * draws what the simulation says and nothing else — a tint or an outline in
 * the scene would be a second thing deciding what a die looks like, and on the
 * phone the only way to see whether it worked — whereas a ring over the
 * surface is one shape in a layer the screen already owns, placed by the same
 * frustum a finger is read against ([RollPresenter.marks]), and tested here
 * on the JVM.
 *
 * **Two inks, not the accent.** Accent never touches felt
 * (`docs/physics-and-rendering.md`, "What is drawn over the table"), and a
 * single ink is the one colour some table will swallow. A broad ring in the
 * plates' ground with a narrow one in their ink inside it reads on a light
 * felt and a dark one alike, the way a focus ring does.
 *
 * Nothing here takes a touch: the finger that un-picks a die goes through to
 * the tray under it.
 *
 * @param marks where each ring goes and how big, as fractions of the picture.
 */
@Composable
internal fun PickRings(
  marks: List<PickMark>,
  modifier: Modifier = Modifier,
) {
  if (marks.isEmpty()) return
  val ground = MaterialTheme.colorScheme.background
  val ink = MaterialTheme.colorScheme.onBackground
  Canvas(modifier = modifier.testTag(RollTestTags.PICKED)) {
    val broad = BROAD.toPx()
    val narrow = NARROW.toPx()
    val least = SMALLEST.toPx()
    marks.forEach { mark ->
      val centre = Offset((mark.acrossFraction * size.width).toFloat(), (mark.downFraction * size.height).toFloat())
      // Just outside the die's ball, so the ring sits on the felt round the
      // die rather than over its printed face.
      val radius = maxOf((mark.radiusOfHeight * size.height).toFloat(), least) + broad
      drawCircle(color = ground, radius = radius, center = centre, style = Stroke(width = broad))
      drawCircle(color = ink, radius = radius, center = centre, style = Stroke(width = narrow))
    }
  }
}

/** The ground-coloured ring, which keeps the ink one off any felt. */
private val BROAD = 5.dp

/** The ink ring inside it. */
private val NARROW = 2.dp

/** However far away a die is, its ring is at least a fingertip's half-width. */
private val SMALLEST = 12.dp
