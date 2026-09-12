package de.drehtuer.dinfinity.feature.tables

import de.drehtuer.dinfinity.data.DataModule
import de.drehtuer.dinfinity.dicesets.format.DicesetsFormatModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureTablesModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":feature:tables", FeatureTablesModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(DicesetsFormatModule.PATH in FeatureTablesModule.DEPENDS_ON)
        assertTrue(DataModule.PATH in FeatureTablesModule.DEPENDS_ON)
        assertEquals(2, FeatureTablesModule.DEPENDS_ON.size)
    }
}
