package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist

/**
 * What the next shake will throw, said over the tray for as long as it is
 * true (`docs/architecture.md`, decision 84).
 *
 * The owner, on the Pixel 10a: the request to shake for dice that landed
 * cocked was "easy to miss", and a picked die gave no hint of what picking it
 * was for. Both are the same missing sentence — *the next shake throws these* —
 * so both get the same prompt, and so does a chain that earned a throw.
 *
 * A value rather than a branch in the screen, so which words go with which
 * state is arithmetic a JVM test reads ([of]).
 *
 * @param why which dice the shake is for, which is what decides the words.
 * @param count how many of them, at least one.
 */
internal data class ShakePrompt(
  val why: Why,
  val count: Int,
) {
  /** The three things a shake can be owed. */
  enum class Why {
    /** Dice nobody could read, or that never stopped: thrown again (decision 70). */
    AGAIN,

    /** Dice an exploding chain earned and nobody has thrown yet. */
    EARNED,

    /** Dice of a landed roll a finger picked up (decisions 68 and 76). */
    PICKED,
  }

  companion object {
    /**
     * The prompt for [state] with [picked] dice picked up, or null when the
     * next shake is not owed to anything — a first throw, or a whole roll
     * thrown again, which the ready plate and the result sheet already say.
     *
     * A roll waiting on dice comes before a pick, because nothing can be
     * picked until every die of a throw is read (decision 76).
     */
    fun of(
      state: RollState,
      picked: Int,
    ): ShakePrompt? =
      when (state) {
        is RollState.ThrowAgain -> ShakePrompt(Why.AGAIN, state.unread)
        is RollState.Stalled -> ShakePrompt(Why.AGAIN, state.unsettled)
        is RollState.ShakeAgain -> ShakePrompt(Why.EARNED, state.waiting)
        is RollState.Settled if picked > 0 -> ShakePrompt(Why.PICKED, picked)
        else -> null
      }
  }
}

/**
 * [ShakePrompt], drawn: a banner in the accent with a shaken phone and the
 * words in the heading face, and — for picked dice — a line on the plates'
 * ground under it saying how to put one back.
 *
 * **Filled in the accent, like a primary button**, because it is the one
 * thing on the screen the player has to do next. The words are `titleLarge`,
 * 20 sp at 800, which is large text: the accent as chosen clears the 3:1 that
 * needs, where it would not clear 4.5:1 for body copy. The second line is
 * body copy, so it is on the ground in the ink rather than on the accent.
 * Nothing accent-coloured is printed *on* felt: the accent is a fill under
 * its own words, behind a `--shadow-md` edge.
 *
 * It **cannot be pressed** — a shake is the only throw (decision 66) — and a
 * finger on it goes through to the tray. It is a **polite live region**, so a
 * screen reader hears it arrive and hears the count change as dice are picked
 * and put back; it leaves with the shake that answers it, because the state
 * it was drawn from does.
 */
@Composable
internal fun ShakePromptBanner(
  prompt: ShakePrompt,
  modifier: Modifier = Modifier,
) {
  val accent = Ink.accent
  val onAccent = MaterialTheme.colorScheme.onPrimary
  Column(
    modifier =
      modifier
        .shadow(elevation = Modernist.Shadow.md, shape = Modernist.square)
        // One node: the words and the hint under them are one notice.
        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        .testTag(RollTestTags.SHAKE_PROMPT),
  ) {
    Row(
      modifier = Modifier.background(accent).padding(horizontal = SIDE, vertical = Modernist.x2),
      horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ShakeMark(ink = onAccent)
      Text(
        text = prompt.words(),
        style = MaterialTheme.typography.titleLarge,
        color = onAccent,
        modifier = Modifier.testTag(RollTestTags.SHAKE_PROMPT_TEXT),
      )
    }
    if (prompt.why == ShakePrompt.Why.PICKED) {
      Box(
        modifier =
          Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = SIDE, vertical = Modernist.x1),
      ) {
        Text(
          text = pluralStringResource(R.plurals.roll_prompt_picked_back, prompt.count),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.testTag(RollTestTags.SHAKE_PROMPT_HINT),
        )
      }
    }
  }
}

/** The banner's main line, with the count in it. */
@Composable
private fun ShakePrompt.words(): String =
  pluralStringResource(
    when (why) {
      ShakePrompt.Why.AGAIN -> R.plurals.roll_prompt_again
      ShakePrompt.Why.EARNED -> R.plurals.roll_prompt_earned
      ShakePrompt.Why.PICKED -> R.plurals.roll_prompt_picked
    },
    count,
    count,
  )

/**
 * A phone, leaning, with a motion arc either side of it.
 *
 * Drawn rather than brought in, for the reason the stalled plate's alert mark
 * is: this app ships no icon set. Silent to a screen reader — the words beside
 * it say "Shake".
 */
@Composable
private fun ShakeMark(ink: Color) {
  Box(
    modifier =
      Modifier
        .size(MARK)
        .clearAndSetSemantics { }
        .drawBehind { shakeMark(ink) },
  )
}

private fun DrawScope.shakeMark(ink: Color) {
  val stroke = MARK_STROKE.toPx()
  val bodyWidth = size.width * BODY_WIDTH
  val bodyHeight = size.height * BODY_HEIGHT
  rotate(degrees = LEAN) {
    drawRoundRect(
      color = ink,
      topLeft = Offset((size.width - bodyWidth) / 2f, (size.height - bodyHeight) / 2f),
      size = Size(bodyWidth, bodyHeight),
      cornerRadius = CornerRadius(stroke * 2f),
      style = Stroke(width = stroke),
    )
  }
  val reach = size.width * ARC_REACH
  listOf(-1f, 1f).forEach { side ->
    val x = size.width / 2f + side * reach
    drawArc(
      color = ink,
      startAngle = if (side < 0f) ARC_LEFT_START else ARC_RIGHT_START,
      sweepAngle = ARC_SWEEP,
      useCenter = false,
      topLeft = Offset(x - size.width * ARC_RADIUS, size.height / 2f - size.height * ARC_RADIUS),
      size = Size(size.width * ARC_RADIUS * 2f, size.height * ARC_RADIUS * 2f),
      style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
  }
}

/** The toast's `padding: 8px 14px`, which this is the louder sibling of. */
private val SIDE = 14.dp

/** The mark sits beside 20 sp words. */
private val MARK = 24.dp
private val MARK_STROKE = 2.dp

private const val BODY_WIDTH = 0.36f
private const val BODY_HEIGHT = 0.62f
private const val LEAN = -18f
private const val ARC_REACH = 0.06f
private const val ARC_RADIUS = 0.42f
private const val ARC_LEFT_START = 150f
private const val ARC_RIGHT_START = -30f
private const val ARC_SWEEP = 60f
