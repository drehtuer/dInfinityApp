package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

/**
 * One option out of many, drawn the way the prototype draws one: **a square
 * box with a 1 dp border that inverts when it is chosen**
 * (`design/dInfinityPhone.dc.html` — `iconOptions`, and every group, table and
 * mark picker on the editor screens).
 *
 * This is the wrapping-set case, and [SegmentedControl] is the other one. A
 * segmented control is a single box with its options butted together, right
 * for two or three fixed choices side by side; this is for a set that wraps
 * and whose size the screen does not know — twelve marks, however many groups
 * somebody has made, one table per installed look.
 *
 * It exists because Material's `FilterChip` is none of those things. A chip is
 * a **pill** with a leading tick slot and a gap to the next one, in a system
 * whose radius is zero and which butts or evenly spaces its options. There
 * were helpers before this that put the right colours on a chip; they fixed
 * the palette and left the shape, so every picker in the app still read as
 * Material with a repaint. This replaced them, and they are gone.
 *
 * **Which fill, and why there are two.** [OptionFill.Accent] is the ordinary
 * one: the chosen option carries the accent, as `.seg-opt` does.
 * [OptionFill.Ink] inverts to the **text** colour instead, and is for a set
 * whose options already carry the accent in their own content — the
 * prototype's `iconOptions` do exactly this (`bg:'var(--color-text)'`,
 * `color:'var(--color-bg)'`), because a red square behind a red mark would be
 * the two saying the same thing over each other.
 *
 * The border follows the fill, as the prototype's does: divider while the
 * option is not chosen, the fill colour once it is. So the edge says the same
 * thing the fill does, which matters where a screen is read in bright sun.
 *
 * @param square draws a fixed [TOUCH_TARGET] box rather than one that fits its
 *   text. For a set of single characters — the marks — where boxes of
 *   different widths would read as a ransom note. The prototype draws these at
 *   42 px; 48 dp is Android's minimum for something pressable, and a box that
 *   is not the size of its own touch target is a worse divergence than six
 *   pixels.
 * @param contentDescription what the option *is*, where its text does not say
 *   — a mark is an emoji, and "🎲" is not a name.
 * @param role what a screen reader calls it. A set where exactly one option is
 *   always chosen is a radio group, which is the default; a set a tap can turn
 *   *off* again — the marks, where tapping the chosen one clears it — is a set
 *   of checkboxes, and saying "radio button" of one would promise a choice
 *   that cannot be unmade.
 * @param enabled false for an option this die or this drawing has no use for —
 *   the paste turn on a kite, whose cells have no turn. Dead rather than
 *   absent, so a set of options does not move about under the finger choosing
 *   from it, and dimmed by `.btn:disabled`'s own [Modernist.DISABLED].
 */
@Composable
fun OptionBox(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  fill: OptionFill = OptionFill.Accent,
  square: Boolean = false,
  contentDescription: String? = null,
  role: Role = Role.RadioButton,
  enabled: Boolean = true,
) {
  OptionBox(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    fill = fill,
    square = square,
    contentDescription = contentDescription,
    role = role,
    enabled = enabled,
  ) { ink ->
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      textAlign = TextAlign.Center,
      color = ink,
    )
  }
}

/**
 * The same option with a **picture** in it rather than a word.
 *
 * The one thing that changes is what is inside the box: the border, the
 * inversion, the touch target and the semantics are the text form's, because
 * an option drawn as an icon is the same control and must read as the same
 * control. The ink the content is handed is the one the box has decided on —
 * the ground once it is chosen, the ink until then — so a glyph inverts with
 * its box instead of drawing its own conclusion about the theme.
 *
 * A [contentDescription] is not optional here. A picture has no words, and the
 * box is the node a screen reader lands on ([Role.RadioButton] with a name).
 */
@Composable
fun OptionBox(
  contentDescription: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  fill: OptionFill = OptionFill.Accent,
  enabled: Boolean = true,
  role: Role = Role.RadioButton,
  content: @Composable (Color) -> Unit,
) {
  OptionBox(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    fill = fill,
    square = true,
    contentDescription = contentDescription,
    role = role,
    enabled = enabled,
    content = content,
  )
}

/**
 * What both forms above are: a box that inverts, with something in the middle.
 *
 * `@NonRestartableComposable` because it can never usefully skip. It is called
 * only by the two overloads above, which pass on the arguments they were just
 * handed — so it is reached exactly when one of *them* decided to recompose,
 * and the lettered form hands it a fresh lambda every time regardless. The
 * annotation drops the skip machinery the compiler would otherwise emit for
 * nine parameters that are already known to have changed.
 */
@Composable
@NonRestartableComposable
private fun OptionBox(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier,
  fill: OptionFill,
  square: Boolean,
  contentDescription: String?,
  role: Role,
  enabled: Boolean,
  content: @Composable (Color) -> Unit,
) {
  val chosen =
    when (fill) {
      OptionFill.Accent -> MaterialTheme.colorScheme.primary
      OptionFill.Ink -> MaterialTheme.colorScheme.onBackground
    }
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
        .then(
          if (contentDescription == null) {
            Modifier
          } else {
            Modifier.semantics { this.contentDescription = contentDescription }
          },
        ).alpha(if (enabled) 1f else Modernist.DISABLED)
        .then(if (square) Modifier.size(TOUCH_TARGET) else Modifier.defaultMinSize(minHeight = TOUCH_TARGET))
        .border(Modernist.hairline, if (selected) chosen else Ink.divider)
        .then(if (selected) Modifier.background(chosen) else Modifier)
        .then(if (square) Modifier else Modifier.padding(horizontal = Modernist.x3, vertical = Modernist.x2)),
  ) {
    content(if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
  }
}

/** What a chosen option is filled with. */
enum class OptionFill {
  /** `.seg-opt`: the accent, which is what an ordinary choice is marked in. */
  Accent,

  /** The ink, for a set whose options already print in the accent themselves. */
  Ink,
}
