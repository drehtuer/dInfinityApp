package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Light, dark, or whatever the phone is doing (design option 1q). */
class AppearanceTest {
  @Test
  fun `following the system means following the system, either way`() {
    assertTrue(Appearance.System.isDark(systemIsDark = true))
    assertFalse(Appearance.System.isDark(systemIsDark = false))
  }

  @Test
  fun `a choice is a choice, whatever the phone is doing`() {
    assertTrue(Appearance.Dark.isDark(systemIsDark = false))
    assertFalse(Appearance.Light.isDark(systemIsDark = true))
  }

  @Test
  fun `a stored choice comes back`() {
    Appearance.entries.forEach { assertEquals(it, Appearance.of(it.id)) }
  }

  @Test
  fun `anything unrecognised follows the system`() {
    // A preferences file written by a newer version, or edited by hand. The
    // app should go on working rather than refuse to start.
    assertEquals(Appearance.System, Appearance.of("sepia"))
    assertEquals(Appearance.System, Appearance.of(null))
    assertEquals(Appearance.System, Appearance.of(""))
  }
}
