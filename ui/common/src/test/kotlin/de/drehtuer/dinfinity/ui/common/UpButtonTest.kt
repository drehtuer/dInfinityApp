package de.drehtuer.dinfinity.ui.common

import android.content.Context
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

  /**
   * The chevron is drawn rather than fetched, so its drawing is the one thing
   * that can go wrong with no word changing. It points **left**: the upper
   * arm of the head runs from the shaft's left end up and to the right, and
   * the same place mirrored about the glyph's middle is empty — an arrow
   * drawn the other way round would have ink there instead.
   */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Config(qualifiers = "xxhdpi")
  fun `it is drawn as an arrow pointing back`() {
    draw()

    val drawn = compose.onNodeWithTag(UpTestTags.UP).captureToImage().toPixelMap()
    val ground = drawn[0, 0]
    val glyph = with(compose.density) { GLYPH.toPx() }
    val left = (drawn.width - glyph) / 2
    val top = (drawn.height - glyph) / 2

    fun at(
      x: Float,
      y: Float,
    ) = drawn[(left + glyph * x / BOX).toInt(), (top + glyph * y / BOX).toInt()]

    assertNotEquals("the shaft is not drawn", ground, at(MIDDLE, MIDDLE))
    assertNotEquals("the head does not open to the right", ground, at(ARM, ARM))
    assertEquals("the head is on the wrong end", ground, at(BOX - ARM, ARM))
  }

  @Test
  fun `it is big enough to hit`() {
    // The drawing is 20 dp in a 36 dp button; what a finger gets is the
    // platform's 48.
    draw()

    compose.onNodeWithTag(UpTestTags.UP).assertWidthIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag(UpTestTags.UP).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  private companion object {
    /** The drawing's size in the button, as the prototype draws it. */
    val GLYPH = 20.dp

    /** `#ic-back`'s own 24-unit box, its middle, and halfway along the head's upper arm. */
    const val BOX = 24f
    const val MIDDLE = 12f
    const val ARM = 8.5f
  }
}
