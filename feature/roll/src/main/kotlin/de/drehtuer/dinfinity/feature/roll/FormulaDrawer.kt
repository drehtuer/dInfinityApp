package de.drehtuer.dinfinity.feature.roll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.ui.common.FormulaField
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.Plate
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET

/**
 * The formula, put away at the side of the table until it is asked for
 * (`design/dInfinity.dc.html`, option 2a; `docs/physics-and-rendering.md`,
 * "What is drawn over the table").
 *
 * **The formula used to be on the felt.** It sat under the menu button as a
 * line of type with a dashed rule under it, and it was on screen in every
 * state whether or not anybody was editing it — one more block over a table
 * that, seen straight down, is the only thing on the screen worth looking at.
 * The second device session asked for it to be hidden by default and to come
 * **in from the side** rather than down from the top, and that is what this
 * is.
 *
 * Shut, it is a tab at the right-hand edge: the word `Formula` and a chevron
 * pointing inwards, on a plate, mirroring `Dice` at the other end of the same
 * top corner. That is the whole of what stays on the table — the formula
 * itself is not drawn at all.
 *
 * **The tab is the one thing that still says something about the formula
 * without printing it**: it turns red when what is typed does not read, so a
 * mistake is not hidden behind a door nobody has a reason to open. *What* is
 * wrong is said inside, under the squiggle, because that is where it can be
 * acted on — which is the rule the line kept too.
 *
 * Open, it slides in horizontally and is the field, the squiggle, the one-tap
 * fix and the keyboard, with the chevron turned round to push it back out.
 *
 * @param open whether the drawer is in. Hoisted, because the dice pull-down at
 *   the other end of the row may not be open at the same time and that is the
 *   screen's rule rather than either control's.
 * @param onSubmit the keyboard's action key: it shuts the drawer and throws,
 *   so what the dice land on is not behind a keyboard.
 */
@Composable
internal fun FormulaDrawer(
  text: String,
  onChange: (String) -> Unit,
  open: Boolean,
  onOpen: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  error: NotationError? = null,
  wrong: Boolean = error != null,
  onSubmit: () -> Unit = {},
) {
  Box(modifier = modifier.fillMaxWidth()) {
    // Both halves come and go the same way — `it` is the width of the thing
    // sliding, so each one leaves and returns through the right-hand edge.
    // No fade and no expansion: the fault being fixed is a menu that dropped
    // down, and something that grew into place would be the same arrival
    // under a different name.
    AnimatedVisibility(
      visible = !open,
      enter = slideInHorizontally { it },
      exit = slideOutHorizontally { it },
      modifier = Modifier.align(Alignment.TopEnd),
    ) {
      Plate { FormulaHandle(open = false, wrong = wrong, onToggle = { onOpen(true) }) }
    }
    AnimatedVisibility(
      visible = open,
      enter = slideInHorizontally { it },
      exit = slideOutHorizontally { it },
      modifier = Modifier.fillMaxWidth(),
    ) {
      // The editor fills the width, because a field is a thing to type into
      // and a field the width of what was last typed is a field that jumps.
      Plate(modifier = Modifier.fillMaxWidth().testTag(RollTestTags.FORMULA_DRAWER)) {
        Column(verticalArrangement = Arrangement.spacedBy(Modernist.x1)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FormulaHandle(open = true, wrong = wrong, onToggle = { onOpen(false) })
          }
          FormulaField(
            text = text,
            onChange = onChange,
            label = stringResource(R.string.roll_formula_label),
            hint = stringResource(R.string.roll_formula_hint),
            error = error,
            wrong = wrong,
            // Enter rolls, and **stays** now that the Roll button has gone. It
            // is not a button: it is what the key on a keyboard already means,
            // and a player typing with a hardware keyboard is a player whose
            // other hand is not free to shake the phone (`docs/architecture.md`,
            // "Accessibility").
            onSubmit = onSubmit,
            takeFocus = true,
          )
        }
      }
    }
  }
}

/**
 * The tab, and the way back out of the drawer.
 *
 * One node to a screen reader carrying what it is, what a press will do and
 * what is behind it — a chevron is a drawing and says none of that, and
 * neither does a red one (`docs/architecture.md`, "Accessibility").
 *
 * The word is drawn only on the shut tab. Inside the drawer the field is
 * already labelled `Formula`, and a heading repeating its own label is a
 * heading that says nothing.
 */
@Composable
private fun FormulaHandle(
  open: Boolean,
  wrong: Boolean,
  onToggle: () -> Unit,
) {
  val name = stringResource(R.string.roll_formula_label)
  val act = stringResource(if (open) R.string.roll_formula_close else R.string.roll_formula_open)
  val where =
    stringResource(
      when {
        open -> R.string.roll_formula_is_open
        wrong -> R.string.roll_formula_is_shut_wrong
        else -> R.string.roll_formula_is_shut
      },
    )
  // The design system has one red and the theme maps `error` onto it, so a
  // formula that does not read marks its door in the accent (`theme/Theme.kt`).
  val ink = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
  Row(
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier
        .sizeIn(minHeight = TOUCH_TARGET, minWidth = TOUCH_TARGET)
        .clickable(onClickLabel = act, role = Role.Button, onClick = onToggle)
        .testTag(RollTestTags.FORMULA_TAB)
        .semantics(mergeDescendants = true) {
          contentDescription = name
          stateDescription = where
        },
  ) {
    if (!open) {
      Text(
        // The heading face, like `Dice` opposite it: these two are the titles
        // of the table rather than captions on it.
        text = name,
        style = MaterialTheme.typography.titleLarge,
        color = ink,
      )
    }
    Canvas(modifier = Modifier.size(CHEVRON)) { caret(ink, pointingIn = !open) }
  }
}

/**
 * `#ic-left` / `#ic-right`, drawn rather than fetched — the app ships no icon
 * set and one glyph is not a reason to start one.
 *
 * The prototype's chevron turned a quarter, as shares of its box rather than
 * as the 24-unit viewBox it is written in: the same drawing at any size, and
 * no number here that is a pixel. It points **in** while the drawer is out,
 * which is the direction a press moves the panel rather than the direction
 * the panel is now.
 */
private fun DrawScope.caret(
  ink: Color,
  pointingIn: Boolean,
) {
  val x = { share: Float -> if (pointingIn) size.width * share else size.width * (1f - share) }
  val y = { share: Float -> size.height * share }
  val path =
    Path().apply {
      moveTo(x(SHOULDER), y(NEAR))
      lineTo(x(POINT), y(MIDDLE))
      lineTo(x(SHOULDER), y(FAR))
    }
  drawPath(
    path = path,
    color = ink,
    style = Stroke(width = Modernist.rule.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

/** The prototype draws its disclosures at 18 px. */
private val CHEVRON = 18.dp

// `m9 6 -6 6 6 6`, as shares of a 24-unit box.
private const val SHOULDER = 15f / 24f
private const val POINT = 9f / 24f
private const val NEAR = 6f / 24f
private const val MIDDLE = 12f / 24f
private const val FAR = 18f / 24f
