package de.drehtuer.dinfinity.feature.roll

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The roll screen holding still while somebody waves the phone about.
 *
 * Both of these exist because a player shaking a phone is not holding it the
 * careful way they hold it to read something, and the system takes the
 * difference as input (`docs/TODO.md`, Step 4.1).
 */
@RunWith(RobolectricTestRunner::class)
class HoldTheScreenStillTest {
  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun `the roll screen is locked to the orientation it opened in`() {
    // The tray *is* the screen, so turning the phone rebuilds the table. That
    // is a surprise nobody asked for in the middle of a throw.
    compose.setContent { LockTheOrientation() }

    compose.waitForIdle()

    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LOCKED,
      compose.activity.requestedOrientation,
    )
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

  private fun excluded(view: android.view.View?): List<android.graphics.Rect> =
    view?.systemGestureExclusionRects.orEmpty()
}
