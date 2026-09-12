package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.SimulationApiModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderFilamentModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":render:filament", RenderFilamentModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(SimulationApiModule.PATH in RenderFilamentModule.DEPENDS_ON)
    assertEquals(1, RenderFilamentModule.DEPENDS_ON.size)
  }
}
