package de.drehtuer.dinfinity.feature.designer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.FaceOutline
import de.drehtuer.dinfinity.designer.GuideMark
import de.drehtuer.dinfinity.designer.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke

/**
 * Drawing the faces of a die (`design/dInfinity.dc.html`, options `1v`, `4c`
 * and `8d`; `docs/face-designer.md`).
 *
 * One face at a time on a square canvas, with the face's outline masked in and
 * its number under the drawing as something to trace or turn off. The strip at
 * the bottom moves between faces.
 *
 * **A d4 shows three numbers, one at each corner**, because its values belong
 * to corners rather than faces. That is not a special case in this file: the
 * guide is a list, and a list of three draws three (`FaceGuide`).
 */
@Composable
fun DesignerScreen(
  presenter: DesignerPresenter,
  modifier: Modifier = Modifier,
  menu: @Composable () -> Unit = {},
) {
  val state = presenter.state
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .testTag(DesignerTestTags.SCREEN),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.designer_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
      )
      menu()
    }

    BaseDice(state, presenter)
    FaceCanvas(state = state, onStroke = presenter::drew)
    Warning(state)
    Tools(state, presenter)
    Palette(state, presenter)
    FaceStrip(state, presenter)
  }
}

/**
 * Which die is being drawn on (`docs/face-designer.md`, "Flow").
 *
 * Every die of every usable set, so somebody else's d18 can be drawn on as
 * readily as the bundled d6. Above the canvas rather than below it, because it
 * is the first decision and everything under the canvas is about the drawing.
 *
 * Its own composable rather than inline, unlike the dialog below: folded in it
 * takes `DesignerScreen` past detekt's length limit, and a screen that has to
 * be read in one sitting is worth more than two skip branches.
 *
 * It scrolls: a set may define a dozen dice and a name is as long as its author
 * made it.
 */
@Composable
private fun BaseDice(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  if (!state.baseChoosable) return
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 8.dp)
        .testTag(DesignerTestTags.BASES),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    state.choosable.forEach { die ->
      TextButton(
        onClick = { presenter.base(die) },
        modifier = Modifier.testTag(DesignerTestTags.baseOf(die.id)),
      ) {
        Text(
          text = die.id,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = if (die.id == state.die.id) FontWeight.Bold else FontWeight.Normal,
          color =
            if (die.id == state.die.id) {
              MaterialTheme.colorScheme.onBackground
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }
    }
  }
}

/**
 * The square the drawing happens on.
 *
 * The outline is a clip rather than a border: drawing outside it would be
 * drawing on a part of the atlas no face shows, so the mask is the rule rather
 * than a hint about it.
 */
@Composable
private fun FaceCanvas(
  state: DesignerState,
  onStroke: (List<Dot>) -> Unit,
) {
  // The stroke in progress, which is the screen's business until the finger
  // lifts: the model takes a whole stroke, so that one undo is one line.
  var drawing by remember { mutableStateOf(emptyList<Dot>()) }
  val outline = state.draft.outline
  val guideColour = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GUIDE_ALPHA)
  val edge = MaterialTheme.colorScheme.outline

  Canvas(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .aspectRatio(1f)
        .border(1.dp, edge)
        .testTag(DesignerTestTags.CANVAS)
        .pointerInput(state.cell, state.nib, state.colorArgb) {
          detectDragGestures(
            onDragStart = { at -> drawing = listOf(at.asDot(size.width.toFloat(), size.height.toFloat())) },
            onDragEnd = {
              onStroke(drawing)
              drawing = emptyList()
            },
            onDragCancel = { drawing = emptyList() },
          ) { change, _ ->
            drawing = drawing + change.position.asDot(size.width.toFloat(), size.height.toFloat())
          }
        },
  ) {
    val face = Path().apply { follow(outline, size.width, size.height) }
    clipPath(face) {
      drawRect(color = Color.White)
      state.guide.forEach { mark -> drawGuide(mark, outline, guideColour) }
      state.face.strokes.forEach { stroke -> drawStroke(stroke) }
      if (drawing.size > 1) {
        drawStroke(Stroke(drawing, state.colorArgb, state.nib.width, state.nib.erases))
      }
    }
  }
}

private fun Offset.asDot(
  width: Float,
  height: Float,
): Dot = Dot(x = (x / width).coerceIn(0f, 1f), y = (y / height).coerceIn(0f, 1f))

/** The outline as a path across a canvas of [width] by [height]. */
private fun Path.follow(
  outline: FaceOutline,
  width: Float,
  height: Float,
) {
  val corners = FaceShapes.corners(outline)
  if (corners.isEmpty()) {
    // The coin, which is the one outline that is not a polygon.
    addOval(
      androidx.compose.ui.geometry
        .Rect(0f, 0f, width, height),
    )
    return
  }
  corners.forEachIndexed { index, dot ->
    val x = dot.x * width
    val y = dot.y * height
    if (index == 0) moveTo(x, y) else lineTo(x, y)
  }
  close()
}

private fun DrawScope.drawStroke(stroke: Stroke) {
  val path =
    Path().apply {
      stroke.dots.forEachIndexed { index, dot ->
        val x = dot.x * size.width
        val y = dot.y * size.height
        if (index == 0) moveTo(x, y) else lineTo(x, y)
      }
    }
  drawPath(
    path = path,
    // The eraser paints the canvas's own white rather than cutting a hole:
    // the drawing is a list of strokes and a hole would be a fourth kind of
    // thing to store, to undo and to rasterise.
    color = if (stroke.erases) Color.White else Color(stroke.colorArgb),
    style = DrawStroke(width = stroke.width * size.width, cap = androidx.compose.ui.graphics.StrokeCap.Round),
  )
}

private fun DrawScope.drawGuide(
  mark: GuideMark,
  outline: FaceOutline,
  colour: Color,
) {
  val at = FaceShapes.spot(outline, mark.spot)
  // A circle where the number goes rather than the number itself: text on a
  // `DrawScope` needs a measurer this file has no reason to hold, and what the
  // guide is *for* is the place. The value is read from the strip below.
  drawCircle(
    color = colour,
    radius = size.width * GUIDE_DOT,
    center = Offset(at.x * size.width, at.y * size.height),
  )
}

@Composable
private fun Warning(state: DesignerState) {
  if (!state.nearlyFull) return
  Text(
    text = stringResource(if (state.full) R.string.designer_face_full else R.string.designer_face_nearly_full),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(horizontal = 24.dp).testTag(DesignerTestTags.WARNING),
  )
}

@Composable
private fun Tools(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Nib.entries.forEach { nib ->
      Tool(
        label = stringResource(labelOf(nib)),
        chosen = state.nib == nib,
        tag = DesignerTestTags.nibOf(nib),
        onChoose = { presenter.use(nib) },
      )
    }
    Tool(
      label = stringResource(R.string.designer_undo),
      chosen = false,
      tag = DesignerTestTags.UNDO,
      enabled = state.canUndo,
      onChoose = presenter::undo,
    )
    Tool(
      label = stringResource(R.string.designer_redo),
      chosen = false,
      tag = DesignerTestTags.REDO,
      enabled = state.canRedo,
      onChoose = presenter::redo,
    )
    Tool(
      label = stringResource(R.string.designer_clear),
      chosen = false,
      tag = DesignerTestTags.CLEAR,
      enabled = !state.face.blank,
      onChoose = presenter::clear,
    )
    Tool(
      label = stringResource(if (state.guideShown) R.string.designer_guide_off else R.string.designer_guide_on),
      chosen = false,
      tag = DesignerTestTags.GUIDE,
      onChoose = { presenter.showGuide(!state.guideShown) },
    )
  }
}

@Composable
private fun Tool(
  label: String,
  chosen: Boolean,
  tag: String,
  enabled: Boolean = true,
  onChoose: () -> Unit,
) {
  TextButton(onClick = onChoose, enabled = enabled, modifier = Modifier.testTag(tag)) {
    Text(
      text = label,
      fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
      color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * The twelve presets (`design/dInfinity.dc.html`, option `4c`).
 *
 * A picker for anything else is still to come; twelve is what the design shows
 * and what a finger can hit without one.
 */
@Composable
private fun Palette(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    PRESETS.forEach { argb ->
      Box(
        modifier =
          Modifier
            .size(if (state.colorArgb == argb && !state.nib.erases) CHOSEN_SWATCH else SWATCH)
            .clip(CircleShape)
            .background(Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable { presenter.ink(argb) }
            .testTag(DesignerTestTags.colourOf(argb)),
      )
    }
  }
}

/** Which face is in front of the player (`docs/face-designer.md`, "Flow"). */
@Composable
private fun FaceStrip(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    state.draft.die.faces.forEach { face ->
      Tool(
        // The label rather than the value: a set may label a face `crit`, and
        // the strip is how somebody finds the face they mean.
        label = face.label,
        chosen = face.index == state.cell,
        tag = DesignerTestTags.faceOf(face.index),
        onChoose = { presenter.show(face.index) },
      )
    }
  }
}

private fun labelOf(nib: Nib): Int =
  when (nib) {
    Nib.Fine -> R.string.designer_nib_fine
    Nib.Medium -> R.string.designer_nib_medium
    Nib.Broad -> R.string.designer_nib_broad
    Nib.Eraser -> R.string.designer_eraser
  }

private const val GUIDE_ALPHA = 0.35f
private const val GUIDE_DOT = 0.05f
private val SWATCH = 28.dp
private val CHOSEN_SWATCH = 36.dp

/** The twelve the design shows (`design/dInfinity.dc.html`, option `4c`). */
private val PRESETS =
  listOf(
    0xFF000000,
    0xFFFFFFFF,
    0xFFEC3013,
    0xFFE15B47,
    0xFFF5A623,
    0xFFF8E71C,
    0xFF7ED321,
    0xFF1F5E3A,
    0xFF50E3C2,
    0xFF4A90D9,
    0xFF9013FE,
    0xFF8B572A,
  ).map { it.toInt() }

/** What the tests reach for. */
object DesignerTestTags {
  const val SCREEN: String = "designer:screen"
  const val CANVAS: String = "designer:canvas"
  const val UNDO: String = "designer:undo"
  const val REDO: String = "designer:redo"
  const val CLEAR: String = "designer:clear"
  const val GUIDE: String = "designer:guide"
  const val WARNING: String = "designer:warning"
  const val BASES: String = "designer:bases"

  fun baseOf(dieId: String): String = "designer:base:$dieId"

  fun nibOf(nib: Nib): String = "designer:nib:${nib.name.lowercase()}"

  fun colourOf(argb: Int): String = "designer:colour:$argb"

  fun faceOf(cell: Int): String = "designer:face:$cell"
}
