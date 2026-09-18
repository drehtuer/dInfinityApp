package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.drehtuer.dinfinity.core.notation.RollRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The three plates that sit across the bottom of the tray
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * What is worth asserting is that each of them says the thing it exists to say
 * and that its buttons reach the presenter: a plate that drew beautifully and
 * offered a dead button would look exactly like one that worked. The colours
 * and the tracking are the design system's and are held in `ui/common`.
 */
@RunWith(RobolectricTestRunner::class)
class TrayPlatesTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the counting plate says the count, the range and what it is`() {
    compose.setContent { CountingPlate(progress(read = 14, of = 20, lowest = 34, highest = 68)) }

    compose.onNodeWithTag(RollTestTags.COUNTING).assertIsDisplayed()
    compose.onNodeWithText("COUNTING", useUnmergedTree = true).assertExists()
    compose.onNodeWithText("14", useUnmergedTree = true).assertExists()
    compose.onNodeWithText("of 20 read", useUnmergedTree = true).assertExists()
    compose.onNodeWithText("34 to 68", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `a chain that is still open marks the top of the range`() {
    // The ceiling is what the throw can reach *without* earning another die,
    // so a `+` is the honest way to say it is not the end of it
    // (`RollRange.more`, `docs/dice-notation.md`).
    compose.setContent { CountingPlate(progress(read = 3, of = 8, lowest = 12, highest = 48, more = true)) }

    compose.onNodeWithTag(RollTestTags.COUNTING_MORE, useUnmergedTree = true).assertExists()
  }

  @Test
  fun `a chain that has stopped does not`() {
    compose.setContent { CountingPlate(progress(read = 3, of = 8, lowest = 12, highest = 48)) }

    compose.onNodeWithTag(RollTestTags.COUNTING_MORE, useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `the counting plate is one sentence to a screen reader, not four figures`() {
    // A row of numbers an eye takes in at a glance is four disconnected
    // fragments read aloud (`docs/architecture.md`, "Accessibility").
    compose.setContent { CountingPlate(progress(read = 14, of = 20, lowest = 34, highest = 68)) }

    compose.onNodeWithTag(RollTestTags.COUNTING).assertContentDescriptionEquals("14 of 20 dice read · 34 to 68")
  }

  @Test
  fun `the progress rule fills as far as the dice have been read`() {
    compose.setContent { CountingPlate(progress(read = 10, of = 20, lowest = 1, highest = 2)) }

    compose.onNodeWithTag(RollTestTags.COUNTING_RULE, useUnmergedTree = true).assertExists()
  }

  @Test
  fun `an earned throw is offered rather than taken`() {
    // An exploding six earns another throw; the app does not make it
    // (`docs/dice-notation.md`, "Evaluation").
    var thrown = 0
    var stopped = 0
    compose.setContent { EarnedPlate(waiting = 3, onThrow = { thrown++ }, onStop = { stopped++ }) }

    compose.onNodeWithTag(RollTestTags.SHAKE_AGAIN).assertIsDisplayed()
    compose.onNodeWithText("ANOTHER THROW EARNED", useUnmergedTree = true).assertExists()
    compose.onNodeWithText("Throw 3 more", useUnmergedTree = true).assertExists()

    compose.onNodeWithTag(RollTestTags.EARNED_THROW).performClick()
    compose.onNodeWithTag(RollTestTags.EARNED_STOP).performClick()

    assertEquals("the earned throw was not made", 1, thrown)
    assertEquals("the chain could not be stopped", 1, stopped)
  }

  @Test
  fun `one earned die asks for it in the singular`() {
    compose.setContent { EarnedPlate(waiting = 1, onThrow = {}, onStop = {}) }

    compose.onNodeWithText("Throw it", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `a roll that could not settle names the dice and offers both ways out`() {
    var again = 0
    var cancelled = 0
    compose.setContent {
      StalledPlate(unsettled = 3, read = 17, onThrowAgain = { again++ }, onCancel = { cancelled++ })
    }

    compose.onNodeWithTag(RollTestTags.STALLED).assertIsDisplayed()
    compose.onNodeWithText("COULD NOT SETTLE", useUnmergedTree = true).assertExists()
    compose.onNodeWithText("Throw those 3 again", useUnmergedTree = true).assertExists()

    compose.onNodeWithTag(RollTestTags.THROW_AGAIN).performClick()
    compose.onNodeWithTag(RollTestTags.STALLED_CANCEL).performClick()

    assertEquals("the dice were not thrown again", 1, again)
    assertEquals("the roll could not be cancelled", 1, cancelled)
  }

  @Test
  fun `the refusal says how many of how many never stopped`() {
    // Three of twenty, seventeen counted — the numbers are what makes it an
    // account of a roll rather than an apology.
    compose.setContent { StalledPlate(unsettled = 3, read = 17, onThrowAgain = {}, onCancel = {}) }

    compose
      .onNodeWithText(
        "3 dice never stopped, so the roll has no total yet — 17 of 20 read and counted.",
        useUnmergedTree = true,
      ).assertExists()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `the refusal carries a mark as well as words`() {
    // The design puts an alert glyph in front of the kicker, and the app has
    // no icon set — so it is drawn. Captured rather than asserted on
    // semantics, because a drawing is the one thing the semantics tree cannot
    // show: what this catches is a mark that stopped being painted at all.
    compose.setContent { StalledPlate(unsettled = 3, read = 17, onThrowAgain = {}, onCancel = {}) }

    val painted = compose.onNodeWithTag(RollTestTags.STALLED).captureToImage().toPixelMap()
    val ink = (0 until painted.height).any { y -> (0 until painted.width).any { x -> painted[x, y] != painted[0, 0] } }

    assertTrue("nothing at all was drawn on the refusal plate", ink)
  }

  private fun progress(
    read: Int,
    of: Int,
    lowest: Long,
    highest: Long,
    more: Boolean = false,
  ) = RollProgress(
    read = read,
    of = of,
    onTheTable = lowest,
    range = RollRange(lowest = lowest, highest = highest, more = more),
  )
}
