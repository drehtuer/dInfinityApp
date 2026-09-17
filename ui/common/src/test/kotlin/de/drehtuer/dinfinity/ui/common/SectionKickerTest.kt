package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

  @Test
  fun `a kicker follows the words when they change`() {
    // A kicker is not always a fixed label: History heads each block with the
    // name of a session, which changes as the list is walked. So it has to
    // recompose rather than hold the first text it was given — and this is
    // also what exercises the skip path a `@Composable` is compiled with.
    val heading = mutableStateOf("Thorin")
    compose.setContent {
      SectionKicker(text = heading.value, modifier = Modifier.testTag("kicker"))
    }

    compose.onNodeWithText("Thorin").assertExists()

    heading.value = "Ezren"

    compose.onNodeWithText("Ezren").assertExists()
    compose.onNodeWithText("Thorin").assertDoesNotExist()
  }

  @Test
  fun `a kicker takes the modifier it is handed`() {
    // Every screen that draws one supplies its own gutter padding, so the
    // modifier is the parameter that is always passed and never defaulted in
    // real use — and was never passed here.
    compose.setContent {
      SectionKicker(text = "Installed", modifier = Modifier.padding(start = Modernist.x4).testTag("kicker"))
    }

    compose.onNodeWithTag("kicker").assertLeftPositionInRootIsEqualTo(Modernist.x4)
  }
}
