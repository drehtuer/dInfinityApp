package de.drehtuer.dinfinity.feature.roll

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The roll screen holding still while somebody waves the phone about — and
 * holding the display on while nobody touches it at all.
 *
 * All three exist because a player rolling dice is not using the phone the
 * careful way they use it to read something, and the system takes the
 * difference — a quarter turn, a swipe in from an edge, fifteen seconds with
 * no touch — as input, or as the absence of it
 * (`docs/physics-and-rendering.md`, "Shake input").
 */
@RunWith(RobolectricTestRunner::class)
class HoldTheScreenStillTest {
  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun `the roll screen holds the shape it opened in`() {
    // The tray *is* the screen, so a quarter turn rebuilds the table. That is
    // a surprise nobody asked for in the middle of a throw.
    compose.setContent { LockTheOrientation() }

    compose.waitForIdle()

    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
      compose.activity.requestedOrientation,
    )
  }

  @Test
  @Config(qualifiers = "land")
  fun `a screen opened in landscape holds landscape, not portrait`() {
    // A player who opened the app in landscape meant it, and taking that away
    // would be a second surprise in place of the first.
    compose.setContent { LockTheOrientation() }

    compose.waitForIdle()

    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
      compose.activity.requestedOrientation,
    )
  }

  @Test
  fun `turning the phone end over end is allowed, because the table does not change`() {
    // The whole of this fix. `SCREEN_ORIENTATION_LOCKED` pins the display to
    // the rotation the screen opened at, so a phone turned upside down keeps
    // an upside-down screen — and `PhoneAxes` is then told the phone is
    // upright while it is being shaken the other way up, which pools the dice
    // at the end away from the hand.
    //
    // The `USER_*` pair is the one that keeps the shape and allows both ways
    // up. Asserting it by name is the point: every other portrait constant
    // either allows a quarter turn as well or forbids the half turn.
    compose.setContent { LockTheOrientation() }
    compose.waitForIdle()

    val held = compose.activity.requestedOrientation
    assertNotEquals("the display was pinned to one rotation", ActivityInfo.SCREEN_ORIENTATION_LOCKED, held)
    assertNotEquals("a half turn was still refused", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, held)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT, held)
  }

  @Test
  fun `leaving the roll screen gives the orientation back`() {
    // The rest of the app turns as it likes; only this screen holds still.
    val onScreen = mutableStateOf(true)
    compose.setContent { if (onScreen.value) LockTheOrientation() }
    compose.waitForIdle()

    onScreen.value = false
    compose.waitForIdle()

    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
      compose.activity.requestedOrientation,
    )
  }

  @Test
  fun `both shapes are answered from the configuration alone`() {
    // A function of its argument and nothing else, so both answers can be
    // asserted without an activity in each orientation to ask. The composable
    // above only decides *when* to ask it.
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
      eitherWayUp(Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
      eitherWayUp(Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }),
    )
  }

  @Test
  fun `a configuration that says nothing is taken as portrait`() {
    // ORIENTATION_UNDEFINED. Portrait is the answer that keeps a phone
    // working; guessing landscape would turn the table sideways under it.
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
      eitherWayUp(Configuration()),
    )
  }

  @Test
  fun `the edges are claimed while the phone is being shaken, and only then`() {
    val shaking = mutableStateOf(false)
    var view: android.view.View? = null
    compose.setContent {
      view = LocalView.current
      // A surface with a size, so there are edges to claim at all.
      Box(Modifier.fillMaxSize())
      HoldTheEdges(shaking.value)
    }
    compose.waitForIdle()
    assertTrue("the edges were claimed before anybody shook anything", excluded(view).isEmpty())

    shaking.value = true
    compose.waitForIdle()
    assertTrue("a shake did not keep the back gesture off the edges", excluded(view).isNotEmpty())

    shaking.value = false
    compose.waitForIdle()
    assertTrue("the edges were never given back", excluded(view).isEmpty())
  }

  @Test
  fun `the claim is a band down each side rather than the whole edge`() {
    // The system caps how much of an edge an app may take and drops the rest,
    // so asking for everything would quietly get most of it ignored.
    var view: android.view.View? = null
    compose.setContent {
      view = LocalView.current
      Box(Modifier.fillMaxSize())
      HoldTheEdges(shaking = true)
    }
    compose.waitForIdle()

    val bands = excluded(view)
    assertEquals("one band per side", 2, bands.size)
    val height = requireNotNull(view).height
    bands.forEach { rect ->
      assertTrue("a band covered the whole side", rect.height() in 1 until height)
    }
    assertEquals("the bands are not on opposite sides", 0, bands.minOf { it.left })
    assertEquals(requireNotNull(view).width, bands.maxOf { it.right })
  }

  @Test
  fun `the screen is held on while the roll screen is composed`() {
    // A shake takes both hands and puts none of them on the glass, so the
    // display would otherwise time out in the middle of a throw.
    var view: android.view.View? = null
    compose.setContent {
      view = LocalView.current
      KeepTheScreenAwake()
    }
    compose.waitForIdle()

    assertTrue("the display was left to time out mid-roll", requireNotNull(view).keepScreenOn)
  }

  @Test
  fun `leaving the roll screen lets the display time out again`() {
    // Held only here. An app that kept the screen on everywhere is a battery
    // complaint rather than a feature.
    val onScreen = mutableStateOf(true)
    var view: android.view.View? = null
    compose.setContent {
      view = LocalView.current
      if (onScreen.value) KeepTheScreenAwake()
    }
    compose.waitForIdle()
    assertTrue(requireNotNull(view).keepScreenOn)

    onScreen.value = false
    compose.waitForIdle()

    assertFalse("the screen was still being held on after the tray went", requireNotNull(view).keepScreenOn)
  }

  @Test
  fun `the flag is the view's, so it goes with the view rather than with the window`() {
    // `View.keepScreenOn` rather than `FLAG_KEEP_SCREEN_ON`: the window flag
    // outlives the screen unless somebody remembers to clear it, and the
    // window here is one activity for the whole app. Asserting that the
    // activity's window never took the flag is what says this is scoped.
    compose.setContent { KeepTheScreenAwake() }
    compose.waitForIdle()

    val flags = compose.activity.window.attributes.flags
    assertEquals(
      "the whole window was pinned awake, not just the tray",
      0,
      flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
    )
  }

  private fun excluded(view: android.view.View?): List<android.graphics.Rect> =
    view?.systemGestureExclusionRects.orEmpty()
}
