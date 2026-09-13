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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Rounding

/**
 * Home: the tray, the formula and the total
 * (`design/dInfinity.dc.html`, options 1a–1j).
 *
 * Stateless over [RollPresenter], which is stateless over [RollMachine] — so
 * what this file contains is layout and nothing that decides anything. What a
 * formula means, whether it fits, what the dice came to and what that adds up
 * to are all settled before a pixel is placed.
 *
 * Not all of the screen's pieces yet: the squiggle under a bad formula, the
 * power-saving path and the first-launch state are still to come
 * (`docs/TODO.md`, Step 4.1).
 */
@Composable
fun RollScreen(
  presenter: RollPresenter,
  modifier: Modifier = Modifier,
  firstLaunch: Boolean = false,
  onWelcomeSeen: () -> Unit = {},
  onSeeTheOdds: (formula: String, total: Long?) -> Unit = { _, _ -> },
) {
  val state = presenter.state
  ShakeToRoll(presenter)
  KeepTheScreenAwake()
  LockTheOrientation()

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag(RollTestTags.SCREEN),
  ) {
    // No surface at all in power-saving mode, rather than one nothing draws
    // to: a surface is a buffer the compositor keeps, and the claim that mode
    // makes is that none of it exists (`docs/architecture.md`, decision 38).
    if (presenter.draws) {
      DiceTray(driver = presenter.tray, geometry = presenter.geometry, modifier = Modifier.fillMaxSize())
    }

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
      Outcome(state, formula = presenter.text, onRound = presenter::round, onSuggestion = presenter::type)
      // The odds for the formula in the field, with the throw that just landed
      // marked on them (`design/dInfinity.dc.html`, option 7a). Offered for a
      // throw the table refuses too: that is exactly when "what would it have
      // been" is the only answer there is (`docs/probability.md`).
      if (state is RollState.Ready || state is RollState.TooMany || state is RollState.Settled) {
        SeeTheOdds(
          onClick = { onSeeTheOdds(presenter.text, (state as? RollState.Settled)?.result?.total) },
        )
      }
      PickerRow(
        dice = presenter.pickable,
        counts = presenter.counts,
        onAdd = presenter::add,
        onRemove = presenter::remove,
      )
      Formula(
        text = presenter.text,
        wrong = state is RollState.Invalid || state is RollState.TooMany,
        onChange = presenter::type,
      )
      ThrowButton(
        enabled = state is RollState.Ready || state is RollState.Settled,
        settled = state is RollState.Settled,
        onRoll = { presenter.roll() },
      )
    }

    if (firstLaunch) FirstLaunch(presenter, onWelcomeSeen)
  }
}

/**
 * The first-launch screen, over the tray, until it is pressed past
 * (`design/dInfinity.dc.html`, option 9a).
 *
 * Dismissed here as well as remembered on disk, so the screen changes the
 * moment a button is pressed rather than when a write comes back — and it
 * survives a rotation, because a welcome that reappeared when the phone turned
 * would be a welcome that looked broken.
 *
 * Its d20 is thrown for real: `1d20` is typed into the field and the roll is
 * asked for, which is what the player would have done. There is no
 * demonstration path and no canned number (`docs/architecture.md`, goal 1).
 */
@Composable
private fun FirstLaunch(
  presenter: RollPresenter,
  onWelcomeSeen: () -> Unit,
) {
  var welcomed by rememberSaveable { mutableStateOf(false) }
  if (welcomed) return
  Welcome(
    sets = presenter.sets,
    onRollNow = {
      welcomed = true
      onWelcomeSeen()
      presenter.type(FIRST_ROLL)
      presenter.roll()
    },
    onDismiss = {
      welcomed = true
      onWelcomeSeen()
    },
  )
}

/** What the first-launch screen offers to throw. One die, and the famous one. */
private const val FIRST_ROLL = "1d20"

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
private fun Outcome(
  state: RollState,
  formula: String,
  onRound: (Rounding) -> Unit,
  onSuggestion: (String) -> Unit,
) {
  when (state) {
    is RollState.Settled ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = state.result.total.toString(),
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.testTag(RollTestTags.TOTAL),
        )
        ResultSheet(result = state.result, divides = state.divides, onRound = onRound)
      }

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

    // The formula again, with a squiggle under the part that is wrong, rather
    // than a sentence about it (design options 6f and 9c).
    is RollState.Invalid ->
      FormulaError(formula = formula, error = state.error, onSuggestion = onSuggestion)

    // Not a blank: a tray with nothing on it and a button that does nothing is
    // a screen with no way in, and shaking is the part nobody would guess
    // (`design/dInfinity.dc.html`, option 9a).
    RollState.Empty ->
      Message(
        text = stringResource(R.string.roll_hint_empty),
        colour = MaterialTheme.colorScheme.onSurfaceVariant,
        tag = RollTestTags.HINT,
      )

    is RollState.Ready ->
      Message(
        text = stringResource(R.string.roll_hint_ready),
        colour = MaterialTheme.colorScheme.onSurfaceVariant,
        tag = RollTestTags.HINT,
      )
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

/** The way to the outcome graph, for whatever is in the field right now. */
@Composable
private fun SeeTheOdds(onClick: () -> Unit) {
  TextButton(
    onClick = onClick,
    modifier = Modifier.testTag(RollTestTags.ODDS),
  ) {
    Text(stringResource(R.string.roll_see_the_odds))
  }
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
  // first mid-flight, which is not what a second tap means. A roll that has
  // landed can be thrown again, and that is one press: the presenter puts the
  // total away itself.
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

  /** The one-tap fix, shown only when the mistake has an obvious reading. */
  const val SUGGESTION: String = "roll:invalid:suggestion"

  /** What to do next, when there is no result and nothing wrong. */
  const val HINT: String = "roll:hint"

  /** The way to the outcome graph (design option 7a). */
  const val ODDS: String = "roll:odds"

  /** The first-launch screen and its two ways out (design option 9a). */
  const val WELCOME: String = "roll:welcome"
  const val WELCOME_SETS: String = "roll:welcome:sets"
  const val WELCOME_ROLL: String = "roll:welcome:roll"
  const val WELCOME_DISMISS: String = "roll:welcome:dismiss"

  /** The dice picker row, and one die on it (design option 1h). */
  const val PICKER: String = "roll:picker"

  fun pickerDie(notation: String): String = "roll:picker:$notation"

  fun pickerCount(notation: String): String = "roll:picker:$notation:count"

  /** The Down / Nearest / Up control, shown only for a formula that divides. */
  const val ROUNDING: String = "roll:sheet:rounding"

  fun roundingOf(rounding: de.drehtuer.dinfinity.core.model.Rounding): String = "roll:sheet:rounding:${rounding.id}"

  /** The breakdown under the total (`design/dInfinity.dc.html`, option 1f). */
  const val SHEET: String = "roll:sheet"
  const val SHEET_FORMULA: String = "roll:sheet:formula"

  /** One group's subtotal, and one die as it landed. */
  fun subtotalOf(groupId: Int): String = "roll:sheet:subtotal:$groupId"

  fun fallbackOf(groupId: Int): String = "roll:sheet:fellback:$groupId"

  fun dieAt(instanceIndex: Int): String = "roll:sheet:die:$instanceIndex"
}
