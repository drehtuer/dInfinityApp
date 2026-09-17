package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import de.drehtuer.dinfinity.core.model.AccentRamp
import de.drehtuer.dinfinity.core.model.Ground

/**
 * A small badge that says what state a row is in — `default`, `disabled`
 * (`.tag` in `design/_ds/modernist-…/styles.css`, and the dice-set rows at
 * `design/dInfinityPhone.dc.html` lines 444–446).
 *
 * Eleven sp, tracked slightly, in a box with no corner. The app said all of
 * this in prose before, and did not say which set was the default **at all**
 * (`docs/design-handover.md`).
 *
 * **All three of the design system's tag styles.** `.tag-accent` is filled
 * with `--color-accent-100` and lettered in `-800`, and those two steps used
 * to exist only for the two accents the system ships — where the app offers
 * six presets and any colour the player likes. The design of 2026-09-17
 * answered it and `AccentRamp` is that answer: **both ends are mixed from the
 * accent the player picked**, `color-mix(accent 16 %, bg)` at the pale end and
 * 40 % toward `--color-text` at the deep one, against the ground's own ink and
 * page rather than against black and white — which is what makes one rule
 * resolve on both grounds.
 *
 * The accent it mixes from is the theme's `primary`, which is the player's
 * colour **after the clamp**, so the pairing starts from something already
 * known to stand off the page. Clamping it again here costs nothing and
 * changes nothing: a colour that clears the bar is returned untouched.
 */
@Composable
fun Tag(
  text: String,
  modifier: Modifier = Modifier,
  kind: TagKind = TagKind.Neutral,
) {
  val dark = MaterialTheme.colorScheme.background.luminance() < HALF
  val accent = MaterialTheme.colorScheme.primary
  val ramp = remember(accent, dark) { AccentRamp.of(accent.toArgb(), if (dark) Ground.Dark else Ground.Light) }
  Box(
    modifier =
      modifier
        .then(
          when (kind) {
            TagKind.Neutral -> Modifier.background(if (dark) Modernist.Neutral.v900 else Modernist.Neutral.v100)
            TagKind.Outline -> Modifier.border(Modernist.hairline, accent)
            TagKind.Accent -> Modifier.background(Color(ramp.v100))
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
          TagKind.Accent -> Color(ramp.v800)
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
   * The one tag style that needs no ramp at all: it is the accent itself,
   * which every accent has.
   */
  Outline,

  /**
   * `.tag-accent`: the pale end of the accent's ramp filled, lettered in the
   * deep one. What a state that wants *doing something about* looks like —
   * "Update available" on a dice-set row.
   *
   * Both ends follow the accent the player chose, mixed by [AccentRamp]
   * against the ground the tag is drawn on, so it is a pale chip with deep
   * letters on paper and a deep chip with pale letters on a dark page. The
   * pairing clears 7:1 for every preset on both grounds and 4.5:1 for any
   * colour at all — `TagTest` and `core/model`'s `AccentRampTest` measure
   * both rather than assuming them.
   */
  Accent,
}

/** `.tag`'s `padding: 3px 10px`, neither of which is on the spacing scale. */
private val HORIZONTAL = 10.dp
private val VERTICAL = 3.dp

/** `.tag`'s `letter-spacing: 0.02em` — a touch, not the kicker's wide track. */
private val TRACKING = 0.02.em

/** Halfway up the luminance range, which is what divides a dark page from a light one. */
private const val HALF = 0.5f
