package de.drehtuer.dinfinity.feature.roll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Shake to roll (`docs/physics-and-rendering.md`, "Shake input").
 *
 * **It is the only way to start a roll.** There is no button, no key and no
 * accessibility action that throws, and no setting that turns this off — with
 * it off nothing could roll at all (`docs/architecture.md`, decision 66).
 *
 * Registered while the screen is resumed and let go when it is not — an
 * accelerometer left running behind a backgrounded app is a battery bill for
 * nothing.
 *
 * **The dice are spawned when the shake begins**, not when it ends. Every
 * moment after that reaches the roll while the dice are already in the air, so
 * what the player sees is dice answering their hand rather than dice thrown
 * once the hand has stopped. The samples carry the step they belong to, so the
 * same record replayed afterwards drives exactly the same roll.
 *
 * A shake with no valid formula behind it throws nothing; the presenter
 * refuses it.
 *
 * **A second shake at dice still in the air keeps them moving.** It starts no
 * throw — those dice are thrown already — so nothing is spawned and nothing
 * replaces the roll in progress; its moments simply join the ones driving it.
 * That is what a hand does at a table, and it is why the shake source is told
 * whether a roll is running (`docs/physics-and-rendering.md`, "Shake input").
 *
 * What it listens to is [ShakeInput.current]: the sensors, or a test's hand
 * standing in for them behind this same code ([ShakeInput]).
 *
 * @param listening false while something covers the whole tray — the
 *   first-launch welcome — so a shake then is not heard at all, rather than
 *   heard and thrown behind a screen nobody can see through
 *   (`docs/architecture.md`, decision 74). Turning it back on registers the
 *   hand there and then, so the first shake after the welcome is a throw.
 */
@Composable
internal fun ShakeToRoll(
  presenter: RollPresenter,
  listening: Boolean = true,
) {
  val context = LocalContext.current
  var shaking by remember { mutableStateOf(false) }

  // A hand around a phone that is being shaken is a hand on both edges of it.
  HoldTheEdges(shaking)

  LifecycleResumeEffect(presenter, listening) {
    // Nothing registered, so nothing to let go of.
    if (!listening) return@LifecycleResumeEffect onPauseOrDispose { shaking = false }
    val heard =
      ShakeInput.current.listen(
        context,
        ShakeInput.Hand(
          onStarted = {
            // The edges are claimed either way: a hand is on the phone whether
            // or not this shake had anything to throw. What the presenter
            // answers is whether it did — and a shake at dice still in the air
            // did not, which is what keeps its moments on their clock.
            shaking = true
            presenter.roll()
          },
          onEnded = { shaking = false },
          onSample = presenter::shaking,
        ),
      )
    onPauseOrDispose {
      heard.stop()
      // Leaving the screen mid-shake must not leave the edges claimed.
      shaking = false
    }
  }
}
