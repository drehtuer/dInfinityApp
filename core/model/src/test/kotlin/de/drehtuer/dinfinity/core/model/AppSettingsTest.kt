package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [AppSettings] is the whole of what Settings owns, so what matters about it is
 * that it behaves as a value: two settings with the same accent are the same
 * settings, and changing one field leaves the rest alone. Compose relies on
 * exactly that to decide whether to recompose.
 */
class AppSettingsTest {
  @Test
  fun `a fresh AppSettings uses the default accent`() {
    assertEquals(AccentColor.Default, AppSettings().accentColor)
  }

  @Test
  fun `settings with the same accent are equal and hash alike`() {
    val one = AppSettings(accentColor = AccentColor.Sky)
    val other = AppSettings(accentColor = AccentColor.Sky)
    assertEquals(one, other)
    assertEquals(one.hashCode(), other.hashCode())
  }

  @Test
  fun `a different accent makes different settings`() {
    assertNotEquals(AppSettings(AccentColor.Sky), AppSettings(AccentColor.Moss))
  }

  @Test
  fun `copy changes the accent and nothing else`() {
    val before = AppSettings(accentColor = AccentColor.Amber)
    val after = before.copy(accentColor = AccentColor.Violet)
    assertEquals(AccentColor.Violet, after.accentColor)
    assertEquals(AccentColor.Amber, before.accentColor)
  }

  /** Only so a log line or a test failure names the accent rather than an address. */
  @Test
  fun `toString names the accent`() {
    assertTrue(AppSettings(AccentColor.Moss).toString().contains("Moss"))
  }
}
