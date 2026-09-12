package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.CoreModelModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DicesetsFormatModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":dicesets:format", DicesetsFormatModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(CoreModelModule.PATH in DicesetsFormatModule.DEPENDS_ON)
    assertEquals(1, DicesetsFormatModule.DEPENDS_ON.size)
  }
}
