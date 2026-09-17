package de.drehtuer.dinfinity.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A kicker says what a block is, in the accent and at the system's smallest
 * size.
 *
 * Two things are worth holding. It prints the words it was given — **not an
 * uppercased copy of them**, because uppercasing in Kotlin is what would
 * change what a screen reader says, and that decision is the designer's
 * (`docs/TODO.md`, Open questions). And it follows the accent the player
 * chose, rather than the design system's own red, which a hard-coded colour
 * would not.
 */
@RunWith(RobolectricTestRunner::class)
class SectionKickerTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a kicker prints the words it was given, in their own case`() {
    compose.setContent { SectionKicker("Install from a URL or file") }

    compose.onNodeWithText("Install from a URL or file").assertExists()
  }

  @Test
  fun `a kicker is the accent, and follows the one the player chose`() {
    var accent: Color? = null
    var used: Color? = null
    compose.setContent {
      accent = MaterialTheme.colorScheme.primary
      used = Ink.accent
      SectionKicker("Installed")
    }

    compose.runOnIdle { assertEquals(accent, used) }
  }

  @Test
  fun `a kicker is smaller than anything the app sets a sentence in`() {
    // The point of the step: it is a label, and a label that competed with
    // body copy for size would be a second heading rather than a kicker.
    assertEquals(true, Modernist.Type.kicker.value < Modernist.Type.caption.value)
  }
}
