package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.CoreModelModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationApiModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":simulation:api", SimulationApiModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(CoreModelModule.PATH in SimulationApiModule.DEPENDS_ON)
    assertEquals(1, SimulationApiModule.DEPENDS_ON.size)
  }
}
