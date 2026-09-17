package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A button whose label is a picture rather than a word
 * (`.btn-icon` in `design/_ds/modernist-…/styles.css`: `width: 36px;
 * height: 36px; padding: 0`).
 *
 * [ModernistButton] takes a `String`, deliberately — every button in this app
 * that says something says it in words, and a slot would invite one that does
 * not. But a handful genuinely do not have words: the export arrow, the **…**
 * that opens a group's actions. The design system has a component for exactly
 * those, and this is it.
 *
 * **Why it is not a `TextButton` with a glyph in it**, which is what these
 * were. Material's is a pill, as every Material button is; and a glyph pushed
 * through [ModernistButton] would come out in the heading face at `ExtraBold`
 * in the accent with `x4` of padding either side, which is a sentence's
 * styling applied to a picture.
 *
 * **It is one node to a screen reader**, and that is the point of the
 * `semantics` here rather than on the glyph inside. A picture has no words, so
 * the button carries the name and merges what is under it — otherwise a
 * listener hears the glyph read out *and* then the description, and a test
 * reaching for the button on the merged tree stops finding it.
 *
 * @param contentDescription what pressing it does. Not optional: a button
 *   with no words and no name is a button nobody can use.
 */
@Composable
fun ModernistIconButton(
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
        .clickable(role = Role.Button, onClick = onClick)
        // `.btn-icon` is 36 px, and Android's smallest pressable thing is 48.
        // The box is the design's size; the target around it is the platform's.
        .defaultMinSize(minWidth = TOUCH_TARGET, minHeight = TOUCH_TARGET),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ICON_BUTTON)) { content() }
  }
}

/** `.btn-icon`'s `width: 36px; height: 36px`. */
private val ICON_BUTTON = 36.dp
