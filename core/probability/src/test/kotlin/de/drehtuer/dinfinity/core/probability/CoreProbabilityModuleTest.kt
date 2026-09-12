package de.drehtuer.dinfinity.core.probability

import de.drehtuer.dinfinity.core.model.CoreModelModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreProbabilityModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":core:probability", CoreProbabilityModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(CoreModelModule.PATH in CoreProbabilityModule.DEPENDS_ON)
        assertEquals(1, CoreProbabilityModule.DEPENDS_ON.size)
    }
}
