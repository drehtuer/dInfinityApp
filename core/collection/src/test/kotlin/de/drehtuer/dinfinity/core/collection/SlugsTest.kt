package de.drehtuer.dinfinity.core.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The ids a collection uses (`docs/dice-notation.md`, "Export and import"). */
class SlugsTest {
  @Test
  fun `a plain name becomes a plain id`() {
    assertEquals("thorin", Slugs.of("Thorin"))
  }

  @Test
  fun `spaces and punctuation become one dash`() {
    assertEquals("curse-of-strahd", Slugs.of("Curse of Strahd"))
    assertEquals("d-d", Slugs.of("D&D"))
  }

  @Test
  fun `dashes are not left hanging off either end`() {
    assertEquals("thorin", Slugs.of("  Thorin!  "))
  }

  @Test
  fun `a name with nothing sluggable in it falls back rather than becoming empty`() {
    // An id is for machines; the name beside it is what a person reads.
    assertEquals("group", Slugs.of("🎲🎲"))
    assertEquals("group", Slugs.of("日本語"))
  }

  @Test
  fun `a taken id gets a number, and the same input gives the same number`() {
    assertEquals("thorin-2", Slugs.of("Thorin", taken = setOf("thorin")))
    assertEquals("thorin-3", Slugs.of("Thorin", taken = setOf("thorin", "thorin-2")))
  }

  @Test
  fun `an id is never longer than an id`() {
    assertTrue(Slugs.valid(Slugs.of("x".repeat(500))))
  }

  @Test
  fun `what counts as an id`() {
    assertTrue(Slugs.valid("thorin"))
    assertTrue(Slugs.valid("thorin-2"))
    assertTrue(Slugs.valid("d_and_d"))
    assertTrue(Slugs.valid("2nd-edition"))

    assertFalse(Slugs.valid(""))
    assertFalse(Slugs.valid("Thorin"))
    assertFalse(Slugs.valid("two words"))
    assertFalse(Slugs.valid("-leading"))
    assertFalse(Slugs.valid("x".repeat(100)))
  }
}
