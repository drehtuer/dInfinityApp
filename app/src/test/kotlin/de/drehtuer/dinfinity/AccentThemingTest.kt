package de.drehtuer.dinfinity

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.theme.DInfinityTheme
import de.drehtuer.dinfinity.theme.LocalModernistColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The accent the player picks has to reach the whole composition — both the
 * Modernist tokens that new UI reads and Material's `primary`, which is what
 * the feature modules style themselves from.
 *
 * The theme is composed once and then driven by state, because a compose rule
 * accepts `setContent` only once; every case here is a recomposition, which is
 * also what happens in the app when the setting changes.
 */
@RunWith(RobolectricTestRunner::class)
class AccentThemingTest {
  @get:Rule
  val compose = createComposeRule()

  private data class Painted(
    val accent: Color,
    val pressed: Color,
    val primary: Color,
    val background: Color,
    val text: Color,
  )

  private val accent = mutableStateOf(AccentColor.Default)
  private val dark = mutableStateOf(false)
  private lateinit var painted: Painted

  private fun start() {
    compose.setContent {
      DInfinityTheme(darkTheme = dark.value, accent = accent.value) {
        val modernist = LocalModernistColors.current
        val primary = MaterialTheme.colorScheme.primary
        SideEffect {
          painted =
            Painted(
              accent = modernist.accent,
              pressed = modernist.accentPressed,
              primary = primary,
              background = modernist.background,
              text = modernist.text,
            )
        }
      }
    }
    compose.waitForIdle()
  }

  private fun paint(
    chosen: AccentColor,
    darkTheme: Boolean = false,
  ): Painted {
    accent.value = chosen
    dark.value = darkTheme
    compose.waitForIdle()
    return painted
  }

  @Test
  fun `the chosen accent reaches the tokens and Material's primary`() {
    start()
    AccentColor.entries.forEach { chosen ->
      val result = paint(chosen)
      assertEquals("${chosen.id} did not reach the tokens", Color(chosen.argb), result.accent)
      assertEquals("${chosen.id} did not reach Material", Color(chosen.argb), result.primary)
    }
  }

  @Test
  fun `each ground gets its own pressed step`() {
    start()
    AccentColor.entries.forEach { chosen ->
      assertEquals(Color(chosen.pressedOnLightArgb), paint(chosen, darkTheme = false).pressed)
      assertEquals(Color(chosen.pressedOnDarkArgb), paint(chosen, darkTheme = true).pressed)
    }
  }

  @Test
  fun `the default accent is the design system's own`() {
    start()
    assertEquals(Color(0xFFEC3013), paint(AccentColor.Default).accent)
  }

  @Test
  fun `choosing a different accent actually changes the painting`() {
    start()
    assertNotEquals(paint(AccentColor.Vermilion).accent, paint(AccentColor.Sky).accent)
  }

  /** The ground is the design's; only the accent is the player's. */
  @Test
  fun `the accent does not disturb the ground`() {
    start()
    AccentColor.entries.forEach { chosen ->
      val result = paint(chosen)
      assertEquals(Color(0xFFF3F2F2), result.background)
      assertEquals(Color(0xFF201E1D), result.text)
    }
  }
}
