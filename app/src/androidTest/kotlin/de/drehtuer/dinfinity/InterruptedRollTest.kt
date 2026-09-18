package de.drehtuer.dinfinity

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.feature.roll.RollTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A roll that is interrupted finishes or is given up, and never stops half way
 * (`docs/TODO.md`, Step 5.3).
 *
 * The four interruptions that item names are a call, backgrounding, rotation
 * and low memory. Three of them reach an app as lifecycle events and are
 * delivered here as lifecycle events; the fourth is the process being killed,
 * which no test can survive to report on and which is covered by there being
 * nothing to leave half-written (`StatisticsRepository` writes a roll in one
 * transaction or not at all).
 *
 * **What is actually at stake is the frames.** The frame callback is what steps
 * the simulation, so a roll that stops being asked for frames is a roll that
 * stops — never read, never reported, never over, with the screen on "Rolling…"
 * for good. That the roll thread's `Choreographer` goes on delivering vsync to
 * a backgrounded process is an assumption the whole design rests on and that
 * nothing has ever checked. This checks it on the phone, which is the only
 * place the question means anything: the JVM tests drive the clock themselves.
 */
@RunWith(AndroidJUnit4::class)
class InterruptedRollTest {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun aRollThatIsBackgroundedMidThrowStillLands() {
    throwSomething()

    // Out of sight, the way a call or the home button leaves it.
    compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    compose.waitForIdle()
    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    // Not "the screen came back" — the dice have to have come to rest. A roll
    // that stopped being stepped sits on "Rolling…" and this is what says so.
    compose.waitUntil(SETTLE) { landed() }
    resolved()
  }

  @Test
  fun aRollGoesOnBeingSteppedWhileTheAppIsAway() {
    // The same question asked the other way round: the dice must land *while*
    // the app is away rather than only once it is looked at again. A roll
    // resumed by being watched would pass the test above and still be a roll
    // that froze in the player's pocket.
    throwSomething()

    // Waited out rather than polled: there is no composition to ask while the
    // activity is stopped, which is the same reason this could never have been
    // a Robolectric test.
    compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    Thread.sleep(SETTLE)

    // And read the instant it is back, with nothing waited for. A roll that had
    // frozen in the player's pocket would still say "Rolling…" here and would
    // need the frames that resuming brings before it could say anything else.
    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    compose.waitForIdle()
    resolved()
  }

  @Test
  fun aRollSurvivesTheScreenBeingStoppedAndStartedRepeatedly() {
    // A phone locked and unlocked and locked again while the dice are in the
    // air: each of those takes the surface away and hands one back, and a tray
    // that mishandled the second would be a black tray or a crash in the
    // driver rather than a slow one.
    throwSomething()

    repeat(THREE) {
      compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
      compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    compose.waitUntil(SETTLE) { landed() }
    compose.onNodeWithTag(RollTestTags.TRAY).assertIsDisplayed()
    resolved()
  }

  @Test
  fun aRollThatTheActivityIsRecreatedUnderIsGivenUpRatherThanLeftHalfWay() {
    // Recreation is what a config change outside the declared list does, and
    // what "don't keep activities" does to every one of them. The roll does not
    // survive it and is not meant to — a throw the player walked away from
    // never landed — so what this asserts is that the screen comes back usable
    // rather than stuck on a roll that is still notionally in the air.
    throwSomething()

    compose.activityRule.scenario.recreate()
    compose.waitForIdle()

    compose.onNodeWithTag(RollTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.TRAY).assertIsDisplayed()
    // Throwable again, which a screen wedged on "Rolling…" would not be.
    compose.waitUntil(SETTLE) { throwable() }
  }

  /** Types a formula and throws it, leaving the dice in the air. */
  private fun throwSomething() {
    compose.onNodeWithTag(RollTestTags.SCREEN).assertIsDisplayed()
    // A phone that has never had this app on it opens on the welcome, and a
    // phone that has does not. Which of those the test is running against is
    // not the test's business, so it handles both.
    if (showing(RollTestTags.WELCOME)) {
      compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()
      compose.waitForIdle()
    }
    // The field is not the line: tapping the line is what opens the keyboard,
    // and the field cannot be typed into before it does.
    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(FORMULA)
    shake()
    compose.waitUntil(SETTLE) { showing(RollTestTags.ROLLING) || landed() }
  }

  /**
   * Throws the dice the only way the screen offers that is not a hand: the
   * table's custom accessibility action.
   *
   * There is no Roll button any more, and an instrumented test cannot shake a
   * phone, so this is how the suite throws. It is the same call the shake
   * source makes (`docs/architecture.md`, "Accessibility").
   */
  private fun shake() {
    val throwThem =
      compose
        .onNodeWithTag(RollTestTags.TRAY)
        .fetchSemanticsNode()
        .config[SemanticsActions.CustomActions]
        .single()
    compose.runOnUiThread { throwThem.action() }
    compose.waitForIdle()
  }

  /** True once the table will take another throw, which is what a usable screen is. */
  private fun throwable(): Boolean =
    compose
      .onAllNodes(hasTag(RollTestTags.TRAY))
      .fetchSemanticsNodes()
      .any { node -> node.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.isNotEmpty() }

  private fun showing(tag: String): Boolean = compose.onAllNodes(hasTag(tag)).fetchSemanticsNodes().isNotEmpty()

  /**
   * The roll came to an answer, and is not still notionally in the air.
   *
   * Both halves, because "never half-resolved" is the claim: a total that
   * arrived while the screen still says "Rolling…" would be a roll that
   * reported twice, and a screen that says neither is one that stopped.
   *
   * The total is asserted to *exist* rather than to be displayed. The keyboard
   * is up — the formula was typed to get here — and where the sheet sits under
   * it is a layout question, where this is asking whether the dice stopped.
   */
  private fun resolved() {
    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
    compose.onNodeWithTag(RollTestTags.ROLLING).assertDoesNotExist()
  }

  /** Whether the dice have come to rest and a total is on the screen. */
  private fun landed(): Boolean = showing(RollTestTags.TOTAL)

  private fun hasTag(tag: String) =
    androidx.compose.ui.test.SemanticsMatcher.expectValue(
      androidx.compose.ui.semantics.SemanticsProperties.TestTag,
      tag,
    )

  private companion object {
    /**
     * Enough dice that the throw is still going when the app is put away, and
     * few enough that it lands well inside the twelve-second cap.
     */
    const val FORMULA = "20d6"

    /** The cap is twelve seconds; this is that with room for a busy phone. */
    const val SETTLE = 20_000L

    const val THREE = 3
  }
}
