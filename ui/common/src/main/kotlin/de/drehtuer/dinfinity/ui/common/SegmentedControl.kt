package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * One choice out of a few, drawn as the design system's `.seg` / `.seg-opt`
 * (`design/_ds/modernist-…/styles.css`; every row of the prototype's Settings
 * screen, `design/dInfinity.dc.html` options 1y and 2d).
 *
 * A single square box with a 1 dp border, its options butted together and
 * divided by hairlines, the chosen one filled in the accent and printed in the
 * ground colour. Not a row of pills: `FilterChip` — what the settings screen
 * used — is a rounded, spaced, tick-prefixed Material component, and this
 * system has no rounded corners, no ticks and no gaps between the options of
 * one control.
 *
 * **Two modes, and the difference is who owns the tap.**
 * - With [onSelect], each option is its own target and reports itself. This is
 *   a picker: Appearance, Division rounding.
 * - Without it, the control is a **read-out** — it says which of the two an
 *   enclosing row is currently on, and that row carries the click and the
 *   semantics. This is how a boolean setting is drawn: the design has no
 *   switch in it, and Material's `Switch` is a fully round pill with a
 *   circular thumb, which is the most rounded thing in a system whose radius
 *   is zero. The row stays `toggleable`, so what a tap does and what TalkBack
 *   says are exactly what they were.
 *
 * The 1 dp border is `outline`, the design system's `--color-divider`. That
 * token measures 2.41:1 on the light ground where a control's own boundary
 * wants 3:1 — a known shortfall recorded in `docs/TODO.md`, and one no token
 * in the system currently fixes. The fill and the label carry the state as
 * well, so the boundary is not the only thing saying which option is chosen.
 *
 * @param label what to print on an option. `@Composable` because the words are
 *   string resources and a feature module should not have to resolve them
 *   before calling.
 * @param tagOf a test handle per option, so a screen keeps the tags its tests
 *   already point at.
 */
@Composable
fun <T> SegmentedControl(
  options: List<T>,
  selected: T,
  label: @Composable (T) -> String,
  modifier: Modifier = Modifier,
  onSelect: ((T) -> Unit)? = null,
  tagOf: ((T) -> String)? = null,
) {
  Row(
    modifier =
      modifier
        .then(if (onSelect == null) Modifier.clearAndSetSemantics { } else Modifier.selectableGroup())
        .height(IntrinsicSize.Min)
        .border(HAIRLINE, MaterialTheme.colorScheme.outline),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    options.forEachIndexed { index, option ->
      if (index > 0) {
        Box(
          modifier =
            Modifier
              .width(HAIRLINE)
              .fillMaxHeight()
              .background(MaterialTheme.colorScheme.outline),
        )
      }
      Option(
        text = label(option),
        isSelected = option == selected,
        onSelect = onSelect?.let { select -> { select(option) } },
        tag = tagOf?.invoke(option),
      )
    }
  }
}

@Composable
private fun Option(
  text: String,
  isSelected: Boolean,
  onSelect: (() -> Unit)?,
  tag: String?,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .then(
          if (onSelect == null) {
            Modifier
          } else {
            Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
          },
        ).then(if (tag == null) Modifier else Modifier.testTag(tag))
        // Android's own minimum, which `FilterChip` used to supply and a bare
        // `selectable` does not.
        .defaultMinSize(minHeight = TOUCH_TARGET)
        .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
        .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
    )
  }
}

/** `.seg`'s own border, and the line between two options. */
private val HAIRLINE = 1.dp

/** Android's minimum touch target. */
private val TOUCH_TARGET = 48.dp
