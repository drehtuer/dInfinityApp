package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.simulation.api.SimulationApiModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":feedback", FeedbackModule.PATH)
  }

  // Importing the object below only compiles when the Gradle dependency is
  // really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(SimulationApiModule.PATH in FeedbackModule.DEPENDS_ON)
    assertEquals(1, FeedbackModule.DEPENDS_ON.size)
  }
}
