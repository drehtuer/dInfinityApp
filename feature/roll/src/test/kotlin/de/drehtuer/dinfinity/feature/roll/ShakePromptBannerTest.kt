package de.drehtuer.dinfinity.feature.roll

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The words on the prompt over the tray, singular and plural, and the line
 * under it that only picked dice get (decision 84).
 */
@RunWith(RobolectricTestRunner::class)
class ShakePromptBannerTest {
  @get:Rule
  val compose = createComposeRule()

  private var prompt by mutableStateOf(ShakePrompt(ShakePrompt.Why.AGAIN, 1))

  @Test
  fun `dice to re-throw are counted, one die and several dice alike`() {
    show()

    assertWords(ShakePrompt(ShakePrompt.Why.AGAIN, 1), "Shake to re-throw 1 die")
    assertWords(ShakePrompt(ShakePrompt.Why.AGAIN, 2), "Shake to re-throw 2 dice")
    compose.onNodeWithTag(RollTestTags.SHAKE_PROMPT_HINT, useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `dice a chain earned are called earned`() {
    show()

    assertWords(ShakePrompt(ShakePrompt.Why.EARNED, 1), "Shake to throw the earned die")
    assertWords(ShakePrompt(ShakePrompt.Why.EARNED, 3), "Shake to throw the 3 earned dice")
    compose.onNodeWithTag(RollTestTags.SHAKE_PROMPT_HINT, useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `picked dice say what the shake does and how to put them back`() {
    show()

    assertWords(ShakePrompt(ShakePrompt.Why.PICKED, 1), "Shake to throw the picked die")
    assertHint("Tap it again to put it back.")
    assertWords(ShakePrompt(ShakePrompt.Why.PICKED, 2), "Shake to throw the 2 picked dice")
    assertHint("Tap a ringed die again to put it back.")
  }

  @Test
  fun `a screen reader is told when it arrives`() {
    show()

    assertNotNull(
      "the prompt is not a live region",
      compose
        .onNodeWithTag(RollTestTags.SHAKE_PROMPT)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.LiveRegion),
    )
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `the shaken phone is drawn beside the words`() {
    // Captured, because a drawing is the one thing the semantics tree cannot
    // show: what this catches is a mark that stopped being painted at all.
    // The mark is the only thing in the strip left of the words, so anything
    // there that is not the accent fill is the mark.
    show()
    val textLeft =
      compose
        .onNodeWithTag(RollTestTags.SHAKE_PROMPT_TEXT, useUnmergedTree = true)
        .fetchSemanticsNode()
        .positionInRoot.x
        .toInt()
    val bannerLeft =
      compose
        .onNodeWithTag(RollTestTags.SHAKE_PROMPT)
        .fetchSemanticsNode()
        .positionInRoot.x
        .toInt()
    val pixels = compose.onNodeWithTag(RollTestTags.SHAKE_PROMPT).captureToImage().toPixelMap()
    val fill = pixels[1, 1]

    val drawn = (0 until textLeft - bannerLeft).any { x -> (0 until pixels.height).any { y -> pixels[x, y] != fill } }

    assertTrue("nothing was drawn beside the words", drawn)
  }

  private fun show() {
    compose.setContent { ShakePromptBanner(prompt = prompt) }
  }

  private fun assertWords(
    next: ShakePrompt,
    words: String,
  ) {
    prompt = next
    compose.waitForIdle()
    compose.onNodeWithTag(RollTestTags.SHAKE_PROMPT_TEXT, useUnmergedTree = true).assertTextEquals(words)
  }

  private fun assertHint(words: String) {
    compose.onNodeWithTag(RollTestTags.SHAKE_PROMPT_HINT, useUnmergedTree = true).assertTextEquals(words)
  }
}
