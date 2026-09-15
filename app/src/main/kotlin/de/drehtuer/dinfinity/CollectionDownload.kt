package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.collection.CollectionLimits
import de.drehtuer.dinfinity.dicesets.install.PackageFetcher
import de.drehtuer.dinfinity.feature.saved.Fetched
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetching a saved-roll collection from a link
 * (`docs/dice-notation.md`, "Export and import"; `SECURITY.md`).
 *
 * **This is the app's only outward request.** Everything else it does happens
 * on the phone, so the rules a stranger's server is held to matter more here
 * than the code does:
 *
 * - it goes through [PackageFetcher], which is the one downloader in the app
 *   and already refuses anything that is not `https`, refuses a redirect that
 *   would leave `https`, and counts the bytes that actually arrive rather than
 *   believing a `Content-Length`;
 * - it is capped at [CollectionLimits.MAX_BYTES], the same megabyte the reader
 *   refuses a file above, so a server cannot spend somebody's data allowance
 *   proving that it should not have;
 * - and what comes back goes through `CollectionReader` exactly as a file
 *   does. There is one validator and no path around it (`.claude/CLAUDE.md`).
 *
 * It lives in `:app` rather than in `feature/saved` because it is the platform
 * half — a cache directory and an HTTP client — and the presenter takes it as
 * a function. A screen that draws a list of saved rolls should not have to
 * carry an HTTP client to do it (`docs/architecture.md`).
 */
class CollectionDownload(
  private val cacheDir: File,
  private val fetcher: PackageFetcher = PackageFetcher(),
) {
  /** The text at [url], or why it did not arrive. */
  suspend fun fetch(url: String): Fetched =
    withContext(Dispatchers.IO) {
      val into = File(cacheDir, DIRECTORY).apply { mkdirs() }
      when (val result = fetcher.fetch(url, into, CollectionLimits.MAX_BYTES.toLong())) {
        is PackageFetcher.Result.Failed -> Fetched.Failed(result.reason)
        // A collection is a megabyte at most and arrives in one breath, so
        // nothing offers to stop it and nothing here asks to be stopped.
        // Reaching this would mean the fetcher gave up for a reason it was
        // never given, which is worth saying rather than swallowing.
        PackageFetcher.Result.Cancelled -> Fetched.Failed("the download was stopped")
        is PackageFetcher.Result.Downloaded -> read(result.file)
      }
    }

  /**
   * The downloaded bytes as text, and then the file goes.
   *
   * Deleted whether or not it read, because it is somebody else's data sitting
   * in a cache and the collection it may contain is already in the database by
   * the time anybody would want it again.
   */
  private fun read(file: File): Fetched =
    try {
      Fetched.Text(file.readText())
    } catch (failure: java.io.IOException) {
      Fetched.Failed(failure.message ?: "the download could not be read")
    } finally {
      if (!file.delete()) file.deleteOnExit()
    }

  private companion object {
    /** Kept apart from installed packages: a collection is not a dice set. */
    const val DIRECTORY = "collections"
  }
}
