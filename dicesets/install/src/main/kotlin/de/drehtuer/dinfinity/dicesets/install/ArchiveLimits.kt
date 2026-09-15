package de.drehtuer.dinfinity.dicesets.install

/**
 * What unpacking an archive is allowed to cost, and what it may write
 * (`docs/dice-sets.md`, "Installing from a URL or file"; `SECURITY.md`).
 *
 * The same three bounds [SafeExtractor] has always applied, named so that
 * something other than a dice set can be unpacked by the same code under
 * bounds of its own. A saved-roll collection is one file of JSON rather than a
 * folder of textures, so it is unpacked under a far smaller cap and an
 * allowlist of one extension (`docs/dice-notation.md`, "Export and import").
 *
 * A value rather than an interface over [InstallLimits], because there is
 * nothing here to implement: the defaults *are* what a dice set gets, and a
 * caller that wants other bounds says which ones and inherits the rest.
 */
data class ArchiveLimits(
  /** The most an archive may expand to, which is the zip-bomb bound. */
  val maxExtractedBytes: Long = InstallLimits.MAX_EXTRACTED_BYTES,
  /** And the most files it may hold. */
  val maxEntries: Int = InstallLimits.MAX_ENTRIES,
  /** What may be extracted at all. Anything else is skipped, not written. */
  val allowedExtensions: Set<String> = InstallLimits.ALLOWED_EXTENSIONS,
)
