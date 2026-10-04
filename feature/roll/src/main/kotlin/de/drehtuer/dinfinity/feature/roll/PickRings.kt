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
import de.drehtuer.dinfinity.ui.common.Ink

/**
 * A ring round each die a finger has picked up (`docs/architecture.md`,
 * decisions 76 and 84).
 *
 * Drawn **over** the picture, in Compose, rather than into it. The renderer
 * draws what the simulation says and nothing else — a tint or an outline in
 * the scene would be a second thing deciding what a die looks like, and on the
 * phone the only way to see whether it worked — whereas a ring over the
 * surface is one shape in a layer the screen already owns, placed by the same
 * frustum a finger is read against ([RollPresenter.marks]), and tested here
 * on the JVM.
 *
 * **The accent, on a halo of the ground.** The owner asked for the ring to be
 * the accent so a picked die pops out (`docs/architecture.md`, decision 84);
 * it used to be the plates' ground with their ink inside it, which on the
 * Pixel 10a did not stand out enough. The accent is the player's own colour
 * ([Ink.accent], the theme's `primary`), so it follows Settings without the
 * renderer knowing about it. It is not drawn straight onto the felt: a broad
 * ring in the plates' ground carries it, and the accent sits inside that with
 * a band of ground showing either side — so what has to be legible is accent
 * on `--color-bg`, the one pairing the plates already rely on, on a light
 * felt and a dark one alike (`docs/physics-and-rendering.md`, "What is drawn
 * over the table").
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
  val ring = Ink.accent
  Canvas(modifier = modifier.testTag(RollTestTags.PICKED)) {
    val halo = HALO.toPx()
    val inner = RING.toPx()
    val least = SMALLEST.toPx()
    marks.forEach { mark ->
      val centre = Offset((mark.acrossFraction * size.width).toFloat(), (mark.downFraction * size.height).toFloat())
      // Just outside the die's ball, so the ring sits on the felt round the
      // die rather than over its printed face.
      val radius = maxOf((mark.radiusOfHeight * size.height).toFloat(), least) + halo
      drawCircle(color = ground, radius = radius, center = centre, style = Stroke(width = halo))
      drawCircle(color = ring, radius = radius, center = centre, style = Stroke(width = inner))
    }
  }
}

/** The ground-coloured halo, which keeps the accent off any felt. */
private val HALO = 8.dp

/** The accent ring inside it, with 2 dp of ground showing either side. */
private val RING = 4.dp

/** However far away a die is, its ring is at least a fingertip's half-width. */
private val SMALLEST = 12.dp
