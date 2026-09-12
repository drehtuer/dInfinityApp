package de.drehtuer.dinfinity.feature.designer

import de.drehtuer.dinfinity.designer.DesignerModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureDesignerModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":feature:designer", FeatureDesignerModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(DesignerModule.PATH in FeatureDesignerModule.DEPENDS_ON)
        assertEquals(1, FeatureDesignerModule.DEPENDS_ON.size)
    }
}
