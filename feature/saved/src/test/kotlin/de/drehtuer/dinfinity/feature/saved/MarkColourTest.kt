package de.drehtuer.dinfinity.feature.saved

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.drehtuer.dinfinity.core.model.AccentRamp
import de.drehtuer.dinfinity.core.model.Ground
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The colour a mark is actually printed in (`docs/dice-notation.md`, "Saved
 * rolls"): chosen by the player, then kept readable on the page it lands on.
 */
@RunWith(RobolectricTestRunner::class)
class MarkColourTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a colour is clamped against the page it is printed on`() {
    // `ink` at night and `bone` on paper are each a mark that is simply not
    // there; each is left alone on the other ground, where it reads.
    val drawn = mutableMapOf<String, Color>()
    compose.setContent {
      MaterialTheme(colorScheme = darkColorScheme()) {
        drawn["ink at night"] = markColour(RollColour.Ink.argb)
      }
      MaterialTheme(colorScheme = lightColorScheme()) {
        drawn["bone on paper"] = markColour(RollColour.Bone.argb)
      }
    }
    compose.waitForIdle()

    assertEquals(Color(AccentRamp.clamp(RollColour.Ink.argb, Ground.Dark)), drawn["ink at night"])
    assertEquals(Color(AccentRamp.clamp(RollColour.Bone.argb, Ground.Light)), drawn["bone on paper"])
  }

  @Test
  fun `a roll with no colour of its own wears the accent`() {
    val scheme = darkColorScheme()
    var drawn: Color? = null
    compose.setContent {
      MaterialTheme(colorScheme = scheme) { drawn = markColour(null) }
    }
    compose.waitForIdle()

    assertEquals(scheme.primary, drawn)
  }
}
