package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import de.drehtuer.dinfinity.render.filament.PicturePoint
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import kotlin.math.roundToInt

/**
 * The dice a later pass of the roll put on the table, outlined and labelled
 * (`docs/architecture.md`, decision 85; `design/dInfinityPhone.dc.html`, the
 * tray's `mark`).
 *
 * A roll that had to throw again — dice nobody could read, dice a chain
 * earned, dice a finger picked up — keeps on the table only what the last
 * throw of each round left there, so a total counting twenty dice can stand
 * over a table holding three. The design's answer is to say which dice came
 * later: a 4 dp `--color-accent-700` stroke round the die's own edge, and a
 * `PASS 2` label under it in the slot the prototype's `dropped` marker uses —
 * ten, semibold, tracked, on a plate of the ground.
 *
 * **Drawn over the picture, like [PickRings], for the same reasons**: the
 * renderer draws what the simulation says and nothing else, and a shape in a
 * layer the screen owns is placed by the frustum a finger is read against and
 * tested on the JVM. **And kept off the felt the same way**: the accent-700
 * stroke runs inside a broader stroke of the ground, so what has to be legible
 * is the accent's deep step on `--color-bg` — the pairing it is made for — on
 * any felt (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * **Told apart from a pick by shape and by step.** A pick is a circle, out
 * round the ball a finger lands in, in the accent itself; this is the die's own
 * polygon, on its edge, in the 700 step, with words under it. A die that is
 * both — a die thrown again by hand and picked once more — wears both, the
 * ring on top.
 *
 * Nothing here takes a touch.
 */
@Composable
internal fun PassMarks(
  marks: List<PassMark>,
  modifier: Modifier = Modifier,
) {
  if (marks.isEmpty()) return
  val ground = MaterialTheme.colorScheme.background
  val ink = Ink.accentDeep
  Layout(
    modifier = modifier.testTag(RollTestTags.PASSES),
    content = {
      Canvas(modifier = Modifier) {
        val halo = HALO.toPx()
        val line = LINE.toPx()
        marks.forEach { mark ->
          val edge = Path()
          mark.outline.forEachIndexed { at, point ->
            val x = (point.acrossFraction * size.width).toFloat()
            val y = (point.downFraction * size.height).toFloat()
            if (at == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
          }
          edge.close()
          drawPath(edge, color = ground, style = Stroke(width = halo, join = StrokeJoin.Round))
          drawPath(edge, color = ink, style = Stroke(width = line, join = StrokeJoin.Round))
        }
      }
      marks.forEach { mark ->
        Box(
          modifier =
            Modifier
              .testTag(RollTestTags.PASS_LABEL)
              .background(ground)
              .padding(horizontal = LABEL_ACROSS, vertical = LABEL_DOWN),
        ) {
          Text(
            text = stringResource(R.string.roll_pass_label, mark.pass),
            style = MaterialTheme.typography.labelSmall,
            fontSize = Modernist.Type.kicker,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = TRACKING,
            color = ink,
          )
        }
      }
    },
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val canvas = measurables.first().measure(Constraints.fixed(width, height))
    val labels = measurables.drop(1).map { it.measure(Constraints()) }
    val gap = GAP.roundToPx()
    layout(width, height) {
      canvas.place(0, 0)
      labels.forEachIndexed { at, placeable ->
        val (across, down) = labelAt(marks[at].outline, width, height)
        // Centred under the die, as the prototype's `translate(-50%, 2px)`
        // does, and kept on the picture at its sides.
        val x = (across - placeable.width / 2f).roundToInt().coerceIn(0, maxOf(0, width - placeable.width))
        placeable.place(x, down.roundToInt() + gap)
      }
    }
  }
}

/**
 * Where the label under [outline] hangs from, in pixels of a picture [width]
 * by [height]: the middle of the die across, and its lowest point down.
 */
internal fun labelAt(
  outline: List<PicturePoint>,
  width: Int,
  height: Int,
): Offset {
  val left = outline.minOf { it.acrossFraction }
  val right = outline.maxOf { it.acrossFraction }
  val bottom = outline.maxOf { it.downFraction }
  return Offset(((left + right) / 2 * width).toFloat(), (bottom * height).toFloat())
}

/** The ground-coloured band the outline runs in, which keeps the accent off any felt. */
private val HALO = 8.dp

/** The outline itself: the design's 4, with 2 dp of ground either side. */
private val LINE = 4.dp

/** How far under the die its label starts: the prototype's `2px`. */
private val GAP = 2.dp

/** The label plate's padding, the prototype's `1px 6px`. */
private val LABEL_ACROSS = 6.dp
private val LABEL_DOWN = 1.dp

/** The prototype's `letter-spacing: .06em`. */
private val TRACKING = 0.06.em
