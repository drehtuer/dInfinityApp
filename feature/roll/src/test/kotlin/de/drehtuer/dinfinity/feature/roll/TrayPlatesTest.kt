package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.drehtuer.dinfinity.core.notation.RollRange
import de.drehtuer.dinfinity.ui.common.Modernist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The four plates that sit across the bottom of the tray
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * What is worth asserting is that each of them says the thing it exists to
 * say, and that the one button left on any of them reaches the presenter: a
 * plate that drew beautifully and offered a dead button would look exactly
 * like one that worked. The colours and the tracking are the design system's
 * and are held in `ui/common`.
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
    // Half read is half the rule, all read is all of it — and nothing read is
    // no fill at all rather than a sliver, which is why the fill is laid out
    // by hand instead of by `fillMaxWidth(fraction)`.
    compose.setContent {
      Column {
        CountingPlate(progress(read = 10, of = 20, lowest = 1, highest = 2))
        CountingPlate(progress(read = 20, of = 20, lowest = 1, highest = 2))
        CountingPlate(progress(read = 0, of = 20, lowest = 1, highest = 2))
      }
    }

    val (half, whole, none) =
      compose
        .onAllNodesWithTag(RollTestTags.COUNTING_RULE, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { it.size.width }

    assertTrue("a whole roll read drew no rule", whole > 0)
    assertEquals("half the dice read is not half the rule", whole / 2f, half.toFloat(), 1f)
    assertEquals("a roll with nothing read drew a sliver of fill", 0, none)
  }

  /**
   * The track the rule runs along is the pale end of the grey ramp on a light
   * ground and the dark end on a dark one — mirrored the way `.dz-dark`
   * mirrors the ramp. A near-white bar under a dark plate would be the
   * brightest thing on the screen and read as the fill rather than as what is
   * left to do (`Modernist.Neutral`).
   */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `the track under the rule is the pale step on a light ground`() {
    assertEquals(Modernist.Neutral.v200, trackUnder(lightColorScheme()))
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `and the mirrored step on a dark one`() {
    assertEquals(Modernist.Neutral.v800, trackUnder(darkColorScheme()))
  }

  /**
   * The colour of the track just past the end of a rule one die in twenty has
   * filled, a pixel inside its 3 dp. One rather than none, because a rule with
   * no fill is a node with no size and nothing to say where the track is.
   */
  private fun trackUnder(scheme: ColorScheme): Color {
    compose.setContent {
      MaterialTheme(colorScheme = scheme) {
        CountingPlate(progress(read = 1, of = 20, lowest = 1, highest = 2))
      }
    }
    val plate = compose.onNodeWithTag(RollTestTags.COUNTING).fetchSemanticsNode().boundsInRoot
    val rule =
      compose
        .onNodeWithTag(
          RollTestTags.COUNTING_RULE,
          useUnmergedTree = true,
        ).fetchSemanticsNode()
        .boundsInRoot
    val pixels = compose.onNodeWithTag(RollTestTags.COUNTING).captureToImage().toPixelMap()
    val across = (rule.right - plate.left + PAST_THE_FILL).toInt()
    val down = (rule.top - plate.top + 1f).toInt()
    return pixels[across, down]
  }

  @Test
  fun `an earned throw is asked for and has no button to press`() {
    // An exploding six earns another throw; the app does not make it, and
    // there is nothing here to press that would
    // (`docs/dice-notation.md`, "Evaluation";
    // `docs/physics-and-rendering.md`, "Starting a roll").
    compose.setContent { EarnedPlate(waiting = 3) }

    compose.onNodeWithTag(RollTestTags.SHAKE_AGAIN).assertIsDisplayed()
    compose.onNodeWithText("ANOTHER THROW EARNED", useUnmergedTree = true).assertExists()
    compose
      .onNodeWithText(
        "3 dice rolled their highest face and earned a throw. " +
          "The dice that are down stay down \u2014 shake to throw the 3 they earned.",
        useUnmergedTree = true,
      ).assertExists()
    compose.onAllNodes(hasClickAction()).assertCountEquals(0)
  }

  @Test
  fun `an earned throw says where the roll can still come out`() {
    // The gap the second device session fell into: the range was on the
    // counting plate, the counting plate went the moment the dice stopped,
    // and what is left to decide with while a chain waits for a hand was
    // nothing at all.
    compose.setContent { EarnedPlate(waiting = 1, range = RollRange(lowest = 7, highest = 26, more = true)) }

    compose.onNodeWithText("STILL TO COME", useUnmergedTree = true).assertExists()
    compose
      .onNodeWithTag(RollTestTags.STILL_TO_COME)
      .assertContentDescriptionEquals("Still to come, 7 to 26+")
  }

  @Test
  fun `a chain whose range nobody kept says nothing about it`() {
    compose.setContent { EarnedPlate(waiting = 1) }

    compose.onNodeWithTag(RollTestTags.STILL_TO_COME).assertDoesNotExist()
  }

  @Test
  fun `a roll that gave up says it too, because it is waiting on the same shake`() {
    compose.setContent {
      StalledPlate(unsettled = 3, read = 17, onCancel = {}, range = RollRange(lowest = 20, highest = 100))
    }

    compose
      .onNodeWithTag(RollTestTags.STILL_TO_COME)
      .assertContentDescriptionEquals("Still to come, 20 to 100")
  }

  @Test
  fun `one earned die asks for it in the singular`() {
    compose.setContent { EarnedPlate(waiting = 1) }

    compose
      .onNodeWithText(
        "A die rolled its highest face and earned a throw. " +
          "The dice that are down stay down \u2014 shake to throw the one they earned.",
        useUnmergedTree = true,
      ).assertExists()
  }

  @Test
  fun `a roll that could not settle names the dice and offers the one way out that is not a shake`() {
    // `Throw those 3 again` is gone with every other throw button: the dice
    // go back in the air with a shake. Cancelling is not throwing, so it
    // stays (`docs/physics-and-rendering.md`, "Starting a roll").
    var cancelled = 0
    compose.setContent { StalledPlate(unsettled = 3, read = 17, onCancel = { cancelled++ }) }

    compose.onNodeWithTag(RollTestTags.STALLED).assertIsDisplayed()
    compose.onNodeWithText("COULD NOT SETTLE", useUnmergedTree = true).assertExists()
    compose.onNodeWithText("Throw those 3 again", useUnmergedTree = true).assertDoesNotExist()

    compose.onNodeWithTag(RollTestTags.STALLED_CANCEL).performClick()

    assertEquals("the roll could not be cancelled", 1, cancelled)
  }

  @Test
  fun `the ready plate says what a shake would be worth`() {
    // What the Roll button's label used to be spent on ([Expectation]).
    compose.setContent {
      ReadyPlate(expected = Expectation(range = RollRange(lowest = 3, highest = 18), mean = 10.5))
    }

    compose.onNodeWithTag(RollTestTags.HINT).assertTextEquals("Shake the phone to roll.")
    compose
      .onNodeWithTag(RollTestTags.EXPECTED)
      .assertContentDescriptionEquals("Expected 3 to 18, average avg 10.5")
  }

  @Test
  fun `a chain that can still climb says so after the ceiling`() {
    // The same `+` the counting plate draws, from the same `RollRange.more`.
    compose.setContent {
      ReadyPlate(expected = Expectation(range = RollRange(lowest = 3, highest = 21, more = true), mean = 12.25))
    }

    compose
      .onNodeWithTag(RollTestTags.EXPECTED)
      .assertContentDescriptionEquals("Expected 3 to 21+, average avg 12.3")
  }

  @Test
  fun `a formula with no average keeps its range`() {
    // `OutcomeGraph` declines past a point; the ends do not
    // (`docs/probability.md`, limits).
    compose.setContent {
      ReadyPlate(expected = Expectation(range = RollRange(lowest = 30, highest = 3_000), mean = null))
    }

    compose
      .onNodeWithTag(RollTestTags.EXPECTED)
      .assertContentDescriptionEquals("Expected 30 to 3000")
    compose.onNodeWithTag(RollTestTags.EXPECTED_AVERAGE, useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `a formula that does not read has a hint and nothing to expect`() {
    compose.setContent { ReadyPlate(expected = null) }

    compose.onNodeWithTag(RollTestTags.HINT).assertExists()
    compose.onNodeWithTag(RollTestTags.EXPECTED).assertDoesNotExist()
  }

  @Test
  fun `the refusal says how many of how many never stopped`() {
    // Three of twenty, seventeen counted — the numbers are what makes it an
    // account of a roll rather than an apology.
    compose.setContent { StalledPlate(unsettled = 3, read = 17, onCancel = {}) }

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
    compose.setContent { StalledPlate(unsettled = 3, read = 17, onCancel = {}) }

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

  private companion object {
    /** Far enough past the fill's end to be clear of its anti-aliased edge. */
    const val PAST_THE_FILL = 4f
  }
}
