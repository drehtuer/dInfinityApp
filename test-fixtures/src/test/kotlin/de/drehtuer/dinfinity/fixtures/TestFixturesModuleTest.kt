package de.drehtuer.dinfinity.fixtures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestFixturesModuleTest {
    @Test
    fun `knows its own Gradle path`() {
        assertEquals(":test-fixtures", TestFixturesModule.PATH)
    }

    @Test
    fun `depends on nothing`() {
        assertTrue(TestFixturesModule.DEPENDS_ON.isEmpty())
    }
}
