package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.SimulationApiModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputShakeModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":input:shake", InputShakeModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(SimulationApiModule.PATH in InputShakeModule.DEPENDS_ON)
    assertEquals(1, InputShakeModule.DEPENDS_ON.size)
  }
}
