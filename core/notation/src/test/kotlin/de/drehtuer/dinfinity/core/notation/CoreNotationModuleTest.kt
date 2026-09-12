package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.CoreModelModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreNotationModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":core:notation", CoreNotationModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(CoreModelModule.PATH in CoreNotationModule.DEPENDS_ON)
    assertEquals(1, CoreNotationModule.DEPENDS_ON.size)
  }
}
