package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.dicesets.install.ArchiveStamps
import de.drehtuer.dinfinity.feature.sets.LatestCommit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whether the archive a set came from has changed
 * (`docs/dice-sets.md`, "Updates").
 *
 * The other half of [CommitLookup], and the same shape: the rule about what
 * may be asked and what may be read out of the answer is [ArchiveStamps]'s,
 * and what is here is the mapping onto the one word a list of sets can show.
 *
 * It lives in `:app` for the reason its siblings do — an HTTP client is the
 * platform's, and the presenter takes this as a function.
 *
 * **Every answer but a stamp is [LatestCommit.Unknown]**, deliberately. A
 * server that is down, a server that sends neither header, and a link that is
 * not `https` are one sentence to somebody looking at a list — *nothing can be
 * said about this set* — and telling them apart would be three ways of saying
 * it.
 */
class ArchiveLookup(
  private val stamps: (String) -> ArchiveStamps.Result = ArchiveStamps()::of,
) {
  /** What [source]'s server says about the file now, or that nothing can be said. */
  suspend fun latest(source: String): LatestCommit =
    withContext(Dispatchers.IO) {
      when (val result = stamps(source)) {
        ArchiveStamps.Result.Silent -> LatestCommit.Unknown
        is ArchiveStamps.Result.Stamped ->
          LatestCommit.Stamped(etag = result.etag, lastModified = result.lastModified)
      }
    }
}

/**
 * Which question to ask about a set, and the answer
 * (`docs/dice-sets.md`, "Updates").
 *
 * **What the install recorded decides it**, not the URL read a second time: a
 * set with a commit against it came from a forge and is told apart by commits;
 * anything else is an archive and is told apart by what its server says about
 * the file. Re-parsing the link would be a second place for "is this a forge"
 * to be answered, and two answers to that question is one too many.
 *
 * A top-level function rather than a method on either lookup, so the decision
 * it makes can be tested without a network — which is the whole of it.
 */
internal suspend fun latestOf(
  source: String,
  commit: String?,
  commits: suspend (String) -> LatestCommit,
  stamps: suspend (String) -> LatestCommit,
): LatestCommit = if (commit != null) commits(source) else stamps(source)
