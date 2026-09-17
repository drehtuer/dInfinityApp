package de.drehtuer.dinfinity.feature.designer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.FaceTransform
import de.drehtuer.dinfinity.designer.Ink
import de.drehtuer.dinfinity.designer.Stamp
import de.drehtuer.dinfinity.designer.StampSize
import de.drehtuer.dinfinity.designer.Stroke
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import de.drehtuer.dinfinity.ui.common.Ink as Colours

// `Colours` is `ui/common`'s `Ink`, aliased because both better names are
// taken here: `Ink` is `designer/`'s own hex and HSV arithmetic, and
// `Palette` is the composable below that draws the twelve colours.

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
  onRoll: (String) -> Unit = {},
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
    Header(rollable = presenter.rollable, onRoll = onRoll, menu = menu)

    Column(
      // Everything above the strip scrolls, as the body does in the prototype:
      // a square canvas and three rows of controls do not fit on a short
      // phone, and a control squeezed off the bottom is one nobody can reach.
      modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      BaseDice(state, presenter)
      FaceCanvas(state = state, onStroke = presenter::drew)
      Warning(state)
      Tools(state, presenter)
      StampBar(state, presenter)
      Clipboard(state, presenter)
      Palette(state, presenter)
    }
    // The strip stays: which face is in front of the player is where the
    // screen is steered from, and a steering wheel that scrolls away is not
    // one.
    FaceStrip(state, presenter)
  }
}

/**
 * The title, the way out to the tray, and the menu.
 *
 * Its own composable for the reason `BaseDice` is: folded in, `DesignerScreen`
 * runs past detekt's length limit, and a screen that can be read in one
 * sitting is worth more than a skip branch.
 */
@Composable
private fun Header(
  rollable: String?,
  onRoll: (String) -> Unit,
  menu: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.designer_title),
      // `titleLarge` is already the heading font at 800; a `Bold` here pulled
      // it back to Material's 700 (`--font-heading-weight: 800`).
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.weight(1f),
    )
    // Step 4 of the flow, and the only one the prototype has instead of a 3D
    // preview: throw the die and watch it (`docs/face-designer.md`). Absent
    // rather than dead for a die plain notation cannot name — a button that is
    // there and does nothing is worse than one that is not.
    rollable?.let { formula ->
      // `btn btn-primary` in the prototype's footer — the one filled button
      // on the screen, because it is the one thing the screen is for.
      Button(
        onClick = { onRoll(formula) },
        shape = Modernist.square,
        modifier = Modifier.testTag(DesignerTestTags.ROLL),
      ) {
        Text(stringResource(R.string.designer_roll))
      }
    }
    menu()
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
    // A `.seg` in the prototype, and so the same option treatment as every
    // other chooser on the screen.
    state.choosable.forEach { die ->
      Tool(
        label = die.id,
        chosen = die.id == state.die.id,
        tag = DesignerTestTags.baseOf(die.id),
        onChoose = { presenter.base(die) },
      )
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
  val guideColour = MaterialTheme.colorScheme.onBackground.copy(alpha = GUIDE_ALPHA)
  val edge = MaterialTheme.colorScheme.outline

  Canvas(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .aspectRatio(1f)
        // `outline:2px solid var(--color-divider)`. The system draws rules,
        // not hairlines.
        .border(Modernist.rule, edge)
        .testTag(DesignerTestTags.CANVAS)
        .pointerInput(state.cell, state.nib, state.colorArgb, state.stamping) {
          // One gesture or the other, never both: the pens answer a drag and
          // the bucket and the stamp answer a tap, and a detector that
          // listened for both would make a slow tap with a pen into a dot of
          // ink.
          if (state.nib.taps) {
            detectTapGestures { at -> onStroke(listOf(at.asDot(size.width.toFloat(), size.height.toFloat()))) }
          } else {
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
          }
        },
  ) {
    val face = Path().apply { follow(outline, size.width, size.height) }
    clipPath(face) {
      drawRect(color = Color.White)
      state.guide.forEach { mark -> drawGuide(mark, guideColour) }
      state.face.marks.forEach { mark -> drawMark(mark) }
      if (drawing.size > 1) {
        drawStroke(Stroke(drawing, state.colorArgb, state.nib.width, state.nib.erases))
      }
    }
  }
}

@Composable
private fun Warning(state: DesignerState) {
  if (!state.nearlyFull) return
  Text(
    text = stringResource(if (state.full) R.string.designer_face_full else R.string.designer_face_nearly_full),
    style = MaterialTheme.typography.labelSmall,
    color = Colours.accent,
    modifier = Modifier.padding(horizontal = 24.dp).testTag(DesignerTestTags.WARNING),
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tools(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  // Wrapping rather than scrolling sideways: a tool hidden off the edge of a
  // row is a tool nobody finds, and there are nine of them.
  FlowRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
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
      onChoose = { presenter.take(Step.Back) },
    )
    Tool(
      label = stringResource(R.string.designer_redo),
      chosen = false,
      tag = DesignerTestTags.REDO,
      enabled = state.canRedo,
      onChoose = { presenter.take(Step.Forward) },
    )
    Tool(
      label = stringResource(R.string.designer_clear),
      chosen = false,
      tag = DesignerTestTags.CLEAR,
      enabled = !state.face.blank,
      onChoose = { presenter.take(Step.Clear) },
    )
    Tool(
      label = stringResource(if (state.guideShown) R.string.designer_guide_off else R.string.designer_guide_on),
      chosen = false,
      tag = DesignerTestTags.GUIDE,
      onChoose = { presenter.showGuide(!state.guideShown) },
    )
  }
}

/**
 * One option of a chooser, or one thing to do (`.seg-opt`, `.btn-ghost`).
 *
 * **Chosen is a filled option, not a coloured word.** That is what the system
 * does everywhere a choice is shown — `.seg-opt:has(input:checked)` puts the
 * accent behind the label and the ground in front of it — and a label that
 * only changed colour was asking the player to compare two greys.
 *
 * Which of the two it is is said in the semantics as well as in the paint, so
 * a screen reader hears "selected" rather than nothing
 * (`docs/architecture.md`, "Accessibility"). A button that is an action rather
 * than an option is never chosen, and so says nothing.
 */
@Composable
private fun Tool(
  label: String,
  chosen: Boolean,
  tag: String,
  enabled: Boolean = true,
  onChoose: () -> Unit,
) {
  TextButton(
    onClick = onChoose,
    enabled = enabled,
    shape = Modernist.square,
    colors =
      if (chosen) {
        ButtonDefaults.textButtonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.background,
        )
      } else {
        ButtonDefaults.textButtonColors(contentColor = Colours.muted)
      },
    modifier = Modifier.semantics { selected = chosen }.testTag(tag),
  ) {
    Text(text = label, fontWeight = FontWeight.SemiBold)
  }
}

/**
 * What the stamp will put down, and how big (`docs/face-designer.md`, "The
 * stamp"; `design/dInfinity.dc.html`, option `1v`).
 *
 * Only while the stamp is in hand, which is what the prototype does: it is two
 * more rows on a screen that already scrolls, and they mean nothing to a pen.
 *
 * The field opens on the face's own number and follows the face until somebody
 * types — so the commonest stamp of all is one tap, and an edit of it survives
 * moving to the next face.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StampBar(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  if (!state.nib.stamps) return
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .testTag(DesignerTestTags.STAMP_BAR),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    val name = stringResource(R.string.designer_stamp_text)
    OutlinedTextField(
      value = state.stamping,
      onValueChange = { presenter.stamp(text = it) },
      label = { Text(name) },
      singleLine = true,
      isError = !state.canStamp,
      modifier =
        Modifier
          .fillMaxWidth()
          .semantics { contentDescription = name }
          .testTag(DesignerTestTags.STAMP_TEXT),
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      StampSize.entries.forEach { size ->
        Tool(
          label = stringResource(labelOf(size)),
          chosen = state.stampSize == size,
          tag = DesignerTestTags.stampSizeOf(size),
          onChoose = { presenter.stamp(size = size) },
        )
      }
    }
    // Said before the tap rather than after it: the font draws digits and a
    // few signs, and a tap that quietly left nothing behind would read as a
    // canvas that had stopped working.
    if (!state.canStamp) {
      Text(
        text = stringResource(R.string.designer_stamp_refused),
        style = MaterialTheme.typography.labelSmall,
        color = Colours.accent,
        modifier = Modifier.testTag(DesignerTestTags.STAMP_REFUSED),
      )
    }
  }
}

/**
 * Copying a face and putting it down on another (`docs/face-designer.md`,
 * "Copy and paste").
 *
 * The turn and the mirror are the **screen's** state rather than the
 * presenter's: they are how the next press of Paste will behave, like the pen
 * width is how the next stroke will, and nothing on the die changes until
 * something is pasted.
 *
 * The turn is a whole step of the cell's own symmetry, so a die whose cells
 * have no turn — the d10 and the d18, whose faces are kites — is offered the
 * mirror and no turn at all. It is *disabled* rather than absent, because a
 * row whose buttons move about as the base die changes is a row nobody learns.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Clipboard(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  var transform by remember { mutableStateOf(FaceTransform()) }
  val steps = state.turnsOffered
  FlowRow(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .testTag(DesignerTestTags.CLIPBOARD),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Tool(
      label = stringResource(R.string.designer_copy),
      chosen = false,
      tag = DesignerTestTags.COPY,
      enabled = state.canCopy,
      onChoose = presenter::copyFace,
    )
    Tool(
      label = stringResource(R.string.designer_turn, transform.turns + 1, steps),
      chosen = transform.turns != 0,
      tag = DesignerTestTags.TURN,
      enabled = steps > 1,
      onChoose = { transform = transform.copy(turns = (transform.turns + 1) % steps) },
    )
    Tool(
      label = stringResource(R.string.designer_mirror),
      chosen = transform.mirrored,
      tag = DesignerTestTags.MIRROR,
      onChoose = { transform = transform.copy(mirrored = !transform.mirrored) },
    )
    Tool(
      label = stringResource(R.string.designer_paste),
      chosen = false,
      tag = DesignerTestTags.PASTE,
      enabled = state.canPaste,
      onChoose = { presenter.paste(transform) },
    )
  }
}

/**
 * The twelve presets, and the way past them (`design/dInfinity.dc.html`,
 * option `4c`).
 *
 * Twelve is what a finger can hit without a dialog, so they stay the fast
 * path and the picker sits after them — which is where the design puts it.
 * The swatch showing what is in the pen is the picker's own: it opens on the
 * colour being drawn with rather than on a colour nobody chose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Palette(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  var picking by remember { mutableStateOf(false) }
  FlowRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    PRESETS.forEach { argb ->
      Swatch(
        argb = argb,
        chosen = state.colorArgb == argb && !state.nib.erases,
        label = stringResource(R.string.designer_ink, Ink.hex(argb)),
        tag = DesignerTestTags.colourOf(argb),
        onChoose = { presenter.ink(argb) },
      )
    }
    Swatch(
      argb = state.colorArgb,
      chosen = state.colorArgb !in PRESETS && !state.nib.erases,
      label = stringResource(R.string.designer_colour_more, Ink.hex(state.colorArgb)),
      tag = DesignerTestTags.MORE_COLOURS,
      onChoose = { picking = true },
    )
    Text(
      text = Ink.hex(state.colorArgb),
      style = MaterialTheme.typography.labelSmall,
      color = Colours.muted,
      modifier = Modifier.testTag(DesignerTestTags.INK_HEX),
    )
  }
  if (picking) {
    ColourPicker(
      start = state.colorArgb,
      onDismiss = { picking = false },
      onChosen = {
        presenter.ink(it)
        picking = false
      },
    )
  }
}

/**
 * One colour to draw with.
 *
 * A square, because `--radius-*` is `0px` and nothing in this system has a
 * rounded corner — the circle this used to draw was the one shape the
 * Modernist palette has no room for.
 *
 * **Which colour is in the pen is said by the edge, not by the size.** The
 * prototype gives every swatch the same 26 px and switches its 2 px border
 * from `--color-divider` to `--color-text`; a swatch that grew when it was
 * chosen made the row reflow under the finger that chose it.
 *
 * The swatch is as small as the design draws it and the thing a finger hits is
 * not: the touch target is a full [TOUCH_TARGET] whatever the swatch inside it
 * measures, which is the floor Android asks for and the reason the row is
 * spaced rather than crowded.
 */
@Composable
private fun Swatch(
  argb: Int,
  chosen: Boolean,
  label: String,
  tag: String,
  onChoose: () -> Unit,
) {
  Box(
    modifier =
      Modifier
        .size(TOUCH_TARGET)
        .clickable(onClick = onChoose)
        .semantics { contentDescription = label }
        .testTag(tag),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(SWATCH)
          .background(Color(argb))
          .border(
            width = Modernist.rule,
            color =
              if (chosen) {
                MaterialTheme.colorScheme.onBackground
              } else {
                MaterialTheme.colorScheme.outline
              },
          ),
    )
  }
}

/**
 * A colour beyond the twelve (`docs/face-designer.md`, "A colour beyond the
 * twelve").
 *
 * Hue, depth and brightness rather than red, green and blue: three sliders a
 * finger can move one at a time and mean something by. The arithmetic behind
 * them is `Ink`, which is where it can be tested — what is left here is three
 * sliders and a patch of the colour they make.
 */
@Composable
private fun ColourPicker(
  start: Int,
  onDismiss: () -> Unit,
  onChosen: (Int) -> Unit,
) {
  var hsv by remember { mutableStateOf(Ink.hsv(start)) }
  AlertDialog(
    modifier = Modifier.testTag(DesignerTestTags.PICKER),
    onDismissRequest = onDismiss,
    // `.dialog-title`: the heading font at 800, 20 px.
    title = {
      Text(
        text = stringResource(R.string.designer_colour_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(TOUCH_TARGET)
              .background(Color(hsv.argb))
              .border(Modernist.rule, MaterialTheme.colorScheme.outline)
              .semantics { contentDescription = Ink.hex(hsv.argb) }
              .testTag(DesignerTestTags.PICKER_PATCH),
        )
        Channel(R.string.designer_hue, hsv.hue, HUE_ROUND, DesignerTestTags.HUE) { hsv = hsv.copy(hue = it) }
        Channel(R.string.designer_depth, hsv.saturation, 1f, DesignerTestTags.DEPTH) {
          hsv = hsv.copy(saturation = it)
        }
        Channel(R.string.designer_brightness, hsv.value, 1f, DesignerTestTags.BRIGHTNESS) { hsv = hsv.copy(value = it) }
      }
    },
    confirmButton = {
      Button(
        onClick = { onChosen(hsv.argb) },
        shape = Modernist.square,
        modifier = Modifier.testTag(DesignerTestTags.PICKER_USE),
      ) {
        Text(stringResource(R.string.designer_colour_use))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        shape = Modernist.square,
        modifier = Modifier.testTag(DesignerTestTags.PICKER_CANCEL),
      ) {
        Text(stringResource(R.string.designer_colour_cancel))
      }
    },
  )
}

/** One of the picker's three sliders, named so a screen reader can say which. */
@Composable
private fun Channel(
  label: Int,
  value: Float,
  most: Float,
  tag: String,
  onChange: (Float) -> Unit,
) {
  val name = stringResource(label)
  Text(text = name, style = MaterialTheme.typography.labelSmall, color = Colours.muted)
  Slider(
    value = value,
    onValueChange = onChange,
    valueRange = 0f..most,
    modifier =
      Modifier
        .semantics { contentDescription = name }
        .testTag(tag),
  )
}

/**
 * Which face is in front of the player, and the one tap that numbers them all
 * (`docs/face-designer.md`, "Flow" and "The stamp").
 *
 * The button sits beside the strip rather than in it, and outside the scroll:
 * "fill all with numbers" is about every face, which is what the strip is
 * about, and a control that scrolls away with the twentieth face is one nobody
 * finds (`design/dInfinity.dc.html`, option `1v`).
 */
@Composable
private fun FaceStrip(
  state: DesignerState,
  presenter: DesignerPresenter,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
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
    Tool(
      label = stringResource(R.string.designer_fill_numbers),
      chosen = false,
      tag = DesignerTestTags.FILL_NUMBERS,
      onChoose = presenter::fillNumbers,
    )
  }
}

private fun labelOf(nib: Nib): Int =
  when (nib) {
    Nib.Fine -> R.string.designer_nib_fine
    Nib.Medium -> R.string.designer_nib_medium
    Nib.Broad -> R.string.designer_nib_broad
    Nib.Eraser -> R.string.designer_eraser
    Nib.Bucket -> R.string.designer_bucket
    Nib.Stamp -> R.string.designer_stamp
  }

private fun labelOf(size: StampSize): Int =
  when (size) {
    StampSize.Small -> R.string.designer_stamp_small
    StampSize.Medium -> R.string.designer_stamp_medium
    StampSize.Large -> R.string.designer_stamp_large
  }

private const val GUIDE_ALPHA = 0.35f

/** All the way round the wheel, which is where hue starts again. */
private const val HUE_ROUND = 360f

/**
 * How big a colour swatch is drawn (`width:26px;height:26px`).
 *
 * This screen's own measurement rather than a token of the design system:
 * nothing else in the app draws a swatch, so there is nothing for it to stay
 * equal to.
 */
private val SWATCH = 26.dp

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
  const val ROLL: String = "designer:roll"
  const val CLIPBOARD: String = "designer:clipboard"
  const val COPY: String = "designer:copy"
  const val PASTE: String = "designer:paste"
  const val TURN: String = "designer:turn"
  const val MIRROR: String = "designer:mirror"
  const val STAMP_BAR: String = "designer:stamp"
  const val STAMP_TEXT: String = "designer:stamp:text"
  const val STAMP_REFUSED: String = "designer:stamp:refused"
  const val FILL_NUMBERS: String = "designer:stamp:fill"
  const val MORE_COLOURS: String = "designer:colour:more"
  const val INK_HEX: String = "designer:colour:hex"
  const val PICKER: String = "designer:picker"
  const val PICKER_PATCH: String = "designer:picker:patch"
  const val PICKER_USE: String = "designer:picker:use"
  const val PICKER_CANCEL: String = "designer:picker:cancel"
  const val HUE: String = "designer:picker:hue"
  const val DEPTH: String = "designer:picker:depth"
  const val BRIGHTNESS: String = "designer:picker:brightness"

  fun baseOf(dieId: String): String = "designer:base:$dieId"

  fun nibOf(nib: Nib): String = "designer:nib:${nib.name.lowercase()}"

  fun stampSizeOf(size: StampSize): String = "designer:stamp:${size.name.lowercase()}"

  fun colourOf(argb: Int): String = "designer:colour:$argb"

  fun faceOf(cell: Int): String = "designer:face:$cell"
}
