package de.drehtuer.dinfinity

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.navigation.LeavingTheApp
import de.drehtuer.dinfinity.ui.common.ModernistToast

/**
 * System back on the roll screen: the first press arms and says so, the second
 * within two seconds leaves (`docs/architecture.md`, "Navigation").
 *
 * Drawn **only over the roll screen**, because that is the only screen where
 * back means "leave". Everywhere else back is the graph's own, which goes to
 * the tray — so this composable is not in the composition at all there, and
 * the handler cannot fire on a screen it was not written for.
 *
 * The rule itself is [LeavingTheApp], which knows nothing about Compose; what
 * is here is the press, the words and the way out. There is no "closed app"
 * screen to draw on the way: the prototype has one because a web page cannot
 * close, and an app that closes is simply closed.
 *
 * @param onLeave what leaving is. The activity finishing, in the app; whatever
 *   a test wants to count, in a test.
 * @param now the clock, in milliseconds on a monotonic scale. Injected for the
 *   same reason [LeavingTheApp] takes its time as an argument — a test that
 *   has to sleep for two seconds to see the window lapse is a test nobody runs
 *   often enough.
 */
@Composable
internal fun TwoStageBack(
  onLeave: () -> Unit,
  modifier: Modifier = Modifier,
  now: () -> Long = { SystemClock.elapsedRealtime() },
) {
  val leaving = remember { LeavingTheApp() }
  // When the words went up, or null while there are none. The time rather than
  // a flag, so that arming again while the last toast is still on screen is a
  // *new* toast with its own time to run rather than the tail of the old one.
  var saidAt by remember { mutableStateOf<Long?>(null) }
  BackHandler {
    val pressed = now()
    when (leaving.pressed(pressed)) {
      LeavingTheApp.Answer.Arm -> saidAt = pressed
      LeavingTheApp.Answer.Leave -> {
        saidAt = null
        onLeave()
      }
    }
  }
  saidAt?.let { armed ->
    key(armed) {
      ModernistToast(
        text = stringResource(R.string.back_again),
        // The toast keeps its own 2.6 s and the window is 2 s, so the words
        // outlive what they promise by six tenths of a second. A press in that
        // gap arms again rather than leaving, which is the failure worth
        // having: an app that stays open when it was asked twice to close is a
        // press away from closing, and one that closes when it was asked once
        // is gone.
        onDismissed = {
          saidAt = null
          leaving.lapse()
        },
        // The prototype puts it 18 px off the bottom edge, over everything and
        // clear of the system's own bar.
        modifier = modifier.safeDrawingPadding().padding(bottom = ABOVE_THE_EDGE),
      )
    }
  }
}

/** The prototype's `bottom: 18px`. */
private val ABOVE_THE_EDGE = 18.dp
