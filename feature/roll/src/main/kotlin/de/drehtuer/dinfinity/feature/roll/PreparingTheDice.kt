package de.drehtuer.dinfinity.feature.roll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import de.drehtuer.dinfinity.render.filament.DiceMaterial
import de.drehtuer.dinfinity.render.filament.ShaderWork
import de.drehtuer.dinfinity.render.filament.monotonicMillis
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.Plate
import kotlin.math.roundToInt

/**
 * "Preparing the dice": what stands over the tray while the roll thread
 * compiles a dice material (`design/dInfinity.dc.html`, option `1za`;
 * `docs/physics-and-rendering.md`, "Preparing the dice").
 *
 * The first launch after an install or an update compiles the material for
 * this phone's driver before anything can be drawn, and a translucent die, a
 * glossy table or a table drawn from pictures compiles one more the first time
 * it is shown. Each is a few seconds of a tray that is black, or a roll that
 * stands still, for no reason the player can see — so this says what is
 * happening, why it will not happen again, and about how long is left
 * (`docs/architecture.md`, decision 95).
 *
 * The bar is an estimate ([ShaderWork.Compiling.fraction]), because the
 * compiler says nothing until it is done: it advances with the clock against
 * what this phone took last time, waits short of full if the phone is slower
 * than that, and the plate goes when the compile does — never before.
 *
 * Drawn on a [Plate], like everything else over the table, and deaf to touch.
 * For a screen reader it is three things: the title and why, which is a polite
 * live region so it is heard when it appears; the bar, which carries its
 * progress; and the time left. Only the first announces itself, because a
 * bar that spoke every time it moved would talk over the whole compile.
 *
 * @param clock the same clock [ShaderWork.Compiling.startedAtMillis] was read
 *   from, which a test replaces.
 */
@Composable
internal fun PreparingTheDice(
  work: ShaderWork,
  modifier: Modifier = Modifier,
  clock: () -> Long = ::monotonicMillis,
) {
  // The last compile, kept while the plate fades out so it fades out showing
  // what it was showing rather than going blank first.
  var shown by remember { mutableStateOf<ShaderWork.Compiling?>(null) }
  if (work is ShaderWork.Compiling) shown = work
  AnimatedVisibility(
    visible = work is ShaderWork.Compiling,
    enter = EnterTransition.None,
    exit = fadeOut(),
    modifier = modifier,
  ) {
    shown?.let { Preparing(it, clock) }
  }
}

@Composable
private fun Preparing(
  compiling: ShaderWork.Compiling,
  clock: () -> Long,
) {
  var now by remember(compiling) { mutableLongStateOf(clock()) }
  // A frame at a time while the plate is up, and an *infinite* frame, because
  // this has no end of its own: the compile ending is what takes the plate
  // away, so a test waiting for the screen to settle is not waiting on it.
  LaunchedEffect(compiling) {
    while (true) withInfiniteAnimationFrameMillis { now = clock() }
  }
  val fraction = compiling.fraction(now)
  val title = stringResource(titleOf(compiling.variant))
  val why = stringResource(R.string.roll_preparing_why)
  val heard = stringResource(R.string.roll_preparing_spoken, title, why)
  val left = compiling.secondsLeft(now)
  Plate(modifier = Modifier.fillMaxWidth().testTag(RollTestTags.PREPARING)) {
    Column(verticalArrangement = Arrangement.spacedBy(Modernist.x2)) {
      Column(
        verticalArrangement = Arrangement.spacedBy(Modernist.x1),
        modifier =
          Modifier.semantics(mergeDescendants = true) {
            contentDescription = heard
            liveRegion = LiveRegionMode.Polite
          },
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.testTag(RollTestTags.PREPARING_TITLE),
        )
        Text(text = why, style = MaterialTheme.typography.bodyMedium, color = Ink.muted)
      }
      ProgressRule(
        filled = fraction,
        fillTag = RollTestTags.PREPARING_FILL,
        modifier =
          Modifier
            // Whole per cent, so the value a screen reader is given does not
            // change on every frame.
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(percentOf(fraction), 0f..1f) }
            .testTag(RollTestTags.PREPARING_BAR),
      )
      Text(
        text =
          if (left == null) {
            stringResource(R.string.roll_preparing_almost)
          } else {
            pluralStringResource(R.plurals.roll_preparing_left, left, left)
          },
        style = MaterialTheme.typography.bodyMedium,
        color = Ink.muted,
        modifier = Modifier.testTag(RollTestTags.PREPARING_LEFT),
      )
    }
  }
}

/** What is being prepared, in the player's words rather than the shader's. */
internal fun titleOf(variant: DiceMaterial.Variant): Int =
  when (variant) {
    DiceMaterial.Variant.OPAQUE -> R.string.roll_preparing_dice
    DiceMaterial.Variant.RESIN -> R.string.roll_preparing_resin
    DiceMaterial.Variant.GLASS -> R.string.roll_preparing_glass
    DiceMaterial.Variant.TABLE -> R.string.roll_preparing_table
  }

/** [fraction] to the nearest whole per cent, as a fraction again. */
internal fun percentOf(fraction: Float): Float = (fraction * PER_CENT).roundToInt() / PER_CENT

private const val PER_CENT = 100f
