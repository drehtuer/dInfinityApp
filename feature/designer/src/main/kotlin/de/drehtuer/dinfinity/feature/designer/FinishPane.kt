package de.drehtuer.dinfinity.feature.designer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Hex
import de.drehtuer.dinfinity.designer.DieFinish
import de.drehtuer.dinfinity.designer.EdgeRounding
import de.drehtuer.dinfinity.designer.MaterialPreset
import de.drehtuer.dinfinity.ui.common.ColourPicker
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.SectionKicker
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET

/**
 * What the die is made of and how round its edges are
 * (`docs/face-designer.md`, "Material, colour and edges"; `design/dInfinityPhone.dc.html`,
 * the designer's Solid tab).
 *
 * On the Solid tab, under the turning die, because both are about the die in
 * the hand rather than the face on the canvas. **The swatch is a hint, not the
 * die**: the Solid tab is a drawing on paper, and how a resin die bends the
 * felt or a metal one catches the light is the renderer's — which **Roll it**
 * shows. The note under the controls says so.
 */
@Composable
internal fun FinishPane(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    SectionKicker(text = stringResource(R.string.designer_material))
    Row(
      horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Swatch(state.draft.shownFinish)
      MaterialMenu(state.preset, presenter::madeOf, Modifier.weight(1f))
    }
    SectionKicker(text = stringResource(R.string.designer_body))
    BodyColours(state, presenter)
    SectionKicker(text = stringResource(R.string.designer_edges))
    Edges(state, presenter)
    Text(
      text = stringResource(R.string.designer_finish_note),
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
      modifier = Modifier.testTag(DesignerTestTags.FINISH_NOTE),
    )
  }
}

/**
 * How round the die is: a slider over what a set file may ask for, in steps of
 * half a per cent of its size (`EdgeRounding`; `docs/face-designer.md`,
 * "Material, colour and edges").
 *
 * Said in words under it twice over — the share of the size the slider moves
 * and the millimetres that come to on *this* die — because a per cent is the
 * number a set file writes and a millimetre is the one a person can picture.
 * The millimetres are the solver's: on a d4 that is half what is asked for,
 * because its points are cut back (`RoundedSolid.radius`).
 */
@Composable
@NonRestartableComposable
private fun Edges(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  val share = state.edgeRounding
  val said =
    stringResource(
      R.string.designer_edges_value,
      (share * PERCENT).toFloat(),
      state.roundedMm.toFloat(),
    )
  val name = stringResource(R.string.designer_edges)
  Slider(
    value = share.toFloat(),
    onValueChange = { presenter.rounded(it.toDouble()) },
    valueRange = EdgeRounding.RANGE.start.toFloat()..EdgeRounding.RANGE.endInclusive.toFloat(),
    steps = EdgeRounding.BETWEEN,
    modifier =
      Modifier
        .fillMaxWidth()
        .semantics {
          contentDescription = name
          stateDescription = said
        }.testTag(DesignerTestTags.EDGES),
  )
  Text(
    text = said,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.testTag(DesignerTestTags.EDGES_SAID),
  )
}

/**
 * The die's body colour (`docs/face-designer.md`, "Material, colour and
 * edges"): the twelve the pen offers, and the same picker past them — the
 * one idiom this screen has for choosing a colour.
 *
 * Its own row, apart from the ink's, because it is a different question: the
 * ink is what the next stroke is drawn in, the body is what every face is
 * drawn *on* and what shows at the rounded edges, where no face reaches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@NonRestartableComposable
private fun BodyColours(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  var picking by remember { mutableStateOf(false) }
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(Modernist.x1),
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    PRESETS.forEach { argb ->
      Swatch(
        argb = argb,
        chosen = state.bodyArgb == argb,
        label = stringResource(R.string.designer_body_colour, Hex.of(argb)),
        tag = DesignerTestTags.bodyOf(argb),
        onChoose = { presenter.coloured(argb) },
      )
    }
    Swatch(
      argb = state.bodyArgb,
      chosen = state.bodyArgb !in PRESETS,
      label = stringResource(R.string.designer_body_more, Hex.of(state.bodyArgb)),
      tag = DesignerTestTags.MORE_BODY_COLOURS,
      onChoose = { picking = true },
    )
    Text(
      text = Hex.of(state.bodyArgb),
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
      modifier = Modifier.testTag(DesignerTestTags.BODY_HEX),
    )
  }
  if (picking) {
    ColourPicker(
      start = state.bodyArgb,
      title = stringResource(R.string.designer_body_title),
      tags = DesignerTestTags.BODY_PICKER,
      onDismiss = { picking = false },
      onChosen = {
        presenter.coloured(it)
        picking = false
      },
    )
  }
}

/**
 * The Material menu: the design system's `.input` with the names behind it.
 *
 * A field-shaped box rather than a button, because what it shows is a value
 * — which material — and a field reads from its left edge. Drawn by hand
 * rather than as Material's `OutlinedButton`, which this system replaces, or
 * its exposed dropdown, which is a rounded, floating-label text field.
 */
@Composable
private fun MaterialMenu(
  chosen: MaterialPreset?,
  onChoose: (MaterialPreset) -> Unit,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  val name = chosen?.let { stringResource(labelOf(it)) } ?: stringResource(R.string.designer_material_custom)
  val described = stringResource(R.string.designer_material_described, name)
  Box(modifier = modifier) {
    Box(
      contentAlignment = Alignment.CenterStart,
      modifier =
        Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = TOUCH_TARGET)
          .border(Modernist.hairline, MaterialTheme.colorScheme.outline)
          .background(MaterialTheme.colorScheme.surface)
          .clickable(role = Role.DropdownList) { open = true }
          .semantics { contentDescription = described }
          .padding(horizontal = Modernist.x3)
          .testTag(DesignerTestTags.MATERIAL),
    ) {
      // The box says it, once, as "Material: Glass"; the word on it is for
      // the eye.
      Text(text = name, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.clearAndSetSemantics { })
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      MaterialPreset.entries.forEach { preset ->
        DropdownMenuItem(
          text = { Text(stringResource(labelOf(preset))) },
          onClick = {
            open = false
            onChoose(preset)
          },
          modifier = Modifier.testTag(DesignerTestTags.materialOf(preset)),
        )
      }
    }
  }
}

/**
 * A square of the die's body colour as the finish would show it: the paper
 * through it as much as light goes through it, and a glint as sharp and as
 * bright as the surface would give one ([SwatchLook]).
 *
 * Silent to TalkBack — the menu beside it says the same thing in words.
 */
@Composable
private fun Swatch(finish: DieFinish) {
  val look = SwatchLook.of(finish)
  val body = Color(finish.colorArgb)
  val paper = MaterialTheme.colorScheme.onBackground
  val ground = MaterialTheme.colorScheme.background
  Canvas(
    modifier =
      Modifier
        .size(TOUCH_TARGET)
        .border(Modernist.hairline, MaterialTheme.colorScheme.outline)
        .testTag(DesignerTestTags.SWATCH)
        .clearAndSetSemantics { },
  ) {
    // A chequer behind, so how much shows through is something to see.
    val cell = size.width / CHEQUER
    for (row in 0 until CHEQUER) {
      for (column in 0 until CHEQUER) {
        drawRect(
          color = if ((row + column) % 2 == 0) paper else ground,
          topLeft = Offset(column * cell, row * cell),
          size = Size(cell, cell),
        )
      }
    }
    drawRect(color = body.copy(alpha = look.body))
    drawRect(
      brush =
        Brush.linearGradient(
          0f to Color.Transparent,
          (HALF - look.spread) to Color.Transparent,
          HALF to Color.White.copy(alpha = look.glint),
          (HALF + look.spread) to Color.Transparent,
          1f to Color.Transparent,
          start = Offset.Zero,
          end = Offset(size.width, size.height),
        ),
    )
  }
}

/**
 * How the swatch draws a finish, worked out where a JVM test can see it.
 *
 * @param body how opaque the body colour is over the chequer: the die's
 *   opacity, but never less than a fifth, or a glass die would be a swatch of
 *   nothing.
 * @param glint how bright the highlight is: a metal die's is bright and a
 *   matte one's is barely there.
 * @param spread how far the highlight spreads either side of the diagonal, as
 *   a share of it: wide on a rough die and a thin line on a polished one.
 */
internal data class SwatchLook(
  val body: Float,
  val glint: Float,
  val spread: Float,
) {
  companion object {
    fun of(finish: DieFinish): SwatchLook {
      val smooth = (1 - finish.roughness).toFloat()
      // A metal reflects all of what reaches it, a dielectric about half of
      // what a swatch can show.
      val shine = METAL_GLINT + (1 - METAL_GLINT) * finish.metallic.toFloat()
      return SwatchLook(
        body = (1 - finish.translucency).toFloat().coerceAtLeast(LEAST_BODY),
        glint = LEAST_GLINT + (1 - LEAST_GLINT) * smooth * shine,
        spread = LEAST_SPREAD + (MOST_SPREAD - LEAST_SPREAD) * finish.roughness.toFloat(),
      )
    }

    private const val LEAST_BODY = 0.2f
    private const val LEAST_GLINT = 0.1f
    private const val METAL_GLINT = 0.5f
    private const val LEAST_SPREAD = 0.04f
    private const val MOST_SPREAD = 0.4f
  }
}

private fun labelOf(preset: MaterialPreset): Int =
  when (preset) {
    MaterialPreset.Plastic -> R.string.designer_material_plastic
    MaterialPreset.Pearl -> R.string.designer_material_pearl
    MaterialPreset.Resin -> R.string.designer_material_resin
    MaterialPreset.Glass -> R.string.designer_material_glass
    MaterialPreset.Metal -> R.string.designer_material_metal
    MaterialPreset.Stone -> R.string.designer_material_stone
  }

/** Squares a side of the swatch's chequer. */
private const val CHEQUER = 4

private const val HALF = 0.5f

private const val PERCENT = 100.0
