package de.drehtuer.dinfinity.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Secondary copy is the text colour, dimmed — and nothing else.
 *
 * The thing worth asserting is the *pigment*: both weaker inks have to be the
 * same colour as the rest of the screen, so that a theme change carries them
 * with it and no screen quietly prints a colour from outside the palette.
 */
@RunWith(RobolectricTestRunner::class)
class InkTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `both weaker inks are the text colour at their documented alpha`() {
    var text: Color? = null
    var muted: Color? = null
    var faint: Color? = null
    compose.setContent {
      text = MaterialTheme.colorScheme.onBackground
      muted = Ink.muted
      faint = Ink.faint
    }

    compose.runOnIdle {
      assertEquals(text?.copy(alpha = Ink.MUTED), muted)
      assertEquals(text?.copy(alpha = Ink.FAINT), faint)
    }
  }

  @Test
  fun `a footnote is fainter than a description`() {
    // `.text-muted` is the design system's own 55 %; a row's description sits
    // at the prototype's 65 %, which is the stronger of the two.
    assertTrue("a footnote is not fainter than a description", Ink.FAINT < Ink.MUTED)
  }
}
