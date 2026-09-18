package de.drehtuer.dinfinity

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.drehtuer.dinfinity.core.model.AccentChoice
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AccentRamp
import de.drehtuer.dinfinity.core.model.Contrast
import de.drehtuer.dinfinity.core.model.Ground
import de.drehtuer.dinfinity.theme.DInfinityTheme
import de.drehtuer.dinfinity.theme.LocalModernistColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

  private val accent = mutableStateOf<AccentChoice>(AccentColor.Default)
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
    chosen: AccentChoice,
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
      val painted = Color(AccentRamp.clamp(chosen.argb, Ground.Light))
      val result = paint(chosen)
      assertEquals("${chosen.id} did not reach the tokens", painted, result.accent)
      assertEquals("${chosen.id} did not reach Material", painted, result.primary)
    }
  }

  /**
   * A colour from the picker reaches the theme exactly as a preset does —
   * there is no second path for it, which is what keeps the clamp unavoidable.
   */
  @Test
  fun `a colour of the player's own reaches the tokens too`() {
    start()
    val chosen = AccentChoice.Custom(0xFF2B5AA8.toInt())
    assertEquals(Color(AccentRamp.clamp(chosen.argb, Ground.Light)), paint(chosen).accent)
    assertEquals(Color(AccentRamp.clamp(chosen.argb, Ground.Dark)), paint(chosen, darkTheme = true).accent)
  }

  /**
   * The clamp is in the theme, not in Settings: **what is painted clears 3:1
   * against the ground it is painted on**, whatever was chosen. A pale yellow
   * is what this is for, and it is the one thing that used to be impossible to
   * say once the palette was opened.
   */
  @Test
  fun `whatever is chosen, what is painted can be read on the ground it is painted on`() {
    start()
    listOf(0xFFFFFF00, 0xFFFFFFFF, 0xFF000000, 0xFF201E1D, 0xFFF3F2F2).forEach { colour ->
      val chosen = AccentChoice.Custom(colour.toInt())
      val onLight = paint(chosen, darkTheme = false)
      val onDark = paint(chosen, darkTheme = true)
      assertTrue(
        "${chosen.hex} on paper",
        Contrast.meets(onLight.accent.toArgb(), onLight.background.toArgb(), Contrast.COMPONENT),
      )
      assertTrue(
        "${chosen.hex} on a dark page",
        Contrast.meets(onDark.accent.toArgb(), onDark.background.toArgb(), Contrast.COMPONENT),
      )
    }
  }

  @Test
  fun `each ground gets its own pressed step`() {
    start()
    AccentColor.entries.forEach { chosen ->
      assertEquals(Color(AccentRamp.of(chosen.argb, Ground.Light).v700), paint(chosen, darkTheme = false).pressed)
      assertEquals(Color(AccentRamp.of(chosen.argb, Ground.Dark).v700), paint(chosen, darkTheme = true).pressed)
    }
  }

  @Test
  fun `the default accent is the design's light blue, clamped onto paper`() {
    start()
    assertEquals(Color(AccentRamp.clamp(0xFF38A8DC.toInt(), Ground.Light)), paint(AccentColor.Default).accent)
    assertEquals(Color(0xFF38A8DC), paint(AccentColor.Default, darkTheme = true).accent)
  }

  @Test
  fun `choosing a different accent actually changes the painting`() {
    start()
    assertNotEquals(paint(AccentColor.ModernistRed).accent, paint(AccentColor.LightBlue).accent)
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
