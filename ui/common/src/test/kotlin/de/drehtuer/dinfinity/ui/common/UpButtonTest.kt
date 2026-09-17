package de.drehtuer.dinfinity.ui.common

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The chevron: one press per press, a word for the drawing, and a target big
 * enough to hit.
 *
 * Where it *goes* is the navigation graph's and is tested there (`ClimbingTest`
 * in `:app`). What is here is that it is a button at all.
 */
@RunWith(RobolectricTestRunner::class)
class UpButtonTest {
  @get:Rule
  val compose = createComposeRule()

  private var climbed = 0

  private fun draw() {
    compose.setContent { UpButton(onUp = { climbed++ }) }
  }

  @Test
  fun `it reports one press per press`() {
    draw()

    compose.onNodeWithTag(UpTestTags.UP).performClick()

    assertEquals(1, climbed)
  }

  @Test
  fun `a drawing is not a word, so it carries one`() {
    draw()

    val label = ApplicationProvider.getApplicationContext<Context>().getString(R.string.up)
    compose.onNodeWithTag(UpTestTags.UP).assertContentDescriptionEquals(label)
    assertEquals("it climbs rather than retraces, and says so", "Up", label)
  }

  @Test
  fun `it is big enough to hit`() {
    // The drawing is 20 dp in a 36 dp button; what a finger gets is the
    // platform's 48.
    draw()

    compose.onNodeWithTag(UpTestTags.UP).assertWidthIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag(UpTestTags.UP).assertHeightIsAtLeast(TOUCH_TARGET)
  }
}
