package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Home: the tray, the formula and the total
 * (`design/dInfinity.dc.html`, options 1a–1j).
 *
 * Stateless over [RollPresenter], which is stateless over [RollMachine] — so
 * what this file contains is layout and nothing that decides anything. What a
 * formula means, whether it fits, what the dice came to and what that adds up
 * to are all settled before a pixel is placed.
 *
 * This is the first of the screen's pieces, not all of them: the dice picker
 * row, the full result sheet, the rounding control and the first-launch state
 * are still to come (`docs/TODO.md`, Step 4.1).
 */
@Composable
fun RollScreen(
  presenter: RollPresenter,
  modifier: Modifier = Modifier,
) {
  val state = presenter.state
  ShakeToRoll(presenter)
  KeepTheScreenAwake()

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag(RollTestTags.SCREEN),
  ) {
    DiceTray(driver = presenter.tray, modifier = Modifier.fillMaxSize())

    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .safeDrawingPadding()
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Outcome(state)
      Formula(
        text = presenter.text,
        wrong = state is RollState.Invalid || state is RollState.TooMany,
        onChange = presenter::type,
      )
      ThrowButton(
        enabled = state is RollState.Ready || state is RollState.Settled,
        settled = state is RollState.Settled,
        onRoll = { if (state is RollState.Settled) presenter.clear() else presenter.roll() },
      )
    }
  }
}

/**
 * Holds the screen on while the tray is up.
 *
 * A dice tray is something a table looks at between turns, and a phone that
 * blanks after fifteen seconds of nobody touching it is a phone that has to be
 * poked every time somebody wants to read the roll. It also took the surface
 * away with it, which is a thing the tray survives now but need not be asked
 * to (`docs/TODO.md`, Step 4.1).
 *
 * On the view rather than on the window's flags, so it is undone by leaving
 * the screen and not by remembering to undo it.
 */
@Composable
private fun KeepTheScreenAwake() {
  val view = LocalView.current
  DisposableEffect(view) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }
  }
}

/** The total, or why there is not one. */
@Composable
private fun Outcome(state: RollState) {
  when (state) {
    is RollState.Settled ->
      Text(
        text = state.result.total.toString(),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag(RollTestTags.TOTAL),
      )

    is RollState.Rolling ->
      Message(
        text = stringResource(R.string.roll_rolling),
        colour = MaterialTheme.colorScheme.onBackground,
        tag = RollTestTags.ROLLING,
      )

    // A refusal is a sentence, not a silence: it says how many dice were asked
    // for and how many would fit (`docs/tables.md`).
    is RollState.TooMany ->
      Message(
        text = state.reason,
        colour = MaterialTheme.colorScheme.error,
        tag = RollTestTags.REFUSED,
      )

    is RollState.Invalid ->
      Message(
        text = state.error.message,
        colour = MaterialTheme.colorScheme.error,
        tag = RollTestTags.INVALID,
      )

    RollState.Empty, is RollState.Ready -> Unit
  }
}

@Composable
private fun Message(
  text: String,
  colour: androidx.compose.ui.graphics.Color,
  tag: String,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = colour,
    textAlign = TextAlign.Center,
    modifier = Modifier.testTag(tag),
  )
}

/**
 * Takes what it draws rather than the presenter that holds it.
 *
 * Not style for its own sake: a composable handed a whole presenter cannot be
 * skipped on recomposition, because Compose has no way to know what changed
 * inside it, so it re-runs on every keystroke and carries the generated code
 * that decides so. Handed a string and a lambda, it skips when the string has
 * not moved.
 */
@Composable
private fun Formula(
  text: String,
  wrong: Boolean,
  onChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = text,
    onValueChange = onChange,
    isError = wrong,
    singleLine = true,
    label = { Text(stringResource(R.string.roll_formula_label)) },
    placeholder = { Text(stringResource(R.string.roll_formula_hint)) },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    modifier =
      Modifier
        .fillMaxWidth()
        .testTag(RollTestTags.FORMULA),
  )
}

/** The same: values in, one lambda out, so it skips when nothing has moved. */
@Composable
private fun ThrowButton(
  enabled: Boolean,
  settled: Boolean,
  onRoll: () -> Unit,
) {
  // Rolling is blocked while the formula is invalid or the table is too small,
  // and while the dice are still in the air — a second throw would replace the
  // first mid-flight, which is not what a second tap means.
  Button(
    onClick = onRoll,
    enabled = enabled,
    modifier =
      Modifier
        .fillMaxWidth()
        .testTag(RollTestTags.THROW),
  ) {
    Text(stringResource(if (settled) R.string.roll_again else R.string.roll_throw))
  }
}

/** What the tests reach the screen by. */
object RollTestTags {
  const val SCREEN: String = "roll:screen"
  const val TRAY: String = "roll:tray"
  const val FORMULA: String = "roll:formula"
  const val THROW: String = "roll:throw"
  const val TOTAL: String = "roll:total"
  const val ROLLING: String = "roll:rolling"
  const val REFUSED: String = "roll:refused"
  const val INVALID: String = "roll:invalid"
}
