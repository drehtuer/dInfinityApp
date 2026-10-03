package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
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

/** What tests reach the shared formula field by. */
object FormulaTestTags {
  const val FIELD: String = "formula:field"
  const val ERROR: String = "formula:error"
  const val SUGGESTION: String = "formula:error:suggestion"
}
