package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.collection.CollectionLimits
import de.drehtuer.dinfinity.dicesets.install.ExtractionResult
import de.drehtuer.dinfinity.dicesets.install.InstallSource
import de.drehtuer.dinfinity.dicesets.install.PackageFetcher
import de.drehtuer.dinfinity.dicesets.install.SafeExtractor
import de.drehtuer.dinfinity.feature.saved.Fetched
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetching a saved-roll collection from a link or from a git repository
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
 * **A link to a git repository is the same journey with an unpacking in the
 * middle.** Which repository is [InstallSource]'s to say — the same resolution
 * a dice set's link goes through, so a forge is recognised in one place
 * (`docs/dice-sets.md`) — and the tarball it names is fetched by the same
 * downloader under the same cap and unpacked by the same hardened
 * [SafeExtractor], looking for [CollectionInRepository]. What comes out is
 * text, and from there it is a pasted link in every respect.
 *
 * Nothing the app keeps is written at any point here. The archive and
 * everything unpacked from it live in a folder of this fetch's own, which goes
 * whatever happens; the collection only becomes rows in the database after
 * `CollectionReader` has passed it, which is the whole of the ordering in
 * `docs/architecture.md`, "Importing".
 *
 * It lives in `:app` rather than in `feature/saved` because it is the platform
 * half — a cache directory and an HTTP client — and the presenter takes it as
 * a function. A screen that draws a list of saved rolls should not have to
 * carry an HTTP client to do it (`docs/architecture.md`).
 */
class CollectionDownload(
  private val cacheDir: File,
  private val fetcher: PackageFetcher = PackageFetcher(),
  private val extractor: SafeExtractor = SafeExtractor(CollectionInRepository.Limits, CollectionInRepository),
  /**
   * How a pasted link is recognised.
   *
   * A seam only so that the routing can be tested without a forge: the real
   * one is [InstallSource.of] and there is no second implementation of it.
   */
  private val sources: (String) -> InstallSource? = InstallSource::of,
) {
  /** The collection at [url], or why it did not arrive. */
  suspend fun fetch(url: String): Fetched =
    withContext(Dispatchers.IO) {
      val workspace = File(cacheDir, DIRECTORY).let { File(it, "fetch-${System.nanoTime()}") }
      workspace.mkdirs()
      try {
        // A link the installer recognises is an archive — a forge's tarball,
        // or a plain `.zip` somebody keeps a repository in. Anything else is
        // the file itself, which is the commonest case and the shortest path.
        when (val source = sources(url)) {
          null -> downloaded(url, workspace, ::text)
          else -> downloaded(source.archiveUrl, workspace) { unpacked(it, workspace) }
        }
      } finally {
        // Somebody else's data, in a directory nobody empties. Whatever it
        // held is in the database by the time anybody would want it again.
        workspace.deleteRecursively()
      }
    }

  /**
   * Fetches [from] into [into] and hands the file to [then].
   *
   * The cap is the collection's, whether what is coming down the wire is the
   * collection or a repository carrying it. A repository with a megabyte of
   * something else in it is not a repository this app needs to unpack.
   */
  private fun downloaded(
    from: String,
    into: File,
    then: (File) -> Fetched,
  ): Fetched =
    when (val result = fetcher.fetch(from, into, CollectionLimits.MAX_BYTES.toLong())) {
      is PackageFetcher.Result.Failed -> Fetched.Failed(result.reason)
      // A collection is a megabyte at most and arrives in one breath, so
      // nothing offers to stop it and nothing here asks to be stopped.
      // Reaching this would mean the fetcher gave up for a reason it was
      // never given, which is worth saying rather than swallowing.
      PackageFetcher.Result.Cancelled -> Fetched.Failed("the download was stopped")
      is PackageFetcher.Result.Downloaded -> then(result.file)
    }

  /**
   * The one collection in an archive, as text.
   *
   * Every refusal the extractor has is passed on as it stands: they are
   * sentences about what is wrong with the archive — a path that climbs out,
   * more files than a package may hold, an expansion bigger than the file it
   * carries — and the ones about the collection itself say what was looked for
   * and what was there instead ([CollectionInRepository]).
   */
  private fun unpacked(
    archive: File,
    workspace: File,
  ): Fetched =
    when (val extracted = extractor.extract(archive, workspace)) {
      is ExtractionResult.Refused -> Fetched.Failed(extracted.detail)
      is ExtractionResult.Extracted ->
        CollectionInRepository
          .collectionsIn(extracted.root)
          .firstOrNull()
          ?.let(::text)
          ?: Fetched.Failed("the collection could not be found after the repository was unpacked")
    }

  /** The bytes of a file that has arrived, as text. */
  private fun text(file: File): Fetched =
    try {
      Fetched.Text(file.readText())
    } catch (failure: java.io.IOException) {
      Fetched.Failed(failure.message ?: "the download could not be read")
    }

  private companion object {
    /** Kept apart from installed packages: a collection is not a dice set. */
    const val DIRECTORY = "collections"
  }
}
