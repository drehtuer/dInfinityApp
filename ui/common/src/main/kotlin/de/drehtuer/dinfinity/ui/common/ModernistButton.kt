package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * A button, drawn as the design system's `.btn`
 * (`design/_ds/modernist-…/styles.css`).
 *
 * Square, because nothing in this system has a rounded corner, and labelled in
 * the heading face at weight 800 because that is what `.btn` says. Material's
 * `Button` and `TextButton` cannot be either: their shape token is
 * `CornerFull`, which resolves to `CircleShape` rather than to anything in
 * `MaterialTheme.shapes`, so the app's zero radius never reached them and
 * every filled button in the app was a pill.
 *
 * [kind] is the variant:
 * - [ModernistButtonKind.Primary] — `.btn-primary`: filled in the accent,
 *   labelled in the ground colour. The app's loudest control.
 * - [ModernistButtonKind.Secondary] — `.btn-secondary`: a 1 dp divider-coloured
 *   border, labelled in the text colour.
 * - [ModernistButtonKind.Ghost] — `.btn-ghost`: no box at all, labelled in the
 *   accent.
 *
 * Two of the three carry a **known contrast shortfall that belongs to the
 * palette, not to this file**: a primary button's label measures 3.76:1 on its
 * own accent where body copy wants 4.5:1, and a ghost button's accent label is
 * 3:1 on the light ground. Both are recorded in `docs/TODO.md`, both clear the
 * 3:1 that large text needs, and both would need a token that does not exist
 * yet — the design system has no "accent dark enough to print small words on
 * the ground" that also survives the player choosing another accent.
 *
 * It takes a [String] rather than a slot: every button in the app is a word,
 * and a slot would invite one that is not.
 */
@Composable
fun ModernistButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  kind: ModernistButtonKind = ModernistButtonKind.Secondary,
  enabled: Boolean = true,
) {
  val accent = MaterialTheme.colorScheme.primary
  val ink =
    when (kind) {
      ModernistButtonKind.Primary -> MaterialTheme.colorScheme.onPrimary
      ModernistButtonKind.Secondary -> MaterialTheme.colorScheme.onBackground
      ModernistButtonKind.Ghost -> accent
    }
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .defaultMinSize(minHeight = TOUCH_TARGET)
        .then(
          when (kind) {
            ModernistButtonKind.Primary -> Modifier.background(accent)
            ModernistButtonKind.Secondary -> Modifier.border(HAIRLINE, Ink.divider)
            ModernistButtonKind.Ghost -> Modifier
          },
        ).padding(horizontal = Modernist.x4, vertical = Modernist.x2),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      // `.btn` prints in the heading face at the heading weight, whatever its
      // size. It is the one place a label is set like a heading.
      fontWeight = FontWeight.ExtraBold,
      textAlign = TextAlign.Center,
      color = if (enabled) ink else ink.copy(alpha = DISABLED),
    )
  }
}

/** The `.btn` variants the design system defines. */
enum class ModernistButtonKind {
  Primary,
  Secondary,
  Ghost,
}

/** `.btn:disabled { opacity: 0.45 }`. */
private const val DISABLED = Modernist.DISABLED

/** `.btn-secondary`'s border. */
private val HAIRLINE = Modernist.hairline
