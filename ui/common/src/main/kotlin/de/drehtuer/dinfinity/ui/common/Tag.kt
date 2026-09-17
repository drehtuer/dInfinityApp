package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * A small badge that says what state a row is in — `default`, `disabled`
 * (`.tag` in `design/_ds/modernist-…/styles.css`, and the dice-set rows at
 * `design/dInfinityPhone.dc.html` lines 444–446).
 *
 * Eleven sp, tracked slightly, in a box with no corner. The app said all of
 * this in prose before, and did not say which set was the default **at all**
 * (`docs/design-handover.md`).
 *
 * **Two of the design system's three tag styles, not three.** `.tag-accent` is
 * filled with `--color-accent-100` and lettered in `-800`, and those two steps
 * exist only for the two accents the system ships — the app offers six, and
 * nothing in the design says how to make the *pale* end of a ramp for the
 * other four. It is recorded as a question in `docs/design-handover.md`, and
 * until it is answered there is no filled accent tag here. [Outline] needs no
 * ramp: it is the accent itself, which every accent has.
 */
@Composable
fun Tag(
  text: String,
  modifier: Modifier = Modifier,
  kind: TagKind = TagKind.Neutral,
) {
  val dark = MaterialTheme.colorScheme.background.luminance() < HALF
  val accent = MaterialTheme.colorScheme.primary
  Box(
    modifier =
      modifier
        .then(
          when (kind) {
            TagKind.Neutral -> Modifier.background(if (dark) Modernist.Neutral.v900 else Modernist.Neutral.v100)
            TagKind.Outline -> Modifier.border(Modernist.hairline, accent)
          },
        ).padding(horizontal = HORIZONTAL, vertical = VERTICAL),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      letterSpacing = TRACKING,
      color =
        when (kind) {
          TagKind.Neutral -> if (dark) Modernist.Neutral.v200 else Modernist.Neutral.v800
          TagKind.Outline -> accent
        },
    )
  }
}

/** The tag styles the design system defines that the app can draw. */
enum class TagKind {
  /**
   * `.tag-neutral`: filled with the grey ramp's pale end, lettered in its deep
   * one. What a state that is simply *true* looks like — "default".
   *
   * **On a dark ground the app mirrors both steps, and that is an inference.**
   * `.dz-dark` in the phone prototype reflects eight ramp steps about the
   * middle one, but `--color-neutral-100` and `-800` — the two this is made of
   * — are not among them. Following the rule the other eight follow gives a
   * dark chip with pale letters; taking the override list literally would give
   * a near-white chip on a dark page. The first is almost certainly meant, and
   * it is written down as a question rather than assumed silently
   * (`docs/design-handover.md`).
   */
  Neutral,

  /**
   * `.tag-outline`: a hairline of the accent with its letters in the same.
   * What a state somebody has *chosen* looks like — "disabled".
   *
   * The one tag style that needs no ramp, and therefore the one that follows
   * whichever accent the player picked in Settings.
   */
  Outline,
}

/** `.tag`'s `padding: 3px 10px`, neither of which is on the spacing scale. */
private val HORIZONTAL = 10.dp
private val VERTICAL = 3.dp

/** `.tag`'s `letter-spacing: 0.02em` — a touch, not the kicker's wide track. */
private val TRACKING = 0.02.em

/** Halfway up the luminance range, which is what divides a dark page from a light one. */
private const val HALF = 0.5f
