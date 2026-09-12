package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.core.stats.CoreStatsModule
import de.drehtuer.dinfinity.data.DataModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureStatsModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":feature:stats", FeatureStatsModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(CoreStatsModule.PATH in FeatureStatsModule.DEPENDS_ON)
        assertTrue(DataModule.PATH in FeatureStatsModule.DEPENDS_ON)
        assertEquals(2, FeatureStatsModule.DEPENDS_ON.size)
    }
}
