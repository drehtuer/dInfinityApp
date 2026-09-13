package de.drehtuer.dinfinity.fixtures

import de.drehtuer.dinfinity.core.model.CoreModelModule
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFixturesModuleTest {
  @Test
  fun `knows its own Gradle path`() {
    assertEquals(":test-fixtures", TestFixturesModule.PATH)
  }

  @Test
  fun `depends on the model and nothing else`() {
    assertEquals(listOf(CoreModelModule.PATH), TestFixturesModule.DEPENDS_ON)
  }
}
