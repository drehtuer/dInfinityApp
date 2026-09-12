package de.drehtuer.dinfinity.feature.graph

import de.drehtuer.dinfinity.core.notation.CoreNotationModule
import de.drehtuer.dinfinity.core.probability.CoreProbabilityModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureGraphModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":feature:graph", FeatureGraphModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(CoreNotationModule.PATH in FeatureGraphModule.DEPENDS_ON)
    assertTrue(CoreProbabilityModule.PATH in FeatureGraphModule.DEPENDS_ON)
    assertEquals(2, FeatureGraphModule.DEPENDS_ON.size)
  }
}
