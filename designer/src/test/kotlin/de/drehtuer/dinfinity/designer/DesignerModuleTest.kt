package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.dicesets.format.DicesetsFormatModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":designer", DesignerModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(DicesetsFormatModule.PATH in DesignerModule.DEPENDS_ON)
        assertEquals(1, DesignerModule.DEPENDS_ON.size)
    }
}
