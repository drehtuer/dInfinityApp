package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.notation.NotationError

/**
 * A formula being typed, with what is wrong with it under it
 * (`design/dInfinity.dc.html`, options 2a, 6f and 9c).
 *
 * Three screens want this and none of them should own it: the tray, the saved
 * roll editor and the outcome graph all take a formula, validate it on every
 * keystroke and have to say the same thing about the same mistake. Two copies
 * of "the same thing" is one copy too many — the second would disagree with
 * the first eventually, and the disagreement would be about whether somebody's
 * formula is valid.
 *
 * It decides nothing. Whether the formula reads is the caller's to work out,
 * because each screen does something different about it: the tray disables a
 * button, the editor disables a save, the graph draws nothing.
 *
 * @param error what is wrong, or `null` when nothing is. Carries the range to
 *   put the squiggle under.
 * @param onSuggestion taking the parser's suggested correction, which arrives
 *   as text to be typed like any other.
 * @param onDone what else the keyboard's action key does, after putting the
 *   keyboard away. **The key says Done on every screen and never throws** —
 *   a shake is the only way to start a roll (`docs/architecture.md`,
 *   decision 66). The tray's drawer uses it to close itself; the editor and the
 *   graph have nothing more to do.
 * @param takeFocus true for a field that has just appeared because somebody
 *   asked for it, so the keyboard comes up without a second tap.
 *
 * **A field with something in it carries a ×** at its trailing end, which
 * empties it in one tap and leaves the cursor in it, so the next formula can
 * be typed straight away — a phone has no select-all worth the name, and
 * holding backspace through `8d6 [Fire] + 2d4kh1` is the alternative. It is
 * not a second way to empty a formula: it hands `""` to [onChange], exactly
 * what the last backspace would, so every screen does with it whatever it
 * already does with an empty field. An empty field has no ×, because there is
 * nothing for it to do.
 */
@Composable
fun FormulaField(
  text: String,
  onChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  hint: String? = null,
  error: NotationError? = null,
  wrong: Boolean = error != null,
  onSuggestion: (String) -> Unit = onChange,
  onDone: () -> Unit = {},
  takeFocus: Boolean = false,
) {
  val focus = remember { FocusRequester() }
  // Once, when it appears. A field that grabbed the focus on every
  // recomposition would take it back from whatever the player moved to.
  LaunchedEffect(takeFocus) { if (takeFocus) focus.requestFocus() }
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    OutlinedTextField(
      value = text,
      onValueChange = onChange,
      isError = wrong,
      singleLine = true,
      label = label?.let { { Text(it) } },
      placeholder = hint?.let { { Text(it) } },
      trailingIcon =
        if (text.isEmpty()) {
          null
        } else {
          {
            ClearButton(
              onClear = {
                onChange("")
                // The point of emptying it is to type something else, so the
                // keyboard stays — or comes up, on a field nobody had tapped.
                focus.requestFocus()
              },
            )
          }
        },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      // The default first — the keyboard goes away whatever the caller does
      // — and then whatever the caller does.
      keyboardActions =
        KeyboardActions(
          onDone = {
            defaultKeyboardAction(ImeAction.Done)
            onDone()
          },
        ),
      modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(FormulaTestTags.FIELD),
    )
    if (error != null) FormulaError(formula = text, error = error, onSuggestion = onSuggestion)
  }
}

/**
 * The ×, `#ic-x` from the prototype's sprite, drawn rather than fetched — the
 * app ships no icon set.
 *
 * A button with no words, so it is a [ModernistIconButton]: one node to a
 * screen reader carrying what it does, and a 48 dp target round an 18 dp
 * drawing.
 */
@Composable
private fun ClearButton(onClear: () -> Unit) {
  val ink = LocalContentColor.current
  ModernistIconButton(
    contentDescription = stringResource(R.string.formula_clear),
    onClick = onClear,
    modifier = Modifier.testTag(FormulaTestTags.CLEAR),
  ) {
    Canvas(modifier = Modifier.size(CROSS)) { cross(ink) }
  }
}

/** `M18 6 6 18M6 6l12 12`, as shares of its 24-unit box. */
private fun DrawScope.cross(ink: Color) {
  val near = size.width * NEAR
  val far = size.width * FAR
  val path =
    Path().apply {
      moveTo(far, near)
      lineTo(near, far)
      moveTo(near, near)
      lineTo(far, far)
    }
  drawPath(path = path, color = ink, style = Stroke(width = Modernist.rule.toPx(), cap = StrokeCap.Round))
}

/** The prototype draws its × at 18 px. */
private val CROSS = 18.dp
private const val NEAR = 6f / 24f
private const val FAR = 18f / 24f

/** What tests reach the shared formula field by. */
object FormulaTestTags {
  const val FIELD: String = "formula:field"
  const val ERROR: String = "formula:error"
  const val SUGGESTION: String = "formula:error:suggestion"
  const val CLEAR: String = "formula:clear"
}
