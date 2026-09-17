package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A sheet that comes up from the bottom edge, full width, with its actions on
 * the **left** (`design/dInfinityPhone.dc.html` — every `.dialog-backdrop` in
 * it carries `align-items: end`, every `.dialog` in it `width: 100%`, and
 * every `.dialog-actions` in it `justify-content: flex-start`).
 *
 * The design system's own `.dialog` is a centred card with its actions at the
 * right, and that is what Material's `AlertDialog` is too — which is why nine
 * of them across six modules looked close enough to leave alone. **The phone
 * prototype overrides all three of those things**, and it does it on every
 * sheet it draws, so this is not a screen's decision: the thumb is at the
 * bottom of a phone, and a sheet that opens under it puts its buttons where
 * the hand already is.
 *
 * It is one component rather than nine, because the alternative is nine
 * places for the padding, the scrim and the action alignment to drift — which
 * is the mistake this pass over the UI has been undoing everywhere else.
 *
 * @param title `.dialog-title`: the heading face at the scale's `h4`.
 * @param actions `.dialog-actions`. They wrap, because the prototype's do
 *   (`flex-wrap: wrap` on the longest of them) and because a sheet with four
 *   actions on a narrow phone would otherwise push one off the edge.
 * @param content everything between the title and the actions. A `Column`,
 *   spaced at `--space-3` like the prototype's `.dialog`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Sheet(
  title: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable () -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    // Material's dialog insets itself and caps its own width. A sheet is the
    // full width of the phone and sits on its bottom edge, so it has to be
    // allowed to be both.
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      contentAlignment = Alignment.BottomCenter,
      modifier =
        Modifier
          .fillMaxSize()
          // A tap outside the sheet closes it, which is what the prototype's
          // backdrop does. A raw pointer handler rather than `clickable`, and
          // for two reasons: this is a way out rather than a control, so
          // announcing it as a nameless button in front of everything else
          // would be wrong — and `clickable` **merges its descendants into one
          // semantics node**, which would flatten the whole sheet into a
          // single lump for a screen reader and hide every `Text` in it from
          // the merged tree. A dialog is dismissible by Back regardless, so
          // nothing is lost by not being a button.
          .pointerInput(Unit) { detectTapGestures { onDismiss() } }
          .background(SCRIM),
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(Modernist.x3),
        modifier =
          modifier
            .fillMaxWidth()
            // The sheet swallows taps rather than letting them reach the
            // backdrop behind it, or every tap inside would close it. Again
            // not `clickable`, for the merging reason above.
            .pointerInput(Unit) { detectTapGestures { } }
            .background(MaterialTheme.colorScheme.surface)
            .padding(Modernist.x4),
      ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        // **The content gives way, not the actions.** A sheet is anchored to
        // the bottom edge and cannot grow past the top of the screen, so
        // something has to yield when a sheet holds a long list — a refusal
        // report is easily a dozen lines. Without this the content took the
        // whole height and the buttons were measured at zero, which left a
        // sheet whose only way out was a tap on the backdrop. `weight` with
        // `fill = false` gives the content what is left after the title and
        // the actions have had theirs, and no more; the scroll is what it does
        // with the overflow. Content that would bring its own scroll should
        // not — two scrolls in one direction fight each other.
        Column(
          verticalArrangement = Arrangement.spacedBy(Modernist.x3),
          modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
          content = content,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
          verticalArrangement = Arrangement.spacedBy(Modernist.x2),
        ) {
          actions()
        }
      }
    }
  }
}

/**
 * `--color-neutral-900` at 50 %, which is what the prototype's
 * `.dialog-backdrop` mixes.
 *
 * A step of the ramp rather than the theme's ink, and deliberately: `-900` is
 * one of the steps the dark ground does **not** remap, so a scrim is the same
 * near-black over a light page and a dark one. A scrim made from the ink would
 * be a *white* veil in dark mode, which is not a scrim.
 */
private val SCRIM = Modernist.Neutral.v900.copy(alpha = 0.5f)
