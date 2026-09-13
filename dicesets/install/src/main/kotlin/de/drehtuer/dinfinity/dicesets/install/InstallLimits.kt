package de.drehtuer.dinfinity.dicesets.install

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What an install is allowed to cost before it is refused
 * (`docs/dice-sets.md`, "Installing from a URL or file"; `SECURITY.md`).
 *
 * Every one of these is a bound on something a stranger chose: how big the
 * archive is, how long the download takes, how much it expands to, how many
 * files it holds. They are checked *while* the archive is being read, not
 * afterwards — an archive that expands to a terabyte has to be refused at the
 * megabyte where it becomes obvious, not at the terabyte where it is too late.
 */
object InstallLimits {
  /** The most that will be downloaded. */
  const val MAX_DOWNLOAD_BYTES: Long = 64L shl 20

  /** The most an archive may expand to, which is the zip-bomb bound. */
  const val MAX_EXTRACTED_BYTES: Long = 64L shl 20

  /** And the most files it may hold. */
  const val MAX_ENTRIES: Int = 500

  /** How long a download may take before it is abandoned. */
  val TIMEOUT: Duration = 60.seconds

  /** How many redirects are followed, and every one of them must be `https`. */
  const val MAX_REDIRECTS: Int = 5

  /** The one scheme an install may use. Plain `http` is refused. */
  const val SCHEME: String = "https"

  /** What may be extracted at all. Anything else is skipped, not written. */
  val ALLOWED_EXTENSIONS: Set<String> = setOf("toml", "png", "webp", "obj", "md", "txt")

  /** What a plain archive URL has to end in to be treated as one. */
  val ARCHIVE_SUFFIXES: List<String> = listOf(".zip", ".tar.gz", ".tgz")
}
