package de.drehtuer.dinfinity.feature.settings

import de.drehtuer.dinfinity.data.DataModule
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
        assertEquals(1, FeatureSettingsModule.DEPENDS_ON.size)
    }
}
