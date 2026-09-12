package de.drehtuer.dinfinity.feature.saved

import de.drehtuer.dinfinity.core.notation.CoreNotationModule
import de.drehtuer.dinfinity.data.DataModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSavedModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":feature:saved", FeatureSavedModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(CoreNotationModule.PATH in FeatureSavedModule.DEPENDS_ON)
        assertTrue(DataModule.PATH in FeatureSavedModule.DEPENDS_ON)
        assertEquals(2, FeatureSavedModule.DEPENDS_ON.size)
    }
}
