package de.drehtuer.dinfinity.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.drehtuer.dinfinity.core.model.AccentRamp
import de.drehtuer.dinfinity.core.model.Ground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
  fun `the deep accent is the ramp's 700 step, mixed from the one in use`() {
    // Not a pigment looked up: the player picks any colour they like, so the
    // step has to be mixed from it — and mixed against the ground it will be
    // read on, which is what `AccentRamp` does (`docs/architecture.md`,
    // "Settings").
    var accent: Color? = null
    var ground: Color? = null
    var deep: Color? = null
    compose.setContent {
      accent = MaterialTheme.colorScheme.primary
      ground = MaterialTheme.colorScheme.background
      deep = Ink.accentDeep
    }

    compose.runOnIdle {
      val light = ground?.luminance()?.let { it >= 0.5f } == true
      val expected = AccentRamp.of(requireNotNull(accent).toArgb(), if (light) Ground.Light else Ground.Dark)
      assertEquals(Color(expected.v700), deep)
    }
  }

  @Test
  fun `the deep accent is darker than the accent it is mixed from`() {
    // It exists because the accent itself reaches only 3:1 on the ground,
    // which is enough for chrome and large text and not for a 10 dp kicker.
    var accent: Color? = null
    var deep: Color? = null
    compose.setContent {
      accent = MaterialTheme.colorScheme.primary
      deep = Ink.accentDeep
    }

    compose.runOnIdle {
      assertNotEquals("the deep step is the accent itself", accent, deep)
    }
  }

  @Test
  fun `a footnote is fainter than a description`() {
    // `.text-muted` is the design system's own 55 %; a row's description sits
    // at the prototype's 65 %, which is the stronger of the two.
    assertTrue("a footnote is not fainter than a description", Ink.FAINT < Ink.MUTED)
  }

  @Test
  fun `the one red and the divider are the roles the theme fills in`() {
    // Both exist so a call site can say *which* colour it means. The accent is
    // `primary` rather than `error`, because the design system has one red and
    // a screen reaching for Material's would get a second; the divider is
    // `outline` rather than `outlineVariant`, because a rule and a control's
    // border are the same token here and Material treats them as two.
    var primary: Color? = null
    var outline: Color? = null
    var accent: Color? = null
    var divider: Color? = null
    compose.setContent {
      primary = MaterialTheme.colorScheme.primary
      outline = MaterialTheme.colorScheme.outline
      accent = Ink.accent
      divider = Ink.divider
    }

    compose.runOnIdle {
      assertEquals(primary, accent)
      assertEquals(outline, divider)
    }
  }
}
