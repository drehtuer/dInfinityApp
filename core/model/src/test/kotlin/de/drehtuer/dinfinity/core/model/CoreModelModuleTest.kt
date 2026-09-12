package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreModelModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":core:model", CoreModelModule.PATH)
    }

    @Test
    fun `depends on nothing`() {
        assertTrue(CoreModelModule.DEPENDS_ON.isEmpty())
    }
}
