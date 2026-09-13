package de.drehtuer.dinfinity.feature.roll

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

// The two ways the phone gets in the way of somebody shaking it
// (`docs/TODO.md`, Step 4.1). Both are the same thing underneath: a player
// waving a phone about is not holding it the careful way they hold it to read
// something, and the system takes the difference as input.

/**
 * Keeps the roll screen in the orientation it was opened in.
 *
 * The tray *is* the screen (`docs/tables.md`), so turning the phone rebuilds
 * the table — a different shape, a different capacity, the camera reframed.
 * That is the right answer for a player who meant it and an unwelcome surprise
 * for one who is shaking the thing, which is most of the time on this screen.
 *
 * Locked to whatever is on screen when it opens rather than to portrait: a
 * player who opened the app in landscape means it, and taking that away would
 * be a second surprise in place of the first.
 *
 * Released when the screen goes, so the rest of the app turns as it likes.
 */
@Composable
internal fun LockTheOrientation() {
  val activity = LocalContext.current.activity()

  DisposableEffect(activity) {
    val wasRequesting = activity?.requestedOrientation
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
    onDispose {
      activity?.requestedOrientation = wasRequesting ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
  }
}

/**
 * Keeps the back gesture off the edges of the screen while a shake is going on.
 *
 * A hand around a phone that is being shaken is a hand on both edges of it, and
 * the back gesture is a swipe in from an edge. It is easy to leave the app
 * mid-throw without meaning to, which loses the roll.
 *
 * **This is a partial answer and worth being honest about.** The exclusion is
 * the only lever Android gives an app here, and it covers the *back* gesture
 * alone: no app can hold on to the home swipe, by design, and nothing here
 * tries to. The system also caps how much of each edge may be claimed, so this
 * asks for a band around the middle of each side — where a hand actually grips
 * — rather than the whole height.
 *
 * Claimed only while the phone is being shaken, and given straight back
 * afterwards: an app that quietly kept the back gesture for itself would be a
 * worse citizen than the problem it is solving.
 */
@Composable
internal fun HoldTheEdges(shaking: Boolean) {
  val view = LocalView.current

  DisposableEffect(view, shaking) {
    if (!shaking) {
      view.systemGestureExclusionRects = emptyList()
      return@DisposableEffect onDispose { }
    }

    // Re-measured as well as measured: a view that has not been laid out yet
    // has no edges to claim, and one that is laid out again has different ones.
    // Reading the size once and hoping is how this quietly does nothing.
    val claim = { view.systemGestureExclusionRects = gripBands(view.width, view.height) }
    claim()
    val onLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> claim() }
    view.addOnLayoutChangeListener(onLayout)

    onDispose {
      view.removeOnLayoutChangeListener(onLayout)
      view.systemGestureExclusionRects = emptyList()
    }
  }
}

/**
 * A band down each side, around where a hand holds the phone.
 *
 * The system allows only so much of an edge to be claimed and ignores the rest,
 * so asking for the whole height would quietly get most of it dropped. The
 * middle is where the grip is.
 */
private fun gripBands(
  width: Int,
  height: Int,
): List<Rect> {
  if (width <= 0 || height <= 0) return emptyList()
  val band = (height * GRIP_SHARE).toInt().coerceAtLeast(1)
  val top = (height - band) / 2
  return listOf(
    Rect(0, top, EDGE_PIXELS, top + band),
    Rect(width - EDGE_PIXELS, top, width, top + band),
  )
}

private tailrec fun Context.activity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
  }

/** How much of the screen's height each grip band covers. */
private const val GRIP_SHARE = 0.4

/** How far in from each side the band reaches. Fingers, not thumbs. */
private const val EDGE_PIXELS = 64
