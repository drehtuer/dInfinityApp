package de.drehtuer.dinfinity.feature.designer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.OptionBox
import de.drehtuer.dinfinity.ui.common.Ink as Colours

/**
 * The die in the hand (`docs/face-designer.md`, "The solid, not just the
 * face"; `design/dInfinityPhone.dc.html`, the designer's Solid tab).
 *
 * The real polyhedron, turned, with each authored face on the face it was
 * drawn for. It turns on its own until a drag takes over, and the tick under
 * it is how somebody puts it back to turning — the drag itself unticks it,
 * because a die that went on turning under the finger holding it would be a
 * die fighting back.
 *
 * **The whole stage is one drag surface.** Nothing on the die is selectable —
 * not a face, not a numeral, not a pip — because a tap that sometimes rotates
 * and sometimes selects is a tap nobody trusts. The face strip under the
 * screen is how a face is chosen, on this tab exactly as on the other.
 *
 * The stage and its tick are **one composable** rather than two with a box
 * round them, because they are one control: a picture and the one thing that
 * can be said about how it moves. What each shape on it *is* was settled in
 * `designer`'s `SolidStage` before any of it reached a `DrawScope`, and how it
 * is put down is `SolidInk`'s — so what is left here is a modifier chain and
 * two rows.
 */
@Composable
internal fun SolidPane(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  val stage = state.stage
  val colours =
    SolidColours(
      // **The die's paper, not the screen's.** The flat editor draws every
      // face on white (`DesignerScreen`'s canvas), so a solid drawn on the
      // theme's surface is the same drawing in two different colours — and on
      // a dark page it is black ink on a dark grey face, which is a numeral
      // nobody can read. A die is a white thing in a room, whichever page it
      // is being drawn on. The question of whether the *canvas* should follow
      // the theme is open either way (`docs/design-handover.md`); what this
      // fixes is the two of them disagreeing.
      paper = PAPER,
      // And a face turned away from the lamp is its paper in shadow, which is
      // darker rather than the colour of the page's ink.
      shade = SHADE,
      edge = MaterialTheme.colorScheme.outline,
      chosen = Colours.accentDeep,
      tint = Colours.accent.copy(alpha = SELECTED_TINT),
    )
  val turning =
    pluralStringResource(R.plurals.designer_solid_stage, state.draft.cells, state.die.id, state.draft.cells)

  Canvas(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .aspectRatio(1f)
        // `outline:2px solid var(--color-divider)`, the same box the canvas on
        // the other tab stands in.
        .border(Modernist.rule, colours.edge)
        .semantics { contentDescription = turning }
        .testTag(DesignerTestTags.SOLID)
        .pointerInput(Unit) {
          detectDragGestures { change, dragged ->
            change.consume()
            presenter.turned(dragged.x / size.width, dragged.y / size.height)
          }
        },
  ) {
    // The silhouette first, because it is the only thing that draws a coin's
    // rim: every other solid is covered by its own faces.
    drawSolid(stage.silhouette, colours.lit(RIM_LIGHT))
    stage.faces.forEach { face -> drawFace(face, colours, selected = face.cell == state.cell) }
  }

  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OptionBox(
      text = stringResource(R.string.designer_spin),
      selected = state.spinning,
      onClick = { presenter.spin(!state.spinning) },
      role = Role.Checkbox,
      modifier = Modifier.testTag(DesignerTestTags.SPIN),
    )
    Text(
      text = stringResource(R.string.designer_solid_note),
      style = MaterialTheme.typography.labelSmall,
      color = Colours.muted,
      modifier = Modifier.weight(1f).testTag(DesignerTestTags.SOLID_NOTE),
    )
  }
}

/** `color-mix(in srgb, var(--color-accent) 16%, …)`, the design's own. */
private const val SELECTED_TINT = 0.16f

/** How much light the band round the outside of a coin catches. */
private const val RIM_LIGHT = 0.2f

/** The paper every face of the flat editor is drawn on, and so every face here. */
private val PAPER = Color.White

/** What the lamp leaves of it on a face turned away. */
private val SHADE = Color.Black
