package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.drehtuer.dinfinity.core.notation.NotationEntry
import de.drehtuer.dinfinity.core.notation.NotationReference
import de.drehtuer.dinfinity.core.notation.NotationSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The notation, looked up in the app (`docs/dice-notation.md`).
 *
 * Whether the reference is *true* is `NotationReferenceTest`'s job and it does
 * it against the real parser. What is left for here is that all of it reaches
 * the screen and that the examples are buttons — because a reference that
 * silently drops half its rows looks exactly like a reference.
 */
@RunWith(RobolectricTestRunner::class)
class NotationScreenTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `every entry of every section is on the screen`() {
    compose.setContent { NotationScreen() }

    NotationReference.entries.forEach { entry ->
      compose
        .onNodeWithTag(NotationTestTags.entryOf(entry.syntax))
        .performScrollTo()
        .assertIsDisplayed()
    }
  }

  @Test
  fun `an entry says what it is and what it means`() {
    val entry = NotationReference.entries.first()
    compose.setContent { NotationScreen() }

    // The merged tree: the row is `clickable`, which merges its children, so
    // the syntax and the meaning are both read off the row itself.
    compose
      .onNodeWithTag(NotationTestTags.entryOf(entry.syntax))
      .performScrollTo()
      .assertTextContains(entry.syntax, substring = true)
    compose
      .onNodeWithTag(NotationTestTags.entryOf(entry.syntax))
      .assertTextContains(entry.meaning, substring = true)
  }

  @Test
  fun `tapping an example hands its formula up`() {
    // The point of the screen: you find out what a modifier does by rolling
    // it, not by reading harder.
    val entry = keepHighest()
    val asked = mutableListOf<String>()
    compose.setContent { NotationScreen(onRoll = { formula -> asked += formula }) }

    compose.onNodeWithTag(NotationTestTags.entryOf(entry.syntax)).performScrollTo().performClick()

    assertEquals(listOf(entry.example), asked)
  }

  @Test
  fun `every example is a button, not decoration`() {
    compose.setContent { NotationScreen() }

    NotationReference.entries.forEach { entry ->
      compose
        .onNodeWithTag(NotationTestTags.entryOf(entry.syntax))
        .performScrollTo()
        .assertHasClickAction()
    }
  }

  @Test
  fun `the limits are on the screen too`() {
    // They are the half of the notation somebody looks up *after* being
    // refused, which is the worst moment to have to leave the app.
    compose.setContent { NotationScreen() }

    compose.onNodeWithTag(NotationTestTags.LIMITS).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `the screen survives being drawn again`() {
    // Recomposition, on purpose. Everything here is drawn from a constant, so
    // a second pass skips every row — and a screen that skipped wrongly would
    // come back empty, which a single-pass test would never see.
    var withMenu by mutableStateOf(false)
    compose.setContent {
      NotationScreen(menu = { if (withMenu) Text("menu") })
    }
    val entry = keepHighest()
    compose.onNodeWithTag(NotationTestTags.entryOf(entry.syntax)).performScrollTo().assertIsDisplayed()

    compose.runOnIdle { withMenu = true }

    compose.onNodeWithText("menu").performScrollTo().assertIsDisplayed()
    compose
      .onNodeWithTag(NotationTestTags.entryOf(entry.syntax))
      .performScrollTo()
      .assertTextContains(entry.meaning, substring = true)
    compose.onNodeWithTag(NotationTestTags.LIMITS).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `an example tapped with nowhere to send it does nothing at all`() {
    // The screen is stateless and has no opinion about what happens next. A
    // caller that supplies no `onRoll` gets a reference that reads rather than
    // one that crashes.
    compose.setContent { NotationScreen(modifier = Modifier.testTag(HOST)) }
    val entry = keepHighest()

    compose.onNodeWithTag(NotationTestTags.entryOf(entry.syntax)).performScrollTo().performClick()

    compose.onNodeWithTag(HOST).assertIsDisplayed()
    compose.onNodeWithTag(NotationTestTags.entryOf(entry.syntax)).assertIsDisplayed()
  }

  @Test
  fun `a recomposition around it that changes nothing leaves it alone`() {
    // The other half of the one above. There the menu changed and the screen
    // had to redraw; here something *beside* it changes and the screen must
    // skip — which is the ordinary case on a screen whose whole content is a
    // constant, and the case where a wrongly-keyed row would vanish.
    var tick by mutableStateOf(0)
    compose.setContent {
      Column {
        Text("tick $tick")
        NotationScreen()
      }
    }
    val entry = keepHighest()

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose
      .onNodeWithTag(NotationTestTags.entryOf(entry.syntax))
      .performScrollTo()
      .assertTextContains(entry.meaning, substring = true)
    compose.onNodeWithTag(NotationTestTags.LIMITS).performScrollTo().assertIsDisplayed()
  }

  /** The advantage row, which is the one anybody opens this screen to find. */
  private fun keepHighest(): NotationEntry =
    NotationReference.sections
      .flatMap(NotationSection::entries)
      .first { it.syntax.startsWith("kh") }

  private companion object {
    /** A tag on the caller's own modifier, to prove it reaches the screen. */
    const val HOST = "notation:host"
  }
}
