package de.drehtuer.dinfinity.core.glyphs

import de.drehtuer.dinfinity.core.model.CoreModelModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreGlyphsModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":core:glyphs", CoreGlyphsModule.PATH)
  }

  // Importing the object below only compiles when the Gradle dependency is
  // really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(CoreModelModule.PATH in CoreGlyphsModule.DEPENDS_ON)
    assertEquals(1, CoreGlyphsModule.DEPENDS_ON.size)
  }
}
