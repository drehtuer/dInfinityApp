package de.drehtuer.dinfinity.feature.roll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * **The dice are in the hand while the phone is shaken, and thrown when it
 * stops** (`docs/architecture.md`, decision 103). A shake beginning takes the
 * waiting dice off the table ([RollPresenter.hold]); its end lets them go the
 * way its last stroke went ([RollPresenter.release]), and from there they
 * fly, land and tumble with nothing but gravity on them, as thrown dice do.
 *
 * A shake with no valid formula behind it throws nothing; the presenter
 * refuses it.
 *
 * **A shake at dice still in the air does nothing.** They have left the hand;
 * that shake holds nothing and throws nothing, and its moments go nowhere.
 *
 * What it listens to is [ShakeInput.current]: the sensors, or a test's hand
 * standing in for them behind this same code ([ShakeInput]).
 *
 * @param listening false while something covers the whole tray — the
 *   first-launch welcome — so a shake then is not heard at all, rather than
 *   heard and thrown behind a screen nobody can see through
 *   (`docs/architecture.md`, decision 74). Turning it back on registers the
 *   hand there and then, so the first shake after the welcome is a throw.
 * @param onThrown a shake that threw dice, told to the screen so it can get
 *   its controls off the table (decision 83). Not called for a shake that
 *   threw nothing — a formula that does not read, or dice already in the air.
 */
@Composable
internal fun ShakeToRoll(
  presenter: RollPresenter,
  listening: Boolean = true,
  onThrown: () -> Unit = {},
) {
  val context = LocalContext.current
  var shaking by remember { mutableStateOf(false) }
  val thrown by rememberUpdatedState(onThrown)

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
            // or not this shake holds anything. What the presenter answers is
            // whether it does — a shake at dice still in the air does not.
            shaking = true
            presenter.hold()
          },
          onEnded = {
            shaking = false
            presenter.release().also { threw -> if (threw) thrown() }
          },
          onSample = presenter::shaking,
        ),
      )
    onPauseOrDispose {
      heard.stop()
      // Leaving the screen mid-shake must not leave the edges claimed, nor
      // the dice in a hand that has gone.
      shaking = false
      presenter.drop()
    }
  }
}
