package de.drehtuer.dinfinity.dicesets.install

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bounds an extraction runs under (`SECURITY.md`).
 *
 * Worth a test of its own for one reason: the defaults are the *documented*
 * ones. A caller that names no bounds gets the dice set's, so lifting these
 * out of `SafeExtractor` cannot have quietly loosened anything
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 */
class ArchiveLimitsTest {
  @Test
  fun `naming nothing is asking for what a dice set gets`() {
    val limits = ArchiveLimits()

    assertEquals(InstallLimits.MAX_EXTRACTED_BYTES, limits.maxExtractedBytes)
    assertEquals(InstallLimits.MAX_ENTRIES, limits.maxEntries)
    assertEquals(InstallLimits.ALLOWED_EXTENSIONS, limits.allowedExtensions)
  }

  @Test
  fun `naming one bound leaves the others where they were`() {
    val limits = ArchiveLimits(allowedExtensions = setOf("json"))

    assertEquals(setOf("json"), limits.allowedExtensions)
    assertEquals(InstallLimits.MAX_EXTRACTED_BYTES, limits.maxExtractedBytes)
    assertEquals(InstallLimits.MAX_ENTRIES, limits.maxEntries)
  }
}
