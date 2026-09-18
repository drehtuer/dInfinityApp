package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A line of words that appears over a screen, says one thing and takes itself
 * away again (`design/dInfinityPhone.dc.html`, the back-arming note).
 *
 * **Inverted**: the ground is `--color-text` and the letters are `--color-bg`,
 * which is the one place in this system where that happens. A toast is not
 * part of the page — it is over it, briefly — and inverting it is how a system
 * with no cards, no radius and one accent says so without borrowing the accent
 * for something that is not a choice.
 *
 * It **cannot be pressed and does not take focus**: everything it says is
 * already true of a control the player is holding. What it does take is a
 * polite live region, so a screen reader reads it when it arrives rather than
 * when somebody swipes onto it — a notice nobody is told about is a notice
 * that did not happen (`docs/architecture.md`, "Accessibility").
 *
 * @param text what it says. A resource at the call site, like every other
 *   string a person reads.
 * @param onDismissed called once [dismissAfter] has passed, so the caller can
 *   drop the state that put it here. The toast does not remember whether it
 *   has been shown; the screen owns that.
 * @param dismissAfter how long the words stay. The design's 2.6 s, which is
 *   long enough to read six words and short enough not to sit over a tray.
 */
@Composable
fun ModernistToast(
  text: String,
  onDismissed: () -> Unit,
  modifier: Modifier = Modifier,
  dismissAfter: Duration = DISMISS_AFTER,
) {
  // Keyed on the words: the same toast raised again with something new to say
  // starts its time over, and one raised again with the same words does not
  // get a second life out of a recomposition.
  LaunchedEffect(text, dismissAfter) {
    delay(dismissAfter)
    onDismissed()
  }
  Box(
    modifier =
      modifier
        .shadow(Modernist.Shadow.md, Modernist.square)
        .background(MaterialTheme.colorScheme.onBackground)
        .padding(horizontal = SIDE, vertical = Modernist.x2)
        // One node, not a box with a label inside it: a screen reader should
        // read a notice as the one thing it is.
        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        .testTag(ToastTestTags.TOAST),
  ) {
    Text(
      text = text,
      // 13 px at 600 in the body face — `bodyMedium` is the scale's 13, and
      // the weight is the prototype's own.
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.background,
    )
  }
}

/** How a test finds the words without spelling them out again. */
object ToastTestTags {
  const val TOAST: String = "toast"
}

/**
 * The prototype's `padding: 8px 14px`. The 8 is the spacing scale's `x2`; the
 * 14 is not on the scale and is the component's own, the way `.btn-icon`'s
 * 36 px is.
 */
private val SIDE = 14.dp

/** The prototype's own 2.6 s. */
private val DISMISS_AFTER = 2_600.milliseconds
