package de.drehtuer.dinfinity.feature.sets

import de.drehtuer.dinfinity.dicesets.format.DicesetsFormatModule
import de.drehtuer.dinfinity.dicesets.install.DicesetsInstallModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSetsModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":feature:sets", FeatureSetsModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(DicesetsFormatModule.PATH in FeatureSetsModule.DEPENDS_ON)
        assertTrue(DicesetsInstallModule.PATH in FeatureSetsModule.DEPENDS_ON)
        assertEquals(2, FeatureSetsModule.DEPENDS_ON.size)
    }
}
