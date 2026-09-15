package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.dicesets.install.InstallSource
import de.drehtuer.dinfinity.dicesets.install.PackageFetcher
import de.drehtuer.dinfinity.feature.sets.FetchedPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetching a dice set from a link
 * (`docs/dice-sets.md`, "Installing from a URL or file"; `SECURITY.md`).
 *
 * The sibling of [CollectionDownload], and deliberately the same shape: the
 * rules a stranger's server is held to are the interesting part, and they are
 * [PackageFetcher]'s — `https` only, a redirect that would leave `https`
 * refused, the bytes that actually arrive counted rather than a
 * `Content-Length` believed, and a size cap applied while downloading rather
 * than after.
 *
 * The cap here is the archive one, `InstallLimits.MAX_DOWNLOAD_BYTES`, because
 * a dice set is textures and meshes where a saved-roll collection is a page of
 * JSON. What arrives is still only an archive: whether it is a *dice set* is
 * the validator's word, and nothing skips it (`.claude/CLAUDE.md`).
 *
 * A link is read by [InstallSource] first, so the forge URLs the documentation
 * promises actually work: a GitHub, GitLab or Codeberg repository — with or
 * without a `/tree/<ref>` on it — becomes the tarball for that ref, and
 * anything else has to already be a `.zip` or `.tar.gz`. A URL it does not
 * recognise is refused *here*, before a socket is opened, because the sentence
 * somebody needs is "that is not a link this app will fetch" rather than a
 * failure three layers down.
 *
 * It lives in `:app` rather than in `feature/sets` because it is the platform
 * half — a cache directory and an HTTP client — and the presenter takes it as
 * a function (`docs/architecture.md`).
 */
class PackageDownload(
  private val cacheDir: File,
  private val fetcher: PackageFetcher = PackageFetcher(),
) {
  /** The archive at [url] on disk, or why it did not arrive. */
  suspend fun fetch(url: String): FetchedPackage {
    val source = InstallSource.of(url) ?: return FetchedPackage.Failed(notASource(url))
    return withContext(Dispatchers.IO) {
      val into = File(cacheDir, DIRECTORY).apply { mkdirs() }
      when (val result = fetcher.fetch(source.archiveUrl, into)) {
        is PackageFetcher.Result.Failed -> FetchedPackage.Failed(result.reason)
        is PackageFetcher.Result.Downloaded -> FetchedPackage.Archive(result.file)
      }
    }
  }

  /**
   * Why [url] is not somewhere a set can come from.
   *
   * Two different mistakes and two different sentences: a plain `http` link is
   * one somebody can fix by typing an `s`, and anything else is a link that was
   * never going to work.
   */
  private fun notASource(url: String): String =
    if (url.trim().startsWith("http://", ignoreCase = true)) {
      "'${url.trim()}' is not an https link"
    } else {
      "that is not a repository or an archive this app can fetch"
    }

  private companion object {
    /** Kept apart from collections: an archive is not a page of JSON. */
    const val DIRECTORY = "packages"
  }
}
