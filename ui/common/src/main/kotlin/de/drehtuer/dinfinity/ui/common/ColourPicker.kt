package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.drehtuer.dinfinity.core.model.Hex
import de.drehtuer.dinfinity.core.model.Hsv

/**
 * A colour of somebody's own: hue, depth and brightness over a patch of what
 * they make (`design/dInfinity.dc.html`, option `4c`;
 * `design/dInfinityPhone.dc.html`, the Settings accent and the saved-roll
 * editor).
 *
 * **Three screens ask for a colour** — the face designer's ink, Settings'
 * accent and a saved roll's tag — and this is the one control all three draw.
 * It was written twice before it was written here, once in `feature/designer`
 * and once in `feature/settings`, each with its own copy of the slider row and
 * its own `designer/Ink` import; the second copy's own KDoc said that two
 * pickers in one app that disagreed about what a hue is would be one too many,
 * which is an argument for this file rather than for a careful third one.
 *
 * It is in `ui/common` for the reason everything here is: **more than one
 * screen needs it, and it needs no screen** (`docs/architecture.md`, Modules).
 * Nothing in it knows what the colour is *for* — it hands back an opaque
 * `0xAARRGGBB` and the caller decides what to do with it. The arithmetic is
 * `core/model`'s [Hsv], which is plain Kotlin under a JVM test, so what is
 * left here is three sliders and a patch.
 *
 * **Hue, depth and brightness rather than red, green and blue**, because
 * dragging one of three colour channels changes all three of the things
 * somebody is actually looking at, and nobody thinks in those.
 *
 * **The patch shows the colour as chosen, never as it will be painted.** Two
 * of the three callers put what comes out of here through
 * `AccentRamp.clamp` before anything is drawn in it, and a picker whose patch
 * answered a drag with a slightly different colour is a picker nobody can aim.
 * Where the clamp can move a colour, the screen says so in words beside the
 * swatches instead (`docs/architecture.md`, decision 22).
 *
 * @param start the colour the sheet opens on — what is already in the pen,
 *   already the accent, already the tag. Opening on a colour nobody chose
 *   would make every visit start by undoing one.
 * @param title what the sheet is called, which is the caller's word for the
 *   thing being coloured rather than this file's.
 * @param tags what a test reaches the sheet and its controls by; see
 *   [ColourPickerTags].
 * @param onDismiss the way out that changes nothing: **Cancel**, the back
 *   gesture and a tap on the backdrop all arrive here.
 * @param onChosen **Use it**, with the colour as chosen. Called once; closing
 *   the sheet is the caller's, because only it knows whether the colour was
 *   accepted.
 */
@Composable
fun ColourPicker(
  start: Int,
  title: String,
  tags: ColourPickerTags,
  onDismiss: () -> Unit,
  onChosen: (Int) -> Unit,
) {
  var hsv by remember(start) { mutableStateOf(Hsv.of(start)) }
  Sheet(
    title = title,
    onDismiss = onDismiss,
    modifier = Modifier.testTag(tags.sheet),
    // Taking the colour first and leaving it after, because the sheet reads
    // left to right and the confirming action is what it is for.
    actions = {
      ModernistButton(
        text = stringResource(R.string.colour_picker_use),
        onClick = { onChosen(hsv.argb) },
        kind = ModernistButtonKind.Primary,
        modifier = Modifier.testTag(tags.use),
      )
      ModernistButton(
        text = stringResource(R.string.colour_picker_cancel),
        onClick = onDismiss,
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(tags.cancel),
      )
    },
  ) {
    // Tighter than the sheet's own spacing: the patch and the three sliders
    // are one control, not four blocks of the sheet.
    Column(verticalArrangement = Arrangement.spacedBy(Modernist.x1)) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(TOUCH_TARGET)
            .background(Color(hsv.argb))
            .border(Modernist.rule, MaterialTheme.colorScheme.outline)
            // A patch of colour is a picture, and a picture with no words is
            // nothing at all to a screen reader. The hex is what it says,
            // because it is the one description of a colour that is exact.
            .semantics { contentDescription = Hex.of(hsv.argb) }
            .testTag(tags.patch),
      )
      Channel(R.string.colour_picker_hue, hsv.hue, Hsv.ROUND, tags.hue) { hsv = hsv.copy(hue = it) }
      Channel(R.string.colour_picker_depth, hsv.saturation, 1f, tags.depth) { hsv = hsv.copy(saturation = it) }
      Channel(R.string.colour_picker_brightness, hsv.value, 1f, tags.brightness) { hsv = hsv.copy(value = it) }
    }
  }
}

/**
 * One of the picker's three sliders, named so a screen reader can say which.
 *
 * **It is 44 dp tall, which is four short of [TOUCH_TARGET].** That is
 * Material's own figure and `Slider` clamps it: neither `heightIn` nor
 * `minimumInteractiveComponentSize` moves the node the label is attached to,
 * so growing the band would mean growing something a screen reader and an
 * accessibility scanner cannot see. Both copies of this picker shipped at 44
 * and this one does too — recorded here and asserted in `ColourPickerTest`
 * rather than quietly left out, so the day Material grows it, the test says
 * so. What softens it is the shape: a slider's target is its whole width,
 * which here is the width of the sheet.
 */
@Composable
private fun Channel(
  label: Int,
  value: Float,
  most: Float,
  tag: String,
  onChange: (Float) -> Unit,
) {
  val name = stringResource(label)
  Text(text = name, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
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
 * What a test reaches one [ColourPicker] by.
 *
 * One string rather than seven, and the rest derived from it: three screens
 * open this sheet and each needs its own tags, and seven constants per screen
 * is twenty-one places for a suffix to be spelled differently. The caller
 * names the sheet — `designer:picker`, `settings:accent:picker`,
 * `editor:colour:picker` — and the controls inside it follow.
 */
@JvmInline
value class ColourPickerTags(
  /** The sheet itself. */
  val sheet: String,
) {
  /** The patch showing the colour the three sliders make. */
  val patch: String get() = "$sheet:patch"

  /** **Use it**, which reports the colour. */
  val use: String get() = "$sheet:use"

  /** **Cancel**, which reports nothing. */
  val cancel: String get() = "$sheet:cancel"

  /** Round the wheel, `0..360`. */
  val hue: String get() = "$sheet:hue"

  /** Grey to as deep as the screen goes. */
  val depth: String get() = "$sheet:depth"

  /** Black to as bright as the screen goes. */
  val brightness: String get() = "$sheet:brightness"
}
