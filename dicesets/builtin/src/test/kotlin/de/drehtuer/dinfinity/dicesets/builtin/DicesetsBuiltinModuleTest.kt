package de.drehtuer.dinfinity.dicesets.builtin

import de.drehtuer.dinfinity.dicesets.format.DicesetsFormatModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DicesetsBuiltinModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":dicesets:builtin", DicesetsBuiltinModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(DicesetsFormatModule.PATH in DicesetsBuiltinModule.DEPENDS_ON)
        assertEquals(1, DicesetsBuiltinModule.DEPENDS_ON.size)
    }
}
