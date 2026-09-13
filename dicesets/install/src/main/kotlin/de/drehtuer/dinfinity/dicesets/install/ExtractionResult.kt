package de.drehtuer.dinfinity.dicesets.install

/** Why an archive was not extracted (`docs/dice-sets.md`, `SECURITY.md`). */
enum class RejectionReason {
  /** A path that is absolute, climbs out with `..`, or is not a path at all. */
  PathEscapesPackage,

  /** A symbolic link, a hard link, a device node — anything that is not a file. */
  NotAPlainFile,

  /** More files than a package may hold. */
  TooManyEntries,

  /** More bytes than a package may expand to. */
  TooLarge,

  /** The bytes are not an archive of a kind the app reads. */
  Unreadable,

  /** An archive with no `diceset.toml` anywhere in it. */
  NoDiceSet,
}

/** What an extraction came to. */
sealed interface ExtractionResult {
  /**
   * The archive was extracted into a temporary folder.
   *
   * @param root the folder the package's own files are in — the one holding
   *   `diceset.toml`, which is not always the root of the archive: a GitHub
   *   tarball wraps everything in `repo-<sha>/`.
   * @param skipped files that were not on the extension allowlist and were
   *   therefore never written. Not an error: a repository is entitled to
   *   contain a `.gitignore` (`docs/dice-sets.md`).
   */
  data class Extracted(
    val root: java.io.File,
    val files: Int,
    val bytes: Long,
    val skipped: Int = 0,
  ) : ExtractionResult

  /** The archive was refused, and nothing was left behind. */
  data class Refused(
    val reason: RejectionReason,
    val detail: String,
  ) : ExtractionResult
}
