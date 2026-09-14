package de.drehtuer.dinfinity.core.stats

import de.drehtuer.dinfinity.core.model.CoreModelModule
import de.drehtuer.dinfinity.core.probability.CoreProbabilityModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreStatsModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":core:stats", CoreStatsModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(CoreModelModule.PATH in CoreStatsModule.DEPENDS_ON)
    assertTrue(CoreProbabilityModule.PATH in CoreStatsModule.DEPENDS_ON)
    assertEquals(2, CoreStatsModule.DEPENDS_ON.size)
  }
}
