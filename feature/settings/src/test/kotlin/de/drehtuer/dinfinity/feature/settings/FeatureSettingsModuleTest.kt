package de.drehtuer.dinfinity.feature.settings

import de.drehtuer.dinfinity.core.notation.CoreNotationModule
import de.drehtuer.dinfinity.data.DataModule
import de.drehtuer.dinfinity.simulation.api.SimulationApiModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSettingsModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":feature:settings", FeatureSettingsModule.PATH)
  }

  // Importing the objects below only compiles when the Gradle dependency
  // is really there, so this asserts the declaration matches the build.
  @Test
  fun `reaches every module it depends on`() {
    assertTrue(DataModule.PATH in FeatureSettingsModule.DEPENDS_ON)
    assertTrue(CoreNotationModule.PATH in FeatureSettingsModule.DEPENDS_ON)
    // The developer screen's replay and anomaly log, which are a `ThrowSpec`
    // and a `SimulationOutcome` (`docs/physics-and-rendering.md`).
    assertTrue(SimulationApiModule.PATH in FeatureSettingsModule.DEPENDS_ON)
    assertEquals(3, FeatureSettingsModule.DEPENDS_ON.size)
  }
}
