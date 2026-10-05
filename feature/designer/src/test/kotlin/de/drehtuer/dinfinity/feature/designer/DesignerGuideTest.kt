package de.drehtuer.dinfinity.feature.designer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import de.drehtuer.dinfinity.core.model.Contrast
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The guide under the drawing, and the switch for it (`docs/face-designer.md`,
 * "The guide").
 */
@RunWith(RobolectricTestRunner::class)
// A real bitmap behind the screen, so that `captureToImage` rasterises.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignerGuideTest {
  @get:Rule
  val compose = createComposeRule()

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }

  @Test
  // Tall enough that the whole canvas is on the screen, so the picture taken
  // of it is all canvas.
  @Config(qualifiers = "w360dp-h1400dp")
  fun `the guide can be seen on the canvas whatever the theme, so its switch visibly does something`() {
    // On the phone, in a dark theme, the guide was the page's pale ink on
    // white paper: switching it changed nothing anybody could see, and the
    // switch — a picture of a landscape — was reported as an image loader that
    // loaded nothing.
    val presenter = DesignerPresenter(d6)
    compose.setContent {
      MaterialTheme(colorScheme = darkColorScheme()) { DesignerScreen(presenter = presenter) }
    }

    val paper = presenter.state.bodyArgb
    val shown = strongestInkOnPaper(compose.onNodeWithTag(DesignerTestTags.CANVAS).captureToImage(), paper)
    // Through the presenter rather than a tap: scrolling down to the switch
    // would scroll the canvas out of the picture taken of it.
    presenter.showGuide(false)
    compose.waitForIdle()
    val hidden = strongestInkOnPaper(compose.onNodeWithTag(DesignerTestTags.CANVAS).captureToImage(), paper)

    assertTrue("the guide is all but invisible on the paper: contrast $shown", shown >= GUIDE_CONTRAST)
    assertEquals("the hidden guide left something behind", 1.0, hidden, 0.01)
  }

  @Test
  fun `the guide's switch is a word a screen reader says, not a picture of a landscape`() {
    compose.setContent { DesignerScreen(presenter = DesignerPresenter(d6)) }

    compose.onNodeWithTag(DesignerTestTags.GUIDE).performScrollTo().assertTextContains("Guide")
  }

  /**
   * The greatest contrast any pixel in the middle half of [image] has against
   * the [paper] — the canvas's edge and its rule are left out, because
   * they are drawn in the page's colours and say nothing about the paper.
   */
  private fun strongestInkOnPaper(
    image: ImageBitmap,
    paper: Int,
  ): Double {
    val pixels = image.toPixelMap()
    var strongest = 1.0
    for (y in image.height / 4 until image.height * 3 / 4) {
      for (x in image.width / 4 until image.width * 3 / 4) {
        strongest = maxOf(strongest, Contrast.ratio(pixels[x, y].toArgb(), paper))
      }
    }
    return strongest
  }

  private companion object {
    /** What the faint guide has to reach against its paper to be something a person sees. */
    const val GUIDE_CONTRAST = 1.5
  }
}
