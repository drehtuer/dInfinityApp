package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.SimulationApiModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationJoltModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":simulation:jolt", SimulationJoltModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(SimulationApiModule.PATH in SimulationJoltModule.DEPENDS_ON)
    assertEquals(1, SimulationJoltModule.DEPENDS_ON.size)
  }
}
