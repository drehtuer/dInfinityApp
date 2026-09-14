package de.drehtuer.dinfinity.core.collection

import kotlin.test.Test
import kotlin.test.assertEquals

/** The module is present, wired into the build, and can see what it depends on. */
class CoreCollectionModuleTest {
  @Test
  fun `the module knows its own path`() {
    assertEquals(":core:collection", CoreCollectionModule.PATH)
  }

  @Test
  fun `it depends on the model and the notation, and reaches both`() {
    assertEquals(listOf(":core:model", ":core:notation"), CoreCollectionModule.DEPENDS_ON)
    // Reaching them is the half a build file cannot prove on its own.
    assertEquals(1, CollectionLimits.FORMAT)
  }
}
