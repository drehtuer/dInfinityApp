package de.drehtuer.dinfinity

import androidx.compose.ui.graphics.Color
import de.drehtuer.dinfinity.theme.ModernistTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tokens are a transcription of the design system's stylesheet
 * (`design/_ds/modernist-.../styles.css`). These tests fail if the
 * transcription drifts from the values documented there.
 */
class ThemeTest {
  @Test
  fun `palette matches the stylesheet`() {
    assertEquals(Color(0xFFF3F2F2), ModernistTokens.Light.background)
    assertEquals(Color(0xFFEAE9E9), ModernistTokens.Light.surface)
    assertEquals(Color(0xFF201E1D), ModernistTokens.Light.text)
    assertEquals(Color(0xFFEC3013), ModernistTokens.accent)
  }

  @Test
  fun `dark mode swaps ink and ground and keeps the accent`() {
    assertEquals(ModernistTokens.Light.background, ModernistTokens.Dark.text)
    assertEquals(ModernistTokens.Light.text, ModernistTokens.Dark.background)
    assertNotEquals(ModernistTokens.Light.background, ModernistTokens.Dark.background)
  }

  @Test
  fun `nothing has a rounded corner`() {
    assertEquals(0f, ModernistTokens.radius.value, 0f)
  }

  @Test
  fun `the spacing scale is the CSS scale`() {
    val scale = with(ModernistTokens.Space) { listOf(x1, x2, x3, x4, x6, x8).map { it.value } }
    assertEquals(listOf(4f, 8f, 12f, 16f, 24f, 32f), scale)
  }

  @Test
  fun `rules are 2dp, not hairlines`() {
    assertTrue(ModernistTokens.ruleThickness.value >= 2f)
  }

  @Test
  fun `the divider is the ink at 40 percent`() {
    assertEquals(0.4f, ModernistTokens.divider(ModernistTokens.Light.text).alpha, 0.001f)
  }
}
