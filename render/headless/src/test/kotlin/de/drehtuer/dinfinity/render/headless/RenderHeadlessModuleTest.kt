package de.drehtuer.dinfinity.render.headless

import de.drehtuer.dinfinity.simulation.api.SimulationApiModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderHeadlessModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":render:headless", RenderHeadlessModule.PATH)
    }

    // Importing the objects below only compiles when the Gradle dependency
    // is really there, so this asserts the declaration matches the build.
    @Test
    fun `reaches every module it depends on`() {
        assertTrue(SimulationApiModule.PATH in RenderHeadlessModule.DEPENDS_ON)
        assertEquals(1, RenderHeadlessModule.DEPENDS_ON.size)
    }
}
