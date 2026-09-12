package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.model.CoreModelModule
import de.drehtuer.dinfinity.core.stats.CoreStatsModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":data", DataModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(CoreModelModule.PATH in DataModule.DEPENDS_ON)
        assertTrue(CoreStatsModule.PATH in DataModule.DEPENDS_ON)
        assertEquals(2, DataModule.DEPENDS_ON.size)
    }
}
