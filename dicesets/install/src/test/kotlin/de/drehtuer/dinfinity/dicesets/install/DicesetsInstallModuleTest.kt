package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.dicesets.format.DicesetsFormatModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DicesetsInstallModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":dicesets:install", DicesetsInstallModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(DicesetsFormatModule.PATH in DicesetsInstallModule.DEPENDS_ON)
    assertEquals(1, DicesetsInstallModule.DEPENDS_ON.size)
  }
}
