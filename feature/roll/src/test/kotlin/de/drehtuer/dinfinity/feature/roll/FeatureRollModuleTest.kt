package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.notation.CoreNotationModule
import de.drehtuer.dinfinity.data.DataModule
import de.drehtuer.dinfinity.simulation.api.SimulationApiModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRollModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":feature:roll", FeatureRollModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(CoreNotationModule.PATH in FeatureRollModule.DEPENDS_ON)
        assertTrue(SimulationApiModule.PATH in FeatureRollModule.DEPENDS_ON)
        assertTrue(DataModule.PATH in FeatureRollModule.DEPENDS_ON)
        assertEquals(3, FeatureRollModule.DEPENDS_ON.size)
    }
}
